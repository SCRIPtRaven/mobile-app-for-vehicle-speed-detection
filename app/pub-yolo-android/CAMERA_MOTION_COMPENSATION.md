# Camera Motion Compensation - Implementation Guide

## Overview

This document explains the **Optical Flow Global Motion Compensation** system implemented to handle camera movement (ego-motion) when measuring vehicle speeds.

## The Problem

When the user moves their arm/device while using the app:
- The entire camera frame shifts
- All detected vehicles appear to move in pixel space
- The app interprets this as vehicle movement
- **Result**: Wildly inaccurate speed estimates (often 10x-100x too high)
- **Additional issue**: False vehicle detections and tracking instability

## The Solution

We implemented a dedicated **CameraMotionCompensator** class that:
1. Detects camera motion using optical flow
2. Estimates global camera movement between frames
3. Subtracts this motion from vehicle tracking
4. Provides accurate speeds even with handheld/moving cameras

## How It Works

### Algorithm Overview

```
For each frame:
1. Detect "good features to track" in static regions (frame borders)
2. Track these features to the next frame using Lucas-Kanade optical flow
3. Calculate median motion vector (robust to outliers)
4. If motion is significant (> 2 pixels), store as global camera motion
5. Subtract this motion from all vehicle displacement calculations
```

### Key Concepts

**Static Regions**: The outer 15% border of the frame on all sides. We avoid the center where vehicles are likely to be, focusing on static background elements like:
- Road edges
- Buildings
- Sky
- Lane markings
- Trees

**Optical Flow**: Lucas-Kanade pyramidal optical flow tracks feature points between consecutive frames, revealing how the entire image has shifted due to camera motion.

**Median Motion**: Using the median (instead of mean) makes the algorithm robust to outliers - even if some features track moving objects, the median will represent the true camera motion.

**Motion Threshold**: Only motions > 2 pixels are considered significant. This filters out tiny jitters and sensor noise.

## File Structure

### Core Implementation

**Location**: `app/src/main/java/org/tensorflow/lite/examples/objectdetection/detectors/CameraMotionCompensator.kt`

This is the **dedicated, standalone file** containing all motion compensation logic. Your team should refer to this file for understanding and modifying the motion compensation system.

### Integration Points

1. **ObjectDetectorHelper.kt** (lines 59-65, 171, 241-263)
   - Creates CameraMotionCompensator instance
   - Calls `estimateMotion()` on each frame
   - Subtracts motion from speed calculations

2. **MainActivity.kt** (lines 23, 37-42)
   - Initializes OpenCV library on app startup

3. **build.gradle** (line 135)
   - OpenCV dependency: `com.quickbirdstudios:opencv:4.5.3.0`

## Using the CameraMotionCompensator

### Basic Usage

```kotlin
// 1. Create instance (typically once, as a class member)
val motionCompensator = CameraMotionCompensator(
    minFeatures = 10,       // Minimum features for reliable estimation
    maxFeatures = 100,      // Maximum features to track
    motionThreshold = 2.0f, // Minimum motion to consider (pixels)
    borderFraction = 0.15f, // Border region size (15% on each side)
    debugMode = true        // Enable logging
)

// 2. For each new frame, estimate motion
motionCompensator.estimateMotion(bitmap)

// 3. Get the global motion vector
val motion = motionCompensator.getGlobalMotion() // Returns PointF(dx, dy) or null

// 4. Subtract motion from your tracked object displacements
if (motion != null) {
    objectDisplacementX -= motion.x
    objectDisplacementY -= motion.y
}
```

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `minFeatures` | 10 | Minimum number of features required for reliable estimation. Below this, returns null. |
| `maxFeatures` | 100 | Maximum number of features to track. More features = more robust but slower. |
| `motionThreshold` | 2.0f | Minimum motion magnitude (pixels) to be considered significant. Filters noise. |
| `borderFraction` | 0.15f | Size of border region as fraction of frame. 0.15 = outer 15% on each side. |
| `debugMode` | false | Enable detailed logging for debugging. Set to false in production. |

### API Reference

#### `estimateMotion(bitmap: Bitmap): Boolean`
Processes a new frame and estimates global camera motion.
- **Returns**: `true` if estimation succeeded, `false` on error
- Call this **once per frame** before speed calculations

#### `getGlobalMotion(): PointF?`
Gets the current global motion estimate.
- **Returns**: `PointF(dx, dy)` in pixels, or `null` if no significant motion
- `dx`: horizontal camera movement (positive = camera moved right)
- `dy`: vertical camera movement (positive = camera moved down)

#### `isMoving(): Boolean`
Quick check if camera is currently moving significantly.
- **Returns**: `true` if global motion is not null

#### `getMotionMagnitude(): Float`
Gets the magnitude of current camera motion in pixels.
- **Returns**: `sqrt(dx² + dy²)`, or 0 if no motion

#### `reset()`
Resets the compensator state (clears history).
- Call when tracking is reset or scene changes dramatically

#### `getStats(): String`
Returns statistics about motion estimation performance.
- Useful for debugging and monitoring

## Implementation Details

### Coordinate System

