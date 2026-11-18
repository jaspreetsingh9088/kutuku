package com.yourstore.app.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Discount
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.*
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.launch

class CartViewModel : ViewModel() {
    private val c = ShopApp.container
    var cart by mutableStateOf<Cart?>(null)
    var error by mutableStateOf<String?>(null)
    var coupon by mutableStateOf<String?>(null)
    var couponInput by mutableStateOf("")
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
        c.setCartCount(r.summary.cartCount.takeIf { it > 0 } ?: r.items.sumOf { it.quantity })
        if (r.summary.couponError != null && coupon != null) { couponError = r.summary.couponError; coupon = null }
    }

    private fun mutate(block: suspend () -> Cart) {
        busy = true
        viewModelScope.launch {
            apiCall { block() }.onSuccess { set(it); if (coupon != null) apiCall { c.api.cart(coupon) }.onSuccess { set(it) } }.onFailure { message = it.message }
            busy = false
        }
    }

    fun setQty(variantId: Int, q: Int) = mutate { c.api.setQty(variantId, QtyReq(q)) }
    fun remove(variantId: Int) = mutate { c.api.removeFromCart(variantId) }
    fun select(variantId: Int, selected: Boolean) = mutate { c.api.selectItem(variantId, SelectReq(selected)) }
    fun selectAll(selected: Boolean) = mutate { c.api.selectAll(SelectReq(selected)) }

    fun applyCoupon(code: String = couponInput) {
        if (code.isBlank()) return
        busy = true; couponError = null
        viewModelScope.launch {
            apiCall { c.api.applyCoupon(CouponReq(code.trim().uppercase())) }
                .onSuccess { r -> if (r.summary.couponError != null) couponError = r.summary.couponError else { coupon = r.summary.couponCode; couponInput = coupon.orEmpty(); set(r) } }
                .onFailure { couponError = it.message }
            busy = false
        }
    }

    fun removeCoupon() { coupon = null; couponInput = ""; couponError = null; load() }
}

@Composable
fun CartScreen(navigate: (String) -> Unit, onBack: () -> Unit, snack: (String) -> Unit, vm: CartViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) { vm.message?.let { snack(it); vm.message = null } }
    CartContent(
        cart = vm.cart, error = vm.error, busy = vm.busy,
        couponInput = vm.couponInput, onCouponInput = { vm.couponInput = it; vm.couponError = null }, appliedCoupon = vm.coupon,
        couponError = vm.couponError, coupons = vm.coupons, onApplyCoupon = { vm.applyCoupon(it) }, onRemoveCoupon = { vm.removeCoupon() },
        onQty = { id, q -> vm.setQty(id, q) }, onRemove = { vm.remove(it) }, onSelect = { id, s -> vm.select(id, s) }, onSelectAll = { vm.selectAll(it) },
        onRetry = { vm.load() }, onBack = onBack, onShop = { navigate(Routes.HOME) },
        onItem = { navigate(Routes.product(it)) },
        onCheckout = { navigate(Routes.checkout(vm.coupon)) },
    )
}

