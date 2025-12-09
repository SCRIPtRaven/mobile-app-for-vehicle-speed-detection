package org.tensorflow.lite.examples.objectdetection.detectors;

/**
 * Transformer that uses camera parameters to map pixels to real-world meters.
 * Assumes a flat ground plane and uses a pinhole camera geometry approximation.
 *
 * Ported from a Python implementation; angles are provided in degrees.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u000b\n\u0002\u0010 \n\u0002\b\u0004\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0011\u001a\u00020\u00042\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0013J.\u0010\u0011\u001a\u00020\u00042\u0012\u0010\u0012\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00152\u0012\u0010\u0014\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u0015J5\u0010\u0011\u001a\u00020\u00042\u0012\u0010\u0012\u001a\u000e\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u00160\u00152\u0012\u0010\u0014\u001a\u000e\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u00160\u0015H\u0007\u00a2\u0006\u0002\b\u0017JL\u0010\u0018\u001a\u00020\u00192\b\b\u0002\u0010\u0003\u001a\u00020\u00042\b\b\u0002\u0010\u001a\u001a\u00020\u00042\b\b\u0002\u0010\u0005\u001a\u00020\u00042\b\b\u0002\u0010\u000f\u001a\u00020\u00042\b\b\u0002\u0010\u001b\u001a\u00020\u00042\b\b\u0002\u0010\n\u001a\u00020\t2\b\b\u0002\u0010\b\u001a\u00020\tJ\u0006\u0010\u001c\u001a\u00020\u0004J\u0006\u0010\u001d\u001a\u00020\tJ\u0006\u0010\u001e\u001a\u00020\tJ\u0006\u0010\u000b\u001a\u00020\fJ\u001a\u0010\u001f\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00152\u0006\u0010 \u001a\u00020\u0013J\"\u0010\u001f\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00152\u0006\u0010!\u001a\u00020\u00162\u0006\u0010\"\u001a\u00020\u0016J&\u0010\u001f\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00152\u0012\u0010 \u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u0015J-\u0010\u001f\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00152\u0012\u0010 \u001a\u000e\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u00160\u0015H\u0007\u00a2\u0006\u0002\b#J2\u0010$\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00150%2\u0018\u0010&\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00150%J2\u0010\'\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00150%2\u0018\u0010&\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u00160\u00150%J&\u0010(\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u00150%2\f\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00130%R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006)"}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/detectors/CameraCalibrationTransformer;", "", "()V", "cameraHeight", "", "focalLengthMm", "fovHorizontal", "fovVertical", "imageHeight", "", "imageWidth", "isCalibrated", "", "panAngleRad", "sensorHeightMm", "sensorWidthMm", "tiltAngleRad", "calculateDistance", "p1", "Landroid/graphics/PointF;", "p2", "Lkotlin/Pair;", "", "calculateDistanceFloatPair", "calibrate", "", "tiltAngleDeg", "panAngleDeg", "getHorizonLine", "getImageHeight", "getImageWidth", "transformPoint", "point", "px", "py", "transformPointFloatPair", "transformPoints", "", "points", "transformPointsFloat", "transformPointsPointF", "app_debug"})
public final class CameraCalibrationTransformer {
    private double cameraHeight = 0.0;
    private double tiltAngleRad = 0.0;
    private double panAngleRad = 0.0;
    private double focalLengthMm = 0.0;
    private double sensorWidthMm = 0.0;
    private double fovHorizontal = 0.0;
    private double sensorHeightMm = 0.0;
    private double fovVertical = 0.0;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private boolean isCalibrated = false;
    
    public CameraCalibrationTransformer() {
        super();
    }
    
    public final int getImageWidth() {
        return 0;
    }
    
    public final int getImageHeight() {
        return 0;
    }
    
    public final void calibrate(double cameraHeight, double tiltAngleDeg, double focalLengthMm, double sensorWidthMm, double panAngleDeg, int imageWidth, int imageHeight) {
    }
    
    public final boolean isCalibrated() {
        return false;
    }
    
    /**
     * Transform a single pixel point (px, py) to ground-plane coordinates in meters.
     * Returns Pair(xMeters, yMeters) where yMeters is the distance forward from camera
     * and xMeters is the lateral offset (positive = right).
     * Throws IllegalStateException if not calibrated and IllegalArgumentException if
     * the ray points above the horizon.
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Double, java.lang.Double> transformPoint(@org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Double, java.lang.Double> point) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Double, java.lang.Double> transformPoint(float px, float py) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Double, java.lang.Double> transformPoint(@org.jetbrains.annotations.NotNull()
    android.graphics.PointF point) {
        return null;
    }
    
    @kotlin.jvm.JvmName(name = "transformPointFloatPair")
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Double, java.lang.Double> transformPointFloatPair(@org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Float, java.lang.Float> point) {
        return null;
    }
    
    /**
     * Transform a list of pixel points to a list of ground-plane coordinates.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<kotlin.Pair<java.lang.Double, java.lang.Double>> transformPoints(@org.jetbrains.annotations.NotNull()
    java.util.List<kotlin.Pair<java.lang.Double, java.lang.Double>> points) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<kotlin.Pair<java.lang.Double, java.lang.Double>> transformPointsFloat(@org.jetbrains.annotations.NotNull()
    java.util.List<kotlin.Pair<java.lang.Float, java.lang.Float>> points) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<kotlin.Pair<java.lang.Double, java.lang.Double>> transformPointsPointF(@org.jetbrains.annotations.NotNull()
    java.util.List<? extends android.graphics.PointF> points) {
        return null;
    }
    
    /**
     * Calculate the euclidean distance (meters) between two pixel points on the ground plane.
     */
    public final double calculateDistance(@org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Double, java.lang.Double> p1, @org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Double, java.lang.Double> p2) {
        return 0.0;
    }
    
    @kotlin.jvm.JvmName(name = "calculateDistanceFloatPair")
    public final double calculateDistanceFloatPair(@org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Float, java.lang.Float> p1, @org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Float, java.lang.Float> p2) {
        return 0.0;
    }
    
    public final double calculateDistance(@org.jetbrains.annotations.NotNull()
    android.graphics.PointF p1, @org.jetbrains.annotations.NotNull()
    android.graphics.PointF p2) {
        return 0.0;
    }
    
    /**
     * Compute the y pixel coordinate of the horizon line. Points above this y are above the horizon
     * and won't intersect the ground plane.
     */
    public final double getHorizonLine() {
        return 0.0;
    }
}