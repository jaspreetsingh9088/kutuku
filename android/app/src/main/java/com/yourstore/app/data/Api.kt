package com.yourstore.app.data

import com.yourstore.app.data.model.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface Api {
    // Auth — password accounts
    @POST("api/v1/auth/register") suspend fun register(@Body b: RegisterReq): Message
    @POST("api/v1/auth/register/verify") suspend fun registerVerify(@Body b: RegisterVerifyReq): AuthResponse
    @POST("api/v1/auth/login") suspend fun login(@Body b: LoginReq): AuthResponse
    @POST("api/v1/auth/password/forgot") suspend fun forgotPassword(@Body b: IdentifierReq): Message
    @POST("api/v1/auth/password/verify-code") suspend fun verifyResetCode(@Body b: VerifyCodeReq): ResetToken
    @POST("api/v1/auth/password/reset") suspend fun resetPassword(@Body b: ResetPasswordReq): AuthResponse
    // Auth — one-time code
    @POST("api/v1/auth/otp/send") suspend fun sendOtp(@Body b: SendOtpReq): Message
    @POST("api/v1/auth/otp/verify") suspend fun verifyOtp(@Body b: VerifyOtpReq): AuthResponse
    @POST("api/v1/auth/logout") suspend fun logout(): Message

    // Profile & addresses
    @GET("api/v1/profile") suspend fun profile(): Customer
    @PUT("api/v1/profile") suspend fun updateProfile(@Body b: ProfileReq): Customer
    @Multipart @POST("api/v1/profile/avatar") suspend fun uploadAvatar(@Part file: MultipartBody.Part): AvatarResult
    @PUT("api/v1/profile/password") suspend fun changePassword(@Body b: ChangePasswordReq): Message
    @PUT("api/v1/profile/notification-settings") suspend fun notificationSettings(@Body b: NotificationSettingsReq): Message
    @PUT("api/v1/profile/fcm-token") suspend fun updateFcmToken(@Body b: TokenReq): Message
    @DELETE("api/v1/profile") suspend fun deleteAccount(): Message
    @GET("api/v1/profile/addresses") suspend fun addresses(): List<Address>
    @POST("api/v1/profile/addresses") suspend fun addAddress(@Body b: AddressReq): Address
    @PUT("api/v1/profile/addresses/{id}") suspend fun updateAddress(@Path("id") id: Int, @Body b: AddressReq): Address
    @DELETE("api/v1/profile/addresses/{id}") suspend fun deleteAddress(@Path("id") id: Int): Message

    // Content
    @GET("api/v1/onboarding") suspend fun onboarding(): List<OnboardingSlide>
    @GET("api/v1/faqs") suspend fun faqs(): List<Faq>
    @GET("api/v1/pages/{slug}") suspend fun page(@Path("slug") slug: String): ContentPage

    // Catalog
    @GET("api/v1/home") suspend fun home(): Home
    @GET("api/v1/categories") suspend fun categories(): List<Category>
    @GET("api/v1/filters") suspend fun filters(): Filters
    @GET("api/v1/products") suspend fun products(
        @Query("q") q: String? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("store_id") storeId: Int? = null,
        @Query("color") color: String? = null,
        @Query("city") city: String? = null,
        @Query("min_price") minPrice: Int? = null,
        @Query("max_price") maxPrice: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("featured") featured: Int? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ProductPage
    @GET("api/v1/products/{id}") suspend fun product(@Path("id") id: Int): Product
    @GET("api/v1/products/{id}/reviews") suspend fun reviews(@Path("id") id: Int): Paged<Review>
    @POST("api/v1/products/{id}/reviews") suspend fun addReview(@Path("id") id: Int, @Body b: ReviewReq): Message
    @GET("api/v1/search/popular") suspend fun popularSearches(): List<PopularSearch>
    @GET("api/v1/stores") suspend fun stores(@Query("followed") followed: Int? = null): List<Store>
    @GET("api/v1/stores/{id}") suspend fun store(@Path("id") id: Int): Store
    @POST("api/v1/stores/{id}/follow") suspend fun follow(@Path("id") id: Int): FollowResult
    @DELETE("api/v1/stores/{id}/follow") suspend fun unfollow(@Path("id") id: Int): FollowResult

    // Cart, coupons, wishlist
    @GET("api/v1/cart") suspend fun cart(@Query("coupon") coupon: String? = null): Cart
    @POST("api/v1/cart") suspend fun addToCart(@Body b: AddCartReq): Cart
    @PUT("api/v1/cart/{variantId}") suspend fun setQty(@Path("variantId") variantId: Int, @Body b: QtyReq): Cart
    @PUT("api/v1/cart/{variantId}/select") suspend fun selectItem(@Path("variantId") variantId: Int, @Body b: SelectReq, @Query("coupon") coupon: String? = null): Cart
    @PUT("api/v1/cart-select-all") suspend fun selectAll(@Body b: SelectReq, @Query("coupon") coupon: String? = null): Cart
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
    @POST("api/v1/orders/{id}/received") suspend fun orderReceived(@Path("id") id: Int): Message
    @POST("api/v1/orders/{id}/reorder") suspend fun reorder(@Path("id") id: Int): Message
    @GET("api/v1/orders/payments/history") suspend fun payments(): List<PaymentRecord>

    // Inbox: notifications and chat
    @GET("api/v1/inbox/notifications") suspend fun notifications(@Query("page") page: Int = 1): NotificationPage
    @POST("api/v1/inbox/notifications/read-all") suspend fun readAllNotifications(): Message
    @GET("api/v1/inbox/conversations") suspend fun conversations(): List<Conversation>
    @POST("api/v1/inbox/conversations") suspend fun openConversation(@Body b: ConversationReq): Conversation
    @GET("api/v1/inbox/conversations/{id}/messages") suspend fun messages(@Path("id") id: Int, @Query("after_id") afterId: Int? = null): MessagesResponse
    @POST("api/v1/inbox/conversations/{id}/messages") suspend fun sendMessage(@Path("id") id: Int, @Body b: SendMessageReq): ChatMessage
}
