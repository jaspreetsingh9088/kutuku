package com.yourstore.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Banner
import com.yourstore.app.data.model.Category
import com.yourstore.app.data.model.Home
import com.yourstore.app.data.model.Product
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val c = ShopApp.container
    var home by mutableStateOf<Home?>(null)
    var name by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
    var tab by mutableIntStateOf(0)

    init { load() }

    fun load(pull: Boolean = false) {
        refreshing = pull; error = null
        viewModelScope.launch {
            apiCall { c.api.home() }.onSuccess { home = it }.onFailure { if (home == null) error = it.message }
            apiCall { c.api.profile() }.onSuccess { name = it.name }
            refreshing = false
        }
    }

    fun toggleWishlist(p: Product) { viewModelScope.launch {
        apiCall { if (p.wishlisted) c.api.removeWishlist(p.id) else c.api.addWishlist(p.id) }.onSuccess { res ->
            fun List<Product>.upd() = map { if (it.id == p.id) it.copy(wishlisted = res.wishlisted) else it }
            home = home?.let { it.copy(featured = it.featured.upd(), popular = it.popular.upd(), latest = it.latest.upd()) }
        }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigate: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val home = vm.home
    val cartCount by ShopApp.container.cartCount.collectAsState()
    Scaffold(
        containerColor = Color.White,
        topBar = {
            Column(Modifier.background(Color.White).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(BrandLight), contentAlignment = Alignment.Center) {
                        Text((vm.name?.firstOrNull() ?: 'K').uppercase(), color = Brand, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hi, ${vm.name?.substringBefore(' ') ?: "there"}", style = MaterialTheme.typography.titleSmall)
                        Text("Let's go shopping", style = MaterialTheme.typography.bodySmall)
                    }
                    CircleIconButton(Icons.Outlined.Search, "Search", { navigate(Routes.products(title = "Search")) })
                    CircleIconButton(Icons.Outlined.ShoppingBag, "Cart", { navigate(Routes.CART) }, badge = cartCount)
                }
                UnderlineTabs(listOf("Home", "Category"), vm.tab, { vm.tab = it }, Modifier.padding(top = 8.dp))
            }
        },
    ) { pad ->
        when {
            home == null && vm.error != null -> ErrorState(vm.error!!, onRetry = { vm.load() }, modifier = Modifier.padding(pad).fillMaxSize())
            home == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.load(true) }, modifier = Modifier.padding(pad)) {
                if (vm.tab == 0) HomeTab(home, navigate, vm::toggleWishlist) else CategoryTab(home.categories, navigate)
            }
        }
    }
}

@Composable
private fun HomeTab(home: Home, navigate: (String) -> Unit, onWishlist: (Product) -> Unit) {
    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (home.banners.isNotEmpty()) item(span = { GridItemSpan(2) }) { BannerCarousel(home.banners, navigate) }
        section("New Arrivals 🔥", home.latest.take(6), { navigate(Routes.products(title = "New Arrivals", sort = "latest")) }, navigate, onWishlist)
        section("Popular", home.popular.take(6), { navigate(Routes.products(title = "Popular", sort = "popular")) }, navigate, onWishlist)
        section("Featured", home.featured.take(6), { navigate(Routes.products(title = "Featured", featured = true)) }, navigate, onWishlist)
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.section(
    title: String, products: List<Product>, onSeeAll: () -> Unit, navigate: (String) -> Unit, onWishlist: (Product) -> Unit,
) {
    if (products.isEmpty()) return
    item(span = { GridItemSpan(2) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("See All", color = Brand, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onSeeAll).padding(4.dp))
        }
    }
    items(products, key = { "$title-${it.id}" }) { p -> ProductCard(p, onClick = { navigate(Routes.product(p.id)) }, onWishlist = onWishlist) }
}

/** Soft grey banner card with a lilac corner shape, headline on the left and image on the right. */
@Composable
private fun BannerCarousel(banners: List<Banner>, navigate: (String) -> Unit) {
    val pager = rememberPagerState { banners.size }
    val uri = LocalUriHandler.current
    LaunchedEffect(banners.size) {
        while (banners.size > 1) { delay(4500); pager.animateScrollToPage((pager.currentPage + 1) % banners.size) }
    }
    Column {
        HorizontalPager(pager, pageSpacing = 12.dp) { i ->
            val b = banners[i]
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(Tile).clickable {
                    val v = b.redirectValue
                    when (b.redirectType) {
                        "product" -> v?.toIntOrNull()?.let { navigate(Routes.product(it)) }
                        "category" -> v?.toIntOrNull()?.let { navigate(Routes.products(categoryId = it, title = b.title)) }
                        "url" -> v?.let { runCatching { uri.openUri(it) } }
                    }
                },
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(BrandLight, radius = size.height * 0.62f, center = Offset(0f, size.height))
                }
                NetImage(b.imageUrl, Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.42f).background(Color.Transparent))
                Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.6f).padding(start = 22.dp), verticalArrangement = Arrangement.Center) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!b.description.isNullOrBlank()) Text(b.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        if (banners.size > 1) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(banners.size) { i ->
                Box(Modifier.padding(3.dp).size(6.dp).clip(CircleShape).background(if (pager.currentPage == i) Brand else Line))
            }
        }
    }
}

/** Category tab: wide grey cards, name and product count on one side, image on the other (alternating). */
@Composable
private fun CategoryTab(categories: List<Category>, navigate: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(categories, key = { it.id }) { cat ->
            val imageLeft = categories.indexOf(cat) % 2 == 1
            Box(
                Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(14.dp)).background(Tile)
                    .clickable { navigate(Routes.products(categoryId = cat.id, title = cat.name)) },
            ) {
                NetImage(cat.imageUrl, Modifier.align(if (imageLeft) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f))
                Column(
                    Modifier.align(if (imageLeft) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 22.dp),
                    horizontalAlignment = if (imageLeft) Alignment.End else Alignment.Start,
                ) {
                    Text(cat.name, style = MaterialTheme.typography.titleMedium)
                    Text("${cat.productCount} Product${if (cat.productCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall.copy(color = Ink))
                }
            }
        }
    }
}
