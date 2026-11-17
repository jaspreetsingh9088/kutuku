package com.yourstore.app.ui.orders

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.yourstore.app.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.BuildConfig
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.OrderDetail
import com.yourstore.app.data.model.OrderSummary
import com.yourstore.app.data.model.ReasonReq
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import kotlinx.coroutines.launch

// ---------------- Orders list ----------------
class OrdersViewModel : ViewModel() {
    private val c = ShopApp.container
    var tab by mutableStateOf("current")
    val items = mutableStateListOf<OrderSummary>()
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var page = 0
    private var pages = 1

    fun reload(pull: Boolean = false) { page = 0; pages = 1; refreshing = pull; items.clear(); loadMore() }
    fun select(t: String) { if (t != tab) { tab = t; reload() } }

    fun loadMore() {
        if (loading || page >= pages) { refreshing = false; return }
        loading = true; error = null
        viewModelScope.launch {
            apiCall { c.api.orders(tab, page + 1) }.onSuccess { r ->
                page = r.meta.page; pages = r.meta.pages; items.addAll(r.data)
            }.onFailure { error = it.message }
            loading = false; refreshing = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(navigate: (String) -> Unit, vm: OrdersViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.reload() }
    val list = rememberLazyListState()
    val cartCount by ShopApp.container.cartCount.collectAsState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(containerColor = Color.White, topBar = {
        AppTopBar("My Order") { CircleIconButton(Icons.Outlined.ShoppingBag, "Cart", { navigate(Routes.CART) }, badge = cartCount) }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            UnderlineTabs(listOf("My Order", "History"), if (vm.tab == "current") 0 else 1, { vm.select(if (it == 0) "current" else "past") })
            PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.reload(true) }, modifier = Modifier.fillMaxSize()) {
                when {
                    vm.items.isEmpty() && vm.loading -> LoadingBox()
                    vm.items.isEmpty() && vm.error != null -> ErrorState(vm.error!!, { vm.reload() })
                    vm.items.isEmpty() -> EmptyState(Icons.Outlined.LocalShipping, if (vm.tab == "current") "No active orders" else "No past orders",
                        "Orders you place will show up here", "Start Shopping") { navigate(Routes.HOME) }
                    else -> LazyColumn(state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(vm.items, key = { it.id }) { o -> OrderCard(o, navigate) }
                        if (vm.loading) item { LoadingBox(Modifier.fillMaxWidth().padding(16.dp)) }
                    }
                }
            }
        }
    }
}

