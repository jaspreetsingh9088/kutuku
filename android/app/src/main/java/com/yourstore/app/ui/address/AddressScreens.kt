package com.yourstore.app.ui.address

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Address
import com.yourstore.app.data.model.AddressReq
import com.yourstore.app.ui.checkout.MapThumb
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.launch

val INDIAN_STATES = listOf(
    "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chandigarh", "Chhattisgarh",
    "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand",
    "Karnataka", "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland",
    "Odisha", "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
)

class AddressesViewModel : ViewModel() {
    private val c = ShopApp.container
    var list by mutableStateOf<List<Address>?>(null)
    var error by mutableStateOf<String?>(null)
    var selectedId by mutableStateOf<Int?>(null)
    var query by mutableStateOf("")
    fun load() {
        viewModelScope.launch {
            apiCall { c.api.addresses() }.onSuccess { l ->
                list = l
                if (selectedId == null || l.none { it.id == selectedId }) selectedId = (l.firstOrNull { it.isDefault == 1 } ?: l.firstOrNull())?.id
            }.onFailure { error = it.message }
        }
    }
    fun delete(a: Address) { viewModelScope.launch { apiCall { c.api.deleteAddress(a.id) }.onSuccess { load() }.onFailure { error = it.message } } }
}

/** "Address" frame: search box, "Select location" cards with a round map, and Confirm. */
@Composable
fun AddressListScreen(navigate: (String) -> Unit, onBack: () -> Unit, onConfirm: (Int) -> Unit, vm: AddressesViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    var confirmDelete by remember { mutableStateOf<Address?>(null) }
    AddressListContent(
        list = vm.list, error = vm.error, query = vm.query, onQuery = { vm.query = it }, selectedId = vm.selectedId, onSelect = { vm.selectedId = it.id },
        onEdit = { navigate(Routes.addressForm(it.id)) }, onDelete = { confirmDelete = it }, onAdd = { navigate(Routes.addressForm()) },
        onConfirm = { vm.selectedId?.let(onConfirm) }, onBack = onBack, onRetry = { vm.load() },
    )
    confirmDelete?.let { a ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, containerColor = Color.White, title = { Text("Delete address?") }, text = { Text(a.oneLine(), color = InkMuted) },
            confirmButton = { TextButton(onClick = { vm.delete(a); confirmDelete = null }) { Text("Delete", color = Warning) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = Ink) } })
    }
}

@Composable
fun AddressListContent(
    list: List<Address>?, error: String?, query: String, onQuery: (String) -> Unit, selectedId: Int?, onSelect: (Address) -> Unit,
    onEdit: (Address) -> Unit, onDelete: (Address) -> Unit, onAdd: () -> Unit, onConfirm: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        KTopBar("Address", onBack = onBack)
        when {
            list == null && error != null -> ErrorState(error, onRetry)
            list == null -> LoadingBox()
            else -> {
                val shown = list.filter { query.isBlank() || it.oneLine().contains(query, true) || it.name.contains(query, true) }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = PagePad, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("Choose your location", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(10.dp))
                        Text("Let's find where your order should go. Choose a location below to get started.", fontSize = 14.sp, color = InkHint, lineHeight = 22.sp)
                        Spacer(Modifier.height(24.dp))
                        KField(query, onQuery, "Search your saved locations", icon = Icons.Outlined.LocationOn,
                            trailing = { Icon(Icons.Outlined.GpsFixed, null, Modifier.size(22.dp), tint = InkMuted) })
                        Spacer(Modifier.height(16.dp))
                        Text("Select location", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    if (list.isEmpty()) item { Text("You have no saved addresses yet. Add one to continue.", color = InkMuted, fontSize = 14.sp) }
                    items(shown, key = { it.id }) { a -> LocationCard(a, a.id == selectedId, { onSelect(a) }, { onEdit(a) }, { onDelete(a) }) }
                    item {
                        KOutlinedButton("Add New Address", onAdd, Modifier.fillMaxWidth(), color = Brand, borderColor = BrandLight,
                            leading = { Icon(Icons.Filled.Add, null, tint = Brand, modifier = Modifier.size(20.dp)) })
                    }
                }
                Box(Modifier.navigationBarsPadding().padding(horizontal = PagePad, vertical = 16.dp)) {
                    KButton("Confirm", onConfirm, Modifier.fillMaxWidth(), enabled = selectedId != null)
                }
            }
        }
    }
}

@Composable
private fun LocationCard(a: Address, selected: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, if (selected) Brand else Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.name.ifBlank { a.city }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(a.oneLine(), fontSize = 12.sp, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Row(Modifier.padding(top = 6.dp)) {
                TextLink("Edit", onEdit, fontSize = 12)
                Spacer(Modifier.width(14.dp))
                TextLink("Delete", onDelete, color = Warning, fontSize = 12)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(70.dp).clip(CircleShape).border(if (selected) 3.dp else 1.dp, if (selected) Brand else Line, CircleShape).padding(4.dp)) {
            MapThumb(Modifier.fillMaxSize().clip(CircleShape), pinColor = if (selected) Color(0xFF14B8A6) else Brand)
        }
    }
}

class AddressFormViewModel(private val id: Int?) : ViewModel() {
    private val c = ShopApp.container
    var name by mutableStateOf(""); var phone by mutableStateOf(""); var line1 by mutableStateOf(""); var line2 by mutableStateOf("")
    var landmark by mutableStateOf(""); var city by mutableStateOf(""); var state by mutableStateOf(""); var pincode by mutableStateOf("")
    var isDefault by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var submitted by mutableStateOf(false)

    init {
        viewModelScope.launch {
            if (id != null) apiCall { c.api.addresses() }.onSuccess { list ->
                list.firstOrNull { it.id == id }?.let { a ->
                    name = a.name; phone = a.phone; line1 = a.line1; line2 = a.line2.orEmpty(); landmark = a.landmark.orEmpty()
                    city = a.city; state = a.state; pincode = a.pincode; isDefault = a.isDefault == 1
                }
            } else apiCall { c.api.profile() }.onSuccess { p -> if (name.isBlank()) name = p.name.orEmpty(); if (phone.isBlank()) phone = p.mobile.orEmpty() }
        }
    }

    fun fieldError(field: String): String? {
        if (!submitted) return null
        return when (field) {
            "name" -> if (name.trim().length < 2) "Enter the recipient's name" else null
            "phone" -> if (!Regex("^[6-9]\\d{9}$").matches(phone)) "Enter a valid 10-digit number" else null
            "line1" -> if (line1.trim().length < 3) "Enter house no. and street" else null
            "city" -> if (city.trim().length < 2) "Enter city" else null
            "state" -> if (state.isBlank()) "Select state" else null
            "pincode" -> if (!Regex("^\\d{6}$").matches(pincode)) "Enter a valid 6-digit pincode" else null
            else -> null
        }
    }

    fun save(onDone: () -> Unit) {
        submitted = true
        if (listOf("name", "phone", "line1", "city", "state", "pincode").any { fieldError(it) != null }) return
        saving = true; error = null
        val body = AddressReq(name.trim(), phone, line1.trim(), line2.trim().ifBlank { null }, landmark.trim().ifBlank { null }, city.trim(), state, pincode, isDefault)
        viewModelScope.launch {
            apiCall { if (id == null) c.api.addAddress(body) else c.api.updateAddress(id, body) }.onSuccess { onDone() }.onFailure { error = it.message }
            saving = false
        }
    }
}

@Composable
fun AddressFormScreen(id: Int?, onBack: () -> Unit) {
    val vm: AddressFormViewModel = viewModel(key = "addr-$id") { AddressFormViewModel(id) }
    var stateOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        KTopBar(if (id == null) "Add Address" else "Edit Address", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = PagePad), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(4.dp))
            KField(vm.name, { vm.name = it }, "Recipient's name", label = "Full Name", icon = Icons.Outlined.Person, error = vm.fieldError("name"))
            KField(vm.phone, { vm.phone = it.filter(Char::isDigit).take(10) }, "10-digit mobile number", label = "Phone Number", icon = Icons.Outlined.PhoneAndroid,
                keyboardType = KeyboardType.Phone, error = vm.fieldError("phone"))
            KField(vm.line1, { vm.line1 = it }, "e.g. H.No 12, Sector 70", label = "House no., building, street", icon = Icons.Outlined.Home, error = vm.fieldError("line1"))
            KField(vm.line2, { vm.line2 = it }, "Optional", label = "Area, locality", icon = Icons.Outlined.Map)
            KField(vm.landmark, { vm.landmark = it }, "Optional", label = "Landmark", icon = Icons.Outlined.Place)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KField(vm.city, { vm.city = it }, "City", label = "City", error = vm.fieldError("city"), modifier = Modifier.weight(1f))
                KField(vm.pincode, { vm.pincode = it.filter(Char::isDigit).take(6) }, "6 digits", label = "Pincode", keyboardType = KeyboardType.Number,
                    error = vm.fieldError("pincode"), modifier = Modifier.weight(1f))
            }
            Box {
                KField(vm.state, {}, "Select state", label = "State", icon = Icons.Outlined.LocationOn, readOnly = true, error = vm.fieldError("state"))
                Box(Modifier.matchParentSize().clickable { stateOpen = true })
                DropdownMenu(stateOpen, { stateOpen = false }, Modifier.heightIn(max = 360.dp).background(Color.White)) {
                    INDIAN_STATES.forEach { st -> DropdownMenuItem(text = { Text(st) }, onClick = { vm.state = st; stateOpen = false }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.isDefault = !vm.isDefault }) {
                KCheckbox(vm.isDefault, { vm.isDefault = it })
                Spacer(Modifier.width(12.dp))
                Text("Make this my default address", fontSize = 14.sp, color = Ink)
            }
            vm.error?.let { Text(it, color = Warning, fontSize = 13.sp) }
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = PagePad, vertical = 16.dp)) {
            KButton("Save Address", { vm.save(onBack) }, Modifier.fillMaxWidth(), loading = vm.saving)
        }
    }
}
