package com.yourstore.app.ui.address

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.yourstore.app.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.Address
import com.yourstore.app.data.model.AddressReq
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
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
    fun load() { viewModelScope.launch { apiCall { c.api.addresses() }.onSuccess { list = it }.onFailure { error = it.message } } }
    fun delete(a: Address) { viewModelScope.launch { apiCall { c.api.deleteAddress(a.id) }.onSuccess { load() }.onFailure { error = it.message } } }
}

@Composable
fun AddressListScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: AddressesViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    var confirm by remember { mutableStateOf<Address?>(null) }
    Scaffold(
        containerColor = Color.White,
        topBar = { AppTopBar("Address", onBack) },
        bottomBar = { PillButton("Add New Address", { navigate(Routes.addressForm()) }, icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) },
    ) { pad ->
        val list = vm.list
        when {
            list == null && vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad))
            list == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            list.isEmpty() -> Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.LocationOn, "No saved addresses", "Add an address for faster checkout") }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("Your locations", style = MaterialTheme.typography.titleMedium)
                    Text("Addresses you've saved for delivery.", color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                }
                items(list, key = { it.id }) { a ->
                    val def = a.isDefault == 1
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, if (def) Brand else Line, RoundedCornerShape(14.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.name, style = MaterialTheme.typography.titleSmall)
                                if (def) Text("Default", color = Brand, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(BrandSoft).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            Text(a.oneLine(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            Text("Phone: ${a.phone}", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.padding(top = 6.dp)) {
                                Text("Edit", color = Brand, fontSize = 13.sp, modifier = Modifier.clickable { navigate(Routes.addressForm(a.id)) }.padding(end = 16.dp, top = 4.dp, bottom = 4.dp))
                                Text("Delete", color = Warning, fontSize = 13.sp, modifier = Modifier.clickable { confirm = a }.padding(vertical = 4.dp))
                            }
                        }
                        Box(Modifier.size(54.dp).clip(CircleShape).background(if (def) BrandSoft else Tile).border(2.dp, if (def) Brand else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center) { Icon(Icons.Filled.LocationOn, null, tint = if (def) Brand else InkHint) }
                    }
                }
            }
        }
    }
    confirm?.let { a ->
        AlertDialog(onDismissRequest = { confirm = null }, containerColor = Color.White, title = { Text("Delete address?") }, text = { Text(a.oneLine(), color = InkMuted) },
            confirmButton = { TextButton(onClick = { vm.delete(a); confirm = null }) { Text("Delete", color = Warning) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = Ink) } })
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
            } else apiCall { c.api.profile() }.onSuccess { p -> if (name.isBlank()) name = p.name.orEmpty(); if (phone.isBlank()) phone = p.mobile }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressFormScreen(id: Int?, onBack: () -> Unit) {
    val vm: AddressFormViewModel = viewModel(key = "addr-$id") { AddressFormViewModel(id) }
    var stateOpen by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.White,
        topBar = { AppTopBar(if (id == null) "Add Address" else "Edit Address", onBack) },
        bottomBar = { PillButton("Confirm", { vm.save(onBack) }, loading = vm.saving, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Choose your location", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text("Enter the delivery address where you'd like your order delivered.", color = InkMuted, modifier = Modifier.offset(y = (-10).dp))
            KField("Full Name", vm.name, { vm.name = it }, "Recipient's name", Icons.Outlined.Person, error = vm.fieldError("name"))
            KField("Phone Number", vm.phone, { vm.phone = it.filter(Char::isDigit).take(10) }, "10-digit mobile number", Icons.Outlined.PhoneAndroid,
                keyboardType = KeyboardType.Phone, prefix = "+91 ", error = vm.fieldError("phone"))
            KField("House no., building, street", vm.line1, { vm.line1 = it }, "e.g. H.No 12, Sector 70", Icons.Outlined.Home, error = vm.fieldError("line1"))
            KField("Area, locality", vm.line2, { vm.line2 = it }, "Optional", Icons.Outlined.Map)
            KField("Landmark", vm.landmark, { vm.landmark = it }, "Optional", Icons.Outlined.Place)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KField("City", vm.city, { vm.city = it }, "City", error = vm.fieldError("city"), modifier = Modifier.weight(1f))
                KField("Pincode", vm.pincode, { vm.pincode = it.filter(Char::isDigit).take(6) }, "6 digits", keyboardType = KeyboardType.Number,
                    error = vm.fieldError("pincode"), modifier = Modifier.weight(1f))
            }
            Column {
                Text("State", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = stateOpen, onExpandedChange = { stateOpen = it }) {
                    OutlinedTextField(vm.state, {}, readOnly = true, placeholder = { Text("Select state", color = InkHint) }, isError = vm.fieldError("state") != null,
                        leadingIcon = { Icon(Icons.Outlined.LocationOn, null, tint = InkHint) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stateOpen) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Field, focusedContainerColor = Color.White, unfocusedBorderColor = Line, focusedBorderColor = Brand),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = stateOpen, onDismissRequest = { stateOpen = false }) {
                        INDIAN_STATES.forEach { st -> DropdownMenuItem(text = { Text(st) }, onClick = { vm.state = st; stateOpen = false }) }
                    }
                }
                vm.fieldError("state")?.let { Text(it, color = Warning, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(vm.isDefault, { vm.isDefault = it }, colors = CheckboxDefaults.colors(checkedColor = Brand))
                Text("Make this my default address")
            }
            vm.error?.let { Text(it, color = Warning) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
