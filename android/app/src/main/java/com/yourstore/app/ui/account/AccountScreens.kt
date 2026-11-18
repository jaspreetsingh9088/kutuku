package com.yourstore.app.ui.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.R
import com.yourstore.app.ShopApp
import com.yourstore.app.data.Prefs
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.*
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

val LANGUAGES = listOf("en" to "English", "id" to "Bahasa Indonesia", "zh" to "Chinese", "de" to "Deutsch", "hi" to "हिन्दी (Hindi)")
private val FLAGS = mapOf("en" to "🇬🇧", "id" to "🇮🇩", "zh" to "🇨🇳", "de" to "🇩🇪", "hi" to "🇮🇳")
fun languageName(code: String) = LANGUAGES.firstOrNull { it.first == code }?.second ?: "English"

// ============================================================ shared profile state
class ProfileViewModel : ViewModel() {
    private val c = ShopApp.container
    var profile by mutableStateOf<Customer?>(null)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun load() { viewModelScope.launch { apiCall { c.api.profile() }.onSuccess { profile = it }.onFailure { if (profile == null) error = it.message } } }

    fun save(name: String, email: String?, onDone: () -> Unit) {
        busy = true
        viewModelScope.launch {
            apiCall { c.api.updateProfile(ProfileReq(name.trim(), email?.trim()?.ifBlank { null }, profile?.gstin)) }
                .onSuccess { profile = it; message = "Profile updated"; onDone() }.onFailure { message = it.message }
            busy = false
        }
    }

    fun uploadAvatar(bytes: ByteArray, mime: String) {
        busy = true
        viewModelScope.launch {
            val part = MultipartBody.Part.createFormData("file", "avatar.${mime.substringAfter('/')}", bytes.toRequestBody(mime.toMediaTypeOrNull()))
            apiCall { c.api.uploadAvatar(part) }.onSuccess { profile = profile?.copy(avatarUrl = it.avatarUrl); message = "Photo updated" }.onFailure { message = it.message }
            busy = false
        }
    }

    fun changePassword(current: String?, password: String, onDone: () -> Unit) {
        busy = true
        viewModelScope.launch {
            apiCall { c.api.changePassword(ChangePasswordReq(current?.ifBlank { null }, password)) }
                .onSuccess { profile = profile?.copy(hasPassword = true); message = "Password changed"; onDone() }.onFailure { message = it.message }
            busy = false
        }
    }

    fun setNotifications(payment: Boolean, tracking: Boolean, complete: Boolean, general: Boolean) {
        val p = profile ?: return
        profile = p.copy(notifPayment = if (payment) 1 else 0, notifTracking = if (tracking) 1 else 0, notifComplete = if (complete) 1 else 0, notifGeneral = if (general) 1 else 0)
        viewModelScope.launch {
            apiCall { c.api.notificationSettings(NotificationSettingsReq(payment, tracking, complete, general)) }.onFailure { message = it.message; profile = p }
        }
    }

    fun logout(onDone: () -> Unit) = viewModelScope.launch { apiCall { c.api.logout() }; c.clearSession(); onDone() }

    fun deleteAccount(onDone: () -> Unit) = viewModelScope.launch {
        apiCall { c.api.deleteAccount() }.onSuccess { c.clearSession(); onDone() }.onFailure { message = it.message }
    }
}

/** Top bar used by every settings frame: "←", centred title, "⋮". */
@Composable
private fun SettingsBar(title: String, onBack: (() -> Unit)?, menu: @Composable (() -> Unit)? = null) {
    KTopBar(title, onBack = onBack, arrowBack = true, divider = true) {
        if (menu != null) menu() else Icon(Icons.Filled.MoreVert, null, Modifier.padding(12.dp).size(22.dp), tint = Ink)
    }
}

