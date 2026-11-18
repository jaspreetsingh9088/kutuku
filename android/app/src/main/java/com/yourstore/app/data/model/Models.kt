package com.yourstore.app.data.model

// JSON uses snake_case; Gson is configured with LOWER_CASE_WITH_UNDERSCORES so camelCase names map automatically.
// Every property has a default so Gson uses the no-arg constructor and missing fields never become null in non-null types.

data class Customer(
    val id: Int = 0,
    val mobile: String? = null,
    val name: String? = null,
    val email: String? = null,
    val gstin: String? = null,
    val avatarUrl: String? = null,
    val hasPassword: Boolean = false,
    val notifPayment: Int = 1,
    val notifTracking: Int = 1,
    val notifComplete: Int = 1,
    val notifGeneral: Int = 1,
    val orderCount: Int = 0,
    val unreadNotifications: Int = 0,
) {
    val contact: String get() = email ?: mobile?.let { "+91 $it" } ?: ""
}

data class AuthResponse(val token: String = "", val isNew: Boolean = false, val customer: Customer = Customer())
data class ResetToken(val resetToken: String = "")

data class Banner(
    val id: Int = 0,
    val title: String = "",
    val description: String? = null,
    val imageUrl: String = "",
    val redirectType: String = "none",
    val redirectValue: String? = null,
)

data class Category(
    val id: Int = 0,
    val parentId: Int? = null,
    val name: String = "",
    val slug: String = "",
    val imageUrl: String? = null,
    val productCount: Int = 0,
    val children: List<Category>? = null,
)

data class Store(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
    val logoUrl: String? = null,
    val description: String? = null,
    val city: String? = null,
    val isVerified: Boolean = false,
    val productCount: Int = 0,
    val followerCount: Int = 0,
    val following: Boolean = false,
    val banners: List<Banner>? = null,
    val popular: List<Product>? = null,
)

data class ProductImage(val id: Int = 0, val url: String = "")

data class Variant(
    val id: Int = 0,
    val sku: String = "",
    val label: String = "",
    val color: String? = null,
    val colorHex: String? = null,
    val mrp: Double = 0.0,
    val price: Double = 0.0,
    val stock: Int = 0,
    val inStock: Boolean = false,
) {
    val discountPercent: Int get() = if (mrp > price && mrp > 0) (((mrp - price) / mrp) * 100).toInt() else 0
}

data class Spec(val key: String = "", val value: String = "")

data class Product(
    val id: Int = 0,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val name: String = "",
    val description: String? = null,
    val specifications: List<Spec>? = null,
    val images: List<ProductImage> = emptyList(),
    val variants: List<Variant> = emptyList(),
    val image: String? = null,
    val price: Double? = null,
    val mrp: Double? = null,
    val inStock: Boolean = false,
    val wishlisted: Boolean = false,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val soldCount: Int = 0,
    val store: Store? = null,
    val related: List<Product>? = null,
)

data class Home(
    val banners: List<Banner> = emptyList(),
    val categories: List<Category> = emptyList(),
    val featured: List<Product> = emptyList(),
    val popular: List<Product> = emptyList(),
    val latest: List<Product> = emptyList(),
)

data class PageMeta(val page: Int = 1, val limit: Int = 20, val total: Int = 0, val pages: Int = 0)
data class Paged<T>(val data: List<T> = emptyList(), val meta: PageMeta = PageMeta())
data class ProductPage(val data: List<Product> = emptyList(), val meta: PageMeta = PageMeta(), val stores: List<Store>? = null)

data class PopularSearch(
    val term: String = "", val count: Int = 0, val productId: Int = 0, val productName: String = "",
    val image: String? = null, val tag: String = "new",
)

data class PriceRange(val min: Double = 0.0, val max: Double = 0.0)
data class ColorOption(val name: String = "", val hex: String? = null)
data class Filters(val price: PriceRange = PriceRange(), val colors: List<ColorOption> = emptyList(), val cities: List<String> = emptyList())

