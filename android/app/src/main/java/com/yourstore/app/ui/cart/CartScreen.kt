package com.yourstore.app.ui.cart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Cart
import com.yourstore.app.data.model.CartSummary
import com.yourstore.app.data.model.Coupon
import com.yourstore.app.data.model.CouponReq
import com.yourstore.app.data.model.QtyReq
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.Discount
import kotlinx.coroutines.launch

class CartViewModel : ViewModel() {
    private val c = ShopApp.container
    var cart by mutableStateOf<Cart?>(null)
    var error by mutableStateOf<String?>(null)
    var coupon by mutableStateOf<String?>(null)
    var couponError by mutableStateOf<String?>(null)
    var coupons by mutableStateOf<List<Coupon>>(emptyList())
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun load() {
        error = null
        viewModelScope.launch {
            apiCall { c.api.cart(coupon) }.onSuccess { set(it) }.onFailure { if (cart == null) error = it.message }
            apiCall { c.api.coupons() }.onSuccess { coupons = it }
        }
    }

    private fun set(r: Cart) {
        cart = r
        c.setCartCount(r.summary.itemCount)
        if (r.summary.couponError != null && coupon != null) { couponError = r.summary.couponError; coupon = null }
    }

    fun setQty(variantId: Int, q: Int) {
        busy = true
        viewModelScope.launch {
            apiCall { c.api.setQty(variantId, QtyReq(q)) }.onSuccess { set(it); refreshWithCoupon() }.onFailure { message = it.message }
            busy = false
        }
    }

    fun remove(variantId: Int) = setQty(variantId, 0)

    private suspend fun refreshWithCoupon() { if (coupon != null) apiCall { c.api.cart(coupon) }.onSuccess { set(it) } }

    fun applyCoupon(code: String) {
        if (code.isBlank()) return
        busy = true; couponError = null
        viewModelScope.launch {
            apiCall { c.api.applyCoupon(CouponReq(code.trim())) }
                .onSuccess { coupon = it.summary.couponCode; set(it); message = "Coupon applied — you save ${inr(it.summary.discount)}" }
                .onFailure { couponError = it.message }
            busy = false
        }
    }

    fun removeCoupon() { coupon = null; couponError = null; load() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(navigate: (String) -> Unit, vm: CartViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    var showCoupons by remember { mutableStateOf(false) }
    val cart = vm.cart

    Scaffold(
        topBar = { AppTopBar(if (cart != null && cart.summary.itemCount > 0) "My cart (${cart.summary.itemCount})" else "My cart") },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (cart != null && cart.items.isNotEmpty()) Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(inr(cart.summary.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (cart.summary.savings > 0) Text("You save ${inr(cart.summary.savings)}", color = Discount, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { navigate(Routes.checkout(vm.coupon)) }, enabled = cart.problems.isEmpty() && !vm.busy, modifier = Modifier.height(50.dp)) {
                        Text("Proceed to checkout")
                    }
                }
            }
        },
    ) { pad ->
        when {
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            cart == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            cart.items.isEmpty() -> Box(Modifier.padding(pad)) {
                EmptyState(Icons.Outlined.ShoppingCart, "Your cart is empty", "Add products to see them here", "Start shopping") { navigate(Routes.HOME) }
            }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cart.problems.isNotEmpty()) item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Please update your cart", fontWeight = FontWeight.SemiBold)
                            cart.problems.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                val s = cart.summary
                if (s.deliveryCharge > 0 && s.freeDeliveryAbove > 0) item {
                    Text("Add ${inr(s.freeDeliveryAbove - (s.subtotal - s.discount))} more for FREE delivery", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                items(cart.items, key = { it.variantId }) { item ->
                    val problem = cart.problems.any { p -> p.variantId == item.variantId }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(12.dp)) {
                            NetImage(item.image, Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).clickable { navigate(Routes.product(item.productId)) })
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(item.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                PriceRow(item.price, item.mrp)
                                if (problem) Text(if (item.stock > 0) "Only ${item.stock} available" else "Out of stock", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QuantityStepper(item.quantity, { q -> vm.setQty(item.variantId, q) }, max = item.stock.coerceAtLeast(item.quantity), enabled = !vm.busy)
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { vm.remove(item.variantId) }, enabled = !vm.busy) { Icon(Icons.Outlined.DeleteOutline, "Remove") }
                                }
                            }
                        }
                    }
                }
                item { CouponBox(vm, onBrowse = { showCoupons = true }) }
                item { BillSummary(cart.summary) }
            }
        }
    }

    if (showCoupons) ModalBottomSheet(onDismissRequest = { showCoupons = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Available coupons", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (vm.coupons.isEmpty()) Text("No coupons available right now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.coupons.forEach { cp ->
                ListItem(
                    headlineContent = { Text(cp.code, fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text((cp.description ?: (if (cp.discountType == "percent") "${cp.discountValue.toInt()}% off" else "${inr(cp.discountValue)} off")) +
                            if (cp.minOrderValue > 0) " · Min. order ${inr(cp.minOrderValue)}" else "")
                    },
                    trailingContent = { TextButton(onClick = { vm.applyCoupon(cp.code); showCoupons = false }) { Text("Apply") } },
                )
            }
        }
    }
}

@Composable
private fun CouponBox(vm: CartViewModel, onBrowse: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            if (vm.coupon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalOffer, null, tint = Discount)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${vm.coupon} applied", fontWeight = FontWeight.SemiBold)
                        Text("You save ${inr(vm.cart?.summary?.discount ?: 0.0)}", color = Discount, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = vm::removeCoupon) { Icon(Icons.Filled.Close, "Remove coupon") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(code, { code = it.uppercase().trim() }, placeholder = { Text("Enter coupon code") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.LocalOffer, null) }, modifier = Modifier.weight(1f), isError = vm.couponError != null)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.applyCoupon(code) }, enabled = code.isNotBlank() && !vm.busy) { Text("Apply") }
                }
                vm.couponError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                TextButton(onClick = onBrowse, contentPadding = PaddingValues(0.dp)) { Text("View available coupons") }
            }
        }
    }
}

@Composable
fun BillSummary(s: CartSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bill details", style = MaterialTheme.typography.titleMedium)
            Line("Item total (MRP)", inr(s.mrpTotal))
            if (s.mrpTotal > s.subtotal) Line("Product discount", "− ${inr(s.mrpTotal - s.subtotal)}", Discount)
            if (s.discount > 0) Line("Coupon ${s.couponCode ?: ""}", "− ${inr(s.discount)}", Discount)
            Line("Delivery charges", if (s.deliveryCharge > 0) inr(s.deliveryCharge) else "FREE", if (s.deliveryCharge > 0) null else Discount)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row { Text("To pay", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(inr(s.total), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    Row {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}
