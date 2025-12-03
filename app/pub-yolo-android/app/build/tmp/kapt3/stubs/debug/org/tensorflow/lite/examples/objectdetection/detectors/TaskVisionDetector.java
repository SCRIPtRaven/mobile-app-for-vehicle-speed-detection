package org.tensorflow.lite.examples.objectdetection.detectors;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\b\u00a2\u0006\u0002\u0010\tJ\u0018\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u0005H\u0016R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001a\u0010\u0006\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\r\"\u0004\b\u0011\u0010\u000fR\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0015\"\u0004\b\u0016\u0010\u0017\u00a8\u0006\u001d"}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/detectors/TaskVisionDetector;", "Lorg/tensorflow/lite/examples/objectdetection/detectors/ObjectDetector;", "options", "Lorg/tensorflow/lite/task/vision/detector/ObjectDetector$ObjectDetectorOptions;", "currentModel", "", "maxResults", "context", "Landroid/content/Context;", "(Lorg/tensorflow/lite/task/vision/detector/ObjectDetector$ObjectDetectorOptions;IILandroid/content/Context;)V", "getContext", "()Landroid/content/Context;", "getCurrentModel", "()I", "setCurrentModel", "(I)V", "getMaxResults", "setMaxResults", "objectDetector", "Lorg/tensorflow/lite/task/vision/detector/ObjectDetector;", "getOptions", "()Lorg/tensorflow/lite/task/vision/detector/ObjectDetector$ObjectDetectorOptions;", "setOptions", "(Lorg/tensorflow/lite/task/vision/detector/ObjectDetector$ObjectDetectorOptions;)V", "detect", "Lorg/tensorflow/lite/examples/objectdetection/detectors/DetectionResult;", "image", "Lorg/tensorflow/lite/support/image/TensorImage;", "imageRotation", "app_debug"})
public final class TaskVisionDetector implements org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetector {
    @org.jetbrains.annotations.NotNull()
    private org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions options;
    private int currentModel;
    private int maxResults;
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private org.tensorflow.lite.task.vision.detector.ObjectDetector objectDetector;
    
    public TaskVisionDetector(@org.jetbrains.annotations.NotNull()
    org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions options, int currentModel, int maxResults, @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions getOptions() {
        return null;
    }
    
    public final void setOptions(@org.jetbrains.annotations.NotNull()
    org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions p0) {
    }
    
    public final int getCurrentModel() {
        return 0;
    }
    
    public final void setCurrentModel(int p0) {
    }
    
    public final int getMaxResults() {
        return 0;
    }
    
    public final void setMaxResults(int p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.content.Context getContext() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public org.tensorflow.lite.examples.objectdetection.detectors.DetectionResult detect(@org.jetbrains.annotations.NotNull()
    org.tensorflow.lite.support.image.TensorImage image, int imageRotation) {
        return null;
    }
}