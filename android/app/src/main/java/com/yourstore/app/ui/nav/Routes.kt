package com.yourstore.app.ui.nav

import android.net.Uri

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val CART = "cart"
    const val ORDERS = "orders"
    const val ACCOUNT = "account"

    const val PRODUCTS = "products?categoryId={categoryId}&q={q}&title={title}&featured={featured}&sort={sort}"
    fun products(categoryId: Int? = null, q: String? = null, title: String? = null, featured: Boolean = false, sort: String? = null) =
        "products?categoryId=${categoryId ?: -1}&q=${Uri.encode(q ?: "")}&title=${Uri.encode(title ?: "")}&featured=$featured&sort=${sort ?: ""}"

    const val PRODUCT = "product/{id}"
    fun product(id: Int) = "product/$id"

    const val CHECKOUT = "checkout?coupon={coupon}"
    fun checkout(coupon: String?) = "checkout?coupon=${Uri.encode(coupon ?: "")}"

    const val ADDRESSES = "addresses"
    const val ADDRESS_FORM = "address_form?id={id}"
    fun addressForm(id: Int? = null) = "address_form?id=${id ?: -1}"

    const val ORDER = "order/{id}"
    fun order(id: Int) = "order/$id"
    const val ORDER_SUCCESS = "order_success/{id}"
    fun orderSuccess(id: Int) = "order_success/$id"

    const val WISHLIST = "wishlist"
    const val PROFILE_EDIT = "profile_edit?onboarding={onboarding}"
    fun profileEdit(onboarding: Boolean = false) = "profile_edit?onboarding=$onboarding"
    const val PAYMENTS = "payments"

    val topLevel = setOf(HOME, CATEGORIES, CART, ORDERS, ACCOUNT)
}
