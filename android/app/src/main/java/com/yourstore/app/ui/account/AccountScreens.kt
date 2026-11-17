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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.yourstore.app.ui.theme.*
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
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val me = vm.me
    Scaffold(containerColor = Color.White, topBar = { AppTopBar("Settings") }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Profile header
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(BrandLight, CircleShape), contentAlignment = Alignment.Center) {
                    Text((me?.name?.firstOrNull() ?: 'U').uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(me?.name ?: "Add your name", style = MaterialTheme.typography.titleMedium)
                    Text(me?.let { "+91 ${it.mobile}" } ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("General", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(Icons.Outlined.Person, "Edit Profile") { navigate(Routes.profileEdit()) }
                SettingRow(Icons.Outlined.LocationOn, "Saved Addresses") { navigate(Routes.ADDRESSES) }
                SettingRow(Icons.Outlined.LocalShipping, "My Orders") { navigate(Routes.ORDERS) }
                SettingRow(Icons.Outlined.Payments, "Payment History") { navigate(Routes.PAYMENTS) }
                SettingRow(Icons.Outlined.Language, "Language", value = "English") { }
            }
            Text("Preferences", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(Icons.Outlined.Shield, "Legal and Policies") { runCatching { uri.openUri(BuildConfig.API_BASE_URL + "privacy") } }
                SettingRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help & Support") { runCatching { uri.openUri("mailto:support@kutuku.app") } }
                SettingRow(Icons.AutoMirrored.Outlined.Logout, "Logout", tint = Warning) { confirmLogout = true }
                SettingRow(Icons.Outlined.DeleteForever, "Delete Account", tint = Warning) { confirmDelete = true }
            }
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 20.dp))
        }
    }
    if (confirmLogout) KDialog("Logout", "Are you sure you want to log out of your account?", "Yes, Logout", { confirmLogout = false; vm.logout() }) { confirmLogout = false }
    if (confirmDelete) KDialog("Delete your account?",
        "Your profile, addresses, cart and wishlist will be permanently deleted. Order records are kept for tax purposes. This cannot be undone.",
        "Delete", { confirmDelete = false; vm.deleteAccount() }) { confirmDelete = false }
}

/** Confirmation dialog in the kit's style (Logout sheet): title, message, red primary + outlined cancel. */
@Composable
private fun KDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, color = InkMuted) },
        confirmButton = { PillButton(confirm, onConfirm, color = Warning, modifier = Modifier.height(44.dp)) },
        dismissButton = { PillButton("Cancel", onDismiss, outlined = true, color = Ink, modifier = Modifier.height(44.dp)) },
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
    Scaffold(
        containerColor = Color.White,
        topBar = { AppTopBar(if (onboarding) "Complete Profile" else "Edit Profile", if (onboarding) null else onBack) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                PillButton(if (onboarding) "Continue" else "Save Changes", { vm.save(onDone) }, loading = vm.saving, modifier = Modifier.fillMaxWidth())
                if (onboarding) TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip for now", color = InkMuted) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).background(BrandLight, CircleShape), contentAlignment = Alignment.Center) {
                    Text((vm.name.firstOrNull() ?: 'U').uppercase(), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Brand)
                }
            }
            if (onboarding) Text("Tell us your name so we can personalise your experience.", color = InkMuted)
            KField("Full Name", vm.name, { vm.name = it }, "Enter your name", Icons.Outlined.Person)
            KField("Email (for invoices)", vm.email, { vm.email = it }, "Enter your email", Icons.Outlined.Email, keyboardType = KeyboardType.Email)
            KField("GSTIN (optional)", vm.gstin, { vm.gstin = it.uppercase().take(15) }, "For business invoices", Icons.Outlined.Business)
            vm.error?.let { Text(it, color = Warning) }
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
fun WishlistScreen(navigate: (String) -> Unit, onBack: (() -> Unit)?, vm: WishlistViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf<String?>(null) }
    Scaffold(containerColor = Color.White, topBar = { AppTopBar("My Favorite", onBack) }) { pad ->
        val all = vm.items
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search something...", color = InkHint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Ink) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Brand),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", "latest" to "Latest", "price_asc" to "Cheapest", "price_desc" to "Highest Price").forEach { (k, l) -> PillChip(l, sort == k, { sort = k }) }
            }
            when {
                all == null && vm.error != null -> ErrorState(vm.error!!, vm::load)
                all == null -> LoadingBox()
                all.isEmpty() -> EmptyState(Icons.Outlined.FavoriteBorder, "No favorites yet", "Tap the heart on any product to save it here", "Browse Products") { navigate(Routes.HOME) }
                else -> {
                    val shown = all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }.let { l ->
                        when (sort) {
                            "price_asc" -> l.sortedBy { it.price ?: 0.0 }
                            "price_desc" -> l.sortedByDescending { it.price ?: 0.0 }
                            else -> l
                        }
                    }
                    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(shown, key = { it.id }) { p -> ProductCard(p, onClick = { navigate(Routes.product(p.id)) }, onWishlist = { vm.remove(it) }) }
                    }
                }
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
    Scaffold(containerColor = Color.White, topBar = { AppTopBar("Payment History", onBack) }) { pad ->
        val items = vm.items
        when {
            items == null && vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad))
            items == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            items.isEmpty() -> Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.Payments, "No payments yet") }
            else -> LazyColumn(Modifier.padding(pad)) {
                items(items, key = { it.id }) { p ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.White),
                        headlineContent = { Text("Order #${p.orderNumber}", style = MaterialTheme.typography.titleSmall) },
                        supportingContent = { Text(formatDate(p.createdAt) + (p.providerPaymentId?.let { "\n$it" } ?: "")) },
                        trailingContent = { Column(horizontalAlignment = Alignment.End) { Text(inr(p.amount), fontWeight = FontWeight.Bold); StatusChip(p.status) } },
                        modifier = Modifier.clickable { navigate(Routes.order(p.orderId)) },
                    )
                    HorizontalDivider(color = Line)
                }
            }
        }
    }
}
