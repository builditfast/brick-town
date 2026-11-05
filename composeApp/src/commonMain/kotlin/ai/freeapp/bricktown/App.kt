package ai.freeapp.bricktown

import ai.freeapp.bricktown.auth.AuthRepository
import ai.freeapp.bricktown.auth.CreateAccountScreen
import ai.freeapp.bricktown.auth.LandingScreen
import ai.freeapp.bricktown.auth.SignInScreen
import ai.freeapp.bricktown.auth.StartScreen
import ai.freeapp.bricktown.brick.AddBrickScreen
import ai.freeapp.bricktown.brick.EditBrickScreen
import ai.freeapp.bricktown.compare.CompareScreen
import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.navigation.BackStack
import ai.freeapp.bricktown.designsystem.BarTab
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.BrickType
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.ProvideReduceMotion
import ai.freeapp.bricktown.designsystem.rememberBrickFonts
import ai.freeapp.bricktown.history.HistoryScreen
import ai.freeapp.bricktown.home.HomeScaffold
import ai.freeapp.bricktown.home.HomeViewModel
import ai.freeapp.bricktown.importing.ImportScreen
import ai.freeapp.bricktown.invite.InviteScreen
import ai.freeapp.bricktown.join.JoinScreen
import ai.freeapp.bricktown.month.MonthScreen
import ai.freeapp.bricktown.onboarding.OnboardingScreen
import ai.freeapp.bricktown.profile.ChangePasswordScreen
import ai.freeapp.bricktown.recap.RecapScreen
import ai.freeapp.bricktown.rollover.RolloverScreen
import ai.freeapp.bricktown.settings.SettingsScreen
import ai.freeapp.bricktown.settle.SettleUpScreen
import ai.freeapp.bricktown.towers.ManageTowersScreen
import ai.freeapp.bricktown.town.TownLoader
import ai.freeapp.bricktown.town.TownSync
import ai.freeapp.bricktown.trail.TrailScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The root both platforms host: `MainActivity` on Android, `MainViewController`
 * on iOS. It fills the whole window, under the system bars.
 *
 * Android 15 and up lay every app out edge to edge, under the status bar, the
 * navigation bar and the keyboard, and the design draws each world under them
 * too. So the pieces that meet a screen edge handle the insets, and screens
 * never do: [ai.freeapp.bricktown.designsystem.World] pads its content,
 * `BottomBar` and `BottomSheet` keep their white under the navigation bar.
 */
@Composable
fun App() {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
        ) {
            AppNavigation()
        }
    }
}