data class CartItem(
    val variantId: Int = 0,
    val productId: Int = 0,
    val quantity: Int = 0,
    val selected: Boolean = true,
    val sku: String = "",
    val label: String = "",
    val color: String? = null,
    val colorHex: String? = null,
    val name: String = "",
    val image: String? = null,
    val mrp: Double = 0.0,
    val price: Double = 0.0,
    val stock: Int = 0,
    val lineTotal: Double = 0.0,
    val productActive: Int = 1,
    val variantActive: Int = 1,
)

data class CartProblem(val variantId: Int = 0, val message: String = "")

data class CartSummary(
    val itemCount: Int = 0,
    val cartCount: Int = 0,
    val mrpTotal: Double = 0.0,
    val subtotal: Double = 0.0,
    val savings: Double = 0.0,
    val discount: Double = 0.0,
    val couponCode: String? = null,
    val couponError: String? = null,
    val deliveryCharge: Double = 0.0,
    val freeDeliveryAbove: Double = 0.0,
    val total: Double = 0.0,
)

data class Cart(
    val items: List<CartItem> = emptyList(),
    val problems: List<CartProblem> = emptyList(),
    val summary: CartSummary = CartSummary(),
)

data class Coupon(
    val code: String = "",
    val description: String? = null,
    val discountType: String = "fixed",
    val discountValue: Double = 0.0,
    val maxDiscount: Double? = null,
    val minOrderValue: Double = 0.0,
    val validTo: String? = null,
)

data class Address(
    val id: Int = 0,
    val name: String = "",
    val phone: String = "",
    val line1: String = "",
    val line2: String? = null,
    val landmark: String? = null,
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val isDefault: Int = 0,
) {
    fun oneLine() = listOfNotNull(line1, line2?.takeIf { it.isNotBlank() }, landmark?.takeIf { it.isNotBlank() }, city, "$state - $pincode").joinToString(", ")
}

data class Prefill(val contact: String = "", val name: String = "", val email: String = "")

data class PlacedOrder(
    val id: Int = 0,
    val orderNumber: String = "",
    val total: Double = 0.0,
    val razorpayOrderId: String = "",
    val razorpayKeyId: String = "",
    val amountPaise: Long = 0,
    val currency: String = "INR",
    val preferredMethod: String? = null,
    val prefill: Prefill = Prefill(),
)

data class OrderSummary(
    val id: Int = 0,
    val orderNumber: String = "",
    val status: String = "",
    val paymentStatus: String = "",
    val total: Double = 0.0,
    val createdAt: String = "",
    val itemCount: Int = 0,
    val image: String? = null,
    val firstItem: String? = null,
    val firstVariant: String? = null,
    val firstQuantity: Int = 0,
    val firstColor: String? = null,
)

data class OrderItem(
    val id: Int = 0,
    val productId: Int? = null,
    val variantId: Int? = null,
    val productName: String = "",
    val variantLabel: String = "",
    val variantColor: String? = null,
    val imageUrl: String? = null,
    val unitPrice: Double = 0.0,
    val mrp: Double = 0.0,
    val quantity: Int = 0,
    val lineTotal: Double = 0.0,
)

data class TimelineEntry(val status: String = "", val note: String? = null, val createdAt: String = "")

data class OrderDetail(
    val id: Int = 0,
    val orderNumber: String = "",
    val status: String = "",
    val paymentStatus: String = "",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val total: Double = 0.0,
    val refundAmount: Double = 0.0,
    val couponCode: String? = null,
    val invoiceNumber: String? = null,
    val cancelReason: String? = null,
    val razorpayPaymentId: String? = null,
    val courierName: String? = null,
    val courierPhone: String? = null,
    val trackingUrl: String? = null,
    val createdAt: String = "",
    val shippingAddress: Address = Address(),
    val items: List<OrderItem> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val canCancel: Boolean = false,
)

