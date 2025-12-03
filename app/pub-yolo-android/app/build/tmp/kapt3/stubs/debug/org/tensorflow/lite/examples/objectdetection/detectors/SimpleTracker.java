package org.tensorflow.lite.examples.objectdetection.detectors;

/**
 * Improved IoU-based tracker using global greedy matching.
 * Matches tracks to detections by sorting all Track-Detection IoU pairs and
 * assigning the highest IoU pairs first (ensuring one-to-one matches).
 * Tracks that are not matched increment a miss counter and are removed after
 * exceeding maxMisses. New detections create new tracks.
 *
 * Also applies simple exponential smoothing to track boxes to reduce jitter.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000P\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0015\n\u0000\n\u0002\u0010\u0011\n\u0002\u0010\u0013\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001:\u0001!B#\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0007J\u001b\u0010\r\u001a\u00020\u000e2\f\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00110\u0010H\u0002\u00a2\u0006\u0002\u0010\u0012J\u0018\u0010\u0013\u001a\u00020\u00032\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0015H\u0002J\u0006\u0010\u0017\u001a\u00020\u0018J \u0010\u0019\u001a\u00020\u00152\u0006\u0010\u001a\u001a\u00020\u00152\u0006\u0010\u001b\u001a\u00020\u00152\u0006\u0010\u001c\u001a\u00020\u0003H\u0002J\u0014\u0010\u001d\u001a\u00020\u00182\f\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020 0\u001fR\u000e\u0010\b\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\f0\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\""}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/detectors/SimpleTracker;", "", "iouThreshold", "", "maxMisses", "", "smoothing", "(FIF)V", "frameCounter", "nextId", "tracks", "", "Lorg/tensorflow/lite/examples/objectdetection/detectors/SimpleTracker$Track;", "hungarian", "", "cost", "", "", "([[D)[I", "iouRect", "a", "Landroid/graphics/RectF;", "b", "reset", "", "smoothRect", "oldBox", "newBox", "alpha", "update", "detections", "", "Lorg/tensorflow/lite/examples/objectdetection/detectors/ObjectDetection;", "Track", "app_debug"})
public final class SimpleTracker {
    private final float iouThreshold = 0.0F;
    private final int maxMisses = 0;
    private final float smoothing = 0.0F;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<org.tensorflow.lite.examples.objectdetection.detectors.SimpleTracker.Track> tracks = null;
    private int nextId = 0;
    private int frameCounter = 0;
    
    public SimpleTracker(float iouThreshold, int maxMisses, float smoothing) {
        super();
    }
    
    public final void reset() {
    }
    
    /**
     * Update tracker with detections for the current frame. This mutates
     * ObjectDetection.id for assigned ids.
     */
    public final void update(@org.jetbrains.annotations.NotNull()
    java.util.List<org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection> detections) {
    }
    
    private final float iouRect(android.graphics.RectF a, android.graphics.RectF b) {
        return 0.0F;
    }
    
    private final android.graphics.RectF smoothRect(android.graphics.RectF oldBox, android.graphics.RectF newBox, float alpha) {
        return null;
    }
    
    private final int[] hungarian(double[][] cost) {
        return null;
    }
    
    public SimpleTracker() {
        super();
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0019\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0082\b\u0018\u00002\u00020\u0001B3\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0007\u001a\u00020\u0003\u0012\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\tJ\t\u0010\u0018\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001b\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u001c\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J=\u0010\u001d\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00032\b\b\u0002\u0010\u0007\u001a\u00020\u00032\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0005H\u00c6\u0001J\u0013\u0010\u001e\u001a\u00020\u001f2\b\u0010 \u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010!\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\"\u001a\u00020#H\u00d6\u0001R\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000f\"\u0004\b\u0010\u0010\u0011R\u001a\u0010\u0006\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u000f\"\u0004\b\u0013\u0010\u0011R\u001a\u0010\u0007\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u000f\"\u0004\b\u0015\u0010\u0011R\u001c\u0010\b\u001a\u0004\u0018\u00010\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u000b\"\u0004\b\u0017\u0010\r\u00a8\u0006$"}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/detectors/SimpleTracker$Track;", "", "id", "", "box", "Landroid/graphics/RectF;", "lastSeenFrame", "misses", "prevBox", "(ILandroid/graphics/RectF;IILandroid/graphics/RectF;)V", "getBox", "()Landroid/graphics/RectF;", "setBox", "(Landroid/graphics/RectF;)V", "getId", "()I", "setId", "(I)V", "getLastSeenFrame", "setLastSeenFrame", "getMisses", "setMisses", "getPrevBox", "setPrevBox", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "", "other", "hashCode", "toString", "", "app_debug"})
    static final class Track {
        private int id;
        @org.jetbrains.annotations.NotNull()
        private android.graphics.RectF box;
        private int lastSeenFrame;
        private int misses;
        @org.jetbrains.annotations.Nullable()
        private android.graphics.RectF prevBox;
        
        public Track(int id, @org.jetbrains.annotations.NotNull()
        android.graphics.RectF box, int lastSeenFrame, int misses, @org.jetbrains.annotations.Nullable()
        android.graphics.RectF prevBox) {
            super();
        }
        
        public final int getId() {
            return 0;
        }
        
        public final void setId(int p0) {
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.graphics.RectF getBox() {
            return null;
        }
        
        public final void setBox(@org.jetbrains.annotations.NotNull()
        android.graphics.RectF p0) {
        }
        
        public final int getLastSeenFrame() {
            return 0;
        }
        
        public final void setLastSeenFrame(int p0) {
        }
        
        public final int getMisses() {
            return 0;
        }
        
        public final void setMisses(int p0) {
        }
        
        @org.jetbrains.annotations.Nullable()
        public final android.graphics.RectF getPrevBox() {
            return null;
        }
        
        public final void setPrevBox(@org.jetbrains.annotations.Nullable()
        android.graphics.RectF p0) {
        }
        
        public final int component1() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.graphics.RectF component2() {
            return null;
        }
        
        public final int component3() {
            return 0;
        }
        
        public final int component4() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final android.graphics.RectF component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final org.tensorflow.lite.examples.objectdetection.detectors.SimpleTracker.Track copy(int id, @org.jetbrains.annotations.NotNull()
        android.graphics.RectF box, int lastSeenFrame, int misses, @org.jetbrains.annotations.Nullable()
        android.graphics.RectF prevBox) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}