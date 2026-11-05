package ai.freeapp.bricktown.core.ui

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable

/** The top bar of every pushed screen: a title and a Back arrow that pops it. */
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = "Back")
            }
        },
    )
}
