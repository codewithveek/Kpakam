package app.kpakam.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.kpakam.R
import app.kpakam.features.customers.CustomerDetailScreen
import app.kpakam.features.customers.CustomerDetailViewModel
import app.kpakam.features.customers.CustomersScreen
import app.kpakam.features.customers.PaymentBarSpace
import app.kpakam.features.dashboard.HomeScreen
import app.kpakam.features.ledger.HistoryScreen
import app.kpakam.features.quickentry.QuickEntryViewModel
import app.kpakam.features.quickentry.VoiceScreen
import app.kpakam.features.quickentry.VoiceViewModel
import app.kpakam.features.quickentry.rememberSpeakAction
import app.kpakam.features.settings.SettingsScreen
import app.kpakam.platform.firebase.AuthState
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.NetworkPill
import app.kpakam.shared.ui.PageGutter
import app.kpakam.shared.ui.PillButton
import app.kpakam.shared.ui.ToastHost

private const val CUSTOMER_ROUTE = "debts/{${CustomerDetailViewModel.ARG_ID}}"

/** The signed-in app, or the sign-in state while it starts. */
@Composable
fun KpakamRoot(viewModel: RootViewModel = hiltViewModel()) {
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    when (auth) {
        is AuthState.SignedIn -> MainScaffold(viewModel)
        AuthState.Starting -> CenteredMessage(stringResource(R.string.signin_loading), showProgress = true)
        AuthState.NeedsNetwork ->
            CenteredMessage(stringResource(R.string.signin_needs_network)) {
                PillButton(stringResource(R.string.retry), viewModel::retrySignIn)
            }
    }
}

/**
 * Layout: a slim top row (brand mark, network status), the page, and a docked bottom bar. The
 * customer page hides the bar and floats its own action; the voice screen covers everything.
 */
@Composable
private fun MainScaffold(root: RootViewModel) {
    val nav = rememberNavController()
    val entry: QuickEntryViewModel = hiltViewModel()
    val voice: VoiceViewModel = hiltViewModel()
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val online by root.online.collectAsStateWithLifecycle()
    val speak = rememberSpeakAction(voice)
    val route =
        nav
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route
    val detail = route == CUSTOMER_ROUTE
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace = navInset + if (detail) PaymentBarSpace else BottomBarHeight + 32.dp
    Box(Modifier.fillMaxSize().background(Kpakam.colors.bg)) {
        Column(
            Modifier.fillMaxSize().imePadding().then(
                if (voiceState.open) Modifier.clearAndSetSemantics {} else Modifier,
            ),
        ) {
            TopRow(online, onHome = { nav.selectTab(Tab.HOME) })
            AppNavHost(nav, entry, speak, PaddingValues(top = 8.dp, bottom = bottomSpace), Modifier.weight(1f))
        }
        if (!detail) {
            BottomBar(
                current = Tab.entries.firstOrNull { it.route == route },
                onSelect = nav::selectTab,
                onSpeak = speak,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        ToastHost(root.toasts, Modifier.align(Alignment.BottomCenter).padding(bottom = toastLift(navInset, detail)))
        if (voiceState.open) {
            VoiceScreen(voiceState, level = { voice.level.value }, voice = voice, onAgain = speak)
        }
    }
}

private fun toastLift(navInset: Dp, detail: Boolean): Dp =
    navInset + if (detail) PaymentBarSpace else BottomBarHeight + 24.dp

@Composable
private fun AppNavHost(
    nav: NavHostController,
    entry: QuickEntryViewModel,
    speak: () -> Unit,
    padding: PaddingValues,
    modifier: Modifier,
) {
    NavHost(nav, startDestination = Tab.HOME.route, modifier = modifier) {
        composable(Tab.HOME.route) {
            HomeScreen(
                entry = entry,
                onSpeak = speak,
                onSeeAll = { nav.selectTab(Tab.HISTORY) },
                contentPadding = padding,
            )
        }
        composable(Tab.HISTORY.route) { HistoryScreen(contentPadding = padding) }
        composable(Tab.DEBTS.route) {
            CustomersScreen(onOpen = { id -> nav.navigate("debts/$id") }, contentPadding = padding)
        }
        composable(CUSTOMER_ROUTE) { CustomerDetailScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
        composable(Tab.SETTINGS.route) { SettingsScreen(contentPadding = padding) }
    }
}

private fun NavHostController.selectTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(Tab.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TopRow(online: Boolean, onHome: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier.fillMaxWidth().statusBarsPadding().padding(
                start = PageGutter,
                end = PageGutter,
                top = 12.dp,
                bottom = 4.dp,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Kpakam.colors.surface)
                    .border(1.dp, Kpakam.colors.border, CircleShape),
        ) {
            androidx.compose.material3.IconButton(onClick = onHome) {
                Image(
                    painterResource(R.drawable.ic_brand),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        NetworkPill(online)
    }
}

/** A full-screen message, used before sign-in and when Firebase is not set up. */
@Composable
fun CenteredMessage(text: String, showProgress: Boolean = false, action: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(Kpakam.colors.bg).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showProgress) CircularProgressIndicator(color = Kpakam.colors.text)
            Text(text, style = KpakamType.body, color = Kpakam.colors.text, textAlign = TextAlign.Center)
            action()
        }
    }
}
