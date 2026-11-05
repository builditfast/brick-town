package ai.freeapp.bricktown.profile

import ai.freeapp.bricktown.core.ui.LegalLinksRow
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.shimmerEffect
import ai.freeapp.bricktown.settings.GroupCard
import ai.freeapp.bricktown.settings.RowDivider
import ai.freeapp.bricktown.settings.SettingsRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * The ACCOUNT group of Settings (ours, spec 5.8): who is signed in, change
 * password, sign out and delete account. It does not scroll by itself: the
 * Settings screen it sits in does.
 */
@Composable
fun AccountSection(
    onChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountContent(
        state = state,
        onChangePassword = onChangePassword,
        onSignOut = viewModel::onSignOut,
        onDeleteAccount = viewModel::onDeleteAccount,
        onLoadUser = viewModel::loadUser,
        modifier = modifier,
    )
    if (state.showDeleteConfirm) {
        DeleteAccountDialog(
            email = state.user?.email,
            isDeleting = state.isDeleting,
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissDelete,
        )
    }
}

/** The ACCOUNT group drawn from [state]; stateless so the gallery can render it. */
@Composable
fun AccountContent(
    state: ProfileUiState,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLoadUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalBrickType.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.space8)) {
        GroupCard {
            val user = state.user
            when {
                user != null -> SettingsRow(title = user.name, subtitle = user.email)
                state.isLoadingUser -> Box(Modifier.fillMaxWidth().padding(Dimens.space16)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(Dimens.space24).shimmerEffect())
                }
                else -> SettingsRow(title = "Load your account details", titleColor = BrickColors.LinkBlue, onClick = onLoadUser)
            }
            RowDivider()
            SettingsRow(title = "Change password", chevron = true, onClick = onChangePassword)
            RowDivider()
            SettingsRow(title = "Delete account", titleColor = BrickColors.Negative, onClick = onDeleteAccount)
        }
        state.error?.let { Text(it.toMessage(), style = type.small13.copy(color = BrickColors.White)) }
        ChunkyButton(
            text = if (state.isSigningOut) "Signing out..." else "Sign out",
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Soft,
            enabled = !state.isSigningOut,
        )
        Text(
            "Deleting removes your account and everything in it, on this phone and on our server.",
            style = type.small12.copy(color = BrickColors.White, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space12),
        )
        LegalLinksRow(Modifier.fillMaxWidth(), link = { text, onClick -> LegalLink(text, onClick, Modifier.weight(1f)) })
    }
}

@Composable
private fun LegalLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    Box(modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = type.body14.copy(color = BrickColors.White, textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center))
    }
}

/** Names exactly what goes, because nothing comes back: the server deletes the rows, not a flag. */
@Composable
private fun DeleteAccountDialog(
    email: String?,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val account = email?.let { "the account $it" } ?: "your account"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your account?") },
        text = {
            Text(
                "This permanently deletes $account: your name, your email address, your password " +
                    "and every brick you paid for, on this phone and on our server. A town you own " +
                    "goes with it, for everyone in it. You cannot undo this.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error,
                    contentColor = MaterialTheme.colors.onError,
                ),
                modifier = Modifier.heightIn(min = Dimens.buttonHeight),
            ) {
                Text(if (isDeleting) "Deleting..." else "Delete account")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.heightIn(min = Dimens.buttonHeight),
            ) {
                Text("Cancel")
            }
        },
    )
}
