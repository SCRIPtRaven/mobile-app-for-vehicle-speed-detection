"""
Example script demonstrating how to use the VehicleSpeedTracker class
"""

from vehicle_speed_tracker import VehicleSpeedTracker
import cv2
import numpy as np


def example_basic_usage():
    """
    Basic usage example - process a video file
    """
    print("=== Example 1: Basic Usage ===")
    
    tracker = VehicleSpeedTracker(
        model_path='yolov8n.pt',
        reference_width_meters=1.8,
        fps=30
    )
    
    # Process video
    tracker.process_video(
        video_path='your_video.mp4',
        output_path='output.mp4',
        display=True
    )
    
    print(f"\nTotal vehicles tracked: {len(tracker.speed_estimates)}")


def example_custom_parameters():
    """
    Example with custom parameters
    """
    print("=== Example 2: Custom Parameters ===")
    
    # Use larger model for better accuracy
    # Use 2.0m for larger vehicles (SUVs, vans)
    tracker = VehicleSpeedTracker(
        model_path='yolov8m.pt',
        reference_width_meters=2.0,
        fps=30
    )
    
    tracker.process_video(
        video_path='highway.mp4',
        output_path='highway_tracked.mp4',
        display=False  # Don't show window
    )


def example_access_results():
    """
    Example showing how to access and analyze results
    """
    print("=== Example 3: Access Results ===")
    
    tracker = VehicleSpeedTracker()
    tracker.process_video('traffic.mp4', display=False)
    
    # Access individual vehicle speeds
    for vehicle_id, speed in tracker.speed_estimates.items():
        print(f"Vehicle {vehicle_id}: {speed:.1f} km/h")
        
        # Check if speeding (assuming 60 km/h speed limit)
        if speed > 60:
            print(f"  ⚠️ SPEEDING! {speed - 60:.1f} km/h over limit")
    
    # Calculate statistics
    if tracker.speed_estimates:
        speeds = list(tracker.speed_estimates.values())
        avg_speed = np.mean(speeds)
        max_speed = np.max(speeds)
        min_speed = np.min(speeds)
        
        print(f"\nStatistics:")
        print(f"  Average: {avg_speed:.1f} km/h")
        print(f"  Maximum: {max_speed:.1f} km/h")
        print(f"  Minimum: {min_speed:.1f} km/h")
        print(f"  Range: {max_speed - min_speed:.1f} km/h")


def example_frame_by_frame():
    """
    Example processing video frame by frame for more control
    """
    print("=== Example 4: Frame-by-Frame Processing ===")
    
    tracker = VehicleSpeedTracker()
    
    # Open video
    cap = cv2.VideoCapture('your_video.mp4')
    fps = int(cap.get(cv2.CAP_PROP_FPS))
    tracker.fps = fps
    
    frame_number = 0
    
    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break
        
        # Process single frame
        annotated_frame = tracker.process_frame(frame, frame_number)
        
        # Do custom processing here
        # For example, save frame if high speed detected
        current_speeds = [
            speed for speed in tracker.speed_estimates.values() 
            if speed > 80
        ]
        
        if current_speeds:
            cv2.imwrite(f'speeding_frame_{frame_number}.jpg', annotated_frame)
            print(f"Saved frame {frame_number} with speeds: {current_speeds}")
        
        frame_number += 1
        
        # Press 'q' to quit
        cv2.imshow('Tracking', annotated_frame)
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break
    
    cap.release()
    cv2.destroyAllWindows()


def example_export_to_csv():
    """
    Example showing how to export results to CSV
    """
    print("=== Example 5: Export to CSV ===")
    
    import csv
    
    tracker = VehicleSpeedTracker()
    tracker.process_video('traffic.mp4', display=False)
    
    # Export results to CSV
    with open('speed_results.csv', 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['Vehicle ID', 'Speed (km/h)', 'Status'])
        
        for vehicle_id, speed in sorted(tracker.speed_estimates.items()):
            status = 'Speeding' if speed > 60 else 'Normal'
            writer.writerow([vehicle_id, f'{speed:.1f}', status])
    
    print("Results exported to speed_results.csv")


def example_with_manual_calibration():
    """
    Example with manual pixel-to-meter calibration
    """
    print("=== Example 6: Manual Calibration ===")
    
    tracker = VehicleSpeedTracker()
    
    # Manually set calibration if you know the pixel-to-meter ratio
    # For example, if you measured that 100 pixels = 2 meters
    tracker.pixels_per_meter = 100 / 2.0  # 50 pixels per meter
    tracker.calibration_done = True
    
    print(f"Manual calibration set: {tracker.pixels_per_meter} pixels/meter")
    
    tracker.process_video('your_video.mp4', output_path='calibrated_output.mp4')


if __name__ == "__main__":
    print("Vehicle Speed Tracker - Examples")
    print("=" * 50)
    print("\nUncomment the example you want to run:\n")
    
    # Uncomment one of these to run:
    
    # example_basic_usage()
    # example_custom_parameters()
    # example_access_results()
    # example_frame_by_frame()
    # example_export_to_csv()
    # example_with_manual_calibration()
    
    print("\nNote: Replace 'your_video.mp4' with your actual video file path")
    print("First run will download the YOLO model automatically")