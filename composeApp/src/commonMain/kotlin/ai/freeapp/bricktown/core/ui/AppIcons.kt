package ai.freeapp.bricktown.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The icons the reference screens use, built from Material's own path data
 * (Apache-2.0). Compose Multiplatform 1.8 no longer ships the icon set with
 * material, and its last release (1.7.3) drags compose ui 1.7.3 in under the
 * pinned 1.8.2, which kmp-doctor's deps check reports.
 *
 * EXTENSION POINT: add an icon by copying its `d` path from
 * fonts.google.com/icons, or put a vector file in composeResources/drawable.
 */
object AppIcons {
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z", autoMirror = true)
    }

    val Person: ImageVector by lazy {
        icon("Person", "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z")
    }

    /** The handoff's round Back button chevron (6a, 6f): stroke 3.5 in a 24 box. */
    val ChevronBack: ImageVector by lazy { strokeIcon("ChevronBack", "m15 18-6-6 6-6", 3.5f) }

    /** The invite row's plus (6a): stroke 3.5 in a 24 box. */
    val Plus: ImageVector by lazy { strokeIcon("Plus", "M5 12h14M12 5v14", 3.5f) }

    /** 6g's share arrow out of a tray. */
    val Share: ImageVector by lazy { strokeIcon("Share", "M12 15V3M7 8l5-5 5 5M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6", 2.8f) }

    /** The row chevron (6a, 6f): stroke 3 in a 24 box. */
    val ChevronForward: ImageVector by lazy { strokeIcon("ChevronForward", "m9 18 6-6-6-6", 3f) }

    /** 6d's "Take a photo of it" row: a camera body, its lens and the viewfinder bump. */
    val Camera: ImageVector by lazy {
        strokeIcon("Camera", "M6 7h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-7a3 3 0 0 1 3-3zM8.5 13.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0M8 7l1.5-3h5L16 7", 2.6f)
    }
}

private fun icon(name: String, path: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    )
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

private fun strokeIcon(name: String, path: String, width: Float): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    )
        .addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
