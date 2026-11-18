package com.yourstore.app.ui.orders

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.BuildConfig
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.OrderDetail
import com.yourstore.app.data.model.OrderSummary
import com.yourstore.app.data.model.ReasonReq
import com.yourstore.app.ui.cart.variantText
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.launch

// ============================================================ My Order / History
class OrdersViewModel : ViewModel() {
    private val c = ShopApp.container
    var tab by mutableStateOf("current")
    val items = mutableStateListOf<OrderSummary>()
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    private var page = 0
    private var pages = 1

    fun reload(pull: Boolean = false) { page = 0; pages = 1; refreshing = pull; items.clear(); loadMore() }
    fun select(t: String) { if (t != tab) { tab = t; reload() } }

    fun loadMore() {
        if (loading || page >= pages) { refreshing = false; return }
        loading = true; error = null
        viewModelScope.launch {
            apiCall { c.api.orders(tab, page + 1) }.onSuccess { r -> page = r.meta.page; pages = r.meta.pages; items.addAll(r.data) }.onFailure { error = it.message }
            loading = false; refreshing = false
        }
    }

    fun received(id: Int) = viewModelScope.launch {
        apiCall { c.api.orderReceived(id) }.onSuccess { message = "Thanks! Your order is marked as received."; reload() }.onFailure { message = it.message }
    }

    fun reorder(id: Int, onDone: () -> Unit) = viewModelScope.launch {
        apiCall { c.api.reorder(id) }.onSuccess { c.refreshCartCount(); onDone() }.onFailure { message = it.message }
    }
}