/**
 * Everything a screen reads from around it: the theme and any
 * CompositionLocal the screens need.
 *
 * EXTENSION POINT: an app's own theme goes here, not in [App]. The screen
 * smoke test (androidUnitTest `ScreenSmokeTest`) draws each screen inside
 * this and nothing else, so a local provided anywhere else is missing there.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val fonts = rememberBrickFonts()
    val brickType = remember(fonts) { BrickType(fonts) }
    val typography = remember(fonts) {
        Typography(
            defaultFontFamily = fonts.nunito,
            h1 = brickType.hero44, h2 = brickType.sheetTitle, h3 = brickType.row22,
            h4 = brickType.title18, h5 = brickType.body16, h6 = brickType.body15,
            subtitle1 = brickType.body16, subtitle2 = brickType.body14,
            body1 = brickType.body15.copy(fontWeight = FontWeight.Bold), body2 = brickType.body14.copy(fontWeight = FontWeight.Bold),
            button = brickType.title18, caption = brickType.small12, overline = brickType.label10,
        )
    }
    val colors = lightColors(
        primary = BrickColors.Green.fill,
        primaryVariant = BrickColors.Green.edge,
        secondary = BrickColors.Gold.fill,
        background = BrickColors.WorldPink,
        surface = BrickColors.White,
        error = BrickColors.Negative,
        onPrimary = BrickColors.White,
        onSecondary = BrickColors.Ink,
        onBackground = BrickColors.Ink,
        onSurface = BrickColors.Ink,
        onError = BrickColors.White,
    )
    ProvideReduceMotion {
        CompositionLocalProvider(LocalBrickType provides brickType) {
            MaterialTheme(colors = colors, typography = typography, content = content)
        }
    }
}

/**
 * Shows the top of the [BackStack] and nothing else.
 *
 * Each entry is provided as `LocalViewModelStoreOwner`, so `koinViewModel()`
 * in a screen gets a ViewModel scoped to that visit: it is cleared when the
 * entry is popped or the stack is reset, and a second visit starts fresh.
 * `rememberSaveable` state is kept per entry too, so a screen comes back from
 * a pushed one with its scroll position and half-typed text intact.
 *
 * The session and the town decide the stack, in one place ([redirectFor]):
 * a session that starts on a signed-out screen goes to Start, one that ends
 * anywhere else (sign-out, account deletion, or the server refusing the
 * refresh token) goes to Landing, and a town screen on an account with no
 * town goes to Onboarding. A reset clears every entry, so nothing of the old
 * session survives.
 *
 * The four tabs share one [HomeScaffold] outside the entry, so the bottom bar
 * stays put while the tab under it changes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppNavigation(
    auth: AuthRepository = koinInject(),
    town: TownLoader = koinInject(),
    sync: TownSync = koinInject(),
    flags: AppFlags = koinInject(),
    inbox: DeepLinkInbox = koinInject(),
) {
    val signedIn by auth.isSignedIn.collectAsStateWithLifecycle()
    val townStatus by town.status.collectAsStateWithLifecycle()
    val pendingLink by inbox.pending.collectAsStateWithLifecycle()
    val savedState = rememberSaveableStateHolder()
    val backStack = remember {
        BackStack<Destination>(
            root = if (auth.isSignedIn.value) Destination.Start else Destination.Landing,
            onRemoved = { entry -> savedState.removeState(entry.key) },
        )
    }
    val onScreen = backStack.current.destination
    LaunchedEffect(signedIn, townStatus, onScreen) {
        onScreen.redirectFor(signedIn, townStatus)?.let(backStack::resetTo)
    }
    // A link waits until Start has picked the first screen; signed out, only
    // a join code is kept (for after sign-in), the rest is dropped.
    LaunchedEffect(pendingLink, signedIn, onScreen) {
        if (pendingLink != null && onScreen != Destination.Start) {
            inbox.take(signedIn)?.let(backStack::openLink)
        }
    }
    // The town refreshes while a town screen is on screen and the app is in
    // the foreground, and stops when either ends.
    val syncActive = signedIn && onScreen is Destination.NeedsTown
    LifecycleStartEffect(syncActive) {
        if (syncActive) {
            sync.onForeground()
            flags.refreshInBackground()
        }
        onStopOrDispose { if (syncActive) sync.onBackground() }
    }
    // The host itself going away (the Activity finishing) is the one exit
    // that does not pass through pop or resetTo.
    DisposableEffect(backStack) {
        onDispose { backStack.clearAll() }
    }
    // System back and the predictive back gesture pop one screen; on the
    // root the callback is disabled, so the platform closes the app.
    BackHandler(enabled = backStack.canGoBack) { backStack.pop() }

    val entry = backStack.current
    val content: @Composable () -> Unit = {
        key(entry.key) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
                savedState.SaveableStateProvider(entry.key) {
                    Screen(entry.destination, backStack)
                }
            }
        }
    }
    val tab = entry.destination.tab
    if (tab != null) HomeTabs(tab, backStack, content) else content()
}

/** The bar tab a destination is, or null for every screen that hides the bar. */
private val Destination.tab: BarTab?
    get() = when (this) {
        is Destination.Month -> BarTab.Month
        Destination.History -> BarTab.History
        Destination.Compare -> BarTab.Compare
        Destination.Trail -> BarTab.Trail
        else -> null
    }

/**
 * The bar's routes. Month is the root of the tabs: the other three open with
 * only Month under them, so Back from any tab lands on Month and Back on
 * Month closes the app.
 */
@Composable
private fun HomeTabs(selected: BarTab, backStack: BackStack<Destination>, content: @Composable () -> Unit) {
    val home = koinViewModel<HomeViewModel>()
    val state by home.state.collectAsStateWithLifecycle()
    HomeScaffold(
        selected = selected,
        onShowMonth = { backStack.returnTo(destination = Destination.Month()) },
        onShowHistory = { backStack.showAbove(base = Destination.Month(), destination = Destination.History) },
        onShowCompare = { backStack.showAbove(base = Destination.Month(), destination = Destination.Compare) },
        onShowTrail = { backStack.showAbove(base = Destination.Month(), destination = Destination.Trail) },
        onAddBrick = { backStack.push(destination = Destination.AddBrick()) },
        onImport = { backStack.push(destination = Destination.Import) },
        pulseAdd = state.pulseAdd,
        content = content,
    )
}

