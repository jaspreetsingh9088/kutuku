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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import com.yourstore.app.ui.theme.Discount
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
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(topBar = { AppTopBar("My orders") }) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = if (vm.tab == "current") 0 else 1) {
                Tab(vm.tab == "current", { vm.select("current") }, text = { Text("Active") })
                Tab(vm.tab == "past", { vm.select("past") }, text = { Text("Past orders") })
            }
            PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.reload(true) }, modifier = Modifier.fillMaxSize()) {
                when {
                    vm.items.isEmpty() && vm.loading -> LoadingBox()
                    vm.items.isEmpty() && vm.error != null -> ErrorState(vm.error!!, { vm.reload() })
                    vm.items.isEmpty() -> EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, if (vm.tab == "current") "No active orders" else "No past orders", "Orders you place will show up here", "Start shopping") { navigate(Routes.HOME) }
                    else -> LazyColumn(state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(vm.items, key = { it.id }) { o ->
                            Card(onClick = { navigate(Routes.order(o.id)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    NetImage(o.image, Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(o.firstItem.orEmpty() + if (o.itemCount > 1) " +${o.itemCount - 1} more" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                        Text("#${o.orderNumber} · ${formatDate(o.createdAt, false)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) { StatusChip(o.status); Spacer(Modifier.weight(1f)); Text(inr(o.total), fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                        if (vm.loading) item { LoadingBox(Modifier.fillMaxWidth().padding(16.dp)) }
                    }
                }
            }
        }
    }
}

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

    Scaffold(topBar = { AppTopBar(o?.let { "Order #${it.orderNumber}" } ?: "Order", onBack) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        when {
            vm.error != null && o == null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad))
            o == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Placed on ${formatDate(o.createdAt)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            StatusChip(o.status)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (o.status in STEPS) Tracker(o) else {
                            Text(if (o.status == "cancelled") "This order was cancelled" else prettyStatus(o.status), fontWeight = FontWeight.SemiBold)
                            o.cancelReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (o.refundAmount > 0) Text("Refunded ${inr(o.refundAmount)}", color = Discount, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Items", style = MaterialTheme.typography.titleMedium)
                        o.items.forEach { it ->
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                NetImage(it.imageUrl, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(it.productName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${it.variantLabel} · Qty ${it.quantity} × ${inr(it.unitPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(inr(it.lineTotal), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Payment summary", style = MaterialTheme.typography.titleMedium)
                        Row { Text("Item total", Modifier.weight(1f)); Text(inr(o.subtotal)) }
                        if (o.discount > 0) Row { Text("Coupon ${o.couponCode.orEmpty()}", Modifier.weight(1f)); Text("− ${inr(o.discount)}", color = Discount) }
                        Row { Text("Delivery", Modifier.weight(1f)); Text(if (o.deliveryCharge > 0) inr(o.deliveryCharge) else "FREE") }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row { Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(inr(o.total), fontWeight = FontWeight.Bold) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Payment", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); StatusChip(o.paymentStatus) }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Delivery address", style = MaterialTheme.typography.titleMedium)
                        Text(o.shippingAddress.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        Text(o.shippingAddress.oneLine())
                        Text("Phone: ${o.shippingAddress.phone}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (o.paymentStatus in listOf("paid", "refunded", "partially_refunded")) {
                    OutlinedButton(onClick = { downloadInvoice(context, o) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Receipt, null); Spacer(Modifier.width(8.dp)); Text("Download GST invoice (PDF)")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (o.canCancel) OutlinedButton(onClick = { showCancel = true }, enabled = !vm.busy, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancel order") }
                    Button(onClick = { vm.reorder { navigate(Routes.CART) } }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Reorder") }
                }
            }
        }
    }

    if (showCancel) {
        var reason by remember { mutableStateOf("Changed my mind") }
        val reasons = listOf("Changed my mind", "Ordered by mistake", "Found a better price", "Delivery is taking too long", "Other")
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("Cancel this order?") },
            text = {
                Column {
                    Text("Why are you cancelling?", style = MaterialTheme.typography.bodyMedium)
                    reasons.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(reason == r, { reason = r }); Text(r) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.cancel(reason); showCancel = false }) { Text("Cancel order", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("Keep order") } },
        )
    }
}

@Composable
private fun Tracker(o: OrderDetail) {
    val current = STEPS.indexOf(o.status)
    val times = o.timeline.associate { it.status to it.createdAt }
    Column {
        STEPS.forEachIndexed { i, step ->
            val done = i <= current
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                    if (i < STEPS.lastIndex) Box(Modifier.width(2.dp).height(28.dp).background(if (i < current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.offset(y = (-3).dp)) {
                    Text(prettyStatus(step), fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                        color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    times[step]?.let { Text(formatDate(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

// ---------------- Success ----------------
@Composable
fun OrderSuccessScreen(id: Int, onViewOrder: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.CheckCircle, null, tint = Discount, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Order placed!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Thank you for shopping with us. We'll notify you when your order is on its way.", textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(28.dp))
        Button(onClick = onViewOrder, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("View order") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Continue shopping") }
    }
}
