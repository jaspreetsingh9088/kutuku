package com.yourstore.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.yourstore.app.data.model.*
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val c = ShopApp.container
    var home by mutableStateOf<Home?>(null)
    var categories by mutableStateOf<List<Category>>(emptyList())
    var profile by mutableStateOf<Customer?>(null)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
    var tab by mutableIntStateOf(0)

    init { load() }

    fun load(pull: Boolean = false) {
        refreshing = pull; error = null
        viewModelScope.launch {
            apiCall { c.api.home() }.onSuccess { home = it }.onFailure { if (home == null) error = it.message }
            apiCall { c.api.categories() }.onSuccess { categories = it }
            refreshing = false
        }
    }

    fun refreshProfile() = viewModelScope.launch { apiCall { c.api.profile() }.onSuccess { profile = it } }

    fun toggleWishlist(p: Product) = viewModelScope.launch {
        apiCall { if (p.wishlisted) c.api.removeWishlist(p.id) else c.api.addWishlist(p.id) }.onSuccess { res ->
            fun List<Product>.upd() = map { if (it.id == p.id) it.copy(wishlisted = res.wishlisted) else it }
            home = home?.let { it.copy(featured = it.featured.upd(), popular = it.popular.upd(), latest = it.latest.upd()) }
        }
    }
}

@Composable
fun HomeScreen(navigate: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.refreshProfile() }
    val uri = LocalUriHandler.current
    HomeContent(
        home = vm.home, categories = vm.categories, profile = vm.profile, error = vm.error, refreshing = vm.refreshing,
        tab = vm.tab, onTab = { vm.tab = it },
        onRefresh = { vm.load(true); vm.refreshProfile() }, onRetry = { vm.load() },
        onSearch = { navigate(Routes.SEARCH) }, onNotifications = { navigate(Routes.NOTIFICATIONS) }, onAvatar = { navigate(Routes.ACCOUNT) },
        onProduct = { navigate(Routes.product(it.id)) }, onWishlist = { vm.toggleWishlist(it) },
        onSeeAll = { navigate(Routes.products(title = "New Arrivals", sort = "latest")) },
        onCategory = { navigate(Routes.products(categoryId = it.id, title = it.name)) },
        onNewArrivals = { navigate(Routes.products(title = "New Arrivals", sort = "latest")) },
        onBanner = { b ->
            when (b.redirectType) {
                "product" -> b.redirectValue?.toIntOrNull()?.let { navigate(Routes.product(it)) }
                "category" -> b.redirectValue?.toIntOrNull()?.let { navigate(Routes.products(categoryId = it, title = b.title)) }
                "store" -> b.redirectValue?.toIntOrNull()?.let { navigate(Routes.store(it)) }
                "url" -> b.redirectValue?.let { runCatching { uri.openUri(it) } }
                else -> {}
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    home: Home?, categories: List<Category>, profile: Customer?, error: String?, refreshing: Boolean,
    tab: Int, onTab: (Int) -> Unit, onRefresh: () -> Unit, onRetry: () -> Unit,
    onSearch: () -> Unit, onNotifications: () -> Unit, onAvatar: () -> Unit,
    onProduct: (Product) -> Unit, onWishlist: (Product) -> Unit, onSeeAll: () -> Unit,
    onCategory: (Category) -> Unit, onNewArrivals: () -> Unit, onBanner: (Banner) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        // Header: avatar, greeting, search and bell
        Row(Modifier.fillMaxWidth().padding(start = PagePad, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).clickable(onClick = onAvatar)) { Avatar(profile?.avatarUrl, profile?.name ?: "K", 40.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Hi, ${profile?.name?.substringBefore(' ')?.ifBlank { null } ?: "there"}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Let's go shopping", fontSize = 11.sp, color = InkHint)
            }
            IconBtn(Icons.Outlined.Search, "Search", onSearch)
            IconBtn(Icons.Outlined.NotificationsNone, "Notifications", onNotifications, badge = (profile?.unreadNotifications ?: 0) > 0)
        }
        UnderlineTabs(listOf("Home", "Category"), tab, onTab, Modifier.padding(horizontal = PagePad))
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when {
                home == null && error != null -> ErrorState(error, onRetry)
                home == null -> LoadingBox()
                tab == 0 -> HomeTab(home, onProduct, onWishlist, onSeeAll, onBanner)
                else -> CategoryTab(home, categories, onCategory, onNewArrivals)
            }
        }
    }
}

@Composable
private fun HomeTab(home: Home, onProduct: (Product) -> Unit, onWishlist: (Product) -> Unit, onSeeAll: () -> Unit, onBanner: (Banner) -> Unit) {
    val products = home.latest.ifEmpty { home.featured }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = PagePad, end = PagePad, top = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (home.banners.isNotEmpty()) item(span = { GridItemSpan(2) }) { BannerPager(home.banners, onBanner) }
        item(span = { GridItemSpan(2) }) { SectionTitle("New Arrivals 🔥", action = "See All", onAction = onSeeAll) }
        items(products, key = { it.id }) { p -> ProductCard(p, { onProduct(p) }, onWishlist = onWishlist) }
    }
}

/** Grey promo banner with lilac quarter circle, text on the left and product photo on the right, plus dots. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerPager(banners: List<Banner>, onBanner: (Banner) -> Unit, byline: String = "By Kutuku Store") {
    val pager = rememberPagerState { banners.size }
    if (banners.size > 1) LaunchedEffect(pager) {
        while (true) { delay(4500); pager.animateScrollToPage((pager.currentPage + 1) % banners.size) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, pageSpacing = 12.dp) { i -> PromoBanner(banners[i], byline) { onBanner(banners[i]) } }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(banners.size) { i -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == pager.currentPage) Brand else Color(0xFFDCDCE4))) }
        }
    }
}

@Composable
fun PromoBanner(b: Banner, byline: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF2F2F4)).clickable(onClick = onClick)) {
        Box(Modifier.size(150.dp).offset(x = (-75).dp, y = 50.dp).clip(CircleShape).background(BrandLight))
        NetImage(b.imageUrl, Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.38f), contentScale = ContentScale.Crop)
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.66f).padding(start = 20.dp, end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(b.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink, lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(b.description?.takeIf { it.isNotBlank() } ?: byline, fontSize = 11.sp, color = InkMuted, maxLines = 1)
        }
    }
}

/** Category tab: grey cards with name + product count, photo alternating right / left. */
@Composable
private fun CategoryTab(home: Home, categories: List<Category>, onCategory: (Category) -> Unit, onNewArrivals: () -> Unit) {
    val list = categories.ifEmpty { home.categories }
    LazyColumn(contentPadding = PaddingValues(horizontal = PagePad, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            CategoryCard("New Arrivals", list.sumOf { it.productCount }.takeIf { it > 0 } ?: home.latest.size,
                home.latest.firstOrNull()?.image, imageRight = true, onClick = onNewArrivals)
        }
        itemsIndexed(list, key = { _, c -> c.id }) { i, c -> CategoryCard(c.name, c.productCount, c.imageUrl, imageRight = i % 2 == 1) { onCategory(c) } }
    }
}

@Composable
fun CategoryCard(name: String, count: Int, image: String?, imageRight: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF2F2F4)).clickable(onClick = onClick)) {
        val text = @Composable {
            Column(Modifier.fillMaxHeight().weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = if (imageRight) Alignment.Start else Alignment.End) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("$count Product", fontSize = 12.sp, color = Ink2)
            }
        }
        val img = @Composable { NetImage(image, Modifier.fillMaxHeight().weight(1f)) }
        if (imageRight) { text(); img() } else { img(); text() }
    }
}
