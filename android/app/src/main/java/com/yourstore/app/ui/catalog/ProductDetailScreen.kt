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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.yourstore.app.ui.theme.Warning
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
    val p = vm.product

    Scaffold(
        topBar = {
            AppTopBar(p?.name ?: "", onBack) {
                if (p != null) IconButton(onClick = vm::toggleWishlist) {
                    Icon(if (p.wishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Wishlist", tint = if (p.wishlisted) Color(0xFFE11D48) else LocalContentColor.current)
                }
                IconButton(onClick = { navigate(Routes.CART) }) {
                    BadgedBox(badge = { if (cartCount > 0) Badge { Text("$cartCount") } }) { Icon(Icons.Filled.ShoppingCart, "Cart") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            val v = vm.selected
            if (p != null && v != null) Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (v.inStock) {
                        QuantityStepper(vm.qty, { vm.qty = it.coerceIn(1, v.stock) }, max = minOf(v.stock, 50))
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                vm.addToCart { msg, ok ->
                                    scope.launch {
                                        val r = snack.showSnackbar(msg, actionLabel = if (ok) "View cart" else null, duration = SnackbarDuration.Short)
                                        if (r == SnackbarResult.ActionPerformed) navigate(Routes.CART)
                                    }
                                }
                            },
                            enabled = !vm.adding, modifier = Modifier.weight(1f).height(50.dp),
                        ) { Text(if (vm.adding) "Adding…" else "Add to cart · ${inr(v.price * vm.qty)}") }
                    } else {
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Out of stock") }
                    }
                }
            }
        },
    ) { pad ->
        when {
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            p == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
                // Images
                val imgs = p.images.ifEmpty { listOf(com.yourstore.app.data.model.ProductImage(url = "")) }
                val pager = rememberPagerState { imgs.size }
                Box(Modifier.background(Color.White)) {
                    HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(1f)) { i -> NetImage(imgs[i].url.ifBlank { null }, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Fit) }
                    if (imgs.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(10.dp)) {
                        repeat(imgs.size) { i -> Box(Modifier.padding(3.dp).size(7.dp).clip(CircleShape).background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else Color.LightGray)) }
                    }
                }

                Column(Modifier.padding(16.dp)) {
                    p.categoryName?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                    Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    vm.selected?.let { v ->
                        PriceRow(v.price, v.mrp, big = true)
                        Text("Inclusive of all taxes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        when {
                            !v.inStock -> Text("Out of stock", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            v.stock <= 5 -> Text("Only ${v.stock} left — order soon", color = Warning, fontWeight = FontWeight.SemiBold)
                            else -> Text("In stock", color = com.yourstore.app.ui.theme.Discount, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (p.variants.size > 1) {
                        Spacer(Modifier.height(16.dp))
                        Text("Choose size / quantity", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            p.variants.forEach { v ->
                                FilterChip(
                                    selected = vm.selected?.id == v.id,
                                    onClick = { vm.select(v) },
                                    enabled = true,
                                    label = {
                                        Column(Modifier.padding(vertical = 6.dp)) {
                                            Text(v.label, fontWeight = FontWeight.SemiBold)
                                            Text(inr(v.price) + if (!v.inStock) " · Out of stock" else "", style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (!p.description.isNullOrBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Text("Description", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(p.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    val specs = p.specifications.orEmpty().filter { it.key.isNotBlank() }
                    if (specs.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("Specifications", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        specs.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(s.key, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                                Text(s.value, modifier = Modifier.weight(0.6f))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                val related = p.related.orEmpty()
                if (related.isNotEmpty()) {
                    SectionHeader("You may also like")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(related, key = { it.id }) { r -> ProductCard(r, onClick = { navigate(Routes.product(r.id)) }, modifier = Modifier.width(150.dp)) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
