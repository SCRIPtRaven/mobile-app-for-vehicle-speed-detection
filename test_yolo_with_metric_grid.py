import argparse
import cv2
import numpy as np
from pathlib import Path

from packages.yolo_tracker.vehicle_speed_tracker import VehicleSpeedTracker
from packages.metric_transformation.camera_calibration import CameraCalibrationTransformer


class CombinedTrackerWithMetrics:
    def __init__(
        self,
        model_path='yolov8n.pt',
        reference_width_meters=1.8,
        camera_height=1.4,
        tilt_angle=-14,
        focal_length=26,
        sensor_width=36.0,
        rotation=0,
    ):
        model_path = Path(model_path)
        self.tracker = VehicleSpeedTracker(
            model_path=str(model_path),
            reference_width_meters=reference_width_meters,
        )

        self.metric_transformer = CameraCalibrationTransformer()

        self.calibration_params = {
            'camera_height': camera_height,
            'tilt_angle': tilt_angle,
            'focal_length': focal_length,
            'sensor_width': sensor_width,
        }

        if rotation not in [0, 90, 180, 270]:
            raise ValueError(f"Rotation must be 0, 90, 180, or 270 degrees, got {rotation}")
        self.rotation = rotation

        self.transformer_calibrated = False

        from collections import defaultdict
        self.metric_track_history = defaultdict(list)
        self.metric_speed_estimates = {}

        self.bbox_speed_estimates = {}

    def rotate_frame(self, frame):
        if self.rotation == 0:
            return frame
        elif self.rotation == 90:
            return cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
        elif self.rotation == 180:
            return cv2.rotate(frame, cv2.ROTATE_180)
        elif self.rotation == 270:
            return cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
        return frame

    def calculate_bbox_speed(self, track_id, pixel_position, current_time, bbox_width):
        speed = self.tracker.calculate_speed(
            track_id,
            pixel_position,
            current_time,
            bbox_width
        )

        if speed is not None:
            self.bbox_speed_estimates[track_id] = speed

        return speed

    def calculate_metric_speed(self, track_id, pixel_position, current_time):
        if not self.transformer_calibrated:
            return None

        try:
            x_meters, y_meters = self.metric_transformer.transform_point(pixel_position)

            self.metric_track_history[track_id].append((x_meters, y_meters, current_time))

            if len(self.metric_track_history[track_id]) > 30:
                self.metric_track_history[track_id] = self.metric_track_history[track_id][-30:]

            if len(self.metric_track_history[track_id]) < 10:
                return None

            recent_history = self.metric_track_history[track_id][-15:]

            if len(recent_history) < 2:
                return None

            first_pos = recent_history[0]
            last_pos = recent_history[-1]

            displacement_x = last_pos[0] - first_pos[0]
            displacement_y = last_pos[1] - first_pos[1]
            displacement_meters = np.sqrt(displacement_x**2 + displacement_y**2)

            time_diff = last_pos[2] - first_pos[2]

            if time_diff <= 0:
                return None

            speed_mps = displacement_meters / time_diff
            speed_kmh = speed_mps * 3.6

            if 0 < speed_kmh < 200:
                self.metric_speed_estimates[track_id] = speed_kmh
                return speed_kmh

            return None

        except (ValueError, RuntimeError) as e:
            return None

    def process_frame(self, frame, frame_number):
        frame = self.rotate_frame(frame)

        if not self.transformer_calibrated:
            height, width = frame.shape[:2]
            self.metric_transformer.calibrate(
                camera_height=self.calibration_params['camera_height'],
                tilt_angle=self.calibration_params['tilt_angle'],
                focal_length=self.calibration_params['focal_length'],
                image_width=width,
                image_height=height,
                sensor_width=self.calibration_params['sensor_width'],
            )
            self.transformer_calibrated = True

        frame_with_grid = self.metric_transformer.draw_metric_grid(
            frame,
            distances=[2, 5, 10, 15, 20, 30, 50],
            show_horizon=True,
            line_thickness=1,
            text_scale=0.5,
        )

        results = self.tracker.model.track(
            frame_with_grid,
            persist=True,
            classes=self.tracker.vehicle_classes,
            verbose=False,
            tracker="bytetrack.yaml"
        )

        annotated_frame = frame_with_grid.copy()
        current_time = frame_number / self.tracker.fps

        if results[0].boxes is not None and results[0].boxes.id is not None:
            boxes = results[0].boxes.xyxy.cpu().numpy()
            track_ids = results[0].boxes.id.cpu().numpy().astype(int)
            confidences = results[0].boxes.conf.cpu().numpy()
            classes = results[0].boxes.cls.cpu().numpy().astype(int)

            for box, track_id, conf, cls in zip(boxes, track_ids, confidences, classes):
                x1, y1, x2, y2 = box

                center_x = (x1 + x2) / 2
                center_y = y2
                bbox_width = x2 - x1

                bbox_speed = self.calculate_bbox_speed(
                    track_id,
                    (center_x, center_y),
                    current_time,
                    bbox_width
                )

                metric_speed = self.calculate_metric_speed(
                    track_id,
                    (center_x, center_y),
                    current_time
                )

                if metric_speed and bbox_speed:
                    color = (0, 255, 0)
                elif metric_speed or bbox_speed:
                    color = (0, 255, 255)
                else:
                    color = (0, 0, 255)

                cv2.rectangle(annotated_frame, (int(x1), int(y1)), (int(x2), int(y2)), color, 2)

                label = f"ID: {track_id}"
                if bbox_speed and metric_speed:
                    diff = abs(metric_speed - bbox_speed)
                    label += f"\nBBox: {bbox_speed:.1f} km/h"
                    label += f"\nMetric: {metric_speed:.1f} km/h"
                    label += f"\nDiff: {diff:.1f} km/h"
                elif bbox_speed:
                    label += f"\nBBox: {bbox_speed:.1f} km/h"
                elif metric_speed:
                    label += f"\nMetric: {metric_speed:.1f} km/h"

                label_lines = label.split('\n')
                line_height = 20
                max_width = 0

                for line in label_lines:
                    (w, h), _ = cv2.getTextSize(line, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
                    max_width = max(max_width, w)

                bg_height = len(label_lines) * line_height + 5
                cv2.rectangle(
                    annotated_frame,
                    (int(x1), int(y1) - bg_height - 5),
                    (int(x1) + max_width + 10, int(y1)),
                    color,
                    -1
                )

                y_offset = int(y1) - bg_height
                for i, line in enumerate(label_lines):
                    cv2.putText(
                        annotated_frame,
                        line,
                        (int(x1) + 5, y_offset + (i + 1) * line_height),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.5,
                        (255, 255, 255),
                        1
                    )

                if track_id in self.metric_track_history and len(self.metric_track_history[track_id]) > 1:
                    if track_id in self.tracker.track_history and len(self.tracker.track_history[track_id]) > 1:
                        points = [(int(h[0]), int(h[1])) for h in self.tracker.track_history[track_id][-20:]]
                        for i in range(1, len(points)):
                            cv2.line(annotated_frame, points[i-1], points[i], color, 2)

                self.tracker.track_history[track_id].append(
                    (center_x, center_y, current_time, x2 - x1)
                )
                if len(self.tracker.track_history[track_id]) > 30:
                    self.tracker.track_history[track_id] = self.tracker.track_history[track_id][-30:]

        info_lines = [
            f"Frame: {frame_number}",
            f"Tracked: {len(self.metric_speed_estimates)} vehicles",
            f"BBox Method: {len(self.bbox_speed_estimates)} speeds",
            f"Metric Method: {len(self.metric_speed_estimates)} speeds",
            "Green=Both | Yellow=One | Red=None"
        ]

        y_pos = 30
        for line in info_lines:
            cv2.putText(
                annotated_frame,
                line,
                (10, y_pos),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.6,
                (0, 255, 255),
                2
            )
            y_pos += 25

        return annotated_frame

    def process_video(
        self,
        video_path,
        output_path=None,
        display=True,
        max_frames=None,
    ):
        cap = cv2.VideoCapture(video_path)

        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        fps = int(cap.get(cv2.CAP_PROP_FPS))
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

        self.tracker.fps = fps

        if self.rotation in [90, 270]:
            output_width, output_height = height, width
        else:
            output_width, output_height = width, height

        out = None
        if output_path:
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            out = cv2.VideoWriter(output_path, fourcc, fps, (output_width, output_height))

        frame_number = 0

        try:
            while cap.isOpened():
                ret, frame = cap.read()
                if not ret:
                    break

                if max_frames and frame_number >= max_frames:
                    print(f"Reached max_frames limit ({max_frames})")
                    break

                annotated_frame = self.process_frame(frame, frame_number)

                if out:
                    out.write(annotated_frame)

                if display:
                    cv2.imshow('YOLO Tracking + Metric Grid (Press Q to quit)', annotated_frame)
                    if cv2.waitKey(1) & 0xFF == ord('q'):
                        break

                frame_number += 1

                if frame_number % 30 == 0:
                    progress = (frame_number / total_frames) * 100
                    print(f"Progress: {progress:.1f}% ({frame_number}/{total_frames})")

        except KeyboardInterrupt:
            print("\nStopped by user")

        finally:
            cap.release()
            if out:
                out.release()
            if display:
                cv2.destroyAllWindows()

        print("\n=== Processing Summary ===")
        print(f"Frames processed: {frame_number}")
        print(f"Total vehicles tracked: {max(len(self.metric_speed_estimates), len(self.bbox_speed_estimates))}")

        print("\n--- BBox Method ---")
        if self.bbox_speed_estimates:
            bbox_speeds = list(self.bbox_speed_estimates.values())
            print(f"Vehicles with speed: {len(self.bbox_speed_estimates)}")
            print(f"Average speed: {np.mean(bbox_speeds):.1f} km/h")
            print(f"Max speed: {np.max(bbox_speeds):.1f} km/h")
            print(f"Min speed: {np.min(bbox_speeds):.1f} km/h")
        else:
            print("No speeds calculated")

        print("\n--- Metric Method ---")
        if self.metric_speed_estimates:
            metric_speeds = list(self.metric_speed_estimates.values())
            print(f"Vehicles with speed: {len(self.metric_speed_estimates)}")
            print(f"Average speed: {np.mean(metric_speeds):.1f} km/h")
            print(f"Max speed: {np.max(metric_speeds):.1f} km/h")
            print(f"Min speed: {np.min(metric_speeds):.1f} km/h")
        else:
            print("No speeds calculated")

        print("\n--- Method Comparison ---")
        common_ids = set(self.metric_speed_estimates.keys()) & set(self.bbox_speed_estimates.keys())
        if common_ids:
            print(f"Vehicles with both measurements: {len(common_ids)}")
            differences = []
            print("\nIndividual vehicle comparison:")
            for track_id in sorted(common_ids):
                bbox_spd = self.bbox_speed_estimates[track_id]
                metric_spd = self.metric_speed_estimates[track_id]
                diff = metric_spd - bbox_spd
                diff_pct = (diff / metric_spd * 100) if metric_spd > 0 else 0
                differences.append(abs(diff))
                print(f"  Vehicle {track_id}:")
                print(f"    BBox:   {bbox_spd:6.1f} km/h")
                print(f"    Metric: {metric_spd:6.1f} km/h")
                print(f"    Diff:   {diff:+6.1f} km/h ({diff_pct:+.1f}%)")

            if differences:
                print(f"\nAverage absolute difference: {np.mean(differences):.1f} km/h")
                print(f"Max absolute difference: {np.max(differences):.1f} km/h")
        else:
            print("No vehicles with both measurements for comparison")


def main():
    parser = argparse.ArgumentParser(
        description='Combined YOLO Vehicle Tracking with Metric Grid Overlay'
    )

    parser.add_argument(
        'video',
        type=str,
        help='Path to input video file'
    )
    parser.add_argument(
        '--output',
        type=str,
        help='Path to output video file (optional)'
    )
    parser.add_argument(
        '--no-display',
        action='store_true',
        help='Do not display video while processing'
    )
    parser.add_argument(
        '--max-frames',
        type=int,
        help='Maximum number of frames to process (useful for testing)'
    )
    parser.add_argument(
        '--rotation',
        type=int,
        choices=[0, 90, 180, 270],
        default=0,
        help='Rotate video by specified degrees clockwise (default: 0, choices: 0, 90, 180, 270)'
    )

    parser.add_argument(
        '--model',
        type=str,
        default='yolov8n.pt',
        help='Path to YOLO model file (.pt). Can be a standard model (yolov8n.pt, yolov8s.pt, etc.) or a custom trained model (default: yolov8n.pt)'
    )
    parser.add_argument(
        '--car-width',
        type=float,
        default=1.8,
        help='Reference car width in meters (default: 1.8)'
    )

    parser.add_argument(
        '--camera-height',
        type=float,
        default=1.4,
        help='Camera height above ground in meters (default: 1.4)'
    )
    parser.add_argument(
        '--tilt-angle',
        type=float,
        default=-14,
        help='Camera tilt angle in degrees, negative=up, positive=down (default: -14)'
    )
    parser.add_argument(
        '--focal-length',
        type=float,
        default=26,
        help='Camera focal length in mm (default: 26)'
    )
    parser.add_argument(
        '--sensor-width',
        type=float,
        default=36.0,
        help='Camera sensor width in mm (default: 36.0 for full-frame)'
    )

    args = parser.parse_args()

    video_path = Path(args.video)
    if not video_path.exists():
        print(f"Error: Video file not found: {video_path}")
        return

    combined_tracker = CombinedTrackerWithMetrics(
        model_path=args.model,
        reference_width_meters=args.car_width,
        camera_height=args.camera_height,
        tilt_angle=args.tilt_angle,
        focal_length=args.focal_length,
        sensor_width=args.sensor_width,
        rotation=args.rotation,
    )

    combined_tracker.process_video(
        video_path=str(video_path),
        output_path=args.output,
        display=not args.no_display,
        max_frames=args.max_frames,
    )


if __name__ == "__main__":
    main()