data class PaymentRecord(
    val id: Int = 0,
    val providerPaymentId: String? = null,
    val amount: Double = 0.0,
    val status: String = "",
    val createdAt: String = "",
    val orderId: Int = 0,
    val orderNumber: String = "",
)

data class AppNotification(
    val id: Int = 0,
    val type: String = "general",
    val title: String = "",
    val body: String? = null,
    val imageUrl: String? = null,
    val data: Map<String, Any>? = null,
    val isRead: Boolean = false,
    val createdAt: String = "",
)
data class NotificationPage(val data: List<AppNotification> = emptyList(), val meta: PageMeta = PageMeta(), val unread: Int = 0)

data class Conversation(
    val id: Int = 0,
    val storeId: Int = 0,
    val name: String = "",
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val unread: Int = 0,
)

data class ChatProduct(val id: Int = 0, val name: String = "", val image: String? = null, val price: Double? = null, val color: String? = null)

data class ChatMessage(
    val id: Int = 0,
    val sender: String = "customer",
    val body: String? = null,
    val productId: Int? = null,
    val orderId: Int? = null,
    val createdAt: String = "",
    val product: ChatProduct? = null,
)
data class MessagesResponse(val conversation: Conversation = Conversation(), val data: List<ChatMessage> = emptyList())

data class OnboardingSlide(val id: Int = 0, val title: String = "", val subtitle: String? = null, val imageUrl: String = "")
data class Faq(val id: Int = 0, val question: String = "", val answer: String = "")
data class ContentPage(val slug: String = "", val title: String = "", val content: String = "")
data class Review(val id: Int = 0, val rating: Int = 0, val comment: String? = null, val createdAt: String = "", val customerName: String = "", val avatarUrl: String? = null)

data class VerifyResult(val success: Boolean = false, val order: OrderDetail? = null)
data class WishlistResult(val wishlisted: Boolean = false)
data class FollowResult(val following: Boolean = false)
data class AvatarResult(val avatarUrl: String = "")
data class Message(val message: String? = null, val ok: Boolean? = null, val added: Int? = null)

// ---- Request bodies ----
data class SendOtpReq(val mobile: String)
data class VerifyOtpReq(val mobile: String, val otp: String, val fcmToken: String?)
data class RegisterReq(val name: String, val identifier: String, val password: String)
data class RegisterVerifyReq(val name: String, val identifier: String, val password: String, val otp: String, val fcmToken: String?)
data class LoginReq(val identifier: String, val password: String, val fcmToken: String?)
data class IdentifierReq(val identifier: String)
data class VerifyCodeReq(val identifier: String, val otp: String)
data class ResetPasswordReq(val resetToken: String, val password: String, val fcmToken: String?)
data class ChangePasswordReq(val current: String?, val password: String)
data class NotificationSettingsReq(val payment: Boolean, val tracking: Boolean, val completeOrder: Boolean, val general: Boolean)
data class ProfileReq(val name: String, val email: String?, val gstin: String?)
data class TokenReq(val token: String)
data class AddCartReq(val variantId: Int, val quantity: Int)
data class QtyReq(val quantity: Int)
data class SelectReq(val selected: Boolean)
data class CouponReq(val code: String)
data class AddressReq(
    val name: String, val phone: String, val line1: String, val line2: String?, val landmark: String?,
    val city: String, val state: String, val pincode: String, val isDefault: Boolean,
)
data class PlaceOrderReq(val addressId: Int, val couponCode: String?, val preferredMethod: String?)
data class VerifyPaymentReq(val razorpayOrderId: String, val razorpayPaymentId: String, val razorpaySignature: String)
data class ReasonReq(val reason: String?)
data class ConversationReq(val storeId: Int)
data class SendMessageReq(val body: String?, val productId: Int? = null, val orderId: Int? = null)
data class ReviewReq(val rating: Int, val comment: String?)
