package com.yourstore.app.ui.catalog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Product
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ProductFilters(val minPrice: Int? = null, val maxPrice: Int? = null, val sort: String? = null)

/** Recent searches kept on the device (the kit's "Last Search" chips). */
private object RecentSearches {
    private fun prefs() = ShopApp.instance.getSharedPreferences("search", Context.MODE_PRIVATE)
    fun all(): List<String> = prefs().getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun add(q: String) {
        val list = (listOf(q.trim()) + all().filter { !it.equals(q.trim(), true) }).take(8)
        prefs().edit().putString("recent", list.joinToString("\n")).apply()
    }
    fun remove(q: String) = prefs().edit().putString("recent", all().filter { it != q }.joinToString("\n")).apply()
    fun clear() = prefs().edit().remove("recent").apply()
}

class ProductListViewModel(private val categoryId: Int?, initialQuery: String, private val featured: Boolean, initialSort: String?) : ViewModel() {
    private val c = ShopApp.container
    var query by mutableStateOf(initialQuery)
    var filters by mutableStateOf(ProductFilters(sort = initialSort))
    val items = mutableStateListOf<Product>()
    var total by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var recent by mutableStateOf(RecentSearches.all())
    var popular by mutableStateOf<List<Product>>(emptyList())
    private var page = 0
    private var pages = 1
    private var job: Job? = null

    /** A pure search screen (no category/featured/sort preset) waits for the user to type. */
    val isSearch = categoryId == null && !featured && initialSort == null

    init {
        if (!isSearch || query.isNotBlank()) reload()
        if (isSearch) viewModelScope.launch { apiCall { c.api.products(sort = "popular", limit = 4) }.onSuccess { popular = it.data } }
    }

    val showingResults get() = !isSearch || query.trim().length >= 2

    fun onQueryChange(q: String) {
        query = q
        job?.cancel()
        job = viewModelScope.launch { delay(400); if (showingResults) reload() else { items.clear(); total = 0 } }
    }

    fun submitSearch(q: String = query) {
        query = q
        if (q.isNotBlank()) { RecentSearches.add(q); recent = RecentSearches.all() }
        reload()
    }

    fun removeRecent(q: String) { RecentSearches.remove(q); recent = RecentSearches.all() }
    fun clearRecent() { RecentSearches.clear(); recent = emptyList() }

    fun applyFilters(f: ProductFilters) { filters = f; reload() }

    fun reload() { page = 0; pages = 1; items.clear(); loadMore() }

    fun loadMore() {
        if (loading || page >= pages || !showingResults) return
        loading = true; error = null
        viewModelScope.launch {
            apiCall {
                c.api.products(
                    q = query.ifBlank { null }, categoryId = categoryId, minPrice = filters.minPrice, maxPrice = filters.maxPrice,
                    sort = filters.sort, featured = if (featured) 1 else null, page = page + 1,
                )
            }.onSuccess { r ->
                page = r.meta.page; pages = r.meta.pages; total = r.meta.total
                items.addAll(r.data.filter { n -> items.none { it.id == n.id } })
            }.onFailure { error = it.message }
            loading = false
        }
    }

    fun toggleWishlist(p: Product) { viewModelScope.launch {
        apiCall { if (p.wishlisted) c.api.removeWishlist(p.id) else c.api.addWishlist(p.id) }.onSuccess { r ->
            val i = items.indexOfFirst { it.id == p.id }
            if (i >= 0) items[i] = items[i].copy(wishlisted = r.wishlisted)
        }
    } }
}

private val SORTS = listOf(null to "All", "latest" to "Latest", "popular" to "Most Popular", "price_asc" to "Cheapest", "price_desc" to "Highest Price")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    categoryId: Int?, initialQuery: String, title: String, featured: Boolean, sort: String?,
    navigate: (String) -> Unit, onBack: () -> Unit,
) {
    val vm: ProductListViewModel = viewModel(key = "plist-$categoryId-$featured-$sort") { ProductListViewModel(categoryId, initialQuery, featured, sort) }
    var showFilters by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (vm.isSearch) runCatching { focus.requestFocus() } }

    val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= grid.layoutInfo.totalItemsCount - 6 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(containerColor = Color.White, topBar = {
        Row(Modifier.background(Color.White).statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", Modifier.size(20.dp), tint = Ink) }
            if (vm.isSearch || vm.query.isNotBlank()) {
                OutlinedTextField(
                    value = vm.query, onValueChange = vm::onQueryChange, singleLine = true,
                    placeholder = { Text("Search products", color = InkHint) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Ink) },
                    trailingIcon = {
                        if (vm.showingResults) IconButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.Tune, "Filter", tint = Ink) }
                        else if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, "Clear") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.submitSearch() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, unfocusedBorderColor = Line),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            } else {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.Tune, "Filter", tint = Ink) }
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!vm.showingResults) {
                SearchHome(vm, navigate)
                return@Column
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SORTS.forEach { (k, l) -> PillChip(l, vm.filters.sort == k, { vm.applyFilters(vm.filters.copy(sort = k)) }) }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    vm.items.isEmpty() && vm.loading -> LoadingBox()
                    vm.items.isEmpty() && vm.error != null -> ErrorState(vm.error!!, vm::reload)
                    vm.items.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "No products found", "Try a different search or remove filters")
                    else -> LazyVerticalGrid(GridCells.Fixed(2), state = grid, contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("${vm.total} products found", style = MaterialTheme.typography.bodySmall)
                        }
                        items(vm.items, key = { it.id }) { p -> ProductCard(p, onClick = { navigate(Routes.product(p.id)) }, onWishlist = vm::toggleWishlist) }
                        if (vm.loading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox(Modifier.fillMaxWidth().padding(16.dp)) }
                    }
                }
            }
        }
    }

    if (showFilters) FilterSheet(vm.filters, onDismiss = { showFilters = false }, onApply = { vm.applyFilters(it); showFilters = false })
}

/** Before typing: "Last Search" chips and a "Popular Search" list with Hot / New / Popular tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHome(vm: ProductListViewModel, navigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if (vm.recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Last Search", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Clear All", color = Brand, fontSize = 13.sp, modifier = Modifier.clickable { vm.clearRecent() })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.recent.forEach { q ->
                    PillChip(q, false, { vm.submitSearch(q) }) {
                        Icon(Icons.Filled.Close, "Remove", Modifier.size(14.dp).clickable { vm.removeRecent(q) }, tint = InkHint)
                    }
                }
            }
        }
        if (vm.popular.isNotEmpty()) {
            Text("Popular Search", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            val tags = listOf(Triple("Hot", HotBg, Warning), Triple("New", NewBg, NewFg), Triple("Popular", PopularBg, Discount), Triple("New", NewBg, NewFg))
            vm.popular.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { navigate(Routes.product(p.id)) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    NetImage(p.image, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(p.price?.let { inr(it) } ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                    val (label, bg, fg) = tags[i % tags.size]
                    Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 14.dp, vertical = 5.dp))
                }
            }
        }
    }
}

/** The kit's "Filter By" bottom sheet: price range slider, sort, and an "Apply Filter" pill button. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(current: ProductFilters, onDismiss: () -> Unit, onApply: (ProductFilters) -> Unit) {
    val maxRange = 5000f
    var range by remember { mutableStateOf((current.minPrice?.toFloat() ?: 0f)..(current.maxPrice?.toFloat() ?: maxRange)) }
    var sort by remember { mutableStateOf(current.sort) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Filter By", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            Row {
                Text("Price", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("₹${range.start.toInt()} – ₹${range.endInclusive.toInt()}${if (range.endInclusive >= maxRange) "+" else ""}", color = InkHint, fontSize = 13.sp)
            }
            RangeSlider(
                value = range, onValueChange = { range = it }, valueRange = 0f..maxRange, steps = 49,
                colors = SliderDefaults.colors(thumbColor = Brand, activeTrackColor = Brand, inactiveTrackColor = Line, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            )
            Spacer(Modifier.height(12.dp))
            Text("Sort by", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SORTS.forEach { (k, l) -> PillChip(if (k == null) "Relevance" else l, sort == k, { sort = k }) }
            }
            Spacer(Modifier.height(28.dp))
            PillButton("Apply Filter", onClick = {
                onApply(ProductFilters(
                    minPrice = range.start.toInt().takeIf { it > 0 },
                    maxPrice = range.endInclusive.toInt().takeIf { it < maxRange.toInt() },
                    sort = sort,
                ))
            }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onApply(ProductFilters()) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Reset", color = InkMuted) }
        }
    }
}
