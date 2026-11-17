package com.yourstore.app.data

import com.yourstore.app.data.model.*
import retrofit2.http.*

interface Api {
    // Auth
    @POST("api/v1/auth/otp/send") suspend fun sendOtp(@Body b: SendOtpReq): Message
    @POST("api/v1/auth/otp/verify") suspend fun verifyOtp(@Body b: VerifyOtpReq): AuthResponse
    @POST("api/v1/auth/logout") suspend fun logout(): Message

    // Profile & addresses
    @GET("api/v1/profile") suspend fun profile(): Customer
    @PUT("api/v1/profile") suspend fun updateProfile(@Body b: ProfileReq): Customer
    @PUT("api/v1/profile/fcm-token") suspend fun updateFcmToken(@Body b: TokenReq): Message
    @DELETE("api/v1/profile") suspend fun deleteAccount(): Message
    @GET("api/v1/profile/addresses") suspend fun addresses(): List<Address>
    @POST("api/v1/profile/addresses") suspend fun addAddress(@Body b: AddressReq): Address
    @PUT("api/v1/profile/addresses/{id}") suspend fun updateAddress(@Path("id") id: Int, @Body b: AddressReq): Address
    @DELETE("api/v1/profile/addresses/{id}") suspend fun deleteAddress(@Path("id") id: Int): Message

    // Catalog
    @GET("api/v1/home") suspend fun home(): Home
    @GET("api/v1/categories") suspend fun categories(): List<Category>
    @GET("api/v1/products") suspend fun products(
        @Query("q") q: String? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("min_price") minPrice: Int? = null,
        @Query("max_price") maxPrice: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("featured") featured: Int? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): Paged<Product>
    @GET("api/v1/products/{id}") suspend fun product(@Path("id") id: Int): Product
    @GET("api/v1/search/suggest") suspend fun suggest(@Query("q") q: String): List<Product>

    // Cart, coupons, wishlist
    @GET("api/v1/cart") suspend fun cart(@Query("coupon") coupon: String? = null): Cart
    @POST("api/v1/cart") suspend fun addToCart(@Body b: AddCartReq): Cart
    @PUT("api/v1/cart/{variantId}") suspend fun setQty(@Path("variantId") variantId: Int, @Body b: QtyReq): Cart
    @DELETE("api/v1/cart/{variantId}") suspend fun removeFromCart(@Path("variantId") variantId: Int): Cart
    @POST("api/v1/cart/apply-coupon") suspend fun applyCoupon(@Body b: CouponReq): Cart
    @GET("api/v1/coupons") suspend fun coupons(): List<Coupon>
    @GET("api/v1/wishlist") suspend fun wishlist(): List<Product>
    @POST("api/v1/wishlist/{productId}") suspend fun addWishlist(@Path("productId") id: Int): WishlistResult
    @DELETE("api/v1/wishlist/{productId}") suspend fun removeWishlist(@Path("productId") id: Int): WishlistResult

    // Orders & payments
    @POST("api/v1/orders") suspend fun placeOrder(@Body b: PlaceOrderReq): PlacedOrder
    @POST("api/v1/orders/{id}/verify-payment") suspend fun verifyPayment(@Path("id") id: Int, @Body b: VerifyPaymentReq): VerifyResult
    @POST("api/v1/orders/{id}/payment-failed") suspend fun paymentFailed(@Path("id") id: Int, @Body b: ReasonReq): Message
    @GET("api/v1/orders") suspend fun orders(@Query("type") type: String? = null, @Query("page") page: Int = 1): Paged<OrderSummary>
    @GET("api/v1/orders/{id}") suspend fun order(@Path("id") id: Int): OrderDetail
    @POST("api/v1/orders/{id}/cancel") suspend fun cancelOrder(@Path("id") id: Int, @Body b: ReasonReq): Message
    @POST("api/v1/orders/{id}/reorder") suspend fun reorder(@Path("id") id: Int): Message
    @GET("api/v1/orders/payments/history") suspend fun payments(): List<PaymentRecord>
}
