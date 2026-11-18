package com.yourstore.app.ui.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
                if (selected == null) selected = p.variants.firstOrNull { it.inStock } ?: p.variants.firstOrNull()
            }.onFailure { error = it.message }
        }
    }

    /** Pick a colour or size; keeps the other attribute when such a variant exists. */
    fun choose(color: String? = selected?.color, label: String? = selected?.label) {
        val vs = product?.variants ?: return
        selected = vs.firstOrNull { it.color == color && it.label == label }
            ?: vs.firstOrNull { it.color == color && it.inStock } ?: vs.firstOrNull { it.color == color }
            ?: vs.firstOrNull { it.label == label } ?: selected
        qty = qty.coerceAtMost((selected?.stock ?: 1).coerceAtLeast(1))
    }

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

    fun toggleFollow() {
        val s = product?.store ?: return
        viewModelScope.launch {
            apiCall { if (s.following) c.api.unfollow(s.id) else c.api.follow(s.id) }.onSuccess { r ->
                product = product?.copy(store = s.copy(following = r.following, followerCount = s.followerCount + if (r.following) 1 else -1))
            }
        }
    }
}

@Composable
fun ProductDetailScreen(id: Int, navigate: (String) -> Unit, onBack: () -> Unit, snack: (String) -> Unit) {
    val vm: ProductDetailViewModel = viewModel(key = "product-$id") { ProductDetailViewModel(id) }
    val p = vm.product
    when {
        p == null && vm.error != null -> Column(Modifier.fillMaxSize().background(Color.White)) {
            KTopBar("Detail Product", onBack = onBack); ErrorState(vm.error!!, { vm.load() })
        }
        p == null -> LoadingBox(Modifier.fillMaxSize().background(Color.White))
        else -> ProductDetailContent(
            p = p, selected = vm.selected, qty = vm.qty, adding = vm.adding,
            onQty = { vm.qty = it }, onColor = { vm.choose(color = it) }, onSize = { vm.choose(label = it) },
            onBack = onBack, onCart = { navigate(Routes.CART) }, onWishlist = { vm.toggleWishlist() },
            onStore = { p.store?.let { navigate(Routes.store(it.id)) } }, onFollow = { vm.toggleFollow() },
            onAdd = { vm.addToCart { msg, _ -> snack(msg) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ProductDetailContent(
    p: Product, selected: Variant?, qty: Int, adding: Boolean,
    onQty: (Int) -> Unit, onColor: (String?) -> Unit, onSize: (String) -> Unit,
    onBack: () -> Unit, onCart: () -> Unit, onWishlist: () -> Unit, onStore: () -> Unit, onFollow: () -> Unit, onAdd: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val images = p.images.map { it.url }.ifEmpty { listOfNotNull(p.image) }.ifEmpty { listOf("") }
    val pager = rememberPagerState { images.size }
    val colors = p.variants.filter { it.color != null }.distinctBy { it.color }
    val sizes = p.variants.map { it.label }.distinct().filter { it.isNotBlank() }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            // Full-bleed photo
            Box(Modifier.fillMaxWidth().height(430.dp)) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { i -> NetImage(images[i], Modifier.fillMaxSize()) }
                if (images.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(images.size) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == pager.currentPage) Brand else Color.White.copy(alpha = 0.7f))) }
                }
            }
            // Rounded white sheet overlapping the photo
            Column(Modifier.offset(y = (-28).dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color.White)
                .padding(horizontal = PagePad).padding(top = 28.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink, lineHeight = 26.sp)
                        Spacer(Modifier.height(8.dp))
                        RatingRow(p.rating, p.ratingCount)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KStepper(qty, onQty, max = (selected?.stock ?: 1).coerceIn(1, 50), enabled = selected?.inStock == true, boxed = true)
                        Spacer(Modifier.height(8.dp))
                        Text(if (selected?.inStock == true) "Available in stock" else "Out of stock", fontSize = 11.sp,
                            fontWeight = FontWeight.Medium, color = if (selected?.inStock == true) Ink else Warning)
                    }
                }
                if (colors.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text("Color", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        colors.forEach { v -> ColorDot(v.colorHex, v.color == selected?.color, { onColor(v.color) }, size = 34.dp,
                            enabled = p.variants.any { it.color == v.color && it.inStock }) }
                    }
                }
                if (sizes.size > 1 || (sizes.size == 1 && colors.isEmpty() && sizes[0] != "Default")) {
                    Spacer(Modifier.height(24.dp))
                    Text("Size", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sizes.forEach { s -> KChip(s, selected?.label == s, { onSize(s) }) }
                    }
                }
                if (!p.description.isNullOrBlank()) {
                    Spacer(Modifier.height(28.dp))
                    Text("Description", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Spacer(Modifier.height(10.dp))
                    val desc = p.description
                    val long = desc.length > 180
                    Text(
                        buildAnnotatedString {
                            append(if (long && !expanded) desc.take(180).trimEnd() + "... " else "$desc ")
                            if (long) withStyle(SpanStyle(color = Brand, fontWeight = FontWeight.Medium)) { append(if (expanded) "Read Less" else "Read More") }
                        },
                        fontSize = 14.sp, color = InkMuted, lineHeight = 26.sp,
                        modifier = Modifier.clickable(enabled = long) { expanded = !expanded },
                    )
                }
                if (!p.specifications.isNullOrEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    p.specifications.forEach { s ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(s.key, fontSize = 13.sp, color = InkHint, modifier = Modifier.width(120.dp))
                            Text(s.value, fontSize = 13.sp, color = Ink)
                        }
                    }
                }
                p.store?.let { s ->
                    Spacer(Modifier.height(20.dp))
                    StoreRow(s, onStore, logoSize = 36.dp, small = true) { FollowButton(s.following, onFollow) }
                }
            }
        }

        // Top bar over the photo
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", Modifier.size(18.dp), tint = Ink) }
            Text("Detail Product", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onCart) { Icon(Icons.Outlined.ShoppingBag, "Cart", Modifier.size(24.dp), tint = Ink) }
        }
        // Favourite button
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 360.dp, end = PagePad).size(44.dp).shadow(6.dp, CircleShape)
            .clip(CircleShape).background(Color.White).clickable(onClick = onWishlist), contentAlignment = Alignment.Center) {
            Icon(if (p.wishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", Modifier.size(22.dp),
                tint = if (p.wishlisted) Color(0xFFE11D2E) else Ink)
        }

        // Price + Add to Cart
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).navigationBarsPadding()
            .padding(horizontal = PagePad, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(inr((selected?.price ?: p.price ?: 0.0) * qty), color = Brand, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                if (selected != null && selected.mrp > selected.price) StrikePrice(selected.mrp * qty, selected.price * qty)
            }
            KButton(if (selected?.inStock != false) "Add to Cart" else "Out of stock", onAdd, Modifier.width(190.dp),
                enabled = selected?.inStock == true, loading = adding, icon = Icons.Outlined.ShoppingBag)
        }
    }
}
