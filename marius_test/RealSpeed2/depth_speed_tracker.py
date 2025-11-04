"""
Vehicle Speed Tracking System - Idea 2
Uses Optical Flow + Monocular Depth Estimation for speed calculation
No calibration needed - works with any camera angle
"""

import cv2
import numpy as np
import torch
from collections import defaultdict
import time


class DepthSpeedTracker:
    def __init__(self, depth_model='ZoeDepth', fps=30, focal_length=None, 
                 reference_distance=None, reference_depth=None):
        """
        Initialize the depth-based speed tracker
        
        Args:
            depth_model: Which depth model to use ('ZoeDepth', 'MiDaS', 'DepthAnything')
            fps: Video frames per second
            focal_length: Camera focal length (will auto-estimate if None)
            reference_distance: Known distance to calibrate depth (meters)
            reference_depth: Depth value at reference_distance for calibration
        """
        self.fps = fps
        self.focal_length = focal_length
        self.reference_distance = reference_distance
        self.reference_depth = reference_depth
        
        # Calibration parameters - improved defaults for typical traffic cameras
        self.min_object_distance = 10  # meters (closer objects likely not cars)
        self.max_object_distance = 80  # meters (farther objects too uncertain)
        
        # Load depth estimation model
        print(f"Loading {depth_model} model...")
        self.depth_model = self._load_depth_model(depth_model)
        self.depth_model_name = depth_model
        
        # Track history: {track_id: [(x, y, depth, timestamp), ...]}
        self.track_history = defaultdict(list)
        
        # Speed estimates: {track_id: speed_kmh}
        self.speed_estimates = {}
        
        # Previous frame for optical flow
        self.prev_gray = None
        self.prev_features = None
        
        # Camera motion compensation
        self.global_motion = None
        self.use_motion_compensation = True
        
        # Tracking data
        self.next_track_id = 0
        self.active_tracks = {}  # {track_id: last_seen_frame}
        self.track_timeout = 30  # frames before track is considered lost        
        # Calibration factors
        self.depth_scale_factor = None
        self.depth_offset = None
        self.calibrated = False
        
        # Initialize calibration if reference provided
        if reference_distance and reference_depth:
            self._calibrate_depth(reference_distance, reference_depth)
        
        print("Depth model loaded successfully!")
    
    def _calibrate_depth(self, reference_distance, reference_depth):
        """Calibrate depth-to-distance mapping using known reference"""
        # Simple linear model: distance = scale * (1 - depth) + offset
        # This is more accurate than the arbitrary heuristic
        self.reference_distance = reference_distance
        self.reference_depth = reference_depth
        
        # Assume depth is normalized [0, 1] where 1=close, 0=far
        # Set scale based on reference
        self.depth_scale_factor = reference_distance / (1 - reference_depth + 0.01)
        self.depth_offset = self.min_object_distance
        self.calibrated = True
        
        print(f"Calibrated: {reference_distance}m at depth {reference_depth:.3f}")
    
    def depth_to_distance(self, depth_value):
        """
        Convert normalized depth to real-world distance
        
        Args:
            depth_value: Normalized depth (0-1, where 1=close)
            
        Returns:
            Estimated distance in meters
        """
        if self.calibrated:
            # Use calibrated values
            distance = self.depth_scale_factor * (1 - depth_value) + self.depth_offset
        else:
            # Improved heuristic for typical traffic camera
            # Assumes: depth 0.8-1.0 = 10-20m (close cars)
            #          depth 0.4-0.8 = 20-50m (medium distance)
            #          depth 0.0-0.4 = 50-80m (far cars)
            if depth_value > 0.8:
                # Very close
                distance = 10 + (1.0 - depth_value) * 50
            elif depth_value > 0.4:
                # Medium range
                distance = 20 + (0.8 - depth_value) * 75
            else:
                # Far range
                distance = 50 + (0.4 - depth_value) * 75
        
        # Clamp to reasonable range
        return np.clip(distance, self.min_object_distance, self.max_object_distance)
        
    def _load_depth_model(self, model_name):
        """Load the specified depth estimation model"""
        device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        print(f"Using device: {device}")
        
        if model_name == 'ZoeDepth':
            # ZoeDepth - high quality depth estimation
            try:
                model = torch.hub.load('isl-org/ZoeDepth', 'ZoeD_NK', pretrained=True)
                model = model.to(device)
                model.eval()
                return model
            except Exception as e:
                print(f"Failed to load ZoeDepth: {e}")
                print("Falling back to MiDaS...")
                return self._load_depth_model('MiDaS')
                
        elif model_name == 'MiDaS':
            # MiDaS - widely used, good quality
            model = torch.hub.load('intel-isl/MiDaS', 'MiDaS_small')
            model = model.to(device)
            model.eval()
            return model
            
        elif model_name == 'DepthAnything':
            # Depth Anything - newer, high quality
            try:
                model = torch.hub.load('LiheYoung/Depth-Anything', 'depth_anything_small', pretrained=True)
                model = model.to(device)
                model.eval()
                return model
            except Exception as e:
                print(f"Failed to load Depth Anything: {e}")
                print("Falling back to MiDaS...")
                return self._load_depth_model('MiDaS')
        
        else:
            raise ValueError(f"Unknown depth model: {model_name}")
    
    def estimate_depth(self, frame):
        """
        Estimate depth map from frame
        
        Args:
            frame: Input frame (BGR)
            
        Returns:
            Depth map (normalized)
        """
        device = next(self.depth_model.parameters()).device
        
        # Prepare image
        img_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        
        # Resize for model (smaller = faster)
        target_size = (384, 384)
        img_resized = cv2.resize(img_rgb, target_size)
        
        # Convert to tensor
        img_tensor = torch.from_numpy(img_resized).permute(2, 0, 1).unsqueeze(0)
        img_tensor = img_tensor.float() / 255.0
        img_tensor = img_tensor.to(device)
        
        # Get depth
        with torch.no_grad():
            if self.depth_model_name == 'ZoeDepth':
                depth = self.depth_model.infer(img_tensor)
            else:
                depth = self.depth_model(img_tensor)
        
        # Process depth map
        depth = depth.squeeze().cpu().numpy()
        
        # Resize back to original
        depth = cv2.resize(depth, (frame.shape[1], frame.shape[0]))
        
        # Normalize for better visualization and processing
        depth = (depth - depth.min()) / (depth.max() - depth.min() + 1e-8)
        
        return depth
    
    def estimate_global_motion(self, prev_gray, curr_gray):
        """
        Estimate global camera motion between frames
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
            
            # Track these features
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
    
    def detect_and_track_features(self, gray_frame, frame_number):
        """
        Detect and track features using optical flow
        Enhanced with camera motion compensation for handheld video
        
        Args:
            gray_frame: Grayscale frame
            frame_number: Current frame number
            
        Returns:
            List of tracked features with movement (compensated for camera motion)
        """
        if self.prev_gray is None:
            # First frame - detect features
            self.prev_features = cv2.goodFeaturesToTrack(
                gray_frame,
                maxCorners=200,
                qualityLevel=0.01,
                minDistance=20,
                blockSize=7
            )
            self.prev_gray = gray_frame
            return []
        
        # Estimate global camera motion (for handheld compensation)
        if self.use_motion_compensation:
            self.global_motion = self.estimate_global_motion(self.prev_gray, gray_frame)
        
        # Calculate optical flow
        if self.prev_features is not None and len(self.prev_features) > 0:
            next_features, status, error = cv2.calcOpticalFlowPyrLK(
                self.prev_gray,
                gray_frame,
                self.prev_features,
                None,
                winSize=(21, 21),
                maxLevel=3,
                criteria=(cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 10, 0.03)
            )
            
            # Filter good features
            good_new = next_features[status == 1]
            good_old = self.prev_features[status == 1]
            
            # Calculate displacement
            displacements = good_new - good_old
            
            # Compensate for global camera motion (handheld shake)
            if self.global_motion is not None:
                # Subtract camera motion from all displacements
                displacements = displacements - self.global_motion
            
            # Store results
            tracked_features = []
            for new_pt, old_pt, disp in zip(good_new, good_old, displacements):
                displacement_magnitude = np.linalg.norm(disp)
                
                # Filter out static points (now compensated for camera motion)
                # Increased threshold since we want real object motion
                if displacement_magnitude > 2.5:  # Increased from 1.0
                    tracked_features.append({
                        'old_pos': old_pt,
                        'new_pos': new_pt,
                        'displacement': disp,  # Already compensated
                        'magnitude': displacement_magnitude
                    })
        else:
            tracked_features = []
        
        # Update for next frame
        self.prev_gray = gray_frame
        self.prev_features = cv2.goodFeaturesToTrack(
            gray_frame,
            maxCorners=200,
            qualityLevel=0.01,
            minDistance=20,
            blockSize=7
        )
        
        return tracked_features
    
    def cluster_features_into_objects(self, tracked_features, depth_map):
        """
        Cluster tracked features into distinct moving objects (vehicles)
        Enhanced to filter out non-vehicle objects
        
        Args:
            tracked_features: List of tracked feature points
            depth_map: Depth map of the frame
            
        Returns:
            List of detected objects with their properties
        """
        if len(tracked_features) == 0:
            return []
        
        # Extract feature data
        positions = np.array([f['new_pos'] for f in tracked_features])
        
        # Filter by movement magnitude - cars move more consistently
        magnitudes = np.array([f['magnitude'] for f in tracked_features])
        movement_threshold = 2.0  # Increased from 1.0 to filter small movements
        valid_mask = magnitudes > movement_threshold
        
        if valid_mask.sum() < 3:
            return []
        
        positions = positions[valid_mask]
        tracked_features = [f for i, f in enumerate(tracked_features) if valid_mask[i]]
        
        # Simple spatial clustering
        objects = []
        used = np.zeros(len(positions), dtype=bool)
        
        for i, pos in enumerate(positions):
            if used[i]:
                continue
            
            # Find nearby points (within 80 pixels - car-sized clusters)
            distances = np.linalg.norm(positions - pos, axis=1)
            cluster_mask = distances < 80  # Reduced from 100 for tighter clustering
            cluster_indices = np.where(cluster_mask)[0]
            
            # Cars need decent number of feature points
            if len(cluster_indices) < 5:  # Increased from 3
                continue
            
            # Mark as used
            used[cluster_indices] = True
            
            # Get cluster properties
            cluster_features = [tracked_features[idx] for idx in cluster_indices]
            cluster_positions = positions[cluster_indices]
            
            # Calculate center
            center = cluster_positions.mean(axis=0)
            
            # Get depth at center
            cx, cy = int(center[0]), int(center[1])
            cx = max(0, min(cx, depth_map.shape[1] - 1))
            cy = max(0, min(cy, depth_map.shape[0] - 1))
            depth = depth_map[cy, cx]
            
            # Calculate average displacement
            avg_displacement = np.mean([f['displacement'] for f in cluster_features], axis=0)
            avg_magnitude = np.mean([f['magnitude'] for f in cluster_features])
            
            # Calculate bounding box
            x_min, y_min = cluster_positions.min(axis=0)
            x_max, y_max = cluster_positions.max(axis=0)
            
            # Filter by bounding box size - should be car-sized
            bbox_width = x_max - x_min
            bbox_height = y_max - y_min
            bbox_area = bbox_width * bbox_height
            
            # Cars typically have: 40-300 pixels width, 30-200 height, area 1200-60000
            if bbox_width < 40 or bbox_width > 300:
                continue
            if bbox_height < 30 or bbox_height > 200:
                continue
            if bbox_area < 1200 or bbox_area > 60000:
                continue
            
            # Filter by aspect ratio - cars are roughly rectangular
            aspect_ratio = bbox_width / (bbox_height + 1e-6)
            if aspect_ratio < 0.8 or aspect_ratio > 4.0:  # Cars: 0.8-4.0 ratio
                continue
            
            objects.append({
                'center': center,
                'depth': depth,
                'displacement': avg_displacement,
                'magnitude': avg_magnitude,
                'bbox': (x_min, y_min, x_max, y_max),
                'num_features': len(cluster_indices),
                'bbox_area': bbox_area
            })
        
        return objects
    
    def assign_track_ids(self, objects, frame_number):
        """
        Assign consistent track IDs to detected objects
        
        Args:
            objects: List of detected objects
            frame_number: Current frame number
            
        Returns:
            Objects with assigned track IDs
        """
        # Remove old tracks
        tracks_to_remove = []
        for track_id, last_frame in self.active_tracks.items():
            if frame_number - last_frame > self.track_timeout:
                tracks_to_remove.append(track_id)
        
        for track_id in tracks_to_remove:
            del self.active_tracks[track_id]
        
        # Assign IDs to objects
        for obj in objects:
            best_match_id = None
            best_match_dist = float('inf')
            
            # Try to match with existing tracks
            for track_id in self.active_tracks.keys():
                if track_id in self.track_history and len(self.track_history[track_id]) > 0:
                    last_pos = self.track_history[track_id][-1][:2]
                    dist = np.linalg.norm(obj['center'] - last_pos)
                    
                    if dist < 150 and dist < best_match_dist:  # Within 150 pixels
                        best_match_dist = dist
                        best_match_id = track_id
            
            # Assign ID
            if best_match_id is not None:
                obj['track_id'] = best_match_id
            else:
                obj['track_id'] = self.next_track_id
                self.next_track_id += 1
            
            # Update active tracks
            self.active_tracks[obj['track_id']] = frame_number
        
        return objects
    
    def calculate_speed(self, track_id, position, depth, timestamp):
        """
        Calculate real-world speed from position and depth changes
        Improved with better distance estimation and filtering
        
        Args:
            track_id: ID of the tracked object
            position: Current position (x, y)
            depth: Normalized depth value (0-1, where smaller = closer)
            timestamp: Current timestamp in seconds
            
        Returns:
            Speed in km/h or None
        """
        # Store history
        self.track_history[track_id].append((position[0], position[1], depth, timestamp))
        
        # Keep limited history
        if len(self.track_history[track_id]) > 30:
            self.track_history[track_id] = self.track_history[track_id][-30:]
        
        # Need enough history - increased requirement for more stable estimates
        if len(self.track_history[track_id]) < 15:
            return None
        
        # Get recent history - use more frames for smoother estimate
        history = self.track_history[track_id][-20:]
        
        if len(history) < 2:
            return None
        
        # Calculate speed
        first_entry = history[0]
        last_entry = history[-1]
        
        # Pixel displacement
        pixel_disp = np.linalg.norm([last_entry[0] - first_entry[0], 
                                     last_entry[1] - first_entry[1]])
        
        # Time difference
        time_diff = last_entry[3] - first_entry[3]
        
        if time_diff <= 0:
            return None
        
        # Convert depth to distance using improved method
        avg_depth = (first_entry[2] + last_entry[2]) / 2
        estimated_distance = self.depth_to_distance(avg_depth)
        
        # Auto-estimate focal length if not provided
        # Improved estimation based on typical camera FOV
        if self.focal_length is None:
            # For typical cameras: focal_length ≈ image_width / (2 * tan(FOV/2))
            # Assuming ~60 degree horizontal FOV and 1920 pixel width
            self.focal_length = 1600  # Increased from 1000 for more realistic speeds
        
        # Convert pixel displacement to real-world displacement
        # Using pinhole camera model: real_size = (pixel_size * distance) / focal_length
        real_displacement = (pixel_disp * estimated_distance) / self.focal_length
        
        # Calculate speed
        speed_mps = real_displacement / time_diff
        speed_kmh = speed_mps * 3.6
        
        # More restrictive filter for realistic car speeds
        # Filter out unrealistic speeds (cars typically 5-120 km/h)
        if 5 < speed_kmh < 120:
            # Additional sanity check: smooth with previous estimate
            if track_id in self.speed_estimates:
                prev_speed = self.speed_estimates[track_id]
                # Don't allow sudden jumps > 30 km/h
                if abs(speed_kmh - prev_speed) > 30:
                    # Average with previous for smoother transition
                    speed_kmh = (speed_kmh + prev_speed) / 2
            
            self.speed_estimates[track_id] = speed_kmh
            return speed_kmh
        
        return None
    
    def process_frame(self, frame, frame_number):
        """
        Process a single frame
        
        Args:
            frame: Input frame
            frame_number: Frame number
            
        Returns:
            Annotated frame with tracking and speed information
        """
        current_time = frame_number / self.fps
        
        # Convert to grayscale for optical flow
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
        # Estimate depth (every N frames for performance)
        if frame_number % 3 == 0 or not hasattr(self, 'last_depth'):
            self.last_depth = self.estimate_depth(frame)
        
        # Track features with optical flow
        tracked_features = self.detect_and_track_features(gray, frame_number)
        
        # Cluster features into objects
        objects = self.cluster_features_into_objects(tracked_features, self.last_depth)
        
        # Assign track IDs
        objects = self.assign_track_ids(objects, frame_number)
        
        # Create annotated frame
        annotated_frame = frame.copy()
        
        # Draw depth map (semi-transparent overlay)
        if hasattr(self, 'last_depth'):
            depth_colored = cv2.applyColorMap(
                (self.last_depth * 255).astype(np.uint8), 
                cv2.COLORMAP_MAGMA
            )
            annotated_frame = cv2.addWeighted(annotated_frame, 0.7, depth_colored, 0.3, 0)
        
        # Draw tracked objects
        for obj in objects:
            track_id = obj['track_id']
            center = obj['center']
            depth = obj['depth']
            bbox = obj['bbox']
            
            # Calculate speed
            speed = self.calculate_speed(track_id, center, depth, current_time)
            
            # Draw bounding box
            color = (0, 255, 0) if speed else (255, 0, 0)
            x1, y1, x2, y2 = [int(v) for v in bbox]
            cv2.rectangle(annotated_frame, (x1, y1), (x2, y2), color, 2)
            
            # Draw center point
            cv2.circle(annotated_frame, tuple(center.astype(int)), 5, color, -1)
            
            # Prepare label
            label = f"ID:{track_id}"
            if speed:
                label += f" {speed:.1f}km/h"
            label += f" D:{depth:.2f}"
            
            # Draw label
            (w, h), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 2)
            cv2.rectangle(annotated_frame, (x1, y1 - h - 5), (x1 + w, y1), color, -1)
            cv2.putText(annotated_frame, label, (x1, y1 - 3), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 2)
            
            # Draw tracking trail
            if track_id in self.track_history and len(self.track_history[track_id]) > 1:
                points = [(int(h[0]), int(h[1])) for h in self.track_history[track_id][-20:]]
                for i in range(1, len(points)):
                    cv2.line(annotated_frame, points[i-1], points[i], color, 2)
        
        # Add info overlay
        camera_motion_status = "Yes" if self.global_motion is not None else "No"
        info_lines = [
            f"Frame: {frame_number}",
            f"Tracked: {len(self.speed_estimates)}",
            f"Active: {len(objects)}",
            f"Model: {self.depth_model_name}",
            f"Focal: {self.focal_length if self.focal_length else 'Auto'}",
            f"Calibrated: {'Yes' if self.calibrated else 'No'}",
            f"Cam Motion: {camera_motion_status}"
        ]
        
        y_offset = 25
        for line in info_lines:
            cv2.putText(annotated_frame, line, (10, y_offset), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)
            y_offset += 25
        
        return annotated_frame
    
    def process_video(self, video_path, output_path=None, display=True, loop=False):
        """
        Process entire video
        
        Args:
            video_path: Path to input video
            output_path: Path to save output (optional)
            display: Show video while processing
            loop: Loop video continuously
        """
        # Get video properties first
        cap_temp = cv2.VideoCapture(video_path)
        width = int(cap_temp.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap_temp.get(cv2.CAP_PROP_FRAME_HEIGHT))
        self.fps = int(cap_temp.get(cv2.CAP_PROP_FPS))
        total_frames = int(cap_temp.get(cv2.CAP_PROP_FRAME_COUNT))
        cap_temp.release()
        
        print(f"\nVideo: {width}x{height} @ {self.fps} FPS, {total_frames} frames")
        if loop:
            print("Loop mode enabled - press 'Q' to quit")
        
        # Setup video writer
        out = None
        if output_path:
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            out = cv2.VideoWriter(output_path, fourcc, self.fps, (width, height))
        
        frame_number = 0
        
        try:
            while True:
                cap = cv2.VideoCapture(video_path)
                
                while cap.isOpened():
                    ret, frame = cap.read()
                    if not ret:
                        break
                    
                    # Process frame
                    annotated_frame = self.process_frame(frame, frame_number)
                    
                    # Write output
                    if out and frame_number < total_frames:
                        out.write(annotated_frame)
                    
                    # Display
                    if display:
                        cv2.imshow('Depth-Based Speed Tracking (Q to quit)', annotated_frame)
                        if cv2.waitKey(1) & 0xFF == ord('q'):
                            cap.release()
                            raise KeyboardInterrupt
                    
                    frame_number += 1
                    
                    # Progress
                    if frame_number % 30 == 0:
                        if loop:
                            loop_num = frame_number // total_frames + 1
                            frame_in_loop = frame_number % total_frames
                            print(f"Loop #{loop_num} - Frame: {frame_in_loop}/{total_frames}")
                        else:
                            progress = (frame_number / total_frames) * 100
                            print(f"Progress: {progress:.1f}% ({frame_number}/{total_frames})")
                
                cap.release()
                
                if not loop:
                    break
                
                print("Completed loop, restarting...")
        
        except KeyboardInterrupt:
            print("\nStopped by user")
        
        finally:
            if out:
                out.release()
            if display:
                cv2.destroyAllWindows()
        
        # Print summary
        print("\n=== Speed Tracking Summary ===")
        print(f"Total objects tracked: {len(self.speed_estimates)}")
        if self.speed_estimates:
            speeds = list(self.speed_estimates.values())
            print(f"Average speed: {np.mean(speeds):.1f} km/h")
            print(f"Max speed: {np.max(speeds):.1f} km/h")
            print(f"Min speed: {np.min(speeds):.1f} km/h")
            
            print("\nIndividual speeds:")
            for track_id, speed in sorted(self.speed_estimates.items()):
                print(f"  Object {track_id}: {speed:.1f} km/h")


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description='Depth-Based Vehicle Speed Tracking')
    parser.add_argument('video', type=str, help='Path to input video')
    parser.add_argument('--output', type=str, help='Path to output video')
    parser.add_argument('--model', type=str, default='MiDaS',
                       choices=['ZoeDepth', 'MiDaS', 'DepthAnything'],
                       help='Depth model to use (default: MiDaS)')
    parser.add_argument('--focal-length', type=float, help='Camera focal length in pixels')
    parser.add_argument('--reference-distance', type=float, 
                       help='Known distance for calibration (meters)')
    parser.add_argument('--reference-depth', type=float,
                       help='Depth value at reference distance (0-1)')
    parser.add_argument('--no-display', action='store_true', help='No display')
    parser.add_argument('--loop', action='store_true', help='Loop video')
    
    args = parser.parse_args()
    
    # Validate calibration args
    if (args.reference_distance and not args.reference_depth) or \
       (args.reference_depth and not args.reference_distance):
        parser.error("--reference-distance and --reference-depth must be used together")
    
    # Initialize tracker
    tracker = DepthSpeedTracker(
        depth_model=args.model,
        focal_length=args.focal_length,
        reference_distance=args.reference_distance,
        reference_depth=args.reference_depth
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