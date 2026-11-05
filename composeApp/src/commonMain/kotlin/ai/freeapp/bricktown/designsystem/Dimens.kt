package ai.freeapp.bricktown.designsystem

import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Screens use these instead of literal `dp` values so that
 * padding stays consistent when a screen is built by a different pass.
 *
 * EXTENSION POINT: add a token only when a value is needed by more than one
 * screen. A number used once belongs at its call site, not in the scale.
 */
object Dimens {
    val space2 = 2.dp
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space48 = 48.dp

    /** Component sizes shared across screens. */
    val iconSize = 24.dp
    val buttonHeight = 48.dp
    val tabRowHeight = 44.dp
    val skeletonLineHeight = 16.dp
}

/** Corner radii. [pill] is deliberately larger than any component height. */
object Radius {
    val sm = 4.dp
    val md = 8.dp
    val lg = 16.dp
    val pill = 999.dp
}
