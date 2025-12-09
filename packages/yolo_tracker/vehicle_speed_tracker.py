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
    def __init__(self, model_path='yolov8n.pt', reference_width_meters=1.8, fps=30, use_motion_compensation=False):
        """
        Initialize the vehicle speed tracker

        Args:
            model_path: Path to YOLO model (will download if not exists)
            reference_width_meters: Average car width in meters (default: 1.8m)
            fps: Video frames per second
            use_motion_compensation: Enable camera motion compensation (default: False)
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

        # Camera motion compensation
        self.use_motion_compensation = use_motion_compensation
        self.prev_gray = None
        self.global_motion = None
        self.motion_history = []  # Store motion vectors for debugging
        
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

    def estimate_global_motion(self, prev_gray, curr_gray):
        """
        Estimate global camera motion between frames using optical flow
        This helps compensate for handheld camera shake

        Args:
            prev_gray: Previous grayscale frame
            curr_gray: Current grayscale frame

        Returns:
            Global motion vector (dx, dy) or None
        """
        try:
            # Detect features in corners (less likely to be moving objects)
            # Focus on edges and corners of frame for camera motion
            h, w = prev_gray.shape
            mask = np.zeros((h, w), dtype=np.uint8)

            # Only use outer 30% of frame (avoid center where cars are)
            border = int(min(h, w) * 0.15)
            mask[0:border, :] = 255  # Top
            mask[h-border:h, :] = 255  # Bottom
            mask[:, 0:border] = 255  # Left
            mask[:, w-border:w] = 255  # Right

            # Detect features in static areas
            static_features = cv2.goodFeaturesToTrack(
                prev_gray,
                maxCorners=100,
                qualityLevel=0.01,
                minDistance=30,
                mask=mask
            )

            if static_features is None or len(static_features) < 10:
                return None

            # Track these features using optical flow
            next_features, status, _ = cv2.calcOpticalFlowPyrLK(
                prev_gray,
                curr_gray,
                static_features,
                None,
                winSize=(21, 21),
                maxLevel=3
            )

            # Get good matches
            good_prev = static_features[status == 1]
            good_next = next_features[status == 1]

            if len(good_prev) < 10:
                return None

            # Calculate median motion (robust to outliers)
            motion_vectors = good_next - good_prev
            median_motion = np.median(motion_vectors, axis=0)

            # Only consider significant camera motion (> 2 pixels)
            if np.linalg.norm(median_motion) > 2.0:
                return median_motion

            return None

        except Exception as e:
            # If anything fails, don't compensate
            return None

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

        displacement_vector = last_pos - first_pos
        original_displacement = np.linalg.norm(displacement_vector)

        # Apply camera motion compensation if enabled
        if self.use_motion_compensation and len(self.motion_history) > 0:
            # Sum up actual camera motion over the tracking period
            # Use the most recent N motion samples where N = number of frames in history
            num_frames = len(recent_history) - 1
            recent_motions = self.motion_history[-num_frames:] if len(self.motion_history) >= num_frames else self.motion_history

            # Sum up the actual motion vectors
            total_camera_motion = np.sum(recent_motions, axis=0)

            print(f"[DEBUG Track {track_id}] Original displacement: {original_displacement:.2f}px over {num_frames} frames")
            print(f"[DEBUG Track {track_id}] Using {len(recent_motions)} motion samples")
            print(f"[DEBUG Track {track_id}] Total camera motion: {np.linalg.norm(total_camera_motion):.2f}px")

            # ADD camera motion to get true object motion
            # Optical flow gives us how static features moved (camera-induced motion)
            # Detected objects are stabilized by tracker, so we add back the camera motion
            displacement_vector += total_camera_motion
            compensated_displacement = np.linalg.norm(displacement_vector)

            print(f"[DEBUG Track {track_id}] After compensation: {compensated_displacement:.2f}px")
            print(f"[DEBUG Track {track_id}] Reduction: {original_displacement - compensated_displacement:.2f}px\n")

        displacement_pixels = np.linalg.norm(displacement_vector)
        
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
        # Estimate camera motion for compensation
        if self.use_motion_compensation:
            curr_gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

            if self.prev_gray is not None:
                self.global_motion = self.estimate_global_motion(self.prev_gray, curr_gray)
                if self.global_motion is not None:
                    motion_mag = np.linalg.norm(self.global_motion)
                    print(f"[MOTION Frame {frame_number}] Camera moving: {motion_mag:.2f}px ({self.global_motion[0]:.2f}, {self.global_motion[1]:.2f})")
                    self.motion_history.append(self.global_motion)
                    # Keep only recent motion history
                    if len(self.motion_history) > 30:
                        self.motion_history = self.motion_history[-30:]
                else:
                    print(f"[MOTION Frame {frame_number}] Camera stable (no significant motion)")

            self.prev_gray = curr_gray.copy()

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

        # Add motion compensation status
        if self.use_motion_compensation:
            motion_text = "Motion Compensation: "
            if self.global_motion is not None:
                motion_mag = np.linalg.norm(self.global_motion)
                motion_text += f"ACTIVE ({motion_mag:.1f}px)"
                motion_color = (0, 255, 0)  # Green when active
            else:
                motion_text += "IDLE (no motion)"
                motion_color = (0, 255, 255)  # Yellow when idle

            cv2.putText(
                annotated_frame,
                motion_text,
                (10, 60),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.7,
                motion_color,
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
    parser.add_argument('--motion-compensation', action='store_true',
                       help='Enable camera motion compensation for handheld videos')

    args = parser.parse_args()

    # Initialize tracker
    tracker = VehicleSpeedTracker(
        model_path=args.model,
        reference_width_meters=args.car_width,
        use_motion_compensation=args.motion_compensation
    )

    if args.motion_compensation:
        print("✓ Camera motion compensation ENABLED")
    else:
        print("✗ Camera motion compensation DISABLED (use --motion-compensation to enable)")
    
    # Process video
    tracker.process_video(
        video_path=args.video,
        output_path=args.output,
        display=not args.no_display,
        loop=args.loop
    )


if __name__ == "__main__":
    main()