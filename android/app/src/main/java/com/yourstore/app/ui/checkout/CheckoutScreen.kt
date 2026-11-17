package com.yourstore.app.ui.checkout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.yourstore.app.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.R
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.*
import com.yourstore.app.payment.PaymentBus
import com.yourstore.app.payment.PaymentResult
import com.yourstore.app.ui.cart.BillSummary
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import kotlinx.coroutines.launch

class CheckoutViewModel(private val coupon: String?) : ViewModel() {
    private val c = ShopApp.container
    var addresses by mutableStateOf<List<Address>?>(null)
    var selectedId by mutableStateOf<Int?>(null)
    var cart by mutableStateOf<Cart?>(null)
    var error by mutableStateOf<String?>(null)
    var paying by mutableStateOf(false)
    var payError by mutableStateOf<String?>(null)
    var successOrderId by mutableStateOf<Int?>(null)
    private var pendingOrder: PlacedOrder? = null

    init {
        viewModelScope.launch {
            PaymentBus.results.collect { r -> pendingOrder?.let { onPaymentResult(it, r) } }
        }
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
            apiCall { c.api.placeOrder(PlaceOrderReq(addressId, cart?.summary?.couponCode)) }
                .onSuccess { order ->
                    pendingOrder = order
                    runCatching { PaymentBus.open(activity, order, storeName) }.onFailure {
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
                // Never trust the device: the backend verifies the Razorpay signature before marking the order paid.
                apiCall { c.api.verifyPayment(order.id, VerifyPaymentReq(r.orderId.ifBlank { order.razorpayOrderId }, r.paymentId, r.signature)) }
                    .onSuccess { c.setCartCount(0); successOrderId = order.id }
                    .onFailure {
                        // The webhook may still confirm it; send the user to the order to check status.
                        payError = "We couldn't confirm your payment yet. If money was deducted, your order will update shortly."
                        successOrderId = null
                    }
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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun CheckoutScreen(coupon: String?, navigate: (String) -> Unit, onSuccess: (Int) -> Unit, onBack: () -> Unit) {
    val vm: CheckoutViewModel = viewModel(key = "checkout-$coupon") { CheckoutViewModel(coupon) }
    val context = LocalContext.current
    val storeName = stringResource(R.string.app_name)
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.successOrderId) { vm.successOrderId?.let { onSuccess(it) } }
    val cart = vm.cart

    Scaffold(
        containerColor = Color.White,
        topBar = { AppTopBar("Payment", onBack) },
        bottomBar = {
            if (cart != null && cart.items.isNotEmpty()) Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                vm.payError?.let { Text(it, color = Warning, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Text("Total amount", color = InkMuted, modifier = Modifier.weight(1f))
                    PriceTag(cart.summary.total, size = 22)
                }
                PillButton(
                    "Checkout Now", onClick = { context.findActivity()?.let { vm.pay(it, storeName) } },
                    enabled = vm.selectedId != null && cart.problems.isEmpty(), loading = vm.paying, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { pad ->
        when {
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            cart == null || vm.addresses == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            cart.items.isEmpty() -> ErrorState("Your cart is empty", null, Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Address", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("Add New", color = Brand, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { navigate(Routes.addressForm()) }.padding(4.dp))
                }
                val list = vm.addresses.orEmpty()
                if (list.isEmpty()) Text("Add a delivery address to continue", color = InkMuted)
                list.forEach { a ->
                    val sel = vm.selectedId == a.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, if (sel) Brand else Line, RoundedCornerShape(14.dp))
                            .selectable(selected = sel, onClick = { vm.selectedId = a.id }).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) BrandSoft else Tile), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.LocationOn, null, tint = if (sel) Brand else InkHint, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall)
                            Text(a.oneLine(), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Text("Edit", color = Brand, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).clickable { navigate(Routes.addressForm(a.id)) })
                        }
                        RadioButton(selected = sel, onClick = { vm.selectedId = a.id }, colors = RadioButtonDefaults.colors(selectedColor = Brand, unselectedColor = Line))
                    }
                }

                Text("Products (${cart.summary.itemCount})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                cart.items.forEach { it ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NetImage(it.image, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text("${it.label} · Qty ${it.quantity}", style = MaterialTheme.typography.bodySmall)
                        }
                        PriceTag(it.lineTotal, size = 16)
                    }
                }
                cart.problems.forEach { Text("• ${it.message}", color = Warning, fontSize = 13.sp) }

                Text("Payment Method", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Field), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Payments, null, tint = Brand)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Razorpay", style = MaterialTheme.typography.titleSmall)
                        Text("UPI · Cards · Net Banking · Wallets", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.Lock, null, tint = InkHint, modifier = Modifier.size(18.dp))
                }
                BillSummary(cart.summary)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