// ============================================================ Settings (My Profile tab)
@Composable
fun SettingsScreen(navigate: (String) -> Unit, onLoggedOut: () -> Unit, vm: ProfileViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    var showLogout by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    SettingsContent(
        language = languageName(Prefs.language), navigate = navigate, onLogout = { showLogout = true }, onDelete = { showDelete = true },
        logoutVisible = showLogout, onDismissLogout = { showLogout = false }, onConfirmLogout = { showLogout = false; vm.logout(onLoggedOut) },
    )
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false }, containerColor = Color.White,
        title = { Text("Delete your account?") },
        text = { Text("Your profile, addresses and cart will be removed. Past orders are kept for tax records. This can't be undone.", color = InkMuted) },
        confirmButton = { TextButton(onClick = { showDelete = false; vm.deleteAccount(onLoggedOut) }) { Text("Delete", color = Warning) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel", color = Ink) } },
    )
}

@Composable
fun SettingsContent(
    language: String, navigate: (String) -> Unit, onLogout: () -> Unit, onDelete: () -> Unit,
    logoutVisible: Boolean = false, onDismissLogout: () -> Unit = {}, onConfirmLogout: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Settings", null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Ink) }
                DropdownMenu(menu, { menu = false }, Modifier.background(Color.White)) {
                    DropdownMenuItem(text = { Text("My Addresses") }, onClick = { menu = false; navigate(Routes.addresses(choose = false)) })
                    DropdownMenuItem(text = { Text("Payment History") }, onClick = { menu = false; navigate(Routes.PAYMENTS) })
                    DropdownMenuItem(text = { Text("Messages") }, onClick = { menu = false; navigate(Routes.MESSAGES) })
                    DropdownMenuItem(text = { Text("Delete Account", color = Warning) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePad)) {
            Spacer(Modifier.height(24.dp))
            Text("General", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingRow(Icons.Outlined.Person, "Edit Profile") { navigate(Routes.PROFILE_EDIT) }
                SettingRow(Icons.Outlined.Lock, "Change Password") { navigate(Routes.CHANGE_PASSWORD) }
                SettingRow(Icons.Outlined.NotificationsNone, "Notifications") { navigate(Routes.NOTIF_SETTINGS) }
                SettingRow(Icons.Outlined.Shield, "Security") { navigate(Routes.SECURITY) }
                SettingRow(Icons.Outlined.Language, "Language", value = language) { navigate(Routes.LANGUAGE) }
            }
            Spacer(Modifier.height(28.dp))
            Text("Preferences", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingRow(Icons.Outlined.Policy, "Legal and Policies") { navigate(Routes.LEGAL) }
                SettingRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help & Support") { navigate(Routes.HELP) }
                SettingRow(Icons.AutoMirrored.Outlined.Logout, "Logout", tint = Warning, chevron = false, onClick = onLogout)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (logoutVisible) LogoutDialog(onDismissLogout, onConfirmLogout)
}

@Composable
fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { LogoutCard(onDismiss, onConfirm) }
}

@Composable
fun LogoutCard(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Close, "Close", Modifier.align(Alignment.End).size(24.dp).clickable(onClick = onDismiss), tint = Ink)
        Spacer(Modifier.height(4.dp))
        Text("Are you sure you want to\nlogout?", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink, textAlign = TextAlign.Center, lineHeight = 24.sp)
        Spacer(Modifier.height(24.dp))
        KButton("Cancel", onDismiss, Modifier.width(180.dp), height = 44.dp, fontSize = 14)
        Spacer(Modifier.height(10.dp))
        TextLink("Log Out", onConfirm, color = Warning, fontSize = 14)
        Spacer(Modifier.height(8.dp))
    }
}

// ============================================================ Edit Profile
@Composable
fun EditProfileScreen(onBack: () -> Unit, snack: (String) -> Unit, vm: ProfileViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) { vm.message?.let { snack(it); vm.message = null } }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (bytes.size > 4_000_000) snack("Please choose a photo under 4 MB") else vm.uploadAvatar(bytes, mime)
        }.onFailure { snack("Could not read that photo") }
    }
    val p = vm.profile
    if (p == null) {
        Column(Modifier.fillMaxSize().background(Color.White)) { SettingsBar("Edit Profile", onBack); if (vm.error != null) ErrorState(vm.error!!, { vm.load() }) else LoadingBox() }
        return
    }
    var name by remember(p.id) { mutableStateOf(p.name.orEmpty()) }
    var contact by remember(p.id) { mutableStateOf(p.email ?: p.mobile.orEmpty()) }
    EditProfileContent(
        avatarUrl = p.avatarUrl, name = name, onName = { name = it }, contact = contact, onContact = { contact = it },
        contactEditable = p.email == null || p.mobile != null, linkedGoogle = p.email?.endsWith("@gmail.com") == true, busy = vm.busy,
        onPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onSave = {
            if (name.trim().length < 2) snack("Enter your name")
            else vm.save(name, if (contact.contains('@')) contact else p.email) { onBack() }
        },
        onBack = onBack,
    )
}

