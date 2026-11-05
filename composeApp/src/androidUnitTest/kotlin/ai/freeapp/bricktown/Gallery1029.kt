package ai.freeapp.bricktown

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the wave 2 settings screens at 402 x 874 dp into composeApp/build/gallery/1029 for a side by side look against the handoff. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
abstract class Gallery1029 {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected fun shot(name: String, settleMillis: Long = 3000, heightDp: Int = 874, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppTheme { Box(Modifier.size(402.dp, heightDp.dp)) { content() } } }
        compose.mainClock.advanceTimeBy(settleMillis)
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/gallery/1029").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
