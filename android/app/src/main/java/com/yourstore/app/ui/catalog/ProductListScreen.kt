package com.yourstore.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourstore.app.ShopApp
import com.yourstore.app.data.Prefs
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.*
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ProductFilters(val minPrice: Int? = null, val maxPrice: Int? = null, val color: String? = null, val city: String? = null) {
    val active get() = minPrice != null || maxPrice != null || color != null || city != null
}

/** Chips on the Search Result frame. "All" uses the default ordering. */
val SORT_CHIPS = listOf("All" to null, "Latest" to "latest", "Most Popular" to "popular", "Cheapest" to "price_asc")

class ProductListViewModel(
    private val categoryId: Int?, initialQuery: String, private val featured: Boolean, initialSort: String?, private val storeId: Int? = null,
) : ViewModel() {
    private val c = ShopApp.container
    var query by mutableStateOf(initialQuery)
    var sort by mutableStateOf(initialSort)
    var filters by mutableStateOf(ProductFilters())
    var filterOptions by mutableStateOf<Filters?>(null)
    var stores by mutableStateOf<List<Store>>(emptyList())
    val items = mutableStateListOf<Product>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var page = 0
    private var pages = 1

    init {
        reload()
        viewModelScope.launch { apiCall { c.api.filters() }.onSuccess { filterOptions = it } }
    }

    fun search(q: String) { query = q.trim(); if (query.isNotBlank()) Prefs.addRecentSearch(query); reload() }
    fun setSortOption(s: String?) { if (s != sort) { sort = s; reload() } }
    fun applyFilters(f: ProductFilters) { filters = f; reload() }
    fun reload() { page = 0; pages = 1; items.clear(); loadMore() }

    fun loadMore() {
        if (loading || page >= pages) return
        loading = true; error = null
        viewModelScope.launch {
            apiCall {
                c.api.products(
                    q = query.ifBlank { null }, categoryId = categoryId, storeId = storeId, color = filters.color, city = filters.city,
                    minPrice = filters.minPrice, maxPrice = filters.maxPrice, sort = sort, featured = if (featured) 1 else null, page = page + 1,
                )
            }.onSuccess { r ->
                page = r.meta.page; pages = r.meta.pages
                items.addAll(r.data.filter { n -> items.none { it.id == n.id } })
                if (r.meta.page == 1) stores = r.stores.orEmpty()
            }.onFailure { error = it.message }
            loading = false
        }
    }

    fun toggleWishlist(p: Product) = viewModelScope.launch {
        apiCall { if (p.wishlisted) c.api.removeWishlist(p.id) else c.api.addWishlist(p.id) }.onSuccess { res ->
            val i = items.indexOfFirst { it.id == p.id }
            if (i >= 0) items[i] = items[i].copy(wishlisted = res.wishlisted)
        }
    }
}

@Composable
fun ProductListScreen(title: String, navigate: (String) -> Unit, onBack: () -> Unit, vm: ProductListViewModel) {
    var showFilter by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= grid.layoutInfo.totalItemsCount - 4 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }
    var draft by remember(vm.query) { mutableStateOf(vm.query) }

    ProductListContent(
        title = title, query = draft, onQuery = { draft = it }, onSubmit = { vm.search(draft) }, searchMode = title.isBlank(),
        sort = vm.sort, onSort = { vm.setSortOption(it) }, stores = vm.stores, items = vm.items, loading = vm.loading, error = vm.error,
        onRetry = { vm.reload() }, onBack = onBack, onFilter = { showFilter = true },
        onStore = { navigate(Routes.store(it.id)) }, onProduct = { navigate(Routes.product(it.id)) }, onWishlist = { vm.toggleWishlist(it) },
        onCart = { navigate(Routes.CART) }, gridState = grid,
        filterVisible = showFilter, onDismissFilter = { showFilter = false },
        filterContent = {
            FilterSheetContent(vm.filterOptions ?: Filters(), vm.filters) { vm.applyFilters(it); showFilter = false }
        },
    )
}

@Composable
fun ProductListContent(
    title: String, query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit, searchMode: Boolean,
    sort: String?, onSort: (String?) -> Unit, stores: List<Store>, items: List<Product>, loading: Boolean, error: String?,
    onRetry: () -> Unit, onBack: () -> Unit, onFilter: () -> Unit, onStore: (Store) -> Unit,
    onProduct: (Product) -> Unit, onWishlist: (Product) -> Unit, onCart: () -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    filterVisible: Boolean = false, onDismissFilter: () -> Unit = {}, filterContent: @Composable ColumnScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            if (searchMode) Column(Modifier.statusBarsPadding()) {
                SearchHeader(onBack) { KSearchBar(query, onQuery, Modifier.weight(1f), onFilter = onFilter, onSubmit = onSubmit) }
            } else KTopBar(title, onBack = onBack) { IconBtn(Icons.Outlined.ShoppingBag, "Cart", onCart) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PagePad, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SORT_CHIPS.forEach { (label, key) -> KChip(label, sort == key, { onSort(key) }) }
                if (!searchMode) KChip("Filter", false, onFilter)
            }
            when {
                items.isEmpty() && loading -> LoadingBox()
                items.isEmpty() && error != null -> ErrorState(error, onRetry)
                items.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "No products found", "Try a different search or remove some filters.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2), state = gridState,
                    contentPadding = PaddingValues(start = PagePad, end = PagePad, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    stores.take(1).forEach { s ->
                        item(span = { GridItemSpan(2) }, key = "store-${s.id}") {
                            StoreRow(s, { onStore(s) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InkHint) }
                        }
                    }
                    items(items, key = { it.id }) { p -> ProductCard(p, { onProduct(p) }, onWishlist = onWishlist) }
                    if (loading) item(span = { GridItemSpan(2) }) { LoadingBox(Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
        SheetOverlay(filterVisible, onDismissFilter, filterContent)
    }
}

/** "Filter By" sheet: price range, colour swatches and location chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.FilterSheetContent(options: Filters, current: ProductFilters, onApply: (ProductFilters) -> Unit) {
    val lo = options.price.min.toFloat().coerceAtLeast(0f)
    val hi = options.price.max.toFloat().coerceAtLeast(lo + 1f)
    var range by remember { mutableStateOf((current.minPrice?.toFloat() ?: lo)..(current.maxPrice?.toFloat() ?: hi)) }
    var color by remember { mutableStateOf(current.color) }
    var city by remember { mutableStateOf(current.city) }

    Text("Filter By", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Price", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
        Text("${inr(range.start.toDouble()).substringBefore('.')}-${inr(range.endInclusive.toDouble()).substringBefore('.')}", fontSize = 13.sp, color = InkHint)
    }
    RangeSlider(
        value = range, onValueChange = { range = it }, valueRange = lo..hi,
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Brand, inactiveTrackColor = Line,
            activeTickColor = Brand, inactiveTickColor = Line),
        startThumb = { SliderThumb() }, endThumb = { SliderThumb() },
    )
    if (options.colors.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Color", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
            Text(color ?: "Any", fontSize = 13.sp, color = InkHint)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
            options.colors.forEach { o -> ColorDot(o.hex, color == o.name, { color = if (color == o.name) null else o.name }, size = 26.dp) }
        }
    }
    if (options.cities.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        Text("Location", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.cities.forEach { c -> KChip(c, city == c, { city = if (city == c) null else c }, big = true) }
        }
    }
    Spacer(Modifier.height(40.dp))
    KButton("Apply Filter", {
        val min = range.start.roundToInt().takeIf { it > lo.roundToInt() }
        val max = range.endInclusive.roundToInt().takeIf { it < hi.roundToInt() }
        onApply(ProductFilters(min, max, color, city))
    }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    if (current.active) TextLink("Clear filters", { onApply(ProductFilters()) }, Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SliderThumb() {
    Box(Modifier.size(16.dp).clip(CircleShape).background(Brand).padding(3.dp).clip(CircleShape).background(Color.White))
}
