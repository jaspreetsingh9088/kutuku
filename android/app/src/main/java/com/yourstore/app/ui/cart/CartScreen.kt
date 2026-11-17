package com.yourstore.app.ui.cart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Discount
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.yourstore.app.ui.theme.*
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
fun CartScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: CartViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    var showCoupons by remember { mutableStateOf(false) }
    val cart = vm.cart

    Scaffold(
        containerColor = Color.White,
        topBar = { AppTopBar("My Cart", onBack) { Icon(Icons.Outlined.ShoppingBag, null, tint = Ink, modifier = Modifier.padding(end = 14.dp)) } },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (cart != null && cart.items.isNotEmpty()) Surface(
                color = Color.White, shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 16.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(CircleShape).background(Line))
                    Spacer(Modifier.height(16.dp))
                    CouponBox(vm, onBrowse = { showCoupons = true })
                    Spacer(Modifier.height(16.dp))
                    BillLines(cart.summary)
                    Spacer(Modifier.height(16.dp))
                    PillButton("Checkout", onClick = { navigate(Routes.checkout(vm.coupon)) }, enabled = cart.problems.isEmpty() && !vm.busy, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { pad ->
        when {
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            cart == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            cart.items.isEmpty() -> Box(Modifier.padding(pad)) {
                EmptyState(Icons.Outlined.ShoppingBag, "Your cart is empty", "Add products to see them here", "Start Shopping") { navigate(Routes.HOME) }
            }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                if (cart.problems.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(HotBg).padding(12.dp)) {
                        Text("Please update your cart", fontWeight = FontWeight.SemiBold, color = Warning)
                        cart.problems.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                val s = cart.summary
                if (s.deliveryCharge > 0 && s.freeDeliveryAbove > 0) item {
                    Text("Add ${inr(s.freeDeliveryAbove - (s.subtotal - s.discount))} more for FREE delivery", color = Brand,
                        style = MaterialTheme.typography.bodySmall.copy(color = Brand, fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(BrandSoft).padding(horizontal = 14.dp, vertical = 8.dp))
                }
                items(cart.items, key = { it.variantId }) { item ->
                    val problem = cart.problems.any { p -> p.variantId == item.variantId }
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetImage(item.image, Modifier.size(78.dp).clip(RoundedCornerShape(12.dp)).clickable { navigate(Routes.product(item.productId)) })
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.DeleteOutline, "Remove", tint = Warning, modifier = Modifier.size(20.dp).clickable(enabled = !vm.busy) { vm.remove(item.variantId) })
                            }
                            Text(buildString { append("Size: "); append(item.label) }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                            if (problem) Text(if (item.stock > 0) "Only ${item.stock} available" else "Out of stock", color = Warning, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QuantityStepper(item.quantity, { q -> vm.setQty(item.variantId, q) }, max = item.stock.coerceAtLeast(item.quantity), enabled = !vm.busy)
                                Spacer(Modifier.weight(1f))
                                PriceTag(item.lineTotal, size = 20)
                            }
                        }
                    }
                    HorizontalDivider(color = Line)
                }
            }
        }
    }

    if (showCoupons) ModalBottomSheet(onDismissRequest = { showCoupons = false }, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Available coupons", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            if (vm.coupons.isEmpty()) Text("No coupons available right now", color = InkMuted)
            vm.coupons.forEach { cp ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Field).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cp.code, fontWeight = FontWeight.Bold, color = Brand)
                        Text((cp.description ?: (if (cp.discountType == "percent") "${cp.discountValue.toInt()}% off" else "${inr(cp.discountValue)} off")) +
                            if (cp.minOrderValue > 0) " · Min. order ${inr(cp.minOrderValue)}" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Apply", color = Brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { vm.applyCoupon(cp.code); showCoupons = false }.padding(6.dp))
                }
            }
        }
    }
}

/** Promo code field in the kit's style: icon, placeholder, chevron/apply on the right. */
@Composable
private fun CouponBox(vm: CartViewModel, onBrowse: () -> Unit) {
    var code by remember { mutableStateOf("") }
    if (vm.coupon != null) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PopularBg).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Discount, null, tint = Discount)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${vm.coupon} applied", fontWeight = FontWeight.SemiBold)
                Text("You save ${inr(vm.cart?.summary?.discount ?: 0.0)}", color = Discount, fontSize = 12.sp)
            }
            IconButton(onClick = vm::removeCoupon) { Icon(Icons.Filled.Close, "Remove coupon", tint = InkMuted) }
        }
    } else {
        OutlinedTextField(
            value = code, onValueChange = { code = it.uppercase().trim() }, singleLine = true,
            placeholder = { Text("Enter your promo code", color = InkHint, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Outlined.Discount, null, tint = InkHint) },
            trailingIcon = {
                if (code.isBlank()) IconButton(onClick = onBrowse) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "View coupons", tint = Ink) }
                else TextButton(onClick = { vm.applyCoupon(code) }, enabled = !vm.busy) { Text("Apply", color = Brand) }
            },
            isError = vm.couponError != null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Brand, unfocusedContainerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        )
        vm.couponError?.let { Text(it, color = Warning, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** Subtotal / discount / shipping, dashed divider, total — the kit's bill block. */
@Composable
fun BillLines(s: CartSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BillLine("Subtotal", inr(s.subtotal))
        if (s.mrpTotal > s.subtotal) BillLine("You save on MRP", "− ${inr(s.mrpTotal - s.subtotal)}", Discount)
        if (s.discount > 0) BillLine("Coupon ${s.couponCode ?: ""}", "− ${inr(s.discount)}", Discount)
        BillLine("Shipping", if (s.deliveryCharge > 0) inr(s.deliveryCharge) else "FREE", if (s.deliveryCharge > 0) null else Discount)
        DashedDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total amount", color = InkMuted, modifier = Modifier.weight(1f))
            PriceTag(s.total, size = 20)
        }
    }
}

/** Kept for the checkout screen: the bill block on a white card. */
@Composable
fun BillSummary(s: CartSummary) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Field).padding(16.dp)) { BillLines(s) }
}

@Composable
private fun BillLine(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    Row {
        Text(label, modifier = Modifier.weight(1f), color = InkMuted)
        Text(value, color = color ?: Ink, fontWeight = FontWeight.Medium)
    }
}
