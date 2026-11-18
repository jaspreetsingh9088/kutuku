package com.yourstore.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.*
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.home.BannerPager
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.launch

class StoreViewModel(private val id: Int) : ViewModel() {
    private val c = ShopApp.container
    var store by mutableStateOf<Store?>(null)
    var error by mutableStateOf<String?>(null)
    var tab by mutableIntStateOf(0)
    var all by mutableStateOf<List<Product>?>(null)
    var best by mutableStateOf<List<Product>?>(null)
    var opening by mutableStateOf(false)

    init { load() }

    fun load() {
        error = null
        viewModelScope.launch { apiCall { c.api.store(id) }.onSuccess { store = it }.onFailure { error = it.message } }
    }

    fun selectTab(t: Int) {
        tab = t
        viewModelScope.launch {
            if (t == 1 && all == null) apiCall { c.api.products(storeId = id, sort = "latest", limit = 50) }.onSuccess { all = it.data }
            if (t == 2 && best == null) apiCall { c.api.products(storeId = id, sort = "best", limit = 50) }.onSuccess { best = it.data }
        }
    }

    fun toggleFollow() {
        val s = store ?: return
        viewModelScope.launch {
            apiCall { if (s.following) c.api.unfollow(s.id) else c.api.follow(s.id) }
                .onSuccess { r -> store = s.copy(following = r.following, followerCount = s.followerCount + if (r.following) 1 else -1) }
        }
    }

    fun toggleWishlist(p: Product) = viewModelScope.launch {
        apiCall { if (p.wishlisted) c.api.removeWishlist(p.id) else c.api.addWishlist(p.id) }.onSuccess { r ->
            fun List<Product>?.upd() = this?.map { if (it.id == p.id) it.copy(wishlisted = r.wishlisted) else it }
            store = store?.copy(popular = store?.popular.upd()); all = all.upd(); best = best.upd()
        }
    }

    fun openChat(onOpen: (Int) -> Unit) {
        if (opening) return
        opening = true
        viewModelScope.launch {
            apiCall { c.api.openConversation(ConversationReq(id)) }.onSuccess { onOpen(it.id) }
            opening = false
        }
    }
}

@Composable
fun StoreScreen(id: Int, navigate: (String) -> Unit, onBack: () -> Unit) {
    val vm: StoreViewModel = viewModel(key = "store-$id") { StoreViewModel(id) }
    val s = vm.store
    when {
        s == null && vm.error != null -> Column(Modifier.fillMaxSize().background(Color.White)) { KTopBar("Store", onBack = onBack); ErrorState(vm.error!!, { vm.load() }) }
        s == null -> LoadingBox(Modifier.fillMaxSize().background(Color.White))
        else -> StoreContent(
            store = s, tab = vm.tab, onTab = { vm.selectTab(it) },
            products = when (vm.tab) { 0 -> s.popular.orEmpty(); 1 -> vm.all; else -> vm.best },
            onBack = onBack, onCart = { navigate(Routes.CART) }, onFollow = { vm.toggleFollow() },
            onProduct = { navigate(Routes.product(it.id)) }, onWishlist = { vm.toggleWishlist(it) },
            onChat = { vm.openChat { cid -> navigate(Routes.chat(cid)) } },
            onBanner = { b -> b.redirectValue?.toIntOrNull()?.let { if (b.redirectType == "product") navigate(Routes.product(it)) } },
        )
    }
}

@Composable
fun StoreContent(
    store: Store, tab: Int, onTab: (Int) -> Unit, products: List<Product>?,
    onBack: () -> Unit, onCart: () -> Unit, onFollow: () -> Unit, onProduct: (Product) -> Unit, onWishlist: (Product) -> Unit,
    onChat: () -> Unit, onBanner: (Banner) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            KTopBar("Store", onBack = onBack) { IconBtn(Icons.Outlined.ShoppingBag, "Cart", onCart) }
            Box(Modifier.padding(horizontal = PagePad)) { StoreRow(store, logoSize = 52.dp) { FollowButton(store.following, onFollow) } }
            UnderlineTabs(listOf("Main Page", "All Products", "Best Sell"), tab, onTab, Modifier.padding(horizontal = 8.dp), underlineWidth = 94.dp)
            when {
                products == null -> LoadingBox()
                products.isEmpty() && tab != 0 -> EmptyState(Icons.Outlined.Inventory2, "No products yet", "This store hasn't listed products here yet.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = PagePad, end = PagePad, top = 24.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (tab == 0) {
                        val banners = store.banners.orEmpty()
                        if (banners.isNotEmpty()) item(span = { GridItemSpan(2) }) { BannerPager(banners, onBanner, byline = "By ${store.name}") }
                        item(span = { GridItemSpan(2) }) { SectionTitle("Popular Products") }
                    }
                    items(products, key = { it.id }) { p -> ProductCard(p, { onProduct(p) }, onWishlist = onWishlist) }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(PagePad).size(52.dp).shadow(8.dp, CircleShape).clip(CircleShape)
            .background(Brand).clickable(onClick = onChat), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.Chat, "Chat with store", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}
