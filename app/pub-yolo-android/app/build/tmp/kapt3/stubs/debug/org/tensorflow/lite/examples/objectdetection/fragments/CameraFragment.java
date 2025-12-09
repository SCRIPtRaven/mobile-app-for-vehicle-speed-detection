package org.tensorflow.lite.examples.objectdetection.fragments;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00ce\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0014\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\t\u0018\u00002\u00020\u00012\u00020\u0002B\u0005\u00a2\u0006\u0002\u0010\u0003J\b\u00100\u001a\u000201H\u0002J\b\u00102\u001a\u000201H\u0003J(\u00103\u001a\u0002012\u0006\u00104\u001a\u0002052\u0006\u00106\u001a\u0002052\u0006\u00107\u001a\u00020\u000b2\u0006\u0010\u0016\u001a\u00020\u000bH\u0003J\u0010\u00108\u001a\u0002012\u0006\u00109\u001a\u00020:H\u0002J\u0016\u0010;\u001a\u0010\u0012\u0004\u0012\u000205\u0012\u0004\u0012\u000205\u0018\u00010<H\u0003J\b\u0010=\u001a\u000201H\u0002J\u0010\u0010>\u001a\u0002012\u0006\u0010?\u001a\u00020@H\u0016J$\u0010A\u001a\u00020B2\u0006\u0010C\u001a\u00020D2\b\u0010E\u001a\u0004\u0018\u00010F2\b\u0010G\u001a\u0004\u0018\u00010HH\u0016J\b\u0010I\u001a\u000201H\u0016J\u0010\u0010J\u001a\u0002012\u0006\u0010K\u001a\u00020\u0007H\u0016J.\u0010L\u001a\u0002012\f\u0010M\u001a\b\u0012\u0004\u0012\u00020O0N2\u0006\u0010P\u001a\u00020\t2\u0006\u0010Q\u001a\u0002052\u0006\u0010R\u001a\u000205H\u0016J\b\u0010S\u001a\u000201H\u0016J\u001a\u0010T\u001a\u0002012\u0006\u0010U\u001a\u00020B2\b\u0010G\u001a\u0004\u0018\u00010HH\u0017J\b\u0010V\u001a\u000201H\u0002J\b\u0010W\u001a\u000201H\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082D\u00a2\u0006\u0002\n\u0000R\u0010\u0010\f\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0012\u001a\u0004\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0017\u001a\u0004\u0018\u00010\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0019\u001a\u00020\r8BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001a\u0010\u001bR\u000e\u0010\u001c\u001a\u00020\u001dX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001e\u001a\u0004\u0018\u00010\u001fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010 \u001a\u00020!X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\"\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0012\u0010#\u001a\u0004\u0018\u00010\u000bX\u0082\u000e\u00a2\u0006\u0004\n\u0002\u0010$R\u000e\u0010%\u001a\u00020&X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\'\u001a\u00020(X\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010)\u001a\u0004\u0018\u00010*X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010+\u001a\u00020,X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010-\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010.\u001a\u0004\u0018\u00010/X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006X"}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/fragments/CameraFragment;", "Landroidx/fragment/app/Fragment;", "Lorg/tensorflow/lite/examples/objectdetection/ObjectDetectorHelper$DetectorListener;", "()V", "GRAVITY_FILTER_ALPHA", "", "TAG", "", "TILT_DEBOUNCE_MS", "", "TILT_RECALIBRATION_THRESHOLD_DEG", "", "_fragmentCameraBinding", "Lorg/tensorflow/lite/examples/objectdetection/databinding/FragmentCameraBinding;", "accelerometerSensor", "Landroid/hardware/Sensor;", "bitmapBuffer", "Landroid/graphics/Bitmap;", "camera", "Landroidx/camera/core/Camera;", "cameraExecutor", "Ljava/util/concurrent/ExecutorService;", "cameraHeightMeters", "cameraProvider", "Landroidx/camera/lifecycle/ProcessCameraProvider;", "fragmentCameraBinding", "getFragmentCameraBinding", "()Lorg/tensorflow/lite/examples/objectdetection/databinding/FragmentCameraBinding;", "gravity", "", "imageAnalyzer", "Landroidx/camera/core/ImageAnalysis;", "isCalibrated", "", "lastRecalibrateTs", "lastTiltDeg", "Ljava/lang/Double;", "mainHandler", "Landroid/os/Handler;", "objectDetectorHelper", "Lorg/tensorflow/lite/examples/objectdetection/ObjectDetectorHelper;", "preview", "Landroidx/camera/core/Preview;", "rotationListener", "Landroid/hardware/SensorEventListener;", "rotationVectorSensor", "sensorManager", "Landroid/hardware/SensorManager;", "attemptCalibrateIfReady", "", "bindCameraUseCases", "calibrateUsingCamera2Intrinsics", "useWidth", "", "useHeight", "tiltDeg", "detectObjects", "image", "Landroidx/camera/core/ImageProxy;", "getSensorPixelArraySize", "Lkotlin/Pair;", "initBottomSheetControls", "onConfigurationChanged", "newConfig", "Landroid/content/res/Configuration;", "onCreateView", "Landroid/view/View;", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onDestroyView", "onError", "error", "onResults", "results", "", "Lorg/tensorflow/lite/examples/objectdetection/detectors/ObjectDetection;", "inferenceTime", "imageHeight", "imageWidth", "onResume", "onViewCreated", "view", "setUpCamera", "updateControlsUi", "app_debug"})
@kotlin.OptIn(markerClass = {androidx.camera.camera2.interop.ExperimentalCamera2Interop.class})
public final class CameraFragment extends androidx.fragment.app.Fragment implements org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper.DetectorListener {
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String TAG = "ObjectDetection";
    @org.jetbrains.annotations.Nullable()
    private org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding _fragmentCameraBinding;
    private org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper objectDetectorHelper;
    private android.graphics.Bitmap bitmapBuffer;
    @org.jetbrains.annotations.Nullable()
    private androidx.camera.core.Preview preview;
    @org.jetbrains.annotations.Nullable()
    private androidx.camera.core.ImageAnalysis imageAnalyzer;
    @org.jetbrains.annotations.Nullable()
    private androidx.camera.core.Camera camera;
    @org.jetbrains.annotations.Nullable()
    private androidx.camera.lifecycle.ProcessCameraProvider cameraProvider;
    