/**
 * Internal, not private: the screen smoke test opens every destination
 * through it. A tab draws only its own content here; the bar is [HomeTabs].
 */
@Composable
internal fun Screen(destination: Destination, backStack: BackStack<Destination>) {
    when (destination) {
        Destination.Start -> StartScreen(
            onShowJoin = { code -> backStack.resetTo(destination = Destination.Join(code)) },
            onShowOnboarding = { backStack.resetTo(destination = Destination.Onboarding) },
            onShowRollover = { monthStart ->
                backStack.showAbove(base = Destination.Month(), destination = Destination.Rollover(monthStart))
            },
            onShowMonth = { backStack.resetTo(destination = Destination.Month()) },
        )
        Destination.Landing -> LandingScreen(
            onSignIn = { backStack.push(destination = Destination.SignIn) },
            onCreateAccount = { backStack.push(destination = Destination.CreateAccount) },
        )
        Destination.SignIn -> SignInScreen(
            onCreateAccount = { backStack.push(destination = Destination.CreateAccount) },
        )
        Destination.CreateAccount -> CreateAccountScreen(onBack = backStack::pop)
        Destination.Onboarding -> OnboardingScreen(
            onJoinTown = { backStack.push(destination = Destination.Join()) },
            onOpenMonth = { backStack.resetTo(destination = Destination.Month()) },
        )
        is Destination.Join -> JoinScreen(
            code = destination.code,
            onOpenMonth = { townName -> backStack.resetTo(destination = Destination.Month(joinedTown = townName)) },
            onBack = { if (backStack.canGoBack) backStack.pop() else backStack.resetTo(destination = Destination.Month()) },
        )
        is Destination.Month -> MonthScreen(
            joinedTown = destination.joinedTown,
            onOpenSettings = { backStack.push(destination = Destination.Settings) },
            onAddBrick = { categoryId -> backStack.push(destination = Destination.AddBrick(categoryId)) },
            onEditBrick = { expenseId -> backStack.push(destination = Destination.EditBrick(expenseId)) },
        )
        Destination.History -> HistoryScreen(
            onOpenRecap = { weekStart -> backStack.push(destination = Destination.Recap(weekStart)) },
            onEditBrick = { expenseId -> backStack.push(destination = Destination.EditBrick(expenseId)) },
        )
        Destination.Compare -> CompareScreen()
        Destination.Trail -> TrailScreen()
        is Destination.AddBrick -> AddBrickScreen(
            categoryId = destination.categoryId,
            onShowMonth = { backStack.returnTo(destination = Destination.Month()) },
            onBack = backStack::pop,
        )
        is Destination.EditBrick -> EditBrickScreen(expenseId = destination.expenseId, onBack = backStack::pop)
        is Destination.Recap -> RecapScreen(weekStart = destination.weekStart, onBack = backStack::pop)
        is Destination.Rollover -> RolloverScreen(
            monthStart = destination.monthStart,
            onStartMonth = { backStack.returnTo(destination = Destination.Month()) },
        )
        Destination.Settings -> SettingsScreen(
            onBack = backStack::pop,
            onChangePassword = { backStack.push(destination = Destination.ChangePassword) },
            onManageTowers = { backStack.push(destination = Destination.ManageTowers) },
            onInvite = { backStack.push(destination = Destination.Invite) },
            onSettleUp = { backStack.push(destination = Destination.SettleUp) },
            onImport = { backStack.push(destination = Destination.Import) },
            onOpenRollover = { monthStart -> backStack.push(destination = Destination.Rollover(monthStart)) },
        )
        Destination.ManageTowers -> ManageTowersScreen(onBack = backStack::pop)
        Destination.Invite -> InviteScreen(onBack = backStack::pop)
        Destination.SettleUp -> SettleUpScreen(onBack = backStack::pop)
        Destination.Import -> ImportScreen(onBack = backStack::pop)
        Destination.ChangePassword -> ChangePasswordScreen(onBack = backStack::pop)
    }
}
