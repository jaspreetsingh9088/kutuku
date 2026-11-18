package com.yourstore.app.ui.checkout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.R
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.*
import com.yourstore.app.payment.PaymentBus
import com.yourstore.app.payment.PaymentResult
import com.yourstore.app.ui.cart.variantText
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.launch

/** Methods offered on "Change Payment Method". Card details are entered inside Razorpay, never stored by the app. */
enum class PayMethod(val key: String, val title: String, val subtitle: String) {
    UPI("upi", "UPI", "Google Pay, PhonePe, Paytm & more"),
    CARD("card", "Credit / Debit Card", "Visa, Mastercard, RuPay"),
    NETBANKING("netbanking", "Net Banking", "All major Indian banks"),
    WALLET("wallet", "Wallet", "Paytm, Mobikwik, Freecharge"),
}

class CheckoutViewModel(private val coupon: String?) : ViewModel() {
    private val c = ShopApp.container
    var addresses by mutableStateOf<List<Address>?>(null)
    var selectedId by mutableStateOf<Int?>(null)
    var cart by mutableStateOf<Cart?>(null)
    var error by mutableStateOf<String?>(null)
    var method by mutableStateOf(PayMethod.UPI)
    var paying by mutableStateOf(false)
    var payError by mutableStateOf<String?>(null)
    var successOrderId by mutableStateOf<Int?>(null)
    private var pendingOrder: PlacedOrder? = null

    init {
        viewModelScope.launch { PaymentBus.results.collect { r -> pendingOrder?.let { onPaymentResult(it, r) } } }
    }

    fun load() {
        error = null
        viewModelScope.launch {
            apiCall { c.api.addresses() }.onSuccess { list ->
                addresses = list
                if (selectedId == null || list.none { it.id == selectedId }) selectedId = (list.firstOrNull { it.isDefault == 1 } ?: list.firstOrNull())?.id
            }.onFailure { error = it.message }
            apiCall { c.api.cart(coupon) }.onSuccess { cart = it }.onFailure { error = it.message }
        }
    }

    fun pay(activity: Activity, storeName: String) {
        val addressId = selectedId ?: run { payError = "Please add a delivery address"; return }
        paying = true; payError = null
        viewModelScope.launch {
            apiCall { c.api.placeOrder(PlaceOrderReq(addressId, cart?.summary?.couponCode, method.key)) }
                .onSuccess { order ->
                    pendingOrder = order
                    runCatching { PaymentBus.open(activity, order.copy(preferredMethod = order.preferredMethod ?: method.key), storeName) }.onFailure {
                        paying = false; payError = "Could not open payment screen"
                        apiCall { c.api.paymentFailed(order.id, ReasonReq("Checkout failed to open")) }
                    }
                }
                .onFailure { paying = false; payError = it.message; load() }
        }
    }

    private suspend fun onPaymentResult(order: PlacedOrder, r: PaymentResult) {
        pendingOrder = null
        when (r) {
            is PaymentResult.Success -> {
                // The backend verifies the Razorpay signature before marking the order paid.
                apiCall { c.api.verifyPayment(order.id, VerifyPaymentReq(r.orderId.ifBlank { order.razorpayOrderId }, r.paymentId, r.signature)) }
                    .onSuccess { c.refreshCartCount(); successOrderId = order.id }
                    .onFailure { payError = "We couldn't confirm your payment yet. If money was deducted, your order will update shortly." }
            }
            is PaymentResult.Failure -> {
                apiCall { c.api.paymentFailed(order.id, ReasonReq(r.description.take(250))) }
                payError = if (r.code == com.razorpay.Checkout.PAYMENT_CANCELED) "Payment cancelled" else "Payment failed: ${r.description}"
                load()
            }
        }
        paying = false
    }
}

fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }

@Composable
fun CheckoutScreen(coupon: String?, selectedAddressId: Int?, navigate: (String) -> Unit, onBack: () -> Unit, onTrack: (Int) -> Unit, onHome: () -> Unit, snack: (String) -> Unit) {
    val vm: CheckoutViewModel = viewModel { CheckoutViewModel(coupon) }
    val context = LocalContext.current
    val storeName = stringResource(R.string.app_name)
    var showMethods by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(selectedAddressId) { if (selectedAddressId != null && selectedAddressId > 0) vm.selectedId = selectedAddressId }
    LaunchedEffect(vm.payError) { vm.payError?.let { snack(it); vm.payError = null } }

    PaymentContent(
        address = vm.addresses?.firstOrNull { it.id == vm.selectedId }, addressesLoaded = vm.addresses != null, cart = vm.cart, error = vm.error,
        method = vm.method, paying = vm.paying,
        onBack = onBack, onRetry = { vm.load() }, onEditAddress = { navigate(Routes.addresses(choose = true)) },
        onChangeMethod = { showMethods = true }, onPay = { context.findActivity()?.let { vm.pay(it, storeName) } },
        sheet = when { vm.successOrderId != null -> PaySheet.SUCCESS; showMethods -> PaySheet.METHODS; else -> PaySheet.NONE },
        onDismissSheet = { if (vm.successOrderId != null) onHome() else showMethods = false },
        onConfirmMethod = { vm.method = it; showMethods = false },
        onTrack = { vm.successOrderId?.let(onTrack) }, onHome = onHome,
    )
}

enum class PaySheet { NONE, METHODS, SUCCESS }

@Composable
fun PaymentContent(
    address: Address?, addressesLoaded: Boolean, cart: Cart?, error: String?, method: PayMethod, paying: Boolean,
    onBack: () -> Unit, onRetry: () -> Unit, onEditAddress: () -> Unit, onChangeMethod: () -> Unit, onPay: () -> Unit,
    sheet: PaySheet = PaySheet.NONE, onDismissSheet: () -> Unit = {}, onConfirmMethod: (PayMethod) -> Unit = {},
    onTrack: () -> Unit = {}, onHome: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            KTopBar("Payment", onBack = onBack)
            when {
                cart == null && error != null -> ErrorState(error, onRetry)
                cart == null || !addressesLoaded -> LoadingBox()
                else -> {
                    val items = cart.items.filter { it.selected }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PagePad)) {
                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Address", action = if (address != null) "Edit" else "Add", onAction = onEditAddress)
                        Spacer(Modifier.height(16.dp))
                        if (address == null) KOutlinedButton("+ Add delivery address", onEditAddress, Modifier.fillMaxWidth(), color = Brand, borderColor = BrandLight)
                        else Row(Modifier.clickable(onClick = onEditAddress), verticalAlignment = Alignment.CenterVertically) {
                            MapThumb(Modifier.size(width = 130.dp, height = 80.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(address.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
                                Spacer(Modifier.height(6.dp))
                                Text(address.oneLine(), fontSize = 12.sp, color = InkMuted, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        SectionTitle("Products (${items.sumOf { it.quantity }})")
                        Spacer(Modifier.height(8.dp))
                        items.forEach { it -> CheckoutItem(it) }
                        Spacer(Modifier.height(20.dp))
                        SectionTitle("Payment Method")
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).clickable(onClick = onChangeMethod)
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            MethodIcon(method, 48.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(method.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                                Text(method.subtitle, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Change", tint = InkMuted)
                        }
                        if (cart.summary.discount > 0) {
                            Spacer(Modifier.height(16.dp))
                            BillLine("Subtotal", cart.summary.subtotal)
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("Discount (${cart.summary.couponCode})", color = Discount, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("- ${inr(cart.summary.discount)}", color = Discount, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        BillLine("Shipping", cart.summary.deliveryCharge)
                        BillLine("Total amount", cart.summary.total, bold = true)
                        Spacer(Modifier.height(16.dp))
                    }
                    Box(Modifier.navigationBarsPadding().padding(horizontal = PagePad, vertical = 16.dp)) {
                        KButton("Checkout Now", onPay, Modifier.fillMaxWidth(), enabled = address != null && items.isNotEmpty(), loading = paying)
                    }
                }
            }
        }
        SheetOverlay(sheet != PaySheet.NONE, onDismissSheet) {
            if (sheet == PaySheet.METHODS) MethodSheet(method, onConfirmMethod)
            else SuccessSheet(onTrack, onHome)
        }
    }
}

@Composable
private fun CheckoutItem(item: CartItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        NetImage(item.image, Modifier.size(width = 74.dp, height = 70.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name + if (item.quantity > 1) "  ×${item.quantity}" else "", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(variantText(item.color, item.label), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                PriceTag(item.price * item.quantity, size = 16)
            }
        }
    }
}

@Composable
fun MethodIcon(m: PayMethod, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Field), contentAlignment = Alignment.Center) {
        if (m == PayMethod.CARD) Icon(painterResource(R.drawable.ic_mastercard), null, Modifier.size(size * 0.5f), tint = Color.Unspecified)
        else Icon(
            when (m) { PayMethod.UPI -> Icons.Outlined.QrCode2; PayMethod.NETBANKING -> Icons.Outlined.AccountBalance; else -> Icons.Outlined.AccountBalanceWallet },
            null, Modifier.size(size * 0.48f), tint = Brand,
        )
    }
}

@Composable
private fun ColumnScope.MethodSheet(current: PayMethod, onConfirm: (PayMethod) -> Unit) {
    var choice by remember { mutableStateOf(current) }
    Text("Change Payment Method", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    PayMethod.entries.forEach { m ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                .border(1.dp, if (choice == m) Brand else Line, RoundedCornerShape(12.dp)).clickable { choice = m }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MethodIcon(m, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text(m.subtitle, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
            KCheckbox(choice == m, { choice = m })
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("Card and bank details are entered securely on Razorpay's payment page.", fontSize = 12.sp, color = InkHint, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))
    KButton("Confirm Payment", { onConfirm(choice) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SuccessSheet(onTrack: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(170.dp).clip(CircleShape).background(BrandSoft))
            Box(Modifier.size(118.dp).clip(RoundedCornerShape(28.dp)).background(Brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LocalMall, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 18.dp).size(36.dp).clip(CircleShape).background(Success), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Order Successfully", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text("Your payment is complete. We'll let you know as soon as your package is on its way.", fontSize = 14.sp, color = InkHint,
            textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(40.dp))
        KButton("Order Tracking", onTrack, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        TextLink("Back to Home", onHome, fontSize = 15)
        Spacer(Modifier.height(24.dp))
    }
}

/** Stylised map tile used for addresses (light land, teal water, white roads and a red pin). */
@Composable
fun MapThumb(modifier: Modifier = Modifier, pinColor: Color = Color(0xFFE5484D)) {
    Box(modifier.background(Color(0xFFEFF4F3)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawPath(Path().apply { moveTo(0f, h * 0.55f); lineTo(w * 0.35f, h); lineTo(0f, h); close() }, Color(0xFF9ED9D2))
            drawPath(Path().apply { moveTo(w * 0.62f, 0f); lineTo(w, 0f); lineTo(w, h * 0.42f); close() }, Color(0xFF9ED9D2))
            drawRect(Color(0xFFDDEBE8), Offset(w * 0.55f, h * 0.55f), Size(w * 0.3f, h * 0.3f))
            drawLine(Color.White, Offset(0f, h * 0.25f), Offset(w, h * 0.7f), strokeWidth = h * 0.08f)
            drawLine(Color.White, Offset(w * 0.3f, 0f), Offset(w * 0.5f, h), strokeWidth = h * 0.06f)
            drawLine(Color.White, Offset(w * 0.75f, 0f), Offset(w * 0.9f, h), strokeWidth = h * 0.04f)
        }
        Icon(Icons.Filled.LocationOn, null, tint = pinColor, modifier = Modifier.size(26.dp))
    }
}
