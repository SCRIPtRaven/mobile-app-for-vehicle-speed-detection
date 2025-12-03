package org.tensorflow.lite.examples.objectdetection.detectors;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\bf\u0018\u00002\u00020\u0001J\u0018\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007H&\u00a8\u0006\b"}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/detectors/ObjectDetector;", "", "detect", "Lorg/tensorflow/lite/examples/objectdetection/detectors/DetectionResult;", "image", "Lorg/tensorflow/lite/support/image/TensorImage;", "imageRotation", "", "app_debug"})
public abstract interface ObjectDetector {
    
    @org.jetbrains.annotations.NotNull()
    public abstract org.tensorflow.lite.examples.objectdetection.detectors.DetectionResult detect(@org.jetbrains.annotations.NotNull()
    org.tensorflow.lite.support.image.TensorImage image, int imageRotation);
}