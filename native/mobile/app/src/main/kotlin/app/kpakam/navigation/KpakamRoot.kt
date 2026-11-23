package app.kpakam.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import app.kpakam.features.dashboard.DashboardScreen
import app.kpakam.features.ledger.LedgerScreen
import app.kpakam.features.quickentry.QuickEntryScreen
import app.kpakam.features.settings.SettingsScreen
import app.kpakam.platform.firebase.AuthState

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    ENTRY("entry", R.string.nav_entry, Icons.Filled.EditNote),
    TODAY("today", R.string.nav_today, Icons.Filled.Today),
    OWED("owed", R.string.nav_owed, Icons.Filled.People),
    HISTORY("history", R.string.nav_history, Icons.AutoMirrored.Filled.ReceiptLong),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

private const val CUSTOMER_ROUTE = "owed/{${CustomerDetailViewModel.ARG_ID}}"

/** The signed-in app, or the sign-in state while it starts. */
@Composable
fun KpakamRoot(viewModel: RootViewModel = hiltViewModel()) {
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    when (auth) {
        is AuthState.SignedIn -> MainScaffold()
        AuthState.Starting -> CenteredMessage(stringResource(R.string.signin_loading), showProgress = true)
        AuthState.NeedsNetwork ->
            CenteredMessage(stringResource(R.string.signin_needs_network)) {
                Button(onClick = viewModel::retrySignIn) { Text(stringResource(R.string.retry)) }
            }
    }
}

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    Scaffold(bottomBar = { BottomBar(nav) }) { padding ->
        NavHost(nav, startDestination = Tab.ENTRY.route, modifier = Modifier.padding(padding)) {
            composable(Tab.ENTRY.route) { QuickEntryScreen() }
            composable(Tab.TODAY.route) { DashboardScreen() }
            composable(Tab.OWED.route) { CustomersScreen(onOpen = { id -> nav.navigate("owed/$id") }) }
            composable(CUSTOMER_ROUTE) { CustomerDetailScreen(onBack = { nav.popBackStack() }) }
            composable(Tab.HISTORY.route) { LedgerScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    NavigationBar {
        Tab.entries.forEach { tab ->
            val selected = route == tab.route || (tab == Tab.OWED && route == CUSTOMER_ROUTE)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(Tab.ENTRY.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

/** A full-screen message, used before sign-in and when Firebase is not set up. */
@Composable
fun CenteredMessage(text: String, showProgress: Boolean = false, action: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showProgress) CircularProgressIndicator()
            Text(text)
            action()
        }
    }
}
