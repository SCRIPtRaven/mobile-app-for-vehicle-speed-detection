"""
Vehicle Speed Tracking System
Uses YOLOv8 for detection and ByteTrack for tracking to estimate vehicle speeds
"""

import cv2
import numpy as np
from ultralytics import YOLO
from collections import defaultdict
import time


class VehicleSpeedTracker:
    def __init__(self, model_path='yolov8n.pt', reference_width_meters=1.8, fps=30):
        """
        Initialize the vehicle speed tracker
        
        Args:
            model_path: Path to YOLO model (will download if not exists)
            reference_width_meters: Average car width in meters (default: 1.8m)
            fps: Video frames per second
        """
        self.model = YOLO(model_path)
        self.reference_width_meters = reference_width_meters
        self.fps = fps
        
        # Track history: {track_id: [(x, y, timestamp, bbox_width), ...]}
        self.track_history = defaultdict(list)
        
        # Speed estimates: {track_id: speed_kmh}
        self.speed_estimates = {}
        
        # Vehicle classes in COCO dataset
        self.vehicle_classes = [2, 3, 5, 7]  # car, motorcycle, bus, truck
        
        # Calibration factors
        self.pixels_per_meter = None
        self.calibration_done = False
        
    def calibrate_scale(self, bbox_width_pixels):
        """
        Calibrate the pixel-to-meter ratio using a detected vehicle
        
        Args:
            bbox_width_pixels: Width of bounding box in pixels
        """
        # Use average car width as reference
        self.pixels_per_meter = bbox_width_pixels / self.reference_width_meters
        self.calibration_done = True
        print(f"Calibration: {self.pixels_per_meter:.2f} pixels per meter")
        
    def calculate_speed(self, track_id, current_pos, current_time, bbox_width):
        """
        Calculate speed based on position change over time
        
        Args:
            track_id: ID of the tracked vehicle
            current_pos: Current position (x, y) in pixels
            current_time: Current timestamp
            bbox_width: Width of bounding box for calibration
            
        Returns:
            Speed in km/h or None if not enough data
        """
        # Auto-calibrate if not done yet
        if not self.calibration_done and bbox_width > 0:
            self.calibrate_scale(bbox_width)
        
        # Store current position
        self.track_history[track_id].append(
            (current_pos[0], current_pos[1], current_time, bbox_width)
        )
        
        # Keep only recent history (last 30 frames)
        if len(self.track_history[track_id]) > 30:
            self.track_history[track_id] = self.track_history[track_id][-30:]
        
        # Need at least 10 frames to estimate speed reliably
        if len(self.track_history[track_id]) < 10:
            return None
        
        # Calculate speed using linear regression on recent positions
        history = self.track_history[track_id]
        
        # Use last 15 frames for calculation
        recent_history = history[-15:]
        
        if len(recent_history) < 2:
            return None
        
        # Calculate displacement
        first_pos = np.array([recent_history[0][0], recent_history[0][1]])
        last_pos = np.array([recent_history[-1][0], recent_history[-1][1]])
        
        displacement_pixels = np.linalg.norm(last_pos - first_pos)
        
        # Calculate time difference
        time_diff = recent_history[-1][2] - recent_history[0][2]
        
        if time_diff == 0 or not self.calibration_done:
            return None
        
        # Convert to real-world units
        displacement_meters = displacement_pixels / self.pixels_per_meter
        speed_mps = displacement_meters / time_diff  # meters per second
        speed_kmh = speed_mps * 3.6  # convert to km/h
        
        # Filter out unrealistic speeds (0-200 km/h)
        if 0 < speed_kmh < 200:
            self.speed_estimates[track_id] = speed_kmh
            return speed_kmh
        
        return None
    
    def process_frame(self, frame, frame_number):
        """
        Process a single frame to detect and track vehicles
        
        Args:
            frame: Input frame (numpy array)
            frame_number: Frame number for timestamp calculation
            
        Returns:
            Annotated frame with tracking and speed information
        """
        # Run YOLO detection and tracking
        results = self.model.track(
            frame, 
            persist=True,
            classes=self.vehicle_classes,
            verbose=False,
            tracker="bytetrack.yaml"
        )
        
        annotated_frame = frame.copy()
        current_time = frame_number / self.fps
        
        if results[0].boxes is not None and results[0].boxes.id is not None:
            # Get detection data
            boxes = results[0].boxes.xyxy.cpu().numpy()
            track_ids = results[0].boxes.id.cpu().numpy().astype(int)
            confidences = results[0].boxes.conf.cpu().numpy()
            classes = results[0].boxes.cls.cpu().numpy().astype(int)
            
            for box, track_id, conf, cls in zip(boxes, track_ids, confidences, classes):
                x1, y1, x2, y2 = box
                
                # Calculate center position and bbox width
                center_x = (x1 + x2) / 2
                center_y = (y1 + y2) / 2
                bbox_width = x2 - x1
                
                # Calculate speed
                speed = self.calculate_speed(
                    track_id, 
                    (center_x, center_y), 
                    current_time,
                    bbox_width
                )
                
                # Draw bounding box
                color = (0, 255, 0) if speed else (255, 0, 0)
                cv2.rectangle(annotated_frame, (int(x1), int(y1)), (int(x2), int(y2)), color, 2)
                
                # Prepare label
                label = f"ID: {track_id}"
                if speed:
                    label += f" | {speed:.1f} km/h"
                
                # Draw label background
                (label_width, label_height), _ = cv2.getTextSize(
                    label, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 2
                )
                cv2.rectangle(
                    annotated_frame,
                    (int(x1), int(y1) - label_height - 10),
                    (int(x1) + label_width, int(y1)),
                    color,
                    -1
                )
                
                # Draw label text
                cv2.putText(
                    annotated_frame,
                    label,
                    (int(x1), int(y1) - 5),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    (255, 255, 255),
                    2
                )
                
                # Draw tracking trail
                if track_id in self.track_history and len(self.track_history[track_id]) > 1:
                    points = [(int(h[0]), int(h[1])) for h in self.track_history[track_id][-20:]]
                    for i in range(1, len(points)):
                        cv2.line(annotated_frame, points[i-1], points[i], color, 2)
        
        # Add info overlay
        info_text = f"Frame: {frame_number} | Tracked vehicles: {len(self.speed_estimates)}"
        if self.calibration_done:
            info_text += f" | Calibrated: {self.pixels_per_meter:.1f} px/m"
        
        cv2.putText(
            annotated_frame,
            info_text,
            (10, 30),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (0, 255, 255),
            2
        )
        
        return annotated_frame
    
    def process_video(self, video_path, output_path=None, display=True, loop=False):
        """
        Process entire video file
        
        Args:
            video_path: Path to input video
            output_path: Path to save output video (optional)
            display: Whether to display video while processing
            loop: Whether to loop the video continuously (only works with display=True)
        """
        cap = cv2.VideoCapture(video_path)
        
        # Get video properties
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        self.fps = int(cap.get(cv2.CAP_PROP_FPS))
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        
        print(f"Video: {width}x{height} @ {self.fps} FPS, {total_frames} frames")
        
        # Setup video writer if output path provided
        out = None
        if output_path:
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            out = cv2.VideoWriter(output_path, fourcc, self.fps, (width, height))
        
        frame_number = 0
        
        try:
            while True:  # Outer loop for video repetition
                cap = cv2.VideoCapture(video_path)
                
                # Get video properties on first iteration
                if frame_number == 0:
                    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
                    self.fps = int(cap.get(cv2.CAP_PROP_FPS))
                    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
                    
                    print(f"Video: {width}x{height} @ {self.fps} FPS, {total_frames} frames")
                    if loop:
                        print("Loop mode enabled - press 'Q' to quit")
                
                while cap.isOpened():
                    ret, frame = cap.read()
                    if not ret:
                        break
                    
                    # Process frame
                    annotated_frame = self.process_frame(frame, frame_number)
                    
                    # Write to output (only on first pass)
                    if out and frame_number < total_frames:
                        out.write(annotated_frame)
                    
                    # Display
                    if display:
                        cv2.imshow('Vehicle Speed Tracking (Press Q to quit)', annotated_frame)
                        if cv2.waitKey(1) & 0xFF == ord('q'):
                            cap.release()
                            raise KeyboardInterrupt  # Exit both loops
                    
                    frame_number += 1
                    
                    # Progress indicator (only on first pass if not looping, or show every time if looping)
                    if frame_number % 30 == 0:
                        if loop:
                            loop_number = frame_number // total_frames + 1
                            frame_in_loop = frame_number % total_frames
                            print(f"Loop #{loop_number} - Frame: {frame_in_loop}/{total_frames}")
                        else:
                            progress = (frame_number / total_frames) * 100
                            print(f"Progress: {progress:.1f}% ({frame_number}/{total_frames})")
                
                cap.release()
                
                # If not looping, exit after first pass
                if not loop:
                    break
                    
                # Reset frame counter for display purposes but keep tracking data
                # This allows continuous tracking across loops
                print(f"Completed loop, restarting video...")
        
        except KeyboardInterrupt:
            print("\nStopped by user")
        
        finally:
            if out:
                out.release()
            if display:
                cv2.destroyAllWindows()
        
        # Print summary
        print("\n=== Speed Tracking Summary ===")
        print(f"Total vehicles tracked: {len(self.speed_estimates)}")
        if self.speed_estimates:
            speeds = list(self.speed_estimates.values())
            print(f"Average speed: {np.mean(speeds):.1f} km/h")
            print(f"Max speed: {np.max(speeds):.1f} km/h")
            print(f"Min speed: {np.min(speeds):.1f} km/h")
            
            print("\nIndividual vehicle speeds:")
            for track_id, speed in sorted(self.speed_estimates.items()):
                print(f"  Vehicle {track_id}: {speed:.1f} km/h")


def main():
    """
    Main function to run the tracker
    """
    import argparse
    
    parser = argparse.ArgumentParser(description='Vehicle Speed Tracking')
    parser.add_argument('video', type=str, help='Path to input video file')
    parser.add_argument('--output', type=str, help='Path to output video file')
    parser.add_argument('--model', type=str, default='yolov8n.pt', 
                       help='YOLO model to use (default: yolov8n.pt)')
    parser.add_argument('--car-width', type=float, default=1.8,
                       help='Reference car width in meters (default: 1.8)')
    parser.add_argument('--no-display', action='store_true',
                       help='Do not display video while processing')
    parser.add_argument('--loop', action='store_true',
                       help='Loop video continuously (press Q to quit)')
    
    args = parser.parse_args()
    
    # Initialize tracker
    tracker = VehicleSpeedTracker(
        model_path=args.model,
        reference_width_meters=args.car_width
    )
    
    # Process video
    tracker.process_video(
        video_path=args.video,
        output_path=args.output,
        display=not args.no_display,
        loop=args.loop
    )


if __name__ == "__main__":
    main()