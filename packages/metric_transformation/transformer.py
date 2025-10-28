from abc import ABC, abstractmethod
from typing import Tuple

import numpy as np
import numpy.typing as npt


class MetricTransformer(ABC):
    def __init__(self):
        self._is_calibrated = False

    @abstractmethod
    def calibrate(self, *args, **kwargs) -> None:
        pass

    @abstractmethod
    def transform_point(self, point: Tuple[float, float]) -> Tuple[float, float]:
        pass

    def transform_points(
            self,
            points: npt.NDArray[np.float64]
    ) -> npt.NDArray[np.float64]:
        if not self._is_calibrated:
            raise RuntimeError("Transformer must be calibrated before transforming points")

        return np.array([
            self.transform_point((pt[0], pt[1]))
            for pt in points
        ])

    def calculate_distance(
            self,
            point1: Tuple[float, float],
            point2: Tuple[float, float]
    ) -> float:
        if not self._is_calibrated:
            raise RuntimeError("Transformer must be calibrated before calculating distances")

        p1_meters = self.transform_point(point1)
        p2_meters = self.transform_point(point2)

        dx = p2_meters[0] - p1_meters[0]
        dy = p2_meters[1] - p1_meters[1]

        return np.sqrt(dx ** 2 + dy ** 2)

    @property
    def is_calibrated(self) -> bool:
        return self._is_calibrated
