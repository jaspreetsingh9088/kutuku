package com.yourstore.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.R
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Banner
import com.yourstore.app.data.model.Home
import com.yourstore.app.data.model.Product
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val c = ShopApp.container
    var home by mutableStateOf<Home?>(null)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)

    init { load() }

    fun load(pull: Boolean = false) {
        refreshing = pull; error = null
        viewModelScope.launch {
            apiCall { c.api.home() }.onSuccess { home = it }.onFailure { if (home == null) error = it.message }
            refreshing = false
        }
    }

    fun toggleWishlist(p: Product) { viewModelScope.launch {
        val r = apiCall { if (p.wishlisted) c.api.removeWishlist(p.id) else c.api.addWishlist(p.id) }
        r.onSuccess { res ->
            fun List<Product>.upd() = map { if (it.id == p.id) it.copy(wishlisted = res.wishlisted) else it }
            home = home?.let { it.copy(featured = it.featured.upd(), popular = it.popular.upd(), latest = it.latest.upd()) }
        }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigate: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val home = vm.home
    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(16.dp, 12.dp)) {
                Text(stringResourceSafe(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { navigate(Routes.products(title = "Search")) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text("Search for products", color = Color.Gray)
                }
            }
        },
    ) { pad ->
        when {
            home == null && vm.error != null -> ErrorState(vm.error!!, onRetry = { vm.load() }, modifier = Modifier.padding(pad).fillMaxSize())
            home == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.load(true) }, modifier = Modifier.padding(pad)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (home.banners.isNotEmpty()) item { BannerCarousel(home.banners, navigate) }
                    if (home.categories.isNotEmpty()) {
                        item { SectionHeader("Shop by category", "See all") { navigate(Routes.CATEGORIES) } }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(home.categories, key = { it.id }) { cat ->
                                    Column(Modifier.width(76.dp).clickable { navigate(Routes.products(categoryId = cat.id, title = cat.name)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        NetImage(cat.imageUrl, Modifier.size(68.dp).clip(CircleShape))
                                        Spacer(Modifier.height(6.dp))
                                        Text(cat.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    productRow("Featured", home.featured, { navigate(Routes.products(title = "Featured", featured = true)) }, navigate, vm::toggleWishlist)
                    productRow("Popular", home.popular, { navigate(Routes.products(title = "Popular", sort = "popular")) }, navigate, vm::toggleWishlist)
                    productRow("New arrivals", home.latest, { navigate(Routes.products(title = "New arrivals", sort = "latest")) }, navigate, vm::toggleWishlist)
                }
            }
        }
    }
}

@Composable private fun stringResourceSafe() = androidx.compose.ui.res.stringResource(R.string.app_name)

private fun androidx.compose.foundation.lazy.LazyListScope.productRow(
    title: String, products: List<Product>, onSeeAll: () -> Unit, navigate: (String) -> Unit, onWishlist: (Product) -> Unit,
) {
    if (products.isEmpty()) return
    item { SectionHeader(title, "See all", onSeeAll) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products, key = { "$title-${it.id}" }) { p ->
                ProductCard(p, onClick = { navigate(Routes.product(p.id)) }, onWishlist = onWishlist, modifier = Modifier.width(156.dp))
            }
        }
    }
}

@Composable
private fun BannerCarousel(banners: List<Banner>, navigate: (String) -> Unit) {
    val pager = rememberPagerState { banners.size }
    val uri = LocalUriHandler.current
    LaunchedEffect(banners.size) {
        while (banners.size > 1) { delay(4000); pager.animateScrollToPage((pager.currentPage + 1) % banners.size) }
    }
    Column(Modifier.padding(top = 12.dp)) {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp) { i ->
            val b = banners[i]
            Box(Modifier.fillMaxWidth().aspectRatio(12f / 5f).clip(RoundedCornerShape(14.dp)).clickable {
                val v = b.redirectValue
                when (b.redirectType) {
                    "product" -> v?.toIntOrNull()?.let { navigate(Routes.product(it)) }
                    "category" -> v?.toIntOrNull()?.let { navigate(Routes.products(categoryId = it, title = b.title)) }
                    "url" -> v?.let { runCatching { uri.openUri(it) } }
                }
            }) { NetImage(b.imageUrl, Modifier.fillMaxSize()) }
        }
        if (banners.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(banners.size) { i ->
                Box(Modifier.padding(3.dp).size(if (pager.currentPage == i) 18.dp else 6.dp, 6.dp).clip(CircleShape)
                    .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}
