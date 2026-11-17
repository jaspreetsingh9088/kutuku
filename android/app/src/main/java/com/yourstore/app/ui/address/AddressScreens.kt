package com.yourstore.app.ui.address

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.LocationOn
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
        topBar = { AppTopBar("Saved addresses", onBack) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { navigate(Routes.addressForm()) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add address") }) },
    ) { pad ->
        val list = vm.list
        when {
            list == null && vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.padding(pad))
            list == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            list.isEmpty() -> Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.LocationOn, "No saved addresses", "Add an address for faster checkout") }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { a ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (a.isDefault == 1) AssistChip(onClick = {}, label = { Text("Default") })
                            }
                            Text(a.oneLine())
                            Text("Phone: ${a.phone}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                TextButton(onClick = { navigate(Routes.addressForm(a.id)) }) { Text("Edit") }
                                TextButton(onClick = { confirm = a }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
    confirm?.let { a ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Delete address?") }, text = { Text(a.oneLine()) },
            confirmButton = { TextButton(onClick = { vm.delete(a); confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
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
        topBar = { AppTopBar(if (id == null) "Add address" else "Edit address", onBack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(onClick = { vm.save(onBack) }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(50.dp)) {
                    Text(if (vm.saving) "Saving…" else "Save address")
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            @Composable fun F(label: String, value: String, key: String, kb: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) =
                OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = vm.fieldError(key) != null, supportingText = vm.fieldError(key)?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = kb))
            F("Full name *", vm.name, "name") { vm.name = it }
            F("Phone number *", vm.phone, "phone", KeyboardType.Phone) { vm.phone = it.filter(Char::isDigit).take(10) }
            F("House no., building, street *", vm.line1, "line1") { vm.line1 = it }
            F("Area, locality", vm.line2, "line2") { vm.line2 = it }
            F("Landmark", vm.landmark, "landmark") { vm.landmark = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { F("City *", vm.city, "city") { vm.city = it } }
                Box(Modifier.weight(1f)) { F("Pincode *", vm.pincode, "pincode", KeyboardType.Number) { vm.pincode = it.filter(Char::isDigit).take(6) } }
            }
            ExposedDropdownMenuBox(expanded = stateOpen, onExpandedChange = { stateOpen = it }) {
                OutlinedTextField(vm.state, {}, readOnly = true, label = { Text("State *") }, isError = vm.fieldError("state") != null,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stateOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(expanded = stateOpen, onDismissRequest = { stateOpen = false }) {
                    INDIAN_STATES.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { vm.state = s; stateOpen = false }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(vm.isDefault, { vm.isDefault = it })
                Text("Make this my default address")
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