    /**
     * Blocking camera operations are performed using this executor
     */
    private java.util.concurrent.ExecutorService cameraExecutor;
    private boolean isCalibrated = false;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.SensorManager sensorManager;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.Sensor rotationVectorSensor;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.Sensor accelerometerSensor;
    @org.jetbrains.annotations.NotNull()
    private final float[] gravity = null;
    private final float GRAVITY_FILTER_ALPHA = 0.8F;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler mainHandler = null;
    @org.jetbrains.annotations.Nullable()
    private java.lang.Double lastTiltDeg;
    private final double TILT_RECALIBRATION_THRESHOLD_DEG = 1.0;
    private final long TILT_DEBOUNCE_MS = 500L;
    private long lastRecalibrateTs = 0L;
    private double cameraHeightMeters = 1.5;
    @org.jetbrains.annotations.NotNull()
    private final android.hardware.SensorEventListener rotationListener = null;
    
    public CameraFragment() {
        super();
    }
    
    private final org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding getFragmentCameraBinding() {
        return null;
    }
    
    @java.lang.Override()
    public void onResume() {
    }
    
    @java.lang.Override()
    public void onDestroyView() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.view.View onCreateView(@org.jetbrains.annotations.NotNull()
    android.view.LayoutInflater inflater, @org.jetbrains.annotations.Nullable()
    android.view.ViewGroup container, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
        return null;
    }
    
    @java.lang.Override()
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public void onViewCreated(@org.jetbrains.annotations.NotNull()
    android.view.View view, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void initBottomSheetControls() {
    }
    
    private final void updateControlsUi() {
    }
    
    private final void setUpCamera() {
    }
    
    @android.annotation.SuppressLint(value = {"UnsafeOptInUsageError"})
    private final void bindCameraUseCases() {
    }
    
    private final void attemptCalibrateIfReady() {
    }
    
    @androidx.annotation.OptIn(markerClass = {androidx.camera.camera2.interop.ExperimentalCamera2Interop.class})
    @kotlin.OptIn(markerClass = {androidx.camera.camera2.interop.ExperimentalCamera2Interop.class})
    private final void calibrateUsingCamera2Intrinsics(int useWidth, int useHeight, double tiltDeg, double cameraHeightMeters) {
    }
    
    @androidx.annotation.OptIn(markerClass = {androidx.camera.camera2.interop.ExperimentalCamera2Interop.class})
    private final kotlin.Pair<java.lang.Integer, java.lang.Integer> getSensorPixelArraySize() {
        return null;
    }
    
    private final void detectObjects(androidx.camera.core.ImageProxy image) {
    }
    
    @java.lang.Override()
    public void onConfigurationChanged(@org.jetbrains.annotations.NotNull()
    android.content.res.Configuration newConfig) {
    }
    
    @java.lang.Override()
    public void onResults(@org.jetbrains.annotations.NotNull()
    java.util.List<org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection> results, long inferenceTime, int imageHeight, int imageWidth) {
    }
    
    @java.lang.Override()
    public void onError(@org.jetbrains.annotations.NotNull()
    java.lang.String error) {
    }
}