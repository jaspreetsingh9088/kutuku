package com.yourstore.app.ui.nav

import android.net.Uri

object Routes {
    // Onboarding & auth
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login?sheet={sheet}"
    fun login(sheet: String = "") = "login?sheet=$sheet"
    const val REGISTER = "register"
    const val VERIFY = "verify"
    val authRoutes = setOf(SPLASH, ONBOARDING, LOGIN, REGISTER, VERIFY)

    // Bottom navigation: Home · My Order · Favorite · My Profile
    const val HOME = "home"
    const val ORDERS = "orders"
    const val WISHLIST = "wishlist"
    const val ACCOUNT = "account"
    val topLevel = setOf(HOME, ORDERS, WISHLIST, ACCOUNT)

    // Shopping
    const val SEARCH = "search"
    const val PRODUCTS = "products?categoryId={categoryId}&q={q}&title={title}&featured={featured}&sort={sort}"
    fun products(categoryId: Int? = null, q: String? = null, title: String? = null, featured: Boolean = false, sort: String? = null) =
        "products?categoryId=${categoryId ?: -1}&q=${Uri.encode(q ?: "")}&title=${Uri.encode(title ?: "")}&featured=$featured&sort=${sort ?: ""}"
    const val PRODUCT = "product/{id}"
    fun product(id: Int) = "product/$id"
    const val STORE = "store/{id}"
    fun store(id: Int) = "store/$id"
    const val CART = "cart"
    const val CHECKOUT = "checkout?coupon={coupon}"
    fun checkout(coupon: String?) = "checkout?coupon=${Uri.encode(coupon ?: "")}"
    const val ADDRESSES = "addresses?choose={choose}"
    fun addresses(choose: Boolean) = "addresses?choose=$choose"
    const val ADDRESS_FORM = "address_form?id={id}"
    fun addressForm(id: Int? = null) = "address_form?id=${id ?: -1}"

    // Orders
    const val ORDER = "order/{id}"
    fun order(id: Int) = "order/$id"
    const val TRACK = "track/{id}"
    fun track(id: Int) = "track/$id"
    const val PAYMENTS = "payments"

    // Inbox
    const val NOTIFICATIONS = "notifications"
    const val MESSAGES = "messages"
    const val CHAT = "chat/{id}"
    fun chat(id: Int) = "chat/$id"

    // Settings
    const val PROFILE_EDIT = "profile_edit"
    const val CHANGE_PASSWORD = "change_password"
    const val NOTIF_SETTINGS = "notification_settings"
    const val SECURITY = "security"
    const val LANGUAGE = "language"
    const val HELP = "help"
    const val LEGAL = "legal"
}
