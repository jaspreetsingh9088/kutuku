package com.yourstore.app.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Category
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import kotlinx.coroutines.launch

class CategoriesViewModel : ViewModel() {
    var cats by mutableStateOf<List<Category>?>(null)
    var error by mutableStateOf<String?>(null)
    init { load() }
    fun load() {
        error = null
        viewModelScope.launch { apiCall { ShopApp.container.api.categories() }.onSuccess { cats = it }.onFailure { error = it.message } }
    }
}

@Composable
fun CategoriesScreen(navigate: (String) -> Unit, vm: CategoriesViewModel = viewModel()) {
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    Scaffold(topBar = { AppTopBar("Categories") }) { pad ->
        val cats = vm.cats
        when {
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            cats == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cats, key = { it.id }) { cat ->
                    val open = cat.id in expanded
                    val kids = cat.children.orEmpty()
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (kids.isEmpty()) navigate(Routes.products(categoryId = cat.id, title = cat.name))
                                else expanded = if (open) expanded - cat.id else expanded + cat.id
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NetImage(cat.imageUrl, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cat.name, fontWeight = FontWeight.SemiBold)
                                if (kids.isNotEmpty()) Text(kids.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (kids.isNotEmpty()) Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                        if (open) {
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text("All ${cat.name}", color = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { navigate(Routes.products(categoryId = cat.id, title = cat.name)) },
                            )
                            kids.forEach { sub ->
                                ListItem(
                                    leadingContent = { NetImage(sub.imageUrl, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))) },
                                    headlineContent = { Text(sub.name) },
                                    modifier = Modifier.clickable { navigate(Routes.products(categoryId = sub.id, title = sub.name)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