@Composable
fun EditProfileContent(
    avatarUrl: String?, name: String, onName: (String) -> Unit, contact: String, onContact: (String) -> Unit, contactEditable: Boolean,
    linkedGoogle: Boolean, busy: Boolean, onPhoto: () -> Unit, onSave: () -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Edit Profile", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PagePad)) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onPhoto)) {
                Avatar(avatarUrl, name.ifBlank { "K" }, 104.dp)
                Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(Brand).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CameraAlt, "Change photo", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            KField(name, onName, "Your name", label = "Username", icon = Icons.Outlined.Person)
            Spacer(Modifier.height(24.dp))
            KField(contact, onContact, "Enter your email", label = "Email or Phone Number", icon = Icons.Outlined.Email, keyboardType = KeyboardType.Email,
                readOnly = !contactEditable)
            Spacer(Modifier.height(24.dp))
            Text("Account Linked With", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFF1F1F5), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_google), null, Modifier.size(22.dp), tint = Color.Unspecified)
                Spacer(Modifier.width(12.dp))
                Text("Google", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                Text(if (linkedGoogle) "" else "Not linked", fontSize = 12.sp, color = InkHint)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Link, null, Modifier.size(20.dp), tint = if (linkedGoogle) Brand else InkHint)
            }
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = PagePad, vertical = 16.dp)) { KButton("Save Changes", onSave, Modifier.fillMaxWidth(), loading = busy) }
    }
}

// ============================================================ Change Password
@Composable
fun ChangePasswordScreen(onBack: () -> Unit, snack: (String) -> Unit, vm: ProfileViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) { vm.message?.let { snack(it); vm.message = null } }
    var current by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ChangePasswordContent(
        needsCurrent = vm.profile?.hasPassword == true, current = current, onCurrent = { current = it; error = null },
        pw = pw, onPw = { pw = it; error = null }, confirm = confirm, onConfirm = { confirm = it; error = null }, error = error, busy = vm.busy,
        onSubmit = {
            error = when {
                vm.profile?.hasPassword == true && current.isBlank() -> "Enter your current password"
                pw.length < 8 -> "Password must be at least 8 characters"
                pw != confirm -> "Passwords do not match"
                else -> null
            }
            if (error == null) vm.changePassword(current, pw, onBack)
        },
        onBack = onBack,
    )
}

@Composable
fun ChangePasswordContent(
    needsCurrent: Boolean, current: String, onCurrent: (String) -> Unit, pw: String, onPw: (String) -> Unit, confirm: String, onConfirm: (String) -> Unit,
    error: String?, busy: Boolean, onSubmit: () -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Change Password", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PagePad)) {
            Spacer(Modifier.height(24.dp))
            if (needsCurrent) {
                KField(current, onCurrent, "Enter current password", label = "Current Password", icon = Icons.Outlined.Lock, isPassword = true)
                Spacer(Modifier.height(24.dp))
            }
            KField(pw, onPw, "Enter new password", label = "New Password", icon = Icons.Outlined.Lock, isPassword = true)
            Spacer(Modifier.height(24.dp))
            KField(confirm, onConfirm, "Confirm your new password", label = "Confirm Password", icon = Icons.Outlined.Lock, isPassword = true,
                imeAction = ImeAction.Done, onIme = onSubmit)
            if (error != null) Text(error, color = Warning, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = PagePad, vertical = 16.dp)) { KButton("Change Now", onSubmit, Modifier.fillMaxWidth(), loading = busy) }
    }
}

// ============================================================ Language
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(Prefs.language) }
    var query by remember { mutableStateOf("") }
    LanguageContent(selected, query, { query = it }, { selected = it; Prefs.language = it }, onBack)
}