@Composable
fun OrdersScreen(navigate: (String) -> Unit, snack: (String) -> Unit, vm: OrdersViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.reload() }
    LaunchedEffect(vm.message) { vm.message?.let { snack(it); vm.message = null } }
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }
    OrdersContent(
        tab = if (vm.tab == "current") 0 else 1, onTab = { vm.select(if (it == 0) "current" else "past") },
        items = vm.items, loading = vm.loading, refreshing = vm.refreshing, error = vm.error,
        onRefresh = { vm.reload(true) }, onRetry = { vm.reload() }, onShop = { navigate(Routes.HOME) }, onCart = { navigate(Routes.CART) },
        onDetail = { navigate(Routes.order(it.id)) }, onTrack = { navigate(Routes.track(it.id)) },
        onReceived = { vm.received(it.id) }, onBuyAgain = { o -> vm.reorder(o.id) { navigate(Routes.CART) } },
        listState = list,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersContent(
    tab: Int, onTab: (Int) -> Unit, items: List<OrderSummary>, loading: Boolean, refreshing: Boolean, error: String?,
    onRefresh: () -> Unit, onRetry: () -> Unit, onShop: () -> Unit, onCart: () -> Unit,
    onDetail: (OrderSummary) -> Unit, onTrack: (OrderSummary) -> Unit, onReceived: (OrderSummary) -> Unit, onBuyAgain: (OrderSummary) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        KTopBar("My Order") { IconBtn(Icons.Outlined.ShoppingBag, "Cart", onCart) }
        UnderlineTabs(listOf("My Order", "History"), tab, onTab, Modifier.padding(horizontal = PagePad))
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when {
                items.isEmpty() && loading -> LoadingBox()
                items.isEmpty() && error != null -> ErrorState(error, onRetry)
                items.isEmpty() -> EmptyState(Icons.Outlined.LocalShipping, if (tab == 0) "No active orders" else "No past orders",
                    "Orders you place will show up here", "Start Shopping", onShop)
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(PagePad), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { o -> OrderCard(o, onDetail, onTrack, onReceived, onBuyAgain) }
                    if (loading) item { LoadingBox(Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(
    o: OrderSummary, onDetail: (OrderSummary) -> Unit, onTrack: (OrderSummary) -> Unit,
    onReceived: (OrderSummary) -> Unit, onBuyAgain: (OrderSummary) -> Unit,
) {
    Column(Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
        .clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onDetail(o) }.padding(14.dp)) {
        Row {
            NetImage(o.image, Modifier.size(width = 84.dp, height = 90.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(o.firstItem.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    OrderStatusPill(o.status)
                }
                Spacer(Modifier.height(6.dp))
                Text(variantText(o.firstColor, o.firstVariant), fontSize = 12.sp, maxLines = 1)
                Text(
                    "Qty: ${o.firstQuantity.coerceAtLeast(1)}" + if (o.itemCount > 1) "  ·  +${o.itemCount - 1} more item${if (o.itemCount > 2) "s" else ""}" else "",
                    fontSize = 12.sp, color = InkHint, modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(6.dp))
                PriceTag(o.total, size = 17)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KOutlinedButton("Detail", { onDetail(o) }, Modifier.weight(1f), height = 40.dp, fontSize = 13)
            val active = o.status !in listOf("delivered", "cancelled", "returned")
            when {
                o.status in listOf("shipped", "out_for_delivery") -> KButton("Received Order", { onReceived(o) }, Modifier.weight(1f), height = 40.dp, fontSize = 13)
                active -> KButton("Tracking", { onTrack(o) }, Modifier.weight(1f), height = 40.dp, fontSize = 13)
                else -> KButton("Buy Again", { onBuyAgain(o) }, Modifier.weight(1f), height = 40.dp, fontSize = 13)
            }
        }
    }
}

// ============================================================ Order detail
class OrderDetailViewModel(private val id: Int) : ViewModel() {
    private val c = ShopApp.container
    var order by mutableStateOf<OrderDetail?>(null)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    init { load() }
    fun load() { viewModelScope.launch { apiCall { c.api.order(id) }.onSuccess { order = it }.onFailure { error = it.message } } }

    fun cancel(reason: String) {
        busy = true
        viewModelScope.launch {
            apiCall { c.api.cancelOrder(id, ReasonReq(reason)) }
                .onSuccess { message = "Order cancelled. Any payment will be refunded to the original method in 5–7 working days."; load() }
                .onFailure { message = it.message }
            busy = false
        }
    }

    fun received() {
        busy = true
        viewModelScope.launch {
            apiCall { c.api.orderReceived(id) }.onSuccess { message = "Thanks! Your order is marked as received."; load() }.onFailure { message = it.message }
            busy = false
        }
    }

    fun reorder(onDone: () -> Unit) {
        busy = true
        viewModelScope.launch {
            apiCall { c.api.reorder(id) }.onSuccess { c.refreshCartCount(); onDone() }.onFailure { message = it.message }
            busy = false
        }
    }
}

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
fun OrderDetailScreen(id: Int, navigate: (String) -> Unit, onBack: () -> Unit, snack: (String) -> Unit) {
    val vm: OrderDetailViewModel = viewModel(key = "order-$id") { OrderDetailViewModel(id) }
    val context = LocalContext.current
    var showCancel by remember { mutableStateOf(false) }
    LaunchedEffect(vm.message) { vm.message?.let { snack(it); vm.message = null } }
    val o = vm.order
    Column(Modifier.fillMaxSize().background(Color.White)) {
        KTopBar("Order Detail", onBack = onBack)
        when {
            o == null && vm.error != null -> ErrorState(vm.error!!, { vm.load() })
            o == null -> LoadingBox()
            else -> OrderDetailContent(
                o, vm.busy, onItem = { pid -> navigate(Routes.product(pid)) }, onTrack = { navigate(Routes.track(o.id)) },
                onInvoice = { downloadInvoice(context, o) }, onCancel = { showCancel = true }, onReceived = { vm.received() },
                onReorder = { vm.reorder { navigate(Routes.CART) } }, onHelp = { navigate(Routes.HELP) },
            )
        }
    }
    if (showCancel) CancelDialog({ showCancel = false }) { vm.cancel(it); showCancel = false }
}

@Composable
fun OrderDetailContent(
    o: OrderDetail, busy: Boolean, onItem: (Int) -> Unit, onTrack: () -> Unit, onInvoice: () -> Unit, onCancel: () -> Unit,
    onReceived: () -> Unit, onReorder: () -> Unit, onHelp: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePad)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Order #${o.orderNumber}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(formatDate(o.createdAt), fontSize = 12.sp, color = InkHint, modifier = Modifier.padding(top = 4.dp))
            }
            OrderStatusPill(o.status)
        }
        o.cancelReason?.takeIf { o.status == "cancelled" }?.let { Text("Reason: $it", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Products (${o.items.sumOf { it.quantity }})")
        o.items.forEach { it ->
            Row(Modifier.fillMaxWidth().clickable(enabled = it.productId != null) { it.productId?.let(onItem) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(it.imageUrl, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.productName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(variantText(it.variantColor, it.variantLabel), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("Qty: ${it.quantity}", fontSize = 12.sp, color = InkHint, modifier = Modifier.padding(top = 2.dp))
                }
                PriceTag(it.lineTotal, size = 16)
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle("Address")
        Spacer(Modifier.height(10.dp))
        Text(o.shippingAddress.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
        Text(o.shippingAddress.oneLine(), fontSize = 13.sp, color = InkMuted, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Phone: ${o.shippingAddress.phone}", fontSize = 13.sp, color = InkMuted)
        Spacer(Modifier.height(24.dp))
        SectionTitle("Payment")
        Spacer(Modifier.height(6.dp))
        BillLine("Subtotal", o.subtotal)
        if (o.discount > 0) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Discount${o.couponCode?.let { " ($it)" } ?: ""}", color = Discount, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("- ${inr(o.discount)}", color = Discount, fontSize = 14.sp)
        }
        BillLine("Shipping", o.deliveryCharge)
        DashedDivider(Modifier.padding(vertical = 8.dp))
        BillLine("Total amount", o.total, bold = true)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Payment status", fontSize = 13.sp, color = InkMuted, modifier = Modifier.weight(1f)); StatusChip(o.paymentStatus)
        }
        if (o.refundAmount > 0) Text("Refunded ${inr(o.refundAmount)} to the original payment method.", fontSize = 12.sp, color = Discount, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(28.dp))
        val active = o.status !in listOf("delivered", "cancelled", "returned", "pending_payment")
        if (active) KButton(if (o.status in listOf("shipped", "out_for_delivery")) "Received Order" else "Order Tracking",
            if (o.status in listOf("shipped", "out_for_delivery")) onReceived else onTrack, Modifier.fillMaxWidth(), loading = busy)
        if (o.status in listOf("shipped", "out_for_delivery")) { Spacer(Modifier.height(12.dp)); KOutlinedButton("Order Tracking", onTrack, Modifier.fillMaxWidth()) }
        if (o.invoiceNumber != null) { Spacer(Modifier.height(12.dp))
            KOutlinedButton("Download GST Invoice", onInvoice, Modifier.fillMaxWidth(), leading = { Icon(Icons.Outlined.Receipt, null, tint = Ink, modifier = Modifier.size(20.dp)) }) }
        if (o.status in listOf("delivered", "cancelled", "returned")) { Spacer(Modifier.height(12.dp)); KButton("Buy Again", onReorder, Modifier.fillMaxWidth(), loading = busy) }
        if (o.canCancel) { Spacer(Modifier.height(12.dp)); KOutlinedButton("Cancel Order", onCancel, Modifier.fillMaxWidth(), color = Warning) }
        Spacer(Modifier.height(16.dp))
        TextLink("Need help with this order?", onHelp, Modifier.align(Alignment.CenterHorizontally), fontSize = 13)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CancelDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val reasons = listOf("Ordered by mistake", "Found a better price", "Delivery is taking too long", "Want to change address or items", "Other")
    var reason by remember { mutableStateOf(reasons[0]) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(20.dp),
        title = { Text("Cancel this order?", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Why are you cancelling?", color = InkMuted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                reasons.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        KCheckbox(reason == r, { reason = r }, size = 18.dp); Spacer(Modifier.width(12.dp)); Text(r, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text("Cancel Order", color = Warning) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep Order", color = Ink) } },
    )
}

// ============================================================ Order tracking
private val TRACK_STEPS = listOf(
    "placed" to "Order Placed", "confirmed" to "Order Confirmed", "packed" to "Packed",
    "shipped" to "Package Sent", "out_for_delivery" to "Out for Delivery", "delivered" to "Delivered",
)

@Composable
fun OrderTrackScreen(id: Int, onBack: () -> Unit, snack: (String) -> Unit) {
    val vm: OrderDetailViewModel = viewModel(key = "order-$id") { OrderDetailViewModel(id) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) { vm.message?.let { snack(it); vm.message = null } }
    val o = vm.order
    when {
        o == null && vm.error != null -> Column(Modifier.fillMaxSize().background(Color.White)) { KTopBar("Order Track", onBack = onBack); ErrorState(vm.error!!, { vm.load() }) }
        o == null -> LoadingBox(Modifier.fillMaxSize().background(Color.White))
        else -> OrderTrackContent(o, vm.busy, onBack, onDone = { vm.received() },
            onCall = { o.courierPhone?.let { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) } },
            onOpenTracking = { o.trackingUrl?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } } })
    }
}

@Composable
fun OrderTrackContent(o: OrderDetail, busy: Boolean, onBack: () -> Unit, onDone: () -> Unit, onCall: () -> Unit, onOpenTracking: () -> Unit) {
    val reached = o.timeline.associateBy { it.status }
    val currentIndex = TRACK_STEPS.indexOfLast { (k, _) -> reached.containsKey(k) }
        .coerceAtLeast(TRACK_STEPS.indexOfFirst { it.first == o.status })
    Box(Modifier.fillMaxSize().background(Color(0xFFEFF4F3))) {
        TrackMap(Modifier.fillMaxWidth().fillMaxHeight(0.5f))
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", Modifier.size(16.dp).offset(x = 3.dp), tint = Ink)
            }
            Text("Order Track", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(40.dp))
        }
        SheetPanel(Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.62f)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Courier
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(null, o.courierName ?: "Courier", 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.courierName ?: "Courier not assigned yet", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text(if (o.courierName != null) "Courier" else "We'll share courier details once shipped", fontSize = 12.sp, color = InkHint, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (o.courierPhone != null) Box(Modifier.size(44.dp).clip(CircleShape).background(BrandSoft).clickable(onClick = onCall), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Call, "Call courier", tint = Brand, modifier = Modifier.size(22.dp))
                    }
                }
                if (o.trackingUrl != null) TextLink("Open courier tracking page", onOpenTracking, Modifier.padding(top = 10.dp), fontSize = 13)
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(20.dp))
                Text("Order #${o.orderNumber}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(16.dp))
                if (o.status == "cancelled") Text("This order was cancelled.", color = Warning, fontSize = 14.sp)
                else TRACK_STEPS.forEachIndexed { i, (key, label) ->
                    TrackStep(label, reached[key]?.let { formatDate(it.createdAt) } ?: if (i == currentIndex + 1) "Next step" else "", done = i <= currentIndex, last = i == TRACK_STEPS.lastIndex)
                }
            }
            if (o.status in listOf("shipped", "out_for_delivery")) {
                Spacer(Modifier.height(12.dp))
                KButton("Mark as Done", onDone, Modifier.fillMaxWidth(), loading = busy)
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun TrackStep(label: String, time: String, done: Boolean, last: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (done) Brand else Color.White).border(1.5.dp, if (done) Brand else Line, CircleShape),
                contentAlignment = Alignment.Center) { if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            if (!last) Canvas(Modifier.width(2.dp).weight(1f).padding(vertical = 2.dp)) {
                drawLine(if (done) Brand else Line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 4f,
                    pathEffect = if (done) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)), cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.padding(bottom = if (last) 0.dp else 22.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (done) Ink else InkHint)
            if (time.isNotBlank()) Text(time, fontSize = 12.sp, color = InkHint, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Map-like background with a dashed purple route between store and home. */
@Composable
private fun TrackMap(modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Color(0xFFEFF4F3))
            for (i in 0..6) drawLine(Color.White, Offset(0f, h * i / 6f + 20f), Offset(w, h * i / 6f - 40f), strokeWidth = 14f)
            for (i in 0..4) drawLine(Color.White, Offset(w * i / 4f, 0f), Offset(w * i / 4f + 60f, h), strokeWidth = 10f)
            drawCircle(Color(0xFF9ED9D2), radius = w * 0.18f, center = Offset(w * 0.85f, h * 0.2f))
            drawLine(Brand, Offset(w * 0.25f, h * 0.75f), Offset(w * 0.5f, h * 0.45f), strokeWidth = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
            drawLine(Brand, Offset(w * 0.5f, h * 0.45f), Offset(w * 0.72f, h * 0.55f), strokeWidth = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.offset(x = maxWidth * 0.25f - 18.dp, y = maxHeight * 0.75f - 18.dp).size(36.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LocalShipping, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Icon(Icons.Filled.LocationOn, null, tint = Warning, modifier = Modifier.offset(x = maxWidth * 0.72f - 18.dp, y = maxHeight * 0.55f - 34.dp).size(36.dp))
        }
    }
}