- **Pixel Motion**: Returned by `getGlobalMotion()` in pixel coordinates
  - `(+x, +y)` = camera moved right and down
  - `(-x, -y)` = camera moved left and up

- **Meter Conversion**: In ObjectDetectorHelper, pixel motion is converted to meters using CameraCalibrationTransformer before being subtracted from vehicle displacement

### Performance

- **Typical execution time**: 5-15ms per frame on modern Android devices
- **Feature detection**: ~2-5ms
- **Optical flow**: ~3-10ms
- **Negligible impact** on overall detection pipeline (typically 30-60 FPS)

### Memory Usage

- Allocates temporary OpenCV Mat objects per frame
- Properly releases resources to prevent leaks
- Minimal persistent memory (~100KB for feature storage)

## Integration Flow

```
CameraFragment captures frame
        ↓
ObjectDetectorHelper.detect(bitmap)
        ↓
motionCompensator.estimateMotion(bitmap)  ← Estimates camera motion
        ↓
YOLO detection + tracking
        ↓
For each tracked vehicle:
    1. Calculate pixel displacement
    2. Convert to meters using CameraCalibrationTransformer
    3. Get global motion (pixels)
    4. Convert motion to meters
    5. Subtract motion from displacement  ← Compensation happens here
    6. Calculate speed from corrected displacement
        ↓
Display results with accurate speeds
```

## Debugging

### Enable Debug Mode

```kotlin
val motionCompensator = CameraMotionCompensator(
    debugMode = true  // Enable detailed logging
)
```

### Log Output Example

```
CameraMotionCompensator: Global motion detected:
    dx=5.2, dy=-3.1, magnitude=6.0, features=47
ObjectDetectorHelper: Track 1: Camera motion compensation:
    pixel(5.2,-3.1) -> meters(0.18,-0.11)
```

### Common Issues and Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| `getGlobalMotion()` always returns null | Not enough features detected | Lower `minFeatures`, adjust lighting, or clean camera lens |
| Motion compensation too aggressive | Threshold too low | Increase `motionThreshold` to 3-4 pixels |
| Still seeing jittery speeds | Border region capturing moving objects | Increase `borderFraction` to 0.20 or 0.25 |
| Performance lag | Too many features tracked | Reduce `maxFeatures` to 50-75 |

## Testing Recommendations

### Unit Testing

Test the motion compensator in isolation:

```kotlin
@Test
fun testMotionCompensation() {
    val compensator = CameraMotionCompensator()

    // Create two test frames with known shift
    val frame1 = createTestBitmap()
    val frame2 = shiftBitmap(frame1, dx = 10f, dy = 5f)

    compensator.estimateMotion(frame1)
    compensator.estimateMotion(frame2)

    val motion = compensator.getGlobalMotion()
    assertNotNull(motion)
    assertEquals(10f, motion!!.x, delta = 2f)
    assertEquals(5f, motion.y, delta = 2f)
}
```

### Real-World Testing

1. **Stationary Camera Test**
   - Hold camera steady
   - Drive vehicle past at known speed
   - Verify speed accuracy (should be within 10%)

2. **Moving Camera Test**
   - Pan camera left/right while vehicle is stationary
   - Vehicle speed should remain ~0 km/h (compensation working)
   - Without compensation, speed would be high

3. **Handheld Test**
   - Natural arm movement while tracking vehicles
   - Speeds should be stable and realistic
   - Compare with/without compensation

## Performance Tuning

### For High-End Devices
```kotlin
CameraMotionCompensator(
    maxFeatures = 150,
    motionThreshold = 1.5f,
    borderFraction = 0.15f
)
```

### For Low-End Devices
```kotlin
CameraMotionCompensator(
    maxFeatures = 50,
    motionThreshold = 3.0f,
    borderFraction = 0.20f
)
```

### For Stabilized Cameras (Tripod/Mount)
```kotlin
CameraMotionCompensator(
    motionThreshold = 5.0f,  // Higher threshold, less compensation
    borderFraction = 0.10f    // Smaller border region
)
```

## Future Improvements

Potential enhancements to consider:

1. **Gyroscope Fusion**: Combine optical flow with gyroscope data for even more robust motion estimation

2. **Adaptive Thresholds**: Automatically adjust motion threshold based on scene characteristics

3. **Multi-Scale Tracking**: Track features at multiple image scales for better robustness

4. **Background Segmentation**: Use semantic segmentation to better identify static regions

5. **IMU Integration**: Use accelerometer data to detect rapid movements and pause tracking

## References

- OpenCV Optical Flow Documentation: https://docs.opencv.org/4.x/d4/dee/tutorial_optical_flow.html
- Lucas-Kanade Method: https://en.wikipedia.org/wiki/Lucas%E2%80%93Kanade_method
- Feature Detection: https://docs.opencv.org/4.x/d4/d8c/tutorial_py_shi_tomasi.html

## Support

For questions or issues with the motion compensation system:
1. Check this documentation
2. Review `CameraMotionCompensator.kt` source code (heavily commented)
3. Enable `debugMode` to see detailed logs
4. Contact the development team

---

**Last Updated**: 2024
**Implementation**: CameraMotionCompensator.kt
**Dependencies**: OpenCV for Android 4.5.3.0