@Composable
fun LanguageContent(selected: String, query: String, onQuery: (String) -> Unit, onSelect: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Language", onBack)
        Column(Modifier.padding(horizontal = PagePad)) {
            Spacer(Modifier.height(16.dp))
            KSearchBar(query, onQuery, placeholder = "Search language", filled = true)
            Spacer(Modifier.height(24.dp))
            LANGUAGES.filter { query.isBlank() || it.second.contains(query, true) }.forEach { (code, name) ->
                val on = code == selected
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).height(52.dp).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, if (on) Brand else Color(0xFFF1F1F5), RoundedCornerShape(10.dp)).clickable { onSelect(code) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(FLAGS[code] ?: "🏳️", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Filled.Check, null, tint = Brand, modifier = Modifier.size(18.dp))
                }
            }
            Text("The app is currently shown in English; your choice is saved for when more languages are added.", fontSize = 12.sp, color = InkHint,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ============================================================ Toggle cards (Notifications, Security)
@Composable
private fun ToggleCard(rows: List<Triple<String, Boolean, (Boolean) -> Unit>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp)) {
        rows.forEachIndexed { i, (label, on, set) ->
            Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 17.sp, color = Ink, modifier = Modifier.weight(1f))
                KSwitch(on, set)
            }
            if (i < rows.lastIndex) HorizontalDivider(color = Line)
        }
    }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, snack: (String) -> Unit, vm: ProfileViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) { vm.message?.let { snack(it); vm.message = null } }
    val p = vm.profile
    NotificationSettingsContent(
        p?.notifPayment != 0, p?.notifTracking != 0, p?.notifComplete != 0, p?.notifGeneral != 0, loaded = p != null,
        onChange = { a, b, c, d -> vm.setNotifications(a, b, c, d) }, onBack = onBack,
    )
}

@Composable
fun NotificationSettingsContent(
    payment: Boolean, tracking: Boolean, complete: Boolean, general: Boolean, loaded: Boolean,
    onChange: (Boolean, Boolean, Boolean, Boolean) -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Notifications", onBack)
        if (!loaded) LoadingBox() else Column(Modifier.padding(PagePad)) {
            ToggleCard(listOf(
                Triple("Payment", payment) { v: Boolean -> onChange(v, tracking, complete, general) },
                Triple("Tracking", tracking) { v: Boolean -> onChange(payment, v, complete, general) },
                Triple("Complete Order", complete) { v: Boolean -> onChange(payment, tracking, v, general) },
                Triple("Notification", general) { v: Boolean -> onChange(payment, tracking, complete, v) },
            ))
        }
    }
}

@Composable
fun SecurityScreen(onBack: () -> Unit, snack: (String) -> Unit) {
    val context = LocalContext.current
    var face by remember { mutableStateOf(Prefs.faceUnlock) }
    var rememberPw by remember { mutableStateOf(Prefs.rememberLogin) }
    var touch by remember { mutableStateOf(Prefs.fingerprintUnlock) }
    fun canUseBiometric(): Boolean {
        val bm = androidx.biometric.BiometricManager.from(context)
        val ok = bm.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        if (!ok) snack("Set up face or fingerprint unlock in your phone settings first")
        return ok
    }
    SecurityContent(face, rememberPw, touch,
        onFace = { if (!it || canUseBiometric()) { face = it; Prefs.faceUnlock = it } },
        onRemember = { rememberPw = it; Prefs.rememberLogin = it; if (!it) Prefs.rememberedIdentifier = "" },
        onTouch = { if (!it || canUseBiometric()) { touch = it; Prefs.fingerprintUnlock = it } },
        onBack = onBack)
}

@Composable
fun SecurityContent(face: Boolean, rememberPw: Boolean, touch: Boolean, onFace: (Boolean) -> Unit, onRemember: (Boolean) -> Unit, onTouch: (Boolean) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Security", onBack)
        Column(Modifier.padding(PagePad)) {
            ToggleCard(listOf(Triple("Face ID", face, onFace), Triple("Remember Password", rememberPw, onRemember), Triple("Touch ID", touch, onTouch)))
            Spacer(Modifier.height(14.dp))
            Text("Face ID and Touch ID ask for your phone's face or fingerprint unlock each time the app opens. Remember Password keeps your email or phone filled in on the login screen.",
                fontSize = 12.sp, color = InkHint, lineHeight = 18.sp)
        }
    }
}

