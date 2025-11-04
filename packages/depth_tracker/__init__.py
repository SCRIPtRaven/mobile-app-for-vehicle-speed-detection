"""
Depth-based vehicle speed tracking implementation.
Uses Optical Flow + Monocular Depth Estimation for speed calculation.
"""

from .depth_speed_tracker import DepthSpeedTracker

__all__ = ['DepthSpeedTracker']
