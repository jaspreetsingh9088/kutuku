package com.yourstore.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.BuildConfig
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Customer
import com.yourstore.app.data.model.PaymentRecord
import com.yourstore.app.data.model.Product
import com.yourstore.app.data.model.ProfileReq
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import kotlinx.coroutines.launch

// ---------------- Account ----------------
class AccountViewModel : ViewModel() {
    private val c = ShopApp.container
    var me by mutableStateOf<Customer?>(null)
    fun load() { viewModelScope.launch { apiCall { c.api.profile() }.onSuccess { me = it } } }
    fun logout() { viewModelScope.launch { apiCall { c.api.logout() }; c.clearSession() } }
    fun deleteAccount() { viewModelScope.launch { apiCall { c.api.deleteAccount() }.onSuccess { c.clearSession() } } }
}

@Composable
fun AccountScreen(navigate: (String) -> Unit, vm: AccountViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val me = vm.me
    Scaffold(topBar = { AppTopBar("My account") }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().clickable { navigate(Routes.profileEdit()) }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Text((me?.name?.firstOrNull() ?: 'U').uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(me?.name ?: "Add your name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(me?.let { "+91 ${it.mobile}" } ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    me?.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
                Text("Edit", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            MenuItem(Icons.AutoMirrored.Outlined.ReceiptLong, "My orders") { navigate(Routes.ORDERS) }
            MenuItem(Icons.Outlined.FavoriteBorder, "My wishlist") { navigate(Routes.WISHLIST) }
            MenuItem(Icons.Outlined.LocationOn, "Saved addresses") { navigate(Routes.ADDRESSES) }
            MenuItem(Icons.Outlined.Payments, "Payment history") { navigate(Routes.PAYMENTS) }
            HorizontalDivider()
            MenuItem(Icons.AutoMirrored.Outlined.Logout, "Log out") { confirmLogout = true }
            MenuItem(Icons.Outlined.DeleteForever, "Delete my account", tint = MaterialTheme.colorScheme.error) { confirmDelete = true }
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp))
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Log out?") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Log out") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete your account?") },
        text = { Text("Your profile, addresses, cart and wishlist will be permanently deleted. Order records are kept for tax purposes. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAccount() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, tint: Color? = null, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(text, color = tint ?: MaterialTheme.colorScheme.onSurface) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ---------------- Profile edit ----------------
class ProfileEditViewModel : ViewModel() {
    private val c = ShopApp.container
    var name by mutableStateOf(""); var email by mutableStateOf(""); var gstin by mutableStateOf("")
    var saving by mutableStateOf(false); var error by mutableStateOf<String?>(null)
    init { viewModelScope.launch { apiCall { c.api.profile() }.onSuccess { name = it.name.orEmpty(); email = it.email.orEmpty(); gstin = it.gstin.orEmpty() } } }
    fun save(onDone: () -> Unit) {
        if (name.trim().length < 2) { error = "Please enter your name"; return }
        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { error = "Enter a valid email"; return }
        saving = true; error = null
        viewModelScope.launch {
            apiCall { c.api.updateProfile(ProfileReq(name.trim(), email.trim().ifBlank { "" }, gstin.trim().uppercase().ifBlank { "" })) }
                .onSuccess { onDone() }.onFailure { error = it.message }
            saving = false
        }
    }
}

@Composable
fun ProfileEditScreen(onboarding: Boolean, onDone: () -> Unit, onBack: () -> Unit, vm: ProfileEditViewModel = viewModel()) {
    Scaffold(topBar = { AppTopBar(if (onboarding) "Complete your profile" else "Edit profile", if (onboarding) null else onBack) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onboarding) Text("Tell us your name so we can personalise your experience.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Full name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vm.email, { vm.email = it }, label = { Text("Email (for invoices)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(vm.gstin, { vm.gstin = it.uppercase().take(15) }, label = { Text("GSTIN (optional, for business invoices)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.save(onDone) }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (vm.saving) "Saving…" else if (onboarding) "Continue" else "Save") }
            if (onboarding) TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip for now") }
        }
    }
}

// ---------------- Wishlist ----------------
class WishlistViewModel : ViewModel() {
    private val c = ShopApp.container
    var items by mutableStateOf<List<Product>?>(null)
    var error by mutableStateOf<String?>(null)
    fun load() { viewModelScope.launch { apiCall { c.api.wishlist() }.onSuccess { items = it }.onFailure { error = it.message } } }
    fun remove(p: Product) { viewModelScope.launch { apiCall { c.api.removeWishlist(p.id) }.onSuccess { items = items?.filter { it.id != p.id } } } }
}

@Composable
fun WishlistScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: WishlistViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    Scaffold(topBar = { AppTopBar("My wishlist", onBack) }) { pad ->
        val items = vm.items
        when {
            items == null && vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad))
            items == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            items.isEmpty() -> Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.FavoriteBorder, "Your wishlist is empty", "Tap the heart on any product to save it here", "Browse products") { navigate(Routes.HOME) } }
            else -> LazyVerticalGrid(GridCells.Adaptive(156.dp), Modifier.padding(pad), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { p -> ProductCard(p, onClick = { navigate(Routes.product(p.id)) }, onWishlist = { vm.remove(it) }) }
            }
        }
    }
}

// ---------------- Payment history ----------------
class PaymentsViewModel : ViewModel() {
    var items by mutableStateOf<List<PaymentRecord>?>(null)
    var error by mutableStateOf<String?>(null)
    init { load() }
    fun load() { viewModelScope.launch { apiCall { ShopApp.container.api.payments() }.onSuccess { items = it }.onFailure { error = it.message } } }
}

@Composable
fun PaymentsScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: PaymentsViewModel = viewModel()) {
    Scaffold(topBar = { AppTopBar("Payment history", onBack) }) { pad ->
        val items = vm.items
        when {
            items == null && vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad))
            items == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            items.isEmpty() -> Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.Payments, "No payments yet") }
            else -> LazyColumn(Modifier.padding(pad)) {
                items(items, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text("Order #${p.orderNumber}") },
                        supportingContent = { Text(formatDate(p.createdAt) + (p.providerPaymentId?.let { "\n$it" } ?: "")) },
                        trailingContent = { Column(horizontalAlignment = Alignment.End) { Text(inr(p.amount), fontWeight = FontWeight.Bold); StatusChip(p.status) } },
                        modifier = Modifier.clickable { navigate(Routes.order(p.orderId)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