// ============================================================ Help and Support
class ContentViewModel : ViewModel() {
    var faqs by mutableStateOf<List<Faq>?>(null)
    var page by mutableStateOf<ContentPage?>(null)
    var error by mutableStateOf<String?>(null)
    fun loadFaqs() = viewModelScope.launch { apiCall { ShopApp.container.api.faqs() }.onSuccess { faqs = it }.onFailure { error = it.message } }
    fun loadPage(slug: String) = viewModelScope.launch { apiCall { ShopApp.container.api.page(slug) }.onSuccess { page = it }.onFailure { error = it.message } }
}

@Composable
fun HelpScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: ContentViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.loadFaqs() }
    var query by remember { mutableStateOf("") }
    HelpContent(vm.faqs, vm.error, query, { query = it }, onBack, onRetry = { vm.loadFaqs() }, onContact = { navigate(Routes.MESSAGES) })
}

@Composable
fun HelpContent(faqs: List<Faq>?, error: String?, query: String, onQuery: (String) -> Unit, onBack: () -> Unit, onRetry: () -> Unit, onContact: () -> Unit, initiallyOpen: Int? = null) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Help and Support", onBack)
        when {
            faqs == null && error != null -> ErrorState(error, onRetry)
            faqs == null -> LoadingBox()
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = PagePad, vertical = 16.dp)) {
                item { KSearchBar(query, onQuery, placeholder = "Search help", filled = true); Spacer(Modifier.height(12.dp)) }
                items(faqs.filter { query.isBlank() || it.question.contains(query, true) || it.answer.contains(query, true) }, key = { it.id }) { f ->
                    Column(Modifier.fillMaxWidth().clickable { open = if (open == f.id) null else f.id }.padding(vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.question, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
                            Icon(if (open == f.id) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Ink)
                        }
                        if (open == f.id) Text(f.answer, fontSize = 13.sp, color = InkMuted, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                    HorizontalDivider(color = Line)
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    KOutlinedButton("Still need help? Chat with us", onContact, Modifier.fillMaxWidth(), color = Brand, borderColor = BrandLight)
                }
            }
        }
    }
}

// ============================================================ Legal and Policies
@Composable
fun LegalScreen(onBack: () -> Unit, vm: ContentViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.loadPage("terms") }
    LegalContent(vm.page, vm.error, onBack, onRetry = { vm.loadPage("terms") })
}

@Composable
fun LegalContent(page: ContentPage?, error: String?, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Legal and Policies", onBack)
        when {
            page == null && error != null -> ErrorState(error, onRetry)
            page == null -> LoadingBox()
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePad, vertical = 20.dp)) {
                page.content.lines().forEach { raw ->
                    val line = raw.trim()
                    when {
                        line.startsWith("#") -> Text(line.trimStart('#').trim(), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink,
                            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
                        line.isBlank() -> Spacer(Modifier.height(8.dp))
                        else -> Text(line, fontSize = 13.sp, color = InkMuted, lineHeight = 22.sp)
                    }
                }
            }
        }
    }
}

// ============================================================ My Favorite
class WishlistViewModel : ViewModel() {
    private val c = ShopApp.container
    var items by mutableStateOf<List<Product>?>(null)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var category by mutableStateOf<String?>(null)
    var sort by mutableStateOf("latest")
    fun load() = viewModelScope.launch { apiCall { c.api.wishlist() }.onSuccess { items = it }.onFailure { if (items == null) error = it.message } }
    fun remove(p: Product) = viewModelScope.launch { apiCall { c.api.removeWishlist(p.id) }.onSuccess { items = items?.filter { it.id != p.id } } }
}

