/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.objectdetection.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.R
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import java.util.LinkedList
import java.util.Locale

@OptIn(ExperimentalCamera2Interop::class)
class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private val TAG = "ObjectDetection"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService
    private var isCalibrated = false

    // Sensor for tilt detection
    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private val gravity = FloatArray(3) { 0f }
    private val GRAVITY_FILTER_ALPHA = 0.8f
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastTiltDeg: Double? = null
    private val TILT_RECALIBRATION_THRESHOLD_DEG = 0.25 // degrees
    private val TILT_DEBOUNCE_MS = 100L
    private var lastRecalibrateTs = 0L

    // Camera movement detection
    private var rotationRate = FloatArray(3) { 0f } // rad/s around x, y, z axes
    private val GYRO_FILTER_ALPHA = 0.8f
    private var cameraMovementLevel = 0 // 0=stable, 1=minor, 2=moderate, 3=major

    // Movement thresholds (rad/s)
    private val MOVEMENT_THRESHOLD_MINOR = 0.009 // ~0.5 deg/s
    private val MOVEMENT_THRESHOLD_MODERATE = 0.087 // ~5 deg/s
    private val MOVEMENT_THRESHOLD_MAJOR = 0.175 // ~10 deg/s

    // Camera height (meters) controlled by new slider. Default 1.5m
    private var cameraHeightMeters: Double = 1.5

    private val rotationListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Handle accelerometer gravity filtering to detect which way camera faces
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // simple low-pass filter to estimate gravity
                gravity[0] = GRAVITY_FILTER_ALPHA * gravity[0] + (1 - GRAVITY_FILTER_ALPHA) * event.values[0]
                gravity[1] = GRAVITY_FILTER_ALPHA * gravity[1] + (1 - GRAVITY_FILTER_ALPHA) * event.values[1]
                gravity[2] = GRAVITY_FILTER_ALPHA * gravity[2] + (1 - GRAVITY_FILTER_ALPHA) * event.values[2]
                return
            }

            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                rotationRate[0] = GYRO_FILTER_ALPHA * rotationRate[0] + (1 - GYRO_FILTER_ALPHA) * event.values[0]
                rotationRate[1] = GYRO_FILTER_ALPHA * rotationRate[1] + (1 - GYRO_FILTER_ALPHA) * event.values[1]
                rotationRate[2] = GYRO_FILTER_ALPHA * rotationRate[2] + (1 - GYRO_FILTER_ALPHA) * event.values[2]

                val rotationMagnitude = kotlin.math.sqrt(
                    rotationRate[0] * rotationRate[0] +
                    rotationRate[1] * rotationRate[1] +
                    rotationRate[2] * rotationRate[2]
                )

                cameraMovementLevel = when {
                    rotationMagnitude > MOVEMENT_THRESHOLD_MAJOR -> 3
                    rotationMagnitude > MOVEMENT_THRESHOLD_MODERATE -> 2
                    rotationMagnitude > MOVEMENT_THRESHOLD_MINOR -> 1
                    else -> 0
                }

                objectDetectorHelper?.setCameraMovementLevel(cameraMovementLevel)

                val minSpeedThreshold = when (cameraMovementLevel) {
                    0 -> 1.0   // Stable: 1.0 m/s (3.6 km/h)
                    1 -> 3.0   // Minor shake: 3.0 m/s (10.8 km/h)
                    2 -> 5.0   // Moderate shake: 5.0 m/s (18 km/h)
                    3 -> 10.0  // Major shake: 10.0 m/s (36 km/h) - speeds are null anyway
                    else -> 1.0
                }
                fragmentCameraBinding.overlay.setMinSpeedThreshold(minSpeedThreshold)

                return
            }

            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            val rotationMatrix = FloatArray(9)
            android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val cameraTiltDeg = calculateCameraTiltAngle(rotationMatrix)

            mainHandler.post {
                try {
                    val activity = activity
                    val tv = activity?.findViewById<android.widget.TextView>(R.id.toolbar_tilt_val)
                    tv?.text = String.format(Locale.US, "%.1f°", cameraTiltDeg)
                } catch (e: Exception) {
                }
            }

            val prev = lastTiltDeg
            lastTiltDeg = cameraTiltDeg
            val now = System.currentTimeMillis()
            if (prev == null || kotlin.math.abs(cameraTiltDeg - prev) >= TILT_RECALIBRATION_THRESHOLD_DEG) {
                if (now - lastRecalibrateTs > TILT_DEBOUNCE_MS) {
                    lastRecalibrateTs = now
                    try {
                        val sensorSize = getSensorPixelArraySize()
                        if (sensorSize != null) {
                            val (useWidth, useHeight) = sensorSize
                            calibrateUsingCamera2Intrinsics(useWidth, useHeight, cameraTiltDeg, cameraHeightMeters)
                        } else {
                            Log.w(TAG, "Sensor pixel array size unavailable -> skipping recalibration on tilt change")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Recalibration on tilt change failed: ${e.message}")
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }

        // Register sensors: accelerometer (for gravity) and rotation vector (for orientation)
        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometerSensor?.also {
            sensorManager?.registerListener(rotationListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationVectorSensor?.also {
            sensorManager?.registerListener(rotationListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscopeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gyroscopeSensor?.also {
            sensorManager?.registerListener(rotationListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()

        // Unregister the sensor listener for both sensors
        sensorManager?.unregisterListener(rotationListener)
    }

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this
        )

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Attach listeners to UI control widgets
        initBottomSheetControls()
        // Update UI to reflect current ObjectDetectorHelper settings (e.g. maxResults)
        updateControlsUi()
    }

    private fun initBottomSheetControls() {
        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    objectDetectorHelper.currentDelegate = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for object detection
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    objectDetectorHelper.currentModel = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // Initialize camera height seekbar (0..1000 cm -> 0.0..10.0 m). Default 150 cm = 1.5m
        try {
            val camSeek = fragmentCameraBinding.bottomSheetLayout.cameraHeightSeek
            val camVal = fragmentCameraBinding.bottomSheetLayout.cameraHeightValue
            camSeek.max = 1000
            camSeek.progress = (cameraHeightMeters * 100.0).toInt()
            camVal.text = String.format(Locale.US, "%.2fm", cameraHeightMeters)
            camSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    cameraHeightMeters = progress / 100.0
                    camVal.text = String.format(Locale.US, "%.2fm", cameraHeightMeters)
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    // Recalibrate using new camera height if camera is bound and sensor size available
                    try {
                        val sensorSize = getSensorPixelArraySize()
                        if (sensorSize != null) {
                            val (w, h) = sensorSize
                            calibrateUsingCamera2Intrinsics(w, h, lastTiltDeg ?: 0.0, cameraHeightMeters)
                        } else {
                            // fallback: call calibrateFromCamera with buffer dims if available
                            // attemptCalibrateIfReady will call calibration later when possible
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Recalibration on camera height change failed: ${e.message}")
                    }
                }
            })
        } catch (e: Exception) {
            // ignore binding errors
        }

        // Initialize grid toggle
        try {
            val gridToggle = fragmentCameraBinding.bottomSheetLayout.gridToggle
            gridToggle.setOnCheckedChangeListener { _, isChecked ->
                fragmentCameraBinding.overlay.setGridEnabled(isChecked)
                if (isChecked) {
                    // Update grid when enabled
                    updateGridVisualization()
                }
                // Force redraw to show/hide grid immediately
                fragmentCameraBinding.overlay.invalidate()
            }
        } catch (e: Exception) {
        }

        try {
            val stationaryToggle = fragmentCameraBinding.bottomSheetLayout.stationaryToggle
            stationaryToggle.setOnCheckedChangeListener { _, isChecked ->
                fragmentCameraBinding.overlay.setShowStationary(isChecked)
                fragmentCameraBinding.overlay.invalidate()
            }
        } catch (e: Exception) {
            // ignore binding errors
        }

        // Initialize threshold seekbar (0..100 -> 0.00..1.00)
        try {
            val thrSeek = fragmentCameraBinding.bottomSheetLayout.thresholdSeek
            val thrVal = fragmentCameraBinding.bottomSheetLayout.thresholdValue
            thrSeek.max = 100
            thrSeek.progress = (objectDetectorHelper.threshold * 100.0f).toInt()
            thrVal.text = String.format(Locale.US, "%.2f", objectDetectorHelper.threshold)
            thrSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress.toFloat() / 100.0f
                    objectDetectorHelper.threshold = v
                    thrVal.text = String.format(Locale.US, "%.2f", v)
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    // Apply new threshold: reset detector
                    updateControlsUi()
                }
            })
        } catch (e: Exception) {
            // ignore binding errors
        }

        // Ensure max results SeekBar updates value live and applies on stop
        try {
            val maxSeek = fragmentCameraBinding.bottomSheetLayout.maxResultsSeek
            val maxVal = fragmentCameraBinding.bottomSheetLayout.maxResultsValue
            maxSeek.max = 50
            maxSeek.progress = objectDetectorHelper.maxResults.coerceIn(1, 50)
            maxSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = if (progress < 1) 1 else progress
                    objectDetectorHelper.maxResults = value
                    maxVal.text = value.toString()
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    updateControlsUi()
                }
            })
        } catch (e: Exception) {
            // ignore
        }
    }

    // Update the values displayed in the bottom sheet. Reset detector.
    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text =
            objectDetectorHelper.maxResults.toString()
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format(Locale.US, "%.2f", objectDetectorHelper.threshold)
        fragmentCameraBinding.bottomSheetLayout.threadsValue.text =
            objectDetectorHelper.numThreads.toString()

        // Keep SeekBar in sync with the displayed value (clamped 1..50)
        try {
            fragmentCameraBinding.bottomSheetLayout.maxResultsSeek.progress =
                objectDetectorHelper.maxResults.coerceIn(1, 50)
            // Keep camera height UI in sync
            fragmentCameraBinding.bottomSheetLayout.cameraHeightSeek.progress = (cameraHeightMeters * 100.0).toInt()
            fragmentCameraBinding.bottomSheetLayout.cameraHeightValue.text = String.format(Locale.US, "%.2fm", cameraHeightMeters)
            // Keep threshold seek in sync
            fragmentCameraBinding.bottomSheetLayout.thresholdSeek.progress = (objectDetectorHelper.threshold * 100.0f).toInt()
        } catch (e: Exception) {
            // If binding/view isn't available yet, ignore — UI will initialize elsewhere
        }

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        objectDetectorHelper.clearObjectDetector()
        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                //.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {

                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                              image.width,
                              image.height,
                              Bitmap.Config.ARGB_8888
                            )

                            // Attempt calibration if camera is already bound; otherwise calibration
                            // will be attempted again after camera is bound.
                            attemptCalibrateIfReady()

                        }

                        detectObjects(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            // After camera is bound, try to calibrate if image dimensions are already known
            attemptCalibrateIfReady()

             // Attach the viewfinder's surface provider to preview use case
             preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private var lastCalibrationRotation: Int? = null

    private fun attemptCalibrateIfReady() {
        if (camera == null) return

        val currentRotation = activity?.windowManager?.defaultDisplay?.rotation
        if (isCalibrated && currentRotation == lastCalibrationRotation) {
            return
        }

        try {
            val sensorSize = getSensorPixelArraySize()
            if (sensorSize == null) {
                Log.w(TAG, "Sensor pixel array size unavailable -> skipping calibration (won't use bitmapBuffer)")
                return
            }
            val (useWidth, useHeight) = sensorSize
            // If no sensor tilt observed yet, default to 0° (straight) after portrait compensation
            calibrateUsingCamera2Intrinsics(useWidth, useHeight, lastTiltDeg ?: 0.0, cameraHeightMeters)
            isCalibrated = true
            lastCalibrationRotation = currentRotation
        } catch (e: Exception) {
            Log.w(TAG, "Camera calibration attempt failed: ${e.message}")
        }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    @OptIn(ExperimentalCamera2Interop::class)
    private fun updateGridVisualization() {
        try {
            val transformer = objectDetectorHelper.getTransformer()
            if (transformer.isCalibrated()) {
                val gridLines = transformer.getGridLines()
                fragmentCameraBinding.overlay.setGridLines(
                    gridLines,
                    transformer.getImageWidth(),
                    transformer.getImageHeight()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update grid visualization: ${e.message}")
        }
    }

    /**
     * Calculate camera tilt angle from rotation matrix.
     * Works in both portrait and landscape orientations.
     *
     * @param rotationMatrix 3x3 rotation matrix from device to world coordinates
     * @return Tilt angle in degrees (negative = up, positive = down, 0 = horizontal)
     */
    private fun calculateCameraTiltAngle(rotationMatrix: FloatArray): Double {
        val displayRotation = activity?.windowManager?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0

        // Camera optical axis in device coordinates
        // For back camera, optical axis points OUT OF THE BACK of device
        // Device coordinates: X=right, Y=top, Z=out of screen (toward user)
        // Back camera points opposite to screen, so along -Z axis
        val cameraAxisDevice = floatArrayOf(0f, 0f, -1f)

        // Transform camera axis to world coordinates
        // rotationMatrix * cameraAxisDevice = cameraAxisWorld
        val cameraAxisWorld = floatArrayOf(
            -rotationMatrix[2], // -R[0][2]
            -rotationMatrix[5], // -R[1][2]
            -rotationMatrix[8]  // -R[2][2]
        )

        // In world coordinates: Z points up (sky)
        // The Z component of camera axis gives us the sine of elevation angle
        // elevation angle = angle from horizontal plane
        // Positive elevation = pointing up, negative = pointing down
        val elevationRad = kotlin.math.asin(cameraAxisWorld[2].toDouble().coerceIn(-1.0, 1.0))
        val elevationDeg = Math.toDegrees(elevationRad)

        // Tilt angle convention: negative = up, positive = down
        // elevation is opposite: positive = up, negative = down
        // So tilt = -elevation
        val tiltDeg = -elevationDeg

        return tiltDeg
    }

    private fun calibrateUsingCamera2Intrinsics(useWidth: Int, useHeight: Int, tiltDeg: Double, cameraHeightMeters: Double) {
        val displayRotationDegrees = when (activity?.windowManager?.defaultDisplay?.rotation) {
            android.view.Surface.ROTATION_0 -> 0
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }

        try {
            if (camera == null) {
                // fallback to buffer-only calibration
                objectDetectorHelper.calibrateFromCamera(useWidth, useHeight, cameraHeightMeters, tiltAngleDeg = tiltDeg, displayRotation = displayRotationDegrees)
                updateGridVisualization()
                return
            }

            val camInfo = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera!!.cameraInfo)

            var focalLengthMm: Double? = null
            try {
                val focalLengths = camInfo.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                if (focalLengths != null && focalLengths.isNotEmpty()) {
                    focalLengthMm = focalLengths[0].toDouble()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read focal lengths from Camera2 characteristics: ${e.message}")
            }

            var sensorWidthMm: Double? = null
            try {
                val sensorSize = camInfo.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                if (sensorSize != null) {
                    sensorWidthMm = sensorSize.width.toDouble()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read sensor physical size from Camera2 characteristics: ${e.message}")
            }

            objectDetectorHelper.calibrateFromCamera(useWidth, useHeight, cameraHeightMeters, tiltAngleDeg = tiltDeg, focalLengthMm = focalLengthMm, sensorWidthMm = sensorWidthMm, displayRotation = displayRotationDegrees)
            Log.i(TAG, "Calibrated using Camera2 intrinsics: focal=${focalLengthMm ?: "n/a"}, sensorW=${sensorWidthMm ?: "n/a"}, img=${useWidth}x${useHeight}")
            updateGridVisualization()
        } catch (e: Exception) {
            Log.w(TAG, "calibrateUsingCamera2Intrinsics failed, falling back: ${e.message}")
            // fallback
            objectDetectorHelper.calibrateFromCamera(useWidth, useHeight, cameraHeightMeters, tiltAngleDeg = tiltDeg, displayRotation = displayRotationDegrees)
            updateGridVisualization()
        }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun getSensorPixelArraySize(): Pair<Int, Int>? {
        try {
            val cam = camera ?: return null
            val camInfo = androidx.camera.camera2.interop.Camera2CameraInfo.from(cam.cameraInfo)
            val pixelSize = camInfo.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            if (pixelSize != null) return Pair(pixelSize.width, pixelSize.height)
        } catch (e: Exception) {
            Log.w(TAG, "getSensorPixelArraySize failed: ${e.message}")
        }
        return null
    }

    private fun detectObjects(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use {
            bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
        }

        val imageRotation = image.imageInfo.rotationDegrees

        attemptCalibrateIfReady()

        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation

        val orientationStr = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"
        Log.i(TAG, "onConfigurationChanged: orientation=$orientationStr")

        try {
            val sensorSize = getSensorPixelArraySize()
            if (sensorSize != null && lastTiltDeg != null) {
                val (useWidth, useHeight) = sensorSize
                Log.i(TAG, "Recalibrating: sensorSize=${useWidth}x${useHeight}, tilt=$lastTiltDeg")
                calibrateUsingCamera2Intrinsics(useWidth, useHeight, lastTiltDeg!!, cameraHeightMeters)
                Log.i(TAG, "Recalibrated after orientation change to $orientationStr")
            } else {
                Log.w(TAG, "Cannot recalibrate: sensorSize=$sensorSize, lastTiltDeg=$lastTiltDeg")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to recalibrate on orientation change: ${e.message}", e)
        }
    }

    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(
        results: List<ObjectDetection>,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        activity?.runOnUiThread {
            // Show inference time in the top toolbar separate TextView
            try {
                val activity = activity
                val tvInf = activity?.findViewById<android.widget.TextView>(R.id.toolbar_inference_val)
                tvInf?.text = String.format(Locale.US, "%d ms", inferenceTime)
            } catch (e: Exception) {
                // ignore
            }

            // Pass necessary information to OverlayView for drawing on the canvas
            fragmentCameraBinding.overlay.setResults(
                results ?: LinkedList<ObjectDetection>(),
                imageHeight,
                imageWidth
            )

            // Force a redraw
            fragmentCameraBinding.overlay.invalidate()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
