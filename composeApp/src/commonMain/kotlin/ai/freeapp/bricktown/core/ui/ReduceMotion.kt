package ai.freeapp.bricktown.core.ui

import androidx.compose.runtime.Composable

/** Whether the system asks for reduced motion. Android: animations switched off or scale 0. iOS: Reduce Motion. */
@Composable
expect fun platformReduceMotion(): Boolean
