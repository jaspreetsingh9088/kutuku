package com.yourstore.app.ui.checkout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Lock
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
        topBar = { AppTopBar("Checkout", onBack) },
        bottomBar = {
            if (cart != null && cart.items.isNotEmpty()) Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 12.dp)) {
                    vm.payError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                    Button(
                        onClick = { context.findActivity()?.let { vm.pay(it, storeName) } },
                        enabled = !vm.paying && vm.selectedId != null && cart.problems.isEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (vm.paying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else { Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pay ${inr(cart.summary.total)} securely") }
                    }
                }
            }
        },
    ) { pad ->
        when {
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            cart == null || vm.addresses == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            cart.items.isEmpty() -> ErrorState("Your cart is empty", null, Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Deliver to", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { navigate(Routes.addressForm()) }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text("Add new") }
                        }
                        val list = vm.addresses.orEmpty()
                        if (list.isEmpty()) Text("Add a delivery address to continue", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        list.forEach { a ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = vm.selectedId == a.id, onClick = { vm.selectedId = a.id }).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                RadioButton(selected = vm.selectedId == a.id, onClick = { vm.selectedId = a.id })
                                Column(Modifier.weight(1f).padding(top = 12.dp)) {
                                    Text(a.name, fontWeight = FontWeight.SemiBold)
                                    Text(a.oneLine(), style = MaterialTheme.typography.bodySmall)
                                    Text("Phone: ${a.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { navigate(Routes.addressForm(a.id)) }) { Text("Edit") }
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Order items (${cart.summary.itemCount})", style = MaterialTheme.typography.titleMedium)
                        cart.items.forEach { Row(Modifier.padding(top = 6.dp)) {
                            Text("${it.name} · ${it.label} × ${it.quantity}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(inr(it.lineTotal), style = MaterialTheme.typography.bodySmall)
                        } }
                        cart.problems.forEach { Text("• ${it.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                BillSummary(cart.summary)
                Text("Payments are processed securely by Razorpay. UPI, cards, net banking and wallets are supported.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
