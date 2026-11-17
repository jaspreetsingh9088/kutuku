package com.yourstore.app.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Product
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ProductFilters(val minPrice: Int? = null, val maxPrice: Int? = null, val sort: String? = null)

class ProductListViewModel(private val categoryId: Int?, initialQuery: String, private val featured: Boolean, initialSort: String?) : ViewModel() {
    private val c = ShopApp.container
    var query by mutableStateOf(initialQuery)
    var filters by mutableStateOf(ProductFilters(sort = initialSort))
    val items = mutableStateListOf<Product>()
    var total by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var page = 0
    private var pages = 1
    private var job: Job? = null

    /** A pure search screen (no category/featured) waits for the user to type. */
    val isSearch = categoryId == null && !featured && initialSort == null

    init { if (!isSearch || query.isNotBlank()) reload() }

    fun onQueryChange(q: String) {
        query = q
        job?.cancel()
        job = viewModelScope.launch { delay(400); if (!isSearch || q.length >= 2) reload() else { items.clear(); total = 0 } }
    }

    fun applyFilters(f: ProductFilters) { filters = f; reload() }

    fun reload() { page = 0; pages = 1; items.clear(); loadMore() }

    fun loadMore() {
        if (loading || page >= pages) return
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

    // Infinite scroll
    val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= grid.layoutInfo.totalItemsCount - 6 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            title = {
                if (vm.isSearch) TextField(
                    value = vm.query, onValueChange = vm::onQueryChange, placeholder = { Text("Search products") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, "Clear") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.reload() }),
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp).focusRequester(focus),
                ) else Text(title)
            },
            actions = { IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.FilterList, "Filter & sort") } },
        )
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                vm.items.isEmpty() && vm.loading -> LoadingBox()
                vm.items.isEmpty() && vm.error != null -> ErrorState(vm.error!!, vm::reload)
                vm.items.isEmpty() && vm.isSearch && vm.query.length < 2 -> EmptyState(Icons.Filled.Search, "What are you looking for?", "Search by product name or SKU")
                vm.items.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "No products found", "Try a different search or remove filters")
                else -> LazyVerticalGrid(GridCells.Adaptive(156.dp), state = grid, contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("${vm.total} products", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(vm.items, key = { it.id }) { p -> ProductCard(p, onClick = { navigate(Routes.product(p.id)) }, onWishlist = vm::toggleWishlist) }
                    if (vm.loading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox(Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
    }

    if (showFilters) FilterSheet(vm.filters, onDismiss = { showFilters = false }, onApply = { vm.applyFilters(it); showFilters = false })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(current: ProductFilters, onDismiss: () -> Unit, onApply: (ProductFilters) -> Unit) {
    var sort by remember { mutableStateOf(current.sort) }
    var min by remember { mutableStateOf(current.minPrice?.toString() ?: "") }
    var max by remember { mutableStateOf(current.maxPrice?.toString() ?: "") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Sort by", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "Relevance", "latest" to "Newest", "popular" to "Popular", "price_asc" to "Price: low to high", "price_desc" to "Price: high to low").forEach { (k, l) ->
                    FilterChip(selected = sort == k, onClick = { sort = k }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Price range (₹)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(min, { min = it.filter(Char::isDigit).take(6) }, label = { Text("Min") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                Text("–")
                OutlinedTextField(max, { max = it.filter(Char::isDigit).take(6) }, label = { Text("Max") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(0 to 100, 100 to 250, 250 to 500, 500 to 1000).forEach { (a, b) ->
                    AssistChip(onClick = { min = a.toString(); max = b.toString() }, label = { Text("₹$a–$b") })
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onApply(ProductFilters()) }, modifier = Modifier.weight(1f)) { Text("Clear all") }
                Button(onClick = { onApply(ProductFilters(min.toIntOrNull(), max.toIntOrNull(), sort)) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}
