package com.yourstore.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourstore.app.ShopApp
import com.yourstore.app.data.Prefs
import com.yourstore.app.ui.account.*
import com.yourstore.app.ui.address.AddressFormScreen
import com.yourstore.app.ui.address.AddressListScreen
import com.yourstore.app.ui.auth.*
import com.yourstore.app.ui.cart.CartScreen
import com.yourstore.app.ui.catalog.*
import com.yourstore.app.ui.checkout.CheckoutScreen
import com.yourstore.app.ui.components.KSnackbarHost
import com.yourstore.app.ui.home.HomeScreen
import com.yourstore.app.ui.inbox.ChatScreen
import com.yourstore.app.ui.inbox.MessagesScreen
import com.yourstore.app.ui.inbox.NotificationsScreen
import com.yourstore.app.ui.orders.OrderDetailScreen
import com.yourstore.app.ui.orders.OrderTrackScreen
import com.yourstore.app.ui.orders.OrdersScreen
import com.yourstore.app.ui.theme.Brand
import com.yourstore.app.ui.theme.InkHint
import kotlinx.coroutines.launch

data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.ORDERS, "My Order", Icons.Outlined.LocalShipping, Icons.Filled.LocalShipping),
    Tab(Routes.WISHLIST, "Favorite", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Tab(Routes.ACCOUNT, "My Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

/** Deep-link target from a push notification. */
data class DeepLink(val orderId: Int? = null, val productId: Int? = null, val conversationId: Int? = null)

@Composable
fun AppNav(deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    val c = ShopApp.container
    val nav = rememberNavController()
    val loggedIn by c.loggedIn.collectAsState()
    val snackState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snack: (String) -> Unit = { m -> scope.launch { snackState.currentSnackbarData?.dismiss(); snackState.showSnackbar(m) } }
    val auth: AuthViewModel = viewModel()

    LaunchedEffect(Unit) { c.refreshCartCount() }
    LaunchedEffect(Unit) { c.events.collect { snack(it) } }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Return to login whenever the session ends (logout, expiry, account deletion)
    LaunchedEffect(loggedIn) {
        if (!loggedIn && route != null && route !in Routes.authRoutes) nav.navigate(Routes.login()) { popUpTo(0) { inclusive = true } }
    }
    LaunchedEffect(deepLink, loggedIn, route) {
        if (deepLink != null && loggedIn && route != null && route !in Routes.authRoutes) {
            deepLink.orderId?.let { nav.navigate(Routes.order(it)) }
            deepLink.productId?.let { nav.navigate(Routes.product(it)) }
            deepLink.conversationId?.let { nav.navigate(Routes.chat(it)) }
            onDeepLinkHandled()
        }
    }

    val go: (String) -> Unit = { r -> if (r in Routes.topLevel) nav.switchTab(r) else nav.navigate(r) }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.switchTab(Routes.HOME) }
    val toHome: () -> Unit = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } }

    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { KSnackbarHost(snackState) },
        bottomBar = { if (route in Routes.topLevel) KBottomBar(route) { nav.switchTab(it) } },
    ) { pad ->
        NavHost(nav, startDestination = Routes.SPLASH, modifier = Modifier.padding(pad).consumeWindowInsets(pad)) {
            // ---------------- onboarding & auth
            composable(Routes.SPLASH) {
                SplashScreen {
                    val next = when { c.loggedIn.value -> Routes.HOME; !Prefs.onboardingSeen -> Routes.ONBOARDING; else -> Routes.login() }
                    nav.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            }
            composable(Routes.ONBOARDING) {
                LaunchedEffect(Unit) { auth.loadOnboarding() }
                OnboardingContent(auth.onboarding,
                    onCreateAccount = { Prefs.onboardingSeen = true; nav.navigate(Routes.REGISTER) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                    onLogin = { Prefs.onboardingSeen = true; nav.navigate(Routes.login()) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
            }
            composable(Routes.LOGIN, arguments = listOf(navArgument("sheet") { type = NavType.StringType; defaultValue = "" })) { e ->
                LoginScreen(
                    auth, startSheet = if (e.arguments?.getString("sheet") == "new") LoginSheet.NEW_PASSWORD else LoginSheet.NONE,
                    onLoggedIn = toHome,
                    onRegister = { nav.navigate(Routes.REGISTER) { launchSingleTop = true } },
                    onVerify = { nav.navigate(Routes.VERIFY) }, snack = snack,
                )
            }
            composable(Routes.REGISTER) {
                RegisterScreen(auth, onVerify = { nav.navigate(Routes.VERIFY) },
                    onLogin = { if (!nav.popBackStack(Routes.LOGIN, false)) nav.navigate(Routes.login()) { popUpTo(Routes.REGISTER) { inclusive = true } } }, snack = snack)
            }
            composable(Routes.VERIFY) {
                VerificationScreen(auth, onBack = { nav.popBackStack() }, onLoggedIn = toHome,
                    onResetVerified = { nav.navigate(Routes.login("new")) { popUpTo(0) { inclusive = true } } }, snack = snack)
            }

            // ---------------- tabs
            composable(Routes.HOME) { HomeScreen(go) }
            composable(Routes.ORDERS) { OrdersScreen(go, snack) }
            composable(Routes.WISHLIST) { WishlistScreen(go) }
            composable(Routes.ACCOUNT) { SettingsScreen(go, onLoggedOut = { nav.navigate(Routes.login()) { popUpTo(0) { inclusive = true } } }) }

            // ---------------- shopping
            composable(Routes.SEARCH) { SearchScreen(go, back) }
            composable(
                Routes.PRODUCTS,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("featured") { type = NavType.BoolType; defaultValue = false },
                    navArgument("sort") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                val a = e.arguments!!
                val categoryId = a.getInt("categoryId").takeIf { it > 0 }
                val q = a.getString("q").orEmpty()
                val featured = a.getBoolean("featured")
                val sort = a.getString("sort")?.ifBlank { null }
                val vm: ProductListViewModel = viewModel(key = "plist-${e.id}") { ProductListViewModel(categoryId, q, featured, sort) }
                ProductListScreen(title = a.getString("title").orEmpty(), navigate = go, onBack = back, vm = vm)
            }
            composable(Routes.PRODUCT, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                ProductDetailScreen(e.arguments!!.getInt("id"), go, back, snack)
            }
            composable(Routes.STORE, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                StoreScreen(e.arguments!!.getInt("id"), go, back)
            }
            composable(Routes.CART) { CartScreen(go, back, snack) }
            composable(Routes.CHECKOUT, arguments = listOf(navArgument("coupon") { type = NavType.StringType; defaultValue = "" })) { e ->
                val chosen by e.savedStateHandle.getStateFlow("address_id", -1).collectAsState()
                CheckoutScreen(
                    coupon = e.arguments!!.getString("coupon")?.ifBlank { null }, selectedAddressId = chosen.takeIf { it > 0 },
                    navigate = go, onBack = back,
                    onTrack = { id -> nav.navigate(Routes.track(id)) { popUpTo(Routes.HOME) } },
                    onHome = { nav.switchTab(Routes.HOME) }, snack = snack,
                )
            }
            composable(Routes.ADDRESSES, arguments = listOf(navArgument("choose") { type = NavType.BoolType; defaultValue = false })) { e ->
                val choose = e.arguments!!.getBoolean("choose")
                AddressListScreen(go, back, onConfirm = { id ->
                    if (choose) nav.previousBackStackEntry?.savedStateHandle?.set("address_id", id)
                    back()
                })
            }
            composable(Routes.ADDRESS_FORM, arguments = listOf(navArgument("id") { type = NavType.IntType; defaultValue = -1 })) { e ->
                AddressFormScreen(e.arguments!!.getInt("id").takeIf { it > 0 }, back)
            }

            // ---------------- orders
            composable(Routes.ORDER, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                OrderDetailScreen(e.arguments!!.getInt("id"), go, back, snack)
            }
            composable(Routes.TRACK, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                OrderTrackScreen(e.arguments!!.getInt("id"), back, snack)
            }
            composable(Routes.PAYMENTS) { PaymentsScreen(go, back) }

            // ---------------- inbox
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(go, back) }
            composable(Routes.MESSAGES) { MessagesScreen(go, back) }
            composable(Routes.CHAT, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                ChatScreen(e.arguments!!.getInt("id"), go, back, snack)
            }

            // ---------------- settings
            composable(Routes.PROFILE_EDIT) { EditProfileScreen(back, snack) }
            composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(back, snack) }
            composable(Routes.NOTIF_SETTINGS) { NotificationSettingsScreen(back, snack) }
            composable(Routes.SECURITY) { SecurityScreen(back, snack) }
            composable(Routes.LANGUAGE) { LanguageScreen(back) }
            composable(Routes.HELP) { HelpScreen(go, back) }
            composable(Routes.LEGAL) { LegalScreen(back) }
        }
    }
}

/** Kutuku bottom bar: white, icon over label, purple when selected. */
@Composable
fun KBottomBar(route: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(16.dp, ambientColor = Color(0x14000000), spotColor = Color(0x14000000)).background(Color.White)
            .navigationBarsPadding().height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEach { t ->
            val sel = route == t.route
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(t.route) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (sel) t.selectedIcon else t.icon, t.label, tint = if (sel) Brand else InkHint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(6.dp))
                Text(t.label, fontSize = 11.sp, color = if (sel) Brand else InkHint, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        // HOME is always the root of the back stack once logged in
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
