package com.yourstore.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.yourstore.app.data.model.*
import com.yourstore.app.ui.account.*
import com.yourstore.app.ui.address.AddressListContent
import com.yourstore.app.ui.auth.*
import com.yourstore.app.ui.cart.CartContent
import com.yourstore.app.ui.catalog.*
import com.yourstore.app.ui.checkout.PayMethod
import com.yourstore.app.ui.checkout.PaySheet
import com.yourstore.app.ui.checkout.PaymentContent
import com.yourstore.app.ui.components.Scrim
import com.yourstore.app.ui.home.HomeContent
import com.yourstore.app.ui.inbox.ChatContent
import com.yourstore.app.ui.inbox.MessagesContent
import com.yourstore.app.ui.inbox.NotificationsContent
import com.yourstore.app.ui.nav.KBottomBar
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.orders.OrderTrackContent
import com.yourstore.app.ui.orders.OrdersContent
import com.yourstore.app.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen with sample data so it can be compared side by side with the Kutuku Figma frames.
 * Run: ./gradlew testDebugUnitTest --tests "*ScreenshotTest*" -Proborazzi.test.record=true
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w375dp-h812dp-xhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { AppTheme { content() } }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val n = {}
    private fun <T> n1(): (T) -> Unit = {}

    // ---------- sample data
    private val store = Store(1, "Upbox Bag", "upbox-bag", null, null, "Mumbai", true, 104, 1300)
    private val products = listOf(
        Product(id = 1, name = "The Mirac Jiz", price = 1950.0, inStock = true, store = Store(name = "Lisa Robber")),
        Product(id = 2, name = "Meriza Kiles", price = 1434.5, inStock = true, wishlisted = true, store = Store(name = "Gazuna Resika")),
        Product(id = 3, name = "Bag Box 283", price = 1630.0, inStock = true, store = Store(name = "Lisa Robber")),
        Product(id = 4, name = "Box Biggan 992", price = 1630.0, inStock = true, store = Store(name = "Gazuna Resika")),
    )
    private val banners = listOf(Banner(1, "24% off shipping today on bag purchases", "By Kutuku Store", ""), Banner(2, "New season", null, ""))
    private val categories = listOf(Category(1, null, "Clothes", "clothes", null, 358), Category(2, null, "Bags", "bags", null, 160),
        Category(3, null, "Shoes", "shoes", null, 230), Category(4, null, "Electronics", "electronics", null, 130))
    private val home = Home(banners, categories, products, products, products)
    private val profile = Customer(1, "9876543210", "Jonathan", "jonathan@mail.com", unreadNotifications = 2)
    private val variants = listOf(
        Variant(11, "SKU1", "Default", "Brown", "#A0522D", 4200.0, 3525.0, 8, true),
        Variant(12, "SKU2", "Default", "Black", "#111111", 4200.0, 3525.0, 3, true),
        Variant(13, "SKU3", "Default", "Teal", "#2AA7B8", 4200.0, 3525.0, 0, false),
        Variant(14, "SKU4", "Default", "Green", "#10C45C", 4200.0, 3525.0, 5, true),
    )
    private val product = Product(id = 9, name = "Box Bag Linar 1883", variants = variants, price = 3525.0, inStock = true, rating = 4.8, ratingCount = 320,
        description = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book.",
        store = store)
    private val cartItems = listOf(
        CartItem(1, 1, 1, true, "S1", "Default", "Brown", "#A0522D", "Bix Bag Limited Edition 229", null, 80.0, 67.0, 5, 67.0),
        CartItem(2, 2, 1, true, "S2", "Default", "Brown", "#A0522D", "BoxHeadphone 132", null, 30.0, 26.0, 5, 26.0),
        CartItem(3, 3, 1, false, "S3", "Default", "Brown", "#A0522D", "BoxHeadphone 345", null, 40.0, 32.0, 5, 32.0),
        CartItem(4, 4, 1, false, "S4", "Default", "Black", "#000000", "Bix Bag 319", null, 30.0, 24.0, 5, 24.0),
    )
    private val cart = Cart(cartItems, emptyList(), CartSummary(itemCount = 2, cartCount = 4, subtotal = 93.0, deliveryCharge = 6.0, total = 99.0))
    private val address = Address(1, "House", "9876543210", "5482 Adobe Falls Rd #15", "San Diego", null, "Mumbai", "Maharashtra", "400001", 1)
    private val addresses = listOf(address, address.copy(id = 2, name = "Office", line1 = "Bandra Kurla Complex", isDefault = 0),
        address.copy(id = 3, name = "Parents", line1 = "Sector 70", city = "Mohali", state = "Punjab", pincode = "160071", isDefault = 0))
    private val orders = listOf(
        OrderSummary(1, "KTK-1001", "shipped", "paid", 1950.0, "2026-11-17T10:00:00", 1, null, "Bix Bag Limited Edition 229", "Default", 1, "Brown"),
        OrderSummary(2, "KTK-1002", "confirmed", "paid", 2600.0, "2026-11-16T10:00:00", 2, null, "BoxHeadphone 132", "Default", 1, "Black"),
    )
    private val orderDetail = OrderDetail(id = 1, orderNumber = "KTK-1001", status = "shipped", paymentStatus = "paid", total = 1950.0, courierName = "Ravi Kumar",
        courierPhone = "9876543210", shippingAddress = address,
        timeline = listOf(TimelineEntry("placed", null, "2026-11-16T08:00:00"), TimelineEntry("confirmed", null, "2026-11-16T09:00:00"),
            TimelineEntry("packed", null, "2026-11-16T15:00:00"), TimelineEntry("shipped", null, "2026-11-17T10:00:00")))
    private val notifications = listOf(
        AppNotification(1, "order", "Purchase Completed!", "You have successfully purchased 334 headphones, thank you and wait for your package to arrive ✨", createdAt = "2026-11-18T04:00:00"),
        AppNotification(2, "message", "Jeremy Send You a Message", "hello your package has almost arrived, are you at home now?", createdAt = "2026-11-18T04:00:00"),
        AppNotification(3, "promo", "Flash Sale!", "Get 20% discount for first transaction in this month! 😍", createdAt = "2026-11-18T04:00:00"),
        AppNotification(4, "order", "Package Sent", "Hi your package has been sent from new york", createdAt = "2026-11-18T03:50:00"),
    )
    private val conversations = listOf(
        Conversation(1, 1, "Upbox Bag", null, true, "Your order is on the way!", "2026-11-18T04:00:00", 2),
        Conversation(2, 0, "Customer Support", null, true, "How can we help?", "2026-11-17T04:00:00", 0),
    )
    private val messages = listOf(
        ChatMessage(1, "customer", "Hi, is this bag available in black?", createdAt = "2026-11-18T04:00:00"),
        ChatMessage(2, "store", null, 9, createdAt = "2026-11-18T04:01:00", product = ChatProduct(9, "Box Bag Linar 1883", null, 3525.0)),
        ChatMessage(3, "store", "Yes! We have 3 left in black. Shall I reserve one for you?", createdAt = "2026-11-18T04:01:00"),
    )
    private val faqs = listOf(Faq(1, "How do I track my order?", "Open My Order and tap Tracking."), Faq(2, "How long does delivery take?", "3–7 working days."),
        Faq(3, "Can I cancel my order?", "Yes, before it is shipped, from the order detail page."))

    // ---------- onboarding & auth
    @Test fun s01_splash() = shot("01_splash") { SplashContent() }
    @Test fun s02_onboarding() = shot("02_onboarding") { OnboardingContent(emptyList(), n, n) }
    @Test fun s03_create_account() = shot("03_create_account") { RegisterContent("", {}, "", {}, "", {}, false, null, n, n, {}) }
    @Test fun s04_create_account_filled() = shot("04_create_account_filled") {
        RegisterContent("Magdalena Succrose", {}, "magdalena83@mail.com", {}, "magdalenasucrose83", {}, false, null, n, n, {})
    }
    @Test fun s05_verification() = shot("05_verification") { VerificationContent("magdalena83@mail.com", "6381", {}, false, null, n, n, n) }
    @Test fun s06_register_success() = shot("06_register_success") { VerificationContent("magdalena83@mail.com", "638112", {}, false, null, n, n, n, success = true) }
    @Test fun s07_login() = shot("07_login") { LoginContent("", {}, "", {}, false, null, false, n, n, n, n, {}) }
    @Test fun s08_login_filled() = shot("08_login_filled") { LoginContent("magdalena83@mail.com", {}, "magdalenasucrose83", {}, false, null, false, n, n, n, n, {}) }
    @Test fun s09_forgot_password() = shot("09_forgot_password") {
        LoginContent("magdalena83@mail.com", {}, "magdalenasucrose83", {}, false, null, false, n, n, n, n, {}, sheet = LoginSheet.FORGOT,
            sheetContent = { ForgotSheetContent("magdalena83@mail.com", {}, true, false, null, n) })
    }
    @Test fun s10_new_password() = shot("10_new_password") {
        LoginContent("magdalena83@mail.com", {}, "", {}, false, null, false, n, n, n, n, {}, sheet = LoginSheet.NEW_PASSWORD,
            sheetContent = { NewPasswordSheetContent("magdalenasucrose83", {}, "magdalenasucrose83", {}, false, null, n) })
    }

    // ---------- shopping
    @Composable private fun WithBar(route: String, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.padding(bottom = 72.dp)) { content() }
            Box(Modifier.align(Alignment.BottomCenter)) { KBottomBar(route) {} }
        }
    }
    @Test fun s11_home() = shot("11_home") {
        WithBar(Routes.HOME) { HomeContent(home, categories, profile, null, false, 0, {}, n, n, n, n, n, {}, {}, n, {}, n, {}) }
    }
    @Test fun s12_home_category() = shot("12_home_category") {
        WithBar(Routes.HOME) { HomeContent(home, categories, profile, null, false, 1, {}, n, n, n, n, n, {}, {}, n, {}, n, {}) }
    }
    @Test fun s13_search() = shot("13_search") {
        SearchContent("", {}, listOf("Electronics", "Pants", "Three Second", "Long shirt"), listOf(
            PopularSearch("jacket", 1600, 1, "Lunilo Hils jacket", null, "hot"), PopularSearch("jeans", 1000, 2, "Denim Jeans", null, "new"),
            PopularSearch("backpack", 1230, 3, "Redil Backpack", null, "popular"), PopularSearch("speaker", 1100, 4, "JBL Speakers", null, "new"),
        ), n, {}, {}, n, {}, n)
    }
    @Test fun s14_search_result() = shot("14_search_result") {
        ProductListContent("", "Bag Bo..", {}, n, true, null, {}, listOf(store), products, false, null, n, n, n, {}, {}, {}, n)
    }
    @Test fun s15_search_filter() = shot("15_search_filter") {
        ProductListContent("", "Bag Bo..", {}, n, true, null, {}, listOf(store), products, false, null, n, n, n, {}, {}, {}, n,
            filterVisible = true, filterContent = {
                FilterSheetContent(Filters(PriceRange(0.0, 8000.0), listOf(ColorOption("Black", "#1E222B"), ColorOption("Lilac", "#D9D2F2"),
                    ColorOption("Sky", "#CFE3F7"), ColorOption("Sand", "#EBDDB0"), ColorOption("Rose", "#F7D3D6")), listOf("San Diego", "New York", "Amsterdam")),
                    ProductFilters(color = "Black", city = "San Diego")) {}
            })
    }
    @Test fun s16_store() = shot("16_store") {
        StoreContent(store.copy(banners = banners, popular = products), 0, {}, products, n, n, n, {}, {}, n, {})
    }
    @Test fun s17_detail() = shot("17_detail") { ProductDetailContent(product, variants[0], 3, false, {}, {}, {}, n, n, n, n, n, n) }

    // ---------- cart & payment
    @Test fun s18_my_cart() = shot("18_my_cart") {
        CartContent(cart, null, false, "", {}, null, null, emptyList(), {}, n, { _, _ -> }, {}, { _, _ -> }, {}, n, n, n, {}, n)
    }
    @Test fun s19_payment() = shot("19_payment") { PaymentContent(address, true, cart, null, PayMethod.CARD, false, n, n, n, n, n) }
    @Test fun s20_change_payment_method() = shot("20_change_payment_method") {
        PaymentContent(address, true, cart, null, PayMethod.UPI, false, n, n, n, n, n, sheet = PaySheet.METHODS)
    }
    @Test fun s21_order_success() = shot("21_order_success") { PaymentContent(address, true, cart, null, PayMethod.UPI, false, n, n, n, n, n, sheet = PaySheet.SUCCESS) }
    @Test fun s22_address() = shot("22_address") { AddressListContent(addresses, null, "", {}, 1, {}, {}, {}, n, n, n, n) }

    // ---------- orders
    @Test fun s23_my_order() = shot("23_my_order") { WithBar(Routes.ORDERS) { OrdersContent(0, {}, orders, false, false, null, n, n, n, n, {}, {}, {}, {}) } }
    @Test fun s24_history() = shot("24_history") {
        WithBar(Routes.ORDERS) { OrdersContent(1, {}, orders.map { it.copy(status = "delivered") }, false, false, null, n, n, n, n, {}, {}, {}, {}) }
    }
    @Test fun s25_order_track() = shot("25_order_track") { OrderTrackContent(orderDetail, false, n, n, n, n) }

    // ---------- social
    @Test fun s26_my_favorite() = shot("26_my_favorite") {
        WithBar(Routes.WISHLIST) { WishlistContent(products.map { it.copy(categoryName = "Bags") }, null, "", {}, null, {}, "latest", {}, n, {}, {}, n, n) }
    }
    @Test fun s27_notification() = shot("27_notification") { NotificationsContent(notifications, null, n, n, n) {} }
    @Test fun s28_message() = shot("28_message") { MessagesContent(conversations, listOf(store, store.copy(id = 2, name = "Gazuna Resika")), null, "", {}, n, n, {}, {}, n) }
    @Test fun s29_message_detail() = shot("29_message_detail") {
        ChatContent(conversations[0], messages, null, "", {}, false, n, n, {}, {})
    }

    // ---------- settings
    @Test fun s30_settings() = shot("30_settings") { WithBar(Routes.ACCOUNT) { SettingsContent("English", {}, n, n) } }
    @Test fun s31_edit_profile() = shot("31_edit_profile") {
        EditProfileContent(null, "Magdalena Succrose", {}, "magdalena83@mail.com", {}, true, true, false, n, n, n)
    }
    @Test fun s32_change_password() = shot("32_change_password") { ChangePasswordContent(false, "", {}, "", {}, "", {}, null, false, n, n) }
    @Test fun s33_language() = shot("33_language") { LanguageContent("en", "", {}, {}, n) }
    @Test fun s34_notifications_settings() = shot("34_notifications_settings") { NotificationSettingsContent(true, true, true, true, true, { _, _, _, _ -> }, n) }
    @Test fun s35_security() = shot("35_security") { SecurityContent(true, true, true, {}, {}, {}, n) }
    @Test fun s36_help() = shot("36_help") { HelpContent(faqs, null, "", {}, n, n, n, initiallyOpen = 3) }
    @Test fun s37_legal() = shot("37_legal") {
        LegalContent(ContentPage("terms", "Terms", "## Terms\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Eget ornare quam vel facilisis feugiat amet sagittis arcu, tortor.\n\n## Changes to the Service and/or Terms:\nLorem ipsum dolor sit amet, consectetur adipiscing elit."), null, n, n)
    }
    @Test fun s38_logout() = shot("38_logout") {
        Box(Modifier.fillMaxSize()) {
            SettingsContent("English", {}, n, n)
            Scrim {}
            Box(Modifier.align(Alignment.Center).padding(horizontal = 40.dp)) { LogoutCard(n, n) }
        }
    }
}
