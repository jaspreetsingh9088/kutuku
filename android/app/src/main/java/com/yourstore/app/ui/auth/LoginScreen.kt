package com.yourstore.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.SendOtpReq
import com.yourstore.app.data.model.VerifyOtpReq
import com.yourstore.app.push.ShopMessagingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private val c = ShopApp.container
    var mobile by mutableStateOf("")
    var otp by mutableStateOf("")
    var otpSent by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var resendIn by mutableIntStateOf(0)

    val mobileValid get() = Regex("^[6-9]\\d{9}$").matches(mobile)

    fun sendOtp() {
        if (!mobileValid) { error = "Enter a valid 10-digit mobile number"; return }
        busy = true; error = null
        viewModelScope.launch {
            apiCall { c.api.sendOtp(SendOtpReq(mobile)) }
                .onSuccess { otpSent = true; otp = ""; startTimer() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    private fun startTimer() = viewModelScope.launch {
        resendIn = 30
        while (resendIn > 0) { delay(1000); resendIn-- }
    }

    fun verify(context: android.content.Context, onDone: (isNew: Boolean) -> Unit) {
        if (otp.length != 6) { error = "Enter the 6-digit OTP"; return }
        busy = true; error = null
        viewModelScope.launch {
            val fcm = ShopMessagingService.currentToken(context)
            apiCall { c.api.verifyOtp(VerifyOtpReq(mobile, otp, fcm)) }
                .onSuccess { c.saveSession(it.token); c.refreshCartCount(); onDone(it.isNew) }
                .onFailure { error = it.message }
            busy = false
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: (isNew: Boolean) -> Unit, vm: LoginViewModel = viewModel()) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ShoppingBag, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(if (vm.otpSent) "Verify your number" else "Welcome!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (vm.otpSent) "Enter the 6-digit code sent to +91 ${vm.mobile}" else "Log in or sign up with your mobile number",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        if (!vm.otpSent) {
            OutlinedTextField(
                value = vm.mobile,
                onValueChange = { v -> vm.mobile = v.filter(Char::isDigit).take(10); vm.error = null },
                label = { Text("Mobile number") },
                prefix = { Text("+91 ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = vm::sendOtp, enabled = !vm.busy && vm.mobile.length == 10, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("Get OTP")
            }
        } else {
            OutlinedTextField(
                value = vm.otp,
                onValueChange = { v ->
                    vm.otp = v.filter(Char::isDigit).take(6); vm.error = null
                    if (vm.otp.length == 6 && !vm.busy) vm.verify(context, onLoggedIn)
                },
                label = { Text("OTP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.verify(context, onLoggedIn) }, enabled = !vm.busy && vm.otp.length == 6, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("Verify & continue")
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.otpSent = false; vm.otp = "" }) { Text("Change number") }
                TextButton(onClick = vm::sendOtp, enabled = vm.resendIn == 0 && !vm.busy) {
                    Text(if (vm.resendIn > 0) "Resend in ${vm.resendIn}s" else "Resend OTP")
                }
            }
        }
        vm.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Text(
            "By continuing you agree to our Terms of Service and Privacy Policy.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
