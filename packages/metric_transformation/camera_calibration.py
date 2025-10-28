from typing import Tuple, Optional

import numpy as np

from .transformer import MetricTransformer


class CameraCalibrationTransformer(MetricTransformer):
    """
    Transformer that uses camera parameters to map pixels to real-world meters.
    The transformer assumes a flat ground plane and uses pinhole camera geometry.
    """

    def __init__(self):
        super().__init__()
        self._camera_height: Optional[float] = None
        self._tilt_angle: Optional[float] = None
        self._focal_length: Optional[float] = None
        self._image_width: Optional[int] = None
        self._image_height: Optional[int] = None
        self._sensor_width: Optional[float] = None

    def calibrate(
            self,
            camera_height: float,
            tilt_angle: float,
            focal_length: float,
            image_width: int,
            image_height: int,
            sensor_width: float = 36.0,
            pan_angle: float = 0.0,
    ) -> None:
        """
        Args:
            camera_height: Height of camera above ground in meters
            tilt_angle: Camera tilt angle in degrees
                       Positive = looking down, Negative = looking up
                       Examples: -10° = tilted up, 0° = horizontal, 10° = tilted down, 90° = straight down
            focal_length: Focal length in mm (can be from EXIF data)
            image_width: Image width in pixels
            image_height: Image height in pixels
            sensor_width: Camera sensor width in mm (default 36mm for full-frame equivalent)
            pan_angle: Camera pan angle in degrees (positive = looking right)
                      Usually 0 for forward-facing cameras
        """
        if camera_height <= 0:
            raise ValueError(f"camera_height must be positive, got {camera_height}")

        if not -45 <= tilt_angle <= 90:
            raise ValueError(f"tilt_angle must be between -45 and 90 degrees, got {tilt_angle}")

        if focal_length <= 0:
            raise ValueError(f"focal_length must be positive, got {focal_length}")

        if sensor_width <= 0:
            raise ValueError(f"sensor_width must be positive, got {sensor_width}")

        self._camera_height = camera_height
        self._tilt_angle = np.deg2rad(tilt_angle)
        self._pan_angle = np.deg2rad(pan_angle)
        self._focal_length = focal_length
        self._image_width = image_width
        self._image_height = image_height
        self._sensor_width = sensor_width

        self._fov_horizontal = 2 * np.arctan(sensor_width / (2 * focal_length))

        aspect_ratio = image_height / image_width
        self._sensor_height = sensor_width * aspect_ratio
        self._fov_vertical = 2 * np.arctan(self._sensor_height / (2 * focal_length))

        self._is_calibrated = True

    def calibrate_from_exif(
            self,
            camera_height: float,
            tilt_angle: float,
            exif_focal_length: float,
            exif_focal_length_35mm: Optional[float],
            image_width: int,
            image_height: int,
    ) -> None:
        """
        Calibrate using EXIF data from an image.

        Args:
            camera_height: Height of camera above ground in meters
            tilt_angle: Camera tilt angle in degrees
            exif_focal_length: Focal length from EXIF (in mm)
            exif_focal_length_35mm: 35mm equivalent focal length from EXIF
            image_width: Image width in pixels
            image_height: Image height in pixels
        """
        focal_length = exif_focal_length_35mm if exif_focal_length_35mm else exif_focal_length

        self.calibrate(
            camera_height=camera_height,
            tilt_angle=tilt_angle,
            focal_length=focal_length,
            image_width=image_width,
            image_height=image_height,
            sensor_width=36.0
        )

    def transform_point(self, point: Tuple[float, float]) -> Tuple[float, float]:
        """
        Transform a single point from pixel coordinates to ground plane meters.

        This assumes the point lies on the ground plane.
        """
        if not self._is_calibrated:
            raise RuntimeError(
                "Transformer must be calibrated before transforming points"
            )

        px, py = point

        norm_x = (px - self._image_width / 2) / (self._image_width / 2)
        norm_y = (py - self._image_height / 2) / (self._image_height / 2)

        alpha = norm_x * (self._fov_horizontal / 2)

        beta = -norm_y * (self._fov_vertical / 2)

        ray_elevation = beta - self._tilt_angle

        if ray_elevation >= 0:
            raise ValueError(f"Point ({px}, {py}) corresponds to a ray above the horizon")

        ground_distance = self._camera_height / np.tan(abs(ray_elevation))

        x_meters = ground_distance * np.tan(alpha + self._pan_angle)
        y_meters = ground_distance

        return (x_meters, y_meters)

    def get_horizon_line(self) -> float:
        """
        Get the y-coordinate (in pixels) of the horizon line in the image.

        Points above this line don't intersect the ground plane.
        """
        if not self._is_calibrated:
            raise RuntimeError("Transformer must be calibrated first")

        # At the horizon, the ray elevation is 0
        # ray_elevation = beta - tilt_angle = 0
        # beta = tilt_angle
        # -norm_y * (fov_vertical / 2) = tilt_angle
        # norm_y = -tilt_angle / (fov_vertical / 2)

        norm_y = -self._tilt_angle / (self._fov_vertical / 2)

        horizon_y = norm_y * (self._image_height / 2) + self._image_height / 2

        return horizon_y

    @classmethod
    def create_typical_smartphone(
            cls,
            camera_height: float = 1.5,
            tilt_angle: float = 10.0,
            image_width: int = 1920,
            image_height: int = 1080,
    ) -> "CameraCalibrationTransformer":
        """
        Create a transformer with typical smartphone camera parameters.
        """
        transformer = cls()
        transformer.calibrate(
            camera_height=camera_height,
            tilt_angle=tilt_angle,
            focal_length=26,
            image_width=image_width,
            image_height=image_height,
            sensor_width=36.0,
        )
        return transformer

    @classmethod
    def create_dashcam(
            cls,
            camera_height: float = 1.2,
            tilt_angle: float = 5.0,
            image_width: int = 1920,
            image_height: int = 1080,
    ) -> "CameraCalibrationTransformer":
        """
        Create a transformer with typical dashcam parameters.
        """
        transformer = cls()
        transformer.calibrate(
            camera_height=camera_height,
            tilt_angle=tilt_angle,
            focal_length=28,
            image_width=image_width,
            image_height=image_height,
            sensor_width=36.0,
        )
        return transformer
