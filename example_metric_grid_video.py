import argparse
import cv2
from pathlib import Path
from packages.metric_transformation import CameraCalibrationTransformer


def process_video_with_metric_grid(
    video_path,
    output_path=None,
    camera_height=1.4,
    tilt_angle=-14,
    focal_length=26,
    sensor_width=36.0,
    distances=None,
    display=True,
    rotation=0,
):
    """
    Process a video file and overlay metric grid on each frame.
    """
    if rotation not in [0, 90, 180, 270]:
        raise ValueError(f"Rotation must be 0, 90, 180, or 270 degrees, got {rotation}")
    cap = cv2.VideoCapture(video_path)

    if not cap.isOpened():
        raise ValueError(f"Could not open video file: {video_path}")

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = int(cap.get(cv2.CAP_PROP_FPS))
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

    if rotation in [90, 270]:
        output_width, output_height = height, width
        calib_width, calib_height = height, width
    else:
        output_width, output_height = width, height
        calib_width, calib_height = width, height

    transformer = CameraCalibrationTransformer()
    transformer.calibrate(
        camera_height=camera_height,
        tilt_angle=tilt_angle,
        focal_length=focal_length,
        image_width=calib_width,
        image_height=calib_height,
        sensor_width=sensor_width,
    )

    out = None
    if output_path:
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = cv2.VideoWriter(output_path, fourcc, fps, (output_width, output_height))

    def rotate_frame(frame):
        if rotation == 0:
            return frame
        elif rotation == 90:
            return cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
        elif rotation == 180:
            return cv2.rotate(frame, cv2.ROTATE_180)
        elif rotation == 270:
            return cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
        return frame

    frame_number = 0

    try:
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break

            frame = rotate_frame(frame)

            frame_with_grid = transformer.draw_metric_grid(
                frame,
                distances=distances,
                show_horizon=True,
                line_thickness=2,
                text_scale=0.7,
            )

            if out:
                out.write(frame_with_grid)

            if display:
                cv2.imshow('Metric Grid Overlay (Press Q to quit)', frame_with_grid)
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

    print(f"\nProcessed {frame_number} frames")


def main():
    parser = argparse.ArgumentParser(
        description='Apply metric grid overlay to video frames'
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
        '--rotation',
        type=int,
        choices=[0, 90, 180, 270],
        default=0,
        help='Rotate video by specified degrees clockwise (default: 0, choices: 0, 90, 180, 270)'
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
        help='Camera sensor width in mm (default: 36.0)'
    )
    parser.add_argument(
        '--distances',
        type=float,
        nargs='+',
        default=[1, 2, 3, 5, 10, 15, 20, 30, 50],
        help='List of distances in meters to draw (default: 1 2 3 5 10 15 20 30 50)'
    )

    args = parser.parse_args()

    video_path = Path(args.video)
    if not video_path.exists():
        print(f"Error: Video file not found: {video_path}")
        return

    process_video_with_metric_grid(
        video_path=str(video_path),
        output_path=args.output,
        camera_height=args.camera_height,
        tilt_angle=args.tilt_angle,
        focal_length=args.focal_length,
        sensor_width=args.sensor_width,
        distances=args.distances,
        display=not args.no_display,
        rotation=args.rotation,
    )


if __name__ == "__main__":
    main()