/** Kit order card: image, title, details, status pill, price, then Detail / Tracking buttons. */
@Composable
private fun OrderCard(o: OrderSummary, navigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row {
            NetImage(o.image, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(o.firstItem.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedStatus(o.status)
                }
                Text(buildAnnotatedLabel("Order: ", "#${o.orderNumber}"), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(buildAnnotatedLabel("Items: ", "${o.itemCount}"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    PriceTag(o.total, size = 17)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Detail", { navigate(Routes.order(o.id)) }, outlined = true, color = Ink, modifier = Modifier.weight(1f).height(44.dp))
            PillButton("Tracking", { navigate(Routes.order(o.id)) }, modifier = Modifier.weight(1f).height(44.dp))
        }
    }
}

@Composable
private fun buildAnnotatedLabel(label: String, value: String) = androidx.compose.ui.text.buildAnnotatedString {
    withStyle(androidx.compose.ui.text.SpanStyle(color = InkMuted)) { append(label) }
    withStyle(androidx.compose.ui.text.SpanStyle(color = Ink)) { append(value) }
}

/** Outlined status pill, e.g. "On Progress" in teal as in the kit. */
@Composable
private fun OutlinedStatus(status: String) {
    val (label, color) = when (status) {
        "delivered" -> "Delivered" to Discount
        "cancelled" -> "Cancelled" to Warning
        "returned" -> "Returned" to InkMuted
        "shipped", "out_for_delivery" -> prettyStatus(status) to Brand
        else -> "On Progress" to Teal
    }
    Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

private val Teal = Color(0xFF14A3B8)

// ---------------- Order detail ----------------
class OrderDetailViewModel(private val id: Int) : ViewModel() {
    private val c = ShopApp.container
    var order by mutableStateOf<OrderDetail?>(null)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    init { load() }
    fun load() { viewModelScope.launch { apiCall { c.api.order(id) }.onSuccess { order = it; error = null }.onFailure { error = it.message } } }

    fun cancel(reason: String) {
        busy = true
        viewModelScope.launch {
            apiCall { c.api.cancelOrder(id, ReasonReq(reason)) }
                .onSuccess { message = "Order cancelled. Any payment will be refunded to the original method in 5–7 working days."; load() }
                .onFailure { message = it.message }
            busy = false
        }
    }

    fun reorder(onDone: () -> Unit) {
        busy = true
        viewModelScope.launch {
            apiCall { c.api.reorder(id) }.onSuccess {
                c.refreshCartCount()
                if ((it.added ?: 0) > 0) onDone() else message = "These items are currently unavailable"
            }.onFailure { message = it.message }
            busy = false
        }
    }
}

private val STEPS = listOf("placed", "confirmed", "packed", "shipped", "out_for_delivery", "delivered")

private fun downloadInvoice(context: Context, order: OrderDetail) {
    val token = ShopApp.container.currentToken() ?: return
    val req = DownloadManager.Request(Uri.parse("${BuildConfig.API_BASE_URL}api/v1/orders/${order.id}/invoice"))
        .addRequestHeader("Authorization", "Bearer $token")
        .setTitle("Invoice ${order.invoiceNumber ?: order.orderNumber}")
        .setMimeType("application/pdf")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "invoice-${order.orderNumber}.pdf")
    context.getSystemService(DownloadManager::class.java).enqueue(req)
    Toast.makeText(context, "Downloading invoice…", Toast.LENGTH_SHORT).show()
}

@Composable
fun OrderDetailScreen(id: Int, navigate: (String) -> Unit, onBack: () -> Unit) {
    val vm: OrderDetailViewModel = viewModel(key = "order-$id") { OrderDetailViewModel(id) }
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    var showCancel by remember { mutableStateOf(false) }
    val o = vm.order

    Scaffold(containerColor = Color.White, topBar = { AppTopBar("Order Tracking", onBack) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        when {
            vm.error != null && o == null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad))
            o == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Order header card (like the courier card in the kit)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(BrandSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Inventory2, null, tint = Brand)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("#${o.orderNumber}", style = MaterialTheme.typography.titleSmall)
                        Text(formatDate(o.createdAt), style = MaterialTheme.typography.bodySmall)
                    }
                    StatusChip(o.status)
                }

                Text("Progress of your Order", style = MaterialTheme.typography.titleMedium)
                if (o.status in STEPS) Tracker(o) else Column {
                    Text(if (o.status == "cancelled") "This order was cancelled" else prettyStatus(o.status), fontWeight = FontWeight.SemiBold)
                    o.cancelReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (o.refundAmount > 0) Text("Refunded ${inr(o.refundAmount)}", color = Discount, fontSize = 13.sp)
                }

                Text("Products (${o.items.size})", style = MaterialTheme.typography.titleMedium)
                o.items.forEach { it ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NetImage(it.imageUrl, Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.productName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${it.variantLabel} · Qty ${it.quantity}", style = MaterialTheme.typography.bodySmall)
                        }
                        PriceTag(it.lineTotal, size = 16)
                    }
                }

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Field).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row { Text("Subtotal", Modifier.weight(1f), color = InkMuted); Text(inr(o.subtotal)) }
                    if (o.discount > 0) Row { Text("Coupon ${o.couponCode.orEmpty()}", Modifier.weight(1f), color = InkMuted); Text("− ${inr(o.discount)}", color = Discount) }
                    Row { Text("Shipping", Modifier.weight(1f), color = InkMuted); Text(if (o.deliveryCharge > 0) inr(o.deliveryCharge) else "FREE") }
                    DashedDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Total amount", Modifier.weight(1f), color = InkMuted); PriceTag(o.total, size = 19) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Payment", Modifier.weight(1f), color = InkMuted); StatusChip(o.paymentStatus) }
                }

                Column {
                    Text("Delivery address", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Tile), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.LocationOn, null, tint = Ink) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(o.shippingAddress.name, style = MaterialTheme.typography.titleSmall)
                            Text(o.shippingAddress.oneLine(), style = MaterialTheme.typography.bodySmall)
                            Text("Phone: ${o.shippingAddress.phone}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (o.paymentStatus in listOf("paid", "refunded", "partially_refunded")) {
                    PillButton("Download GST Invoice", { downloadInvoice(context, o) }, outlined = true, icon = Icons.Outlined.Receipt, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    if (o.canCancel) PillButton("Cancel Order", { showCancel = true }, outlined = true, color = Warning, enabled = !vm.busy, modifier = Modifier.weight(1f))
                    PillButton("Reorder", { vm.reorder { navigate(Routes.CART) } }, enabled = !vm.busy, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showCancel) {
        var reason by remember { mutableStateOf("Changed my mind") }
        val reasons = listOf("Changed my mind", "Ordered by mistake", "Found a better price", "Delivery is taking too long", "Other")
        AlertDialog(
            onDismissRequest = { showCancel = false }, containerColor = Color.White,
            title = { Text("Cancel this order?") },
            text = {
                Column {
                    Text("Why are you cancelling?", color = InkMuted)
                    reasons.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(reason == r, { reason = r }, colors = RadioButtonDefaults.colors(selectedColor = Brand)); Text(r)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.cancel(reason); showCancel = false }) { Text("Cancel order", color = Warning) } },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("Keep order", color = Ink) } },
        )
    }
}

@Composable
private fun Tracker(o: OrderDetail) {
    val current = STEPS.indexOf(o.status)
    val times = o.timeline.associate { it.status to it.createdAt }
    val icons = listOf(Icons.Outlined.ReceiptLong, Icons.Outlined.TaskAlt, Icons.Outlined.Inventory2, Icons.Outlined.LocalShipping, Icons.Outlined.DeliveryDining, Icons.Outlined.Home)
    Column {
        STEPS.forEachIndexed { i, step ->
            val done = i <= current
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(if (done) Brand else Tile), contentAlignment = Alignment.Center) {
                        Icon(icons[i], null, tint = if (done) Color.White else InkHint, modifier = Modifier.size(18.dp))
                    }
                    if (i < STEPS.lastIndex) Box(Modifier.width(2.dp).height(22.dp).background(if (i < current) Brand else Line))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(top = 2.dp)) {
                    Text(prettyStatus(step), style = MaterialTheme.typography.titleSmall, color = if (done) Ink else InkHint)
                    Text(times[step]?.let { formatDate(it) } ?: if (done) "" else "Pending", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------- Success ----------------
@Composable
fun OrderSuccessScreen(id: Int, onViewOrder: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(190.dp).clip(CircleShape).background(BrandLight), contentAlignment = Alignment.Center) {
            Box(Modifier.size(110.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Order Successfully", style = MaterialTheme.typography.headlineSmall)
        Text("Your order will be packed by the store and we'll notify you at every step until it reaches your door.",
            textAlign = TextAlign.Center, color = InkMuted, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(36.dp))
        PillButton("Order Tracking", onViewOrder, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onContinue, modifier = Modifier.padding(top = 8.dp)) { Text("Continue Shopping", color = Brand) }
    }
}
