package com.yourstore.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.yourstore.app.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.AddCartReq
import com.yourstore.app.data.model.Product
import com.yourstore.app.data.model.Variant
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import kotlinx.coroutines.launch

class ProductDetailViewModel(private val id: Int) : ViewModel() {
    private val c = ShopApp.container
    var product by mutableStateOf<Product?>(null)
    var error by mutableStateOf<String?>(null)
    var selected by mutableStateOf<Variant?>(null)
    var qty by mutableIntStateOf(1)
    var adding by mutableStateOf(false)

    init { load() }

    fun load() {
        error = null
        viewModelScope.launch {
            apiCall { c.api.product(id) }.onSuccess { p ->
                product = p
                selected = p.variants.firstOrNull { it.inStock } ?: p.variants.firstOrNull()
            }.onFailure { error = it.message }
        }
    }

    fun select(v: Variant) { selected = v; qty = 1 }

    fun addToCart(onResult: (String, Boolean) -> Unit) {
        val v = selected ?: return
        adding = true
        viewModelScope.launch {
            apiCall { c.api.addToCart(AddCartReq(v.id, qty)) }
                .onSuccess { c.setCartCount(it.summary.itemCount); onResult("Added to cart", true) }
                .onFailure { onResult(it.message ?: "Could not add to cart", false) }
            adding = false
        }
    }

    fun toggleWishlist() {
        val p = product ?: return
        viewModelScope.launch {
            apiCall { if (p.wishlisted) c.api.removeWishlist(p.id) else c.api.addWishlist(p.id) }.onSuccess { product = p.copy(wishlisted = it.wishlisted) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(id: Int, navigate: (String) -> Unit, onBack: () -> Unit) {
    val vm: ProductDetailViewModel = viewModel(key = "product-$id") { ProductDetailViewModel(id) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cartCount by ShopApp.container.cartCount.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val p = vm.product

    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            val v = vm.selected
            if (p != null && v != null) Surface(color = Color.White, shadowElevation = 12.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Price", style = MaterialTheme.typography.bodySmall)
                        Text(inr(v.price * vm.qty), color = Brand, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    PillButton(
                        text = if (v.inStock) "Add to Cart" else "Out of stock",
                        icon = Icons.Outlined.ShoppingBag,
                        enabled = v.inStock, loading = vm.adding,
                        onClick = {
                            vm.addToCart { msg, ok ->
                                scope.launch {
                                    val r = snack.showSnackbar(msg, actionLabel = if (ok) "View cart" else null, duration = SnackbarDuration.Short)
                                    if (r == SnackbarResult.ActionPerformed) navigate(Routes.CART)
                                }
                            }
                        },
                        modifier = Modifier.widthIn(min = 180.dp),
                    )
                }
            }
        },
    ) { pad ->
        when {
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            p == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Box(Modifier.padding(bottom = pad.calculateBottomPadding()).fillMaxSize()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Full-bleed image area
                    val imgs = p.images.ifEmpty { listOf(com.yourstore.app.data.model.ProductImage(url = "")) }
                    val pager = rememberPagerState { imgs.size }
                    Box(Modifier.fillMaxWidth().height(400.dp).background(Tile)) {
                        HorizontalPager(pager, Modifier.fillMaxSize()) { i -> NetImage(imgs[i].url.ifBlank { null }, Modifier.fillMaxSize()) }
                        if (imgs.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)) {
                            repeat(imgs.size) { i -> Box(Modifier.padding(3.dp).size(if (pager.currentPage == i) 16.dp else 6.dp, 6.dp).clip(CircleShape).background(if (pager.currentPage == i) Brand else Color.White)) }
                        }
                    }
                    // White sheet overlapping the image
                    Column(
                        Modifier.offset(y = (-28).dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(Color.White).padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleLarge)
                                p.categoryName?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                            }
                            vm.selected?.let { v ->
                                if (v.inStock) Column(horizontalAlignment = Alignment.End) {
                                    QuantityStepper(vm.qty, { vm.qty = it.coerceIn(1, v.stock) }, max = minOf(v.stock, 50))
                                    Text(if (v.stock <= 5) "Only ${v.stock} left" else "Available in stock",
                                        fontSize = 11.sp, color = if (v.stock <= 5) Warning else Ink, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                        vm.selected?.let { v ->
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (v.mrp > v.price) {
                                    Text(inr(v.mrp), textDecoration = TextDecoration.LineThrough, color = InkHint, fontSize = 13.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${v.discountPercent}% off", color = Discount, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Inclusive of all taxes", style = MaterialTheme.typography.bodySmall)
                            }
                            if (!v.inStock) Text("Out of stock", color = Warning, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        }

                        if (p.variants.size > 1) {
                            Text("Choose size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                p.variants.forEach { v ->
                                    PillChip("${v.label} · ${inr(v.price)}", vm.selected?.id == v.id, { vm.select(v) }, enabled = v.inStock)
                                }
                            }
                        }

                        if (!p.description.isNullOrBlank()) {
                            Text("Description", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
                            Text(p.description, color = InkMuted, maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                            if (p.description.length > 160) Text(if (expanded) "Read Less" else "Read More", color = Brand, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded })
                        }
                        val specs = p.specifications.orEmpty().filter { it.key.isNotBlank() }
                        if (specs.isNotEmpty()) {
                            Text("Specifications", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
                            specs.forEach { s ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                    Text(s.key, color = InkMuted, modifier = Modifier.weight(0.4f))
                                    Text(s.value, modifier = Modifier.weight(0.6f))
                                }
                                HorizontalDivider(color = Line)
                            }
                        }
                        val related = p.related.orEmpty()
                        if (related.isNotEmpty()) {
                            Text("You may also like", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 26.dp, bottom = 12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(related, key = { it.id }) { r -> ProductCard(r, onClick = { navigate(Routes.product(r.id)) }, modifier = Modifier.width(150.dp)) }
                            }
                        }
                    }
                }
                // Floating top bar over the image: back · "Detail Product" · bag
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", Modifier.size(20.dp), tint = Ink) }
                    Text("Detail Product", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::toggleWishlist) {
                        Icon(if (p.wishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = if (p.wishlisted) Warning else Ink)
                    }
                    CircleIconButton(Icons.Outlined.ShoppingBag, "Cart", { navigate(Routes.CART) }, badge = cartCount)
                }
            }
        }
    }
}
