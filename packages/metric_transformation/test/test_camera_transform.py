from pathlib import Path

import cv2

from metric_transformation import CameraCalibrationTransformer


def main():
    CAMERA_HEIGHT = 1.4  # meters (1.5m = chest, 1.7m = eye level)
    TILT_ANGLE = -14  # negative = up, 0 = horizontal, positive = down
    FOCAL_LENGTH = 26  # mm

    image_path = Path(__file__).parent / "fixtures" / "test.jpg"

    image = cv2.imread(str(image_path))
    if image is None:
        raise FileNotFoundError(f"Could not load image from {image_path}")

    height, width = image.shape[:2]

    transformer = CameraCalibrationTransformer()
    transformer.calibrate(
        camera_height=CAMERA_HEIGHT,
        tilt_angle=TILT_ANGLE,
        focal_length=FOCAL_LENGTH,
        image_width=width,
        image_height=height,
        sensor_width=36.0
    )

    horizon_y = transformer.get_horizon_line()

    test_points = [
        (width // 2, int(height * 0.9), "Bottom center (close)"),
        (width // 2, int(height * 0.7), "Middle center"),
        (width // 2, int(height * 0.5), "Upper middle"),
        (width // 4, int(height * 0.8), "Left side"),
        (width * 3 // 4, int(height * 0.8), "Right side"),
    ]

    vis_image = image.copy()

    distances = [1, 2, 3, 5, 10, 15, 20, 30, 50]
    colors_cycle = [
        (255, 255, 0),  # Cyan
        (255, 200, 0),  # Light blue
        (255, 150, 0),  # Blue
        (200, 100, 0),  # Dark blue
        (150, 50, 0),  # Navy
        (100, 0, 0),  # Dark navy
        (50, 0, 50),  # Very dark
        (100, 0, 100),  # Purple
        (150, 0, 150),  # Light purple
    ]

    for distance, color in zip(distances, colors_cycle):
        points_on_line = []

        for x_px in range(0, width, max(1, width // 20)):
            y_min, y_max = int(horizon_y) + 1, height - 1

            if y_min >= y_max:
                continue

            try:
                best_y = None
                best_diff = float('inf')

                for y_px in range(y_min, y_max, max(1, (y_max - y_min) // 50)):
                    try:
                        x_m, y_m = transformer.transform_point((x_px, y_px))
                        diff = abs(y_m - distance)
                        if diff < best_diff:
                            best_diff = diff
                            best_y = y_px
                    except:
                        pass

                if best_y is not None and best_diff < distance * 0.1:
                    points_on_line.append((x_px, best_y))

            except:
                pass

        if len(points_on_line) > 1:
            for i in range(len(points_on_line) - 1):
                cv2.line(vis_image, points_on_line[i], points_on_line[i + 1],
                         color, 2)

            if points_on_line:
                label_x, label_y = points_on_line[len(points_on_line) // 2]
                cv2.putText(vis_image, f"{distance}m", (label_x + 10, label_y),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)

    for px, py, label in test_points:
        try:
            x_m, y_m = transformer.transform_point((px, py))

            cv2.circle(vis_image, (px, py), 10, (0, 255, 0), -1)
            cv2.circle(vis_image, (px, py), 12, (0, 0, 0), 2)
            cv2.putText(vis_image, f"{y_m:.1f}m", (px + 15, py),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
        except ValueError as e:
            continue

    cv2.line(vis_image, (0, int(horizon_y)), (width, int(horizon_y)),
             (0, 0, 255), 3)
    cv2.putText(vis_image, "HORIZON", (10, int(horizon_y) - 10),
                cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 255), 3)

    fixtures_dir = Path(__file__).parent / "fixtures"
    output_path = fixtures_dir / "camera_transform_test.png"
    cv2.imwrite(str(output_path), vis_image)

    print("Adjustment guide:")

    print("\nIf horizon is too high:")
    print("Decrease tilt_angle")
    print("\nIf horizon is too low:")
    print("Increase tilt_angle")

    print("\ntilt_angle:")
    print("Positive values = looking down")
    print("Zero = looking horizontal (horizon at image center)")
    print("Negative values = looking up")

    print("\nIf distances are too large:")
    print("Decrease camera_height")
    print("Or decrease tilt_angle")
    print("\nIf distances are too small:")
    print("Increase camera_height")
    print("Or increase tilt_angle")

    print("\nIf objects appear too far left/right:")
    print("Adjust focal_length")
    print("Smaller focal_length = wider angle")
    print("Larger focal_length = narrower angle")


if __name__ == "__main__":
    main()
