package org.tensorflow.lite.examples.objectdetection.detectors;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0010\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001BI\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0005\u001a\u00020\u0006\u0012\b\b\u0002\u0010\u0007\u001a\u00020\u0006\u0012\b\b\u0002\u0010\b\u001a\u00020\u0006\u0012\b\b\u0002\u0010\t\u001a\u00020\u0006\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fJ\u0018\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020&2\u0006\u0010\'\u001a\u00020\u0006H\u0016R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u001a\u0010\b\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016R\u001a\u0010\t\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0014\"\u0004\b\u0018\u0010\u0016R\u001a\u0010\u0004\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0019\u0010\u000e\"\u0004\b\u001a\u0010\u0010R\u000e\u0010\u001b\u001a\u00020\u001cX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0007\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001d\u0010\u0014\"\u0004\b\u001e\u0010\u0016R\u001a\u0010\u0005\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001f\u0010\u0014\"\u0004\b \u0010\u0016R\u000e\u0010!\u001a\u00020\"X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006("}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/detectors/YoloDetector;", "Lorg/tensorflow/lite/examples/objectdetection/detectors/ObjectDetector;", "confidenceThreshold", "", "iouThreshold", "numThreads", "", "maxResults", "currentDelegate", "currentModel", "context", "Landroid/content/Context;", "(FFIIIILandroid/content/Context;)V", "getConfidenceThreshold", "()F", "setConfidenceThreshold", "(F)V", "getContext", "()Landroid/content/Context;", "getCurrentDelegate", "()I", "setCurrentDelegate", "(I)V", "getCurrentModel", "setCurrentModel", "getIouThreshold", "setIouThreshold", "ip", "Lcom/ultralytics/yolo/ImageProcessing;", "getMaxResults", "setMaxResults", "getNumThreads", "setNumThreads", "yolo", "Lcom/ultralytics/yolo/predict/detect/TfliteDetector;", "detect", "Lorg/tensorflow/lite/examples/objectdetection/detectors/DetectionResult;", "image", "Lorg/tensorflow/lite/support/image/TensorImage;", "imageRotation", "app_debug"})
public final class YoloDetector implements org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetector {
    private float confidenceThreshold;
    private float iouThreshold;
    private int numThreads;
    private int maxResults;
    private int currentDelegate;
    private int currentModel;
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private com.ultralytics.yolo.predict.detect.TfliteDetector yolo;
    @org.jetbrains.annotations.NotNull()
    private com.ultralytics.yolo.ImageProcessing ip;
    
    public YoloDetector(float confidenceThreshold, float iouThreshold, int numThreads, int maxResults, int currentDelegate, int currentModel, @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    public final float getConfidenceThreshold() {
        return 0.0F;
    }
    
    public final void setConfidenceThreshold(float p0) {
    }
    
    public final float getIouThreshold() {
        return 0.0F;
    }
    
    public final void setIouThreshold(float p0) {
    }
    
    public final int getNumThreads() {
        return 0;
    }
    
    public final void setNumThreads(int p0) {
    }
    
    public final int getMaxResults() {
        return 0;
    }
    
    public final void setMaxResults(int p0) {
    }
    
    public final int getCurrentDelegate() {
        return 0;
    }
    
    public final void setCurrentDelegate(int p0) {
    }
    
    public final int getCurrentModel() {
        return 0;
    }
    
    public final void setCurrentModel(int p0) {
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