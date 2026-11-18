package com.yourstore.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.Prefs
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.PopularSearch
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
    var recent by mutableStateOf(Prefs.recentSearches())
    var popular by mutableStateOf<List<PopularSearch>>(emptyList())

    init { viewModelScope.launch { apiCall { ShopApp.container.api.popularSearches() }.onSuccess { popular = it } } }

    fun remember(q: String) { Prefs.addRecentSearch(q); recent = Prefs.recentSearches() }
    fun remove(q: String) { Prefs.removeRecentSearch(q); recent = Prefs.recentSearches() }
    fun clear() { Prefs.clearRecentSearches(); recent = emptyList() }
}

@Composable
fun SearchScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: SearchViewModel = viewModel()) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun go(q: String) {
        if (q.isBlank()) return
        vm.remember(q.trim())
        navigate(Routes.products(q = q.trim()))
    }
    SearchContent(
        query = vm.query, onQuery = { vm.query = it }, recent = vm.recent, popular = vm.popular, focusRequester = focus,
        onSubmit = { go(vm.query) }, onRecent = { go(it) }, onRemoveRecent = { vm.remove(it) }, onClear = { vm.clear() },
        onPopular = { p -> vm.remember(p.term); if (p.productId > 0) navigate(Routes.product(p.productId)) else go(p.term) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchContent(
    query: String, onQuery: (String) -> Unit, recent: List<String>, popular: List<PopularSearch>,
    onSubmit: () -> Unit, onRecent: (String) -> Unit, onRemoveRecent: (String) -> Unit, onClear: () -> Unit,
    onPopular: (PopularSearch) -> Unit, onBack: () -> Unit, focusRequester: FocusRequester? = null,
) {
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        SearchHeader(onBack) { KSearchBar(query, onQuery, Modifier.weight(1f), placeholder = "Search something...", onSubmit = onSubmit, focusRequester = focusRequester) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePad)) {
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionTitle("Last Search", action = "Clear All", onAction = onClear)
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    recent.forEach { r ->
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFE6E7EC), RoundedCornerShape(10.dp)).clickable { onRecent(r) }
                                .padding(start = 20.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r, fontSize = 15.sp, color = InkMuted)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp).clip(CircleShape).clickable { onRemoveRecent(r) }, tint = InkMuted)
                        }
                    }
                }
            }
            if (popular.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionTitle("Popular Search")
                Spacer(Modifier.height(8.dp))
                popular.forEach { p -> PopularRow(p) { onPopular(p) } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** "<" chevron followed by a search field, as on the Search and Search Result frames. */
@Composable
fun SearchHeader(onBack: () -> Unit, field: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = PagePad, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", Modifier.size(18.dp), tint = Ink) }
        field()
    }
}

@Composable
private fun PopularRow(p: PopularSearch, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        NetImage(p.image, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.productName.ifBlank { p.term }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text("${compact(p.count).replace('.', ',')} Search today", fontSize = 14.sp, color = InkHint)
        }
        val (label, bg, fg) = when (p.tag) {
            "hot" -> Triple("Hot", HotBg, Warning)
            "popular" -> Triple("Popular", PopularBg, Success)
            else -> Triple("New", NewBg, NewFg)
        }
        Text(label, color = fg, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 16.dp, vertical = 7.dp))
    }
}
