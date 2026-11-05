package ai.freeapp.bricktown.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun platformReduceMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