@Composable
fun WishlistScreen(navigate: (String) -> Unit, vm: WishlistViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    WishlistContent(vm.items, vm.error, vm.query, { vm.query = it }, vm.category, { vm.category = it }, vm.sort, { vm.sort = it },
        onRetry = { vm.load() }, onProduct = { navigate(Routes.product(it.id)) }, onRemove = { vm.remove(it) },
        onNotifications = { navigate(Routes.NOTIFICATIONS) }, onShop = { navigate(Routes.HOME) })
}

@Composable
fun WishlistContent(
    items: List<Product>?, error: String?, query: String, onQuery: (String) -> Unit, category: String?, onCategory: (String?) -> Unit,
    sort: String, onSort: (String) -> Unit, onRetry: () -> Unit, onProduct: (Product) -> Unit, onRemove: (Product) -> Unit,
    onNotifications: () -> Unit, onShop: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        KTopBar("My Favorite") { IconBtn(Icons.Outlined.NotificationsNone, "Notifications", onNotifications) }
        Box(Modifier.padding(horizontal = PagePad)) {
            KSearchBar(query, onQuery, placeholder = "Search something...", onFilter = { sortMenu = true })
            Box(Modifier.align(Alignment.TopEnd)) {
                DropdownMenu(sortMenu, { sortMenu = false }, Modifier.background(Color.White)) {
                    listOf("latest" to "Recently added", "price_asc" to "Price: Low to High", "price_desc" to "Price: High to Low").forEach { (k, l) ->
                        DropdownMenuItem(text = { Text(l, color = if (k == sort) Brand else Ink) }, onClick = { onSort(k); sortMenu = false })
                    }
                }
            }
        }
        when {
            items == null && error != null -> ErrorState(error, onRetry)
            items == null -> LoadingBox()
            items.isEmpty() -> EmptyState(Icons.Outlined.FavoriteBorder, "No favorites yet", "Tap the heart on any product to save it here.", "Start Shopping", onShop)
            else -> {
                val cats = items.mapNotNull { it.categoryName }.distinct()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PagePad, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KChip("All", category == null, { onCategory(null) })
                    cats.forEach { c -> KChip(c, category == c, { onCategory(c) }) }
                }
                val shown = items.filter { (category == null || it.categoryName == category) && (query.isBlank() || it.name.contains(query, true)) }
                    .let { l -> when (sort) { "price_asc" -> l.sortedBy { it.price ?: 0.0 }; "price_desc" -> l.sortedByDescending { it.price ?: 0.0 }; else -> l } }
                LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(start = PagePad, end = PagePad, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(shown, key = { it.id }) { p -> ProductCard(p.copy(wishlisted = true), { onProduct(p) }, onWishlist = onRemove) }
                    if (shown.isEmpty()) item(span = { GridItemSpan(2) }) { Text("Nothing matches your search.", color = InkHint, fontSize = 14.sp) }
                }
            }
        }
    }
}

// ============================================================ Payment history
class PaymentsViewModel : ViewModel() {
    var items by mutableStateOf<List<PaymentRecord>?>(null)
    var error by mutableStateOf<String?>(null)
    fun load() = viewModelScope.launch { apiCall { ShopApp.container.api.payments() }.onSuccess { items = it }.onFailure { error = it.message } }
}

@Composable
fun PaymentsScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: PaymentsViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val items = vm.items
    Column(Modifier.fillMaxSize().background(Color.White)) {
        SettingsBar("Payment History", onBack)
        when {
            items == null && vm.error != null -> ErrorState(vm.error!!, { vm.load() })
            items == null -> LoadingBox()
            items.isEmpty() -> EmptyState(Icons.Outlined.ShoppingBag, "No payments yet", "Payments for your orders will appear here.")
            else -> LazyColumn(contentPadding = PaddingValues(PagePad), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Field).clickable { navigate(Routes.order(p.orderId)) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Order #${p.orderNumber}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Text(formatDate(p.createdAt), fontSize = 12.sp, color = InkHint, modifier = Modifier.padding(top = 4.dp))
                            p.providerPaymentId?.let { Text(it, fontSize = 11.sp, color = InkHint) }
                        }
                        Column(horizontalAlignment = Alignment.End) { PriceTag(p.amount, size = 16); Spacer(Modifier.height(4.dp)); StatusChip(p.status) }
                    }
                }
            }
        }
    }
}
