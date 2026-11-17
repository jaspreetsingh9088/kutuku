package com.yourstore.app.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourstore.app.ShopApp
import com.yourstore.app.ui.account.*
import com.yourstore.app.ui.address.AddressFormScreen
import com.yourstore.app.ui.address.AddressListScreen
import com.yourstore.app.ui.auth.LoginScreen
import com.yourstore.app.ui.cart.CartScreen
import com.yourstore.app.ui.catalog.CategoriesScreen
import com.yourstore.app.ui.catalog.ProductDetailScreen
import com.yourstore.app.ui.catalog.ProductListScreen
import com.yourstore.app.ui.checkout.CheckoutScreen
import com.yourstore.app.ui.home.HomeScreen
import com.yourstore.app.ui.orders.OrderDetailScreen
import com.yourstore.app.ui.orders.OrderSuccessScreen
import com.yourstore.app.ui.orders.OrdersScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.CATEGORIES, "Categories", Icons.Outlined.GridView, Icons.Filled.GridView),
    Tab(Routes.CART, "Cart", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    Tab(Routes.ORDERS, "Orders", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab(Routes.ACCOUNT, "Account", Icons.Outlined.Person, Icons.Filled.Person),
)

/** Deep-link target from a push notification. */
data class DeepLink(val orderId: Int? = null, val productId: Int? = null)

@Composable
fun AppNav(deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    val c = ShopApp.container
    val nav = rememberNavController()
    val loggedIn by c.loggedIn.collectAsState()
    val startDestination = remember { if (c.loggedIn.value) Routes.HOME else Routes.LOGIN }
    val cartCount by c.cartCount.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { c.refreshCartCount() }
    LaunchedEffect(Unit) { c.events.collect { snack.showSnackbar(it) } }

    // Return to login whenever the session ends (logout, expiry, account deletion)
    LaunchedEffect(loggedIn) {
        if (!loggedIn && nav.currentDestination?.route != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }
    LaunchedEffect(deepLink, loggedIn) {
        if (deepLink != null && loggedIn) {
            deepLink.orderId?.let { nav.navigate(Routes.order(it)) }
            deepLink.productId?.let { nav.navigate(Routes.product(it)) }
            onDeepLinkHandled()
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val go: (String) -> Unit = { r -> if (r in Routes.topLevel) nav.switchTab(r) else nav.navigate(r) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (route in Routes.topLevel) NavigationBar {
                TABS.forEach { t ->
                    val sel = route == t.route
                    NavigationBarItem(
                        selected = sel,
                        onClick = { nav.switchTab(t.route) },
                        icon = {
                            if (t.route == Routes.CART && cartCount > 0) BadgedBox(badge = { Badge { Text("$cartCount") } }) { Icon(if (sel) t.selectedIcon else t.icon, t.label) }
                            else Icon(if (sel) t.selectedIcon else t.icon, t.label)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = startDestination, modifier = Modifier.padding(pad).consumeWindowInsets(pad)) {
            composable(Routes.LOGIN) {
                LoginScreen(onLoggedIn = { isNew ->
                    nav.navigate(if (isNew) Routes.profileEdit(onboarding = true) else Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                })
            }
            composable(Routes.HOME) { HomeScreen(go) }
            composable(Routes.CATEGORIES) { CategoriesScreen(go) }
            composable(Routes.CART) { CartScreen(go) }
            composable(Routes.ORDERS) { OrdersScreen(go) }
            composable(Routes.ACCOUNT) { AccountScreen(go) }

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
                ProductListScreen(
                    categoryId = a.getInt("categoryId").takeIf { it > 0 },
                    initialQuery = a.getString("q").orEmpty(),
                    title = a.getString("title").orEmpty().ifBlank { "Products" },
                    featured = a.getBoolean("featured"),
                    sort = a.getString("sort")?.ifBlank { null },
                    navigate = go, onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.PRODUCT, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                ProductDetailScreen(e.arguments!!.getInt("id"), go) { nav.popBackStack() }
            }
            composable(Routes.CHECKOUT, arguments = listOf(navArgument("coupon") { type = NavType.StringType; defaultValue = "" })) { e ->
                CheckoutScreen(
                    coupon = e.arguments!!.getString("coupon")?.ifBlank { null },
                    navigate = go,
                    onSuccess = { id -> nav.navigate(Routes.orderSuccess(id)) { popUpTo(Routes.CART) } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.ADDRESSES) { AddressListScreen(go, { nav.popBackStack() }) }
            composable(Routes.ADDRESS_FORM, arguments = listOf(navArgument("id") { type = NavType.IntType; defaultValue = -1 })) { e ->
                AddressFormScreen(e.arguments!!.getInt("id").takeIf { it > 0 }) { nav.popBackStack() }
            }
            composable(Routes.ORDER, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                OrderDetailScreen(e.arguments!!.getInt("id"), go) { nav.popBackStack() }
            }
            composable(Routes.ORDER_SUCCESS, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                val id = e.arguments!!.getInt("id")
                OrderSuccessScreen(id,
                    onViewOrder = { nav.navigate(Routes.order(id)) { popUpTo(Routes.CART) } },
                    onContinue = { nav.switchTab(Routes.HOME) })
            }
            composable(Routes.WISHLIST) { WishlistScreen(go, { nav.popBackStack() }) }
            composable(Routes.PAYMENTS) { PaymentsScreen(go, { nav.popBackStack() }) }
            composable(Routes.PROFILE_EDIT, arguments = listOf(navArgument("onboarding") { type = NavType.BoolType; defaultValue = false })) { e ->
                val onboarding = e.arguments!!.getBoolean("onboarding")
                ProfileEditScreen(
                    onboarding = onboarding,
                    onDone = { if (onboarding) nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } else nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
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