@Composable
fun CartContent(
    cart: Cart?, error: String?, busy: Boolean,
    couponInput: String, onCouponInput: (String) -> Unit, appliedCoupon: String?, couponError: String?, coupons: List<Coupon>,
    onApplyCoupon: (String) -> Unit, onRemoveCoupon: () -> Unit,
    onQty: (Int, Int) -> Unit, onRemove: (Int) -> Unit, onSelect: (Int, Boolean) -> Unit, onSelectAll: (Boolean) -> Unit,
    onRetry: () -> Unit, onBack: () -> Unit, onShop: () -> Unit, onItem: (Int) -> Unit, onCheckout: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            KTopBar("My Cart", onBack = onBack) { IconBtn(Icons.Outlined.ShoppingBag, "Shop", onShop) }
            when {
                cart == null && error != null -> ErrorState(error, onRetry)
                cart == null -> LoadingBox()
                cart.items.isEmpty() -> EmptyState(Icons.Outlined.ShoppingBag, "Your cart is empty", "Add something you love and it will show up here.", "Start Shopping", onShop)
                else -> {
                    val allSelected = cart.items.all { it.selected }
                    Row(Modifier.fillMaxWidth().padding(horizontal = PagePad, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        KCheckbox(allSelected, onSelectAll)
                        Spacer(Modifier.width(12.dp))
                        Text("Select all (${cart.items.size})", fontSize = 13.sp, color = InkMuted, modifier = Modifier.weight(1f))
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = PagePad, end = PagePad, bottom = 380.dp)) {
                        items(cart.items, key = { it.variantId }) { item ->
                            val problem = cart.problems.firstOrNull { it.variantId == item.variantId }?.message
                            CartRow(item, problem, busy, onQty, onRemove, onSelect, onItem)
                            HorizontalDivider(color = Line)
                        }
                    }
                }
            }
        }
        if (cart != null && cart.items.isNotEmpty()) {
            SheetPanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                PromoField(couponInput, onCouponInput, appliedCoupon, couponError, coupons, onApplyCoupon, onRemoveCoupon)
                Spacer(Modifier.height(20.dp))
                CartSummaryLines(cart.summary)
                Spacer(Modifier.height(20.dp))
                KButton(if (cart.summary.itemCount == 0) "Select items to checkout" else "Checkout", onCheckout, Modifier.fillMaxWidth(),
                    enabled = cart.summary.itemCount > 0 && cart.problems.none { p -> cart.items.any { it.variantId == p.variantId && it.selected } })
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun CartSummaryLines(s: CartSummary) {
    BillLine("Subtotal", s.subtotal)
    if (s.discount > 0) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Discount${s.couponCode?.let { " ($it)" } ?: ""}", color = Discount, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("- ${inr(s.discount)}", color = Discount, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
    BillLine("Shipping", s.deliveryCharge)
    Spacer(Modifier.height(10.dp))
    DashedDivider()
    Spacer(Modifier.height(10.dp))
    BillLine("Total amount", s.total, bold = true)
}

@Composable
private fun CartRow(
    item: CartItem, problem: String?, busy: Boolean,
    onQty: (Int, Int) -> Unit, onRemove: (Int) -> Unit, onSelect: (Int, Boolean) -> Unit, onItem: (Int) -> Unit,
) {
    Column(Modifier.padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KCheckbox(item.selected, { onSelect(item.variantId, it) })
            Spacer(Modifier.width(16.dp))
            NetImage(item.image, Modifier.size(76.dp, 90.dp).clip(RoundedCornerShape(8.dp)).clickable { onItem(item.productId) })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.DeleteOutline, "Remove", Modifier.padding(start = 4.dp).size(18.dp).clip(CircleShape).clickable { onRemove(item.variantId) }, tint = InkHint)
                }
                Spacer(Modifier.height(4.dp))
                Text(variantText(item.color, item.label), fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { KStepper(item.quantity, { onQty(item.variantId, it) }, max = item.stock.coerceIn(1, 50), enabled = !busy) }
                    PriceTag(item.price * item.quantity, size = 22)
                }
            }
        }
        if (problem != null) Text(problem, color = Warning, fontSize = 12.sp, modifier = Modifier.padding(start = 36.dp, top = 8.dp))
    }
}

/** "Color: Brown" with the label in grey and the value in ink; falls back to the size label. */
fun variantText(color: String?, label: String?) = buildAnnotatedString {
    val (k, v) = when {
        !color.isNullOrBlank() -> "Color: " to color + (label?.takeIf { it.isNotBlank() && it != "Default" && it != color }?.let { " · $it" } ?: "")
        else -> "Variant: " to (label ?: "")
    }
    withStyle(SpanStyle(color = InkHint)) { append(k) }
    withStyle(SpanStyle(color = Ink2)) { append(v) }
}

@Composable
private fun PromoField(
    value: String, onValue: (String) -> Unit, applied: String?, error: String?, coupons: List<Coupon>,
    onApply: (String) -> Unit, onRemove: () -> Unit,
) {
    var showList by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(Field)
            .border(1.dp, if (error != null) Warning else Line, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Discount, "Offers", Modifier.size(22.dp).clickable { showList = !showList }, tint = if (applied != null) Brand else InkHint)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Enter your promo code", fontSize = 13.sp, color = InkHint)
            BasicTextField(value, { onValue(it.uppercase()) }, singleLine = true, readOnly = applied != null,
                textStyle = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium), cursorBrush = SolidColor(Brand),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onApply(value) }), modifier = Modifier.fillMaxWidth())
        }
        if (applied != null) Icon(Icons.Filled.Close, "Remove coupon", Modifier.size(20.dp).clickable(onClick = onRemove), tint = InkMuted)
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Apply", Modifier.size(24.dp).clickable { if (value.isBlank()) showList = !showList else onApply(value) }, tint = Ink)
    }
    if (error != null) Text(error, color = Warning, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
    if (showList && applied == null && coupons.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        coupons.take(3).forEach { cp ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showList = false; onValue(cp.code); onApply(cp.code) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(cp.code, color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.border(1.dp, BrandLight, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                Spacer(Modifier.width(10.dp))
                Text(cp.description ?: "", fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
