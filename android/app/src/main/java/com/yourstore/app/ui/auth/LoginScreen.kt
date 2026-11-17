package com.yourstore.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.yourstore.app.ui.components.HaloIcon
import com.yourstore.app.ui.components.KField
import com.yourstore.app.ui.components.PillButton
import com.yourstore.app.ui.theme.*
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
        Modifier.fillMaxSize().background(Color.White).statusBarsPadding().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp),
    ) {
        if (!vm.otpSent) {
            Spacer(Modifier.height(40.dp))
            Text("Login Account", style = MaterialTheme.typography.headlineMedium)
            Text("Please login with your registered mobile number", color = InkHint, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(32.dp))
            KField(
                label = "Phone Number", value = vm.mobile,
                onChange = { v -> vm.mobile = v.filter(Char::isDigit).take(10); vm.error = null },
                placeholder = "Enter your phone number", icon = Icons.Outlined.PhoneAndroid,
                prefix = "+91 ", keyboardType = KeyboardType.Phone, error = vm.error,
            )
            Text("We'll send a 6-digit code to verify it's you. New here? Your account is created automatically.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(32.dp))
            PillButton("Sign In", onClick = vm::sendOtp, enabled = vm.mobile.length == 10, loading = vm.busy, modifier = Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.otpSent = false; vm.otp = ""; vm.error = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                Text("Verification", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(32.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HaloIcon(Icons.Filled.MarkEmailRead, Brand) }
            Spacer(Modifier.height(24.dp))
            Text("Verification Code", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("We have sent the verification code to\n+91 ${vm.mobile}", color = InkMuted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Spacer(Modifier.height(28.dp))
            OtpBoxes(vm.otp) { v ->
                vm.otp = v.filter(Char::isDigit).take(6); vm.error = null
                if (vm.otp.length == 6 && !vm.busy) vm.verify(context, onLoggedIn)
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) }
            Spacer(Modifier.height(28.dp))
            PillButton("Verify & Continue", onClick = { vm.verify(context, onLoggedIn) }, enabled = vm.otp.length == 6, loading = vm.busy, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Didn't receive the code? ", color = InkMuted)
                TextButton(onClick = vm::sendOtp, enabled = vm.resendIn == 0 && !vm.busy, contentPadding = PaddingValues(4.dp)) {
                    Text(if (vm.resendIn > 0) "Resend in ${vm.resendIn}s" else "Resend", color = if (vm.resendIn > 0) InkHint else Brand)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "By continuing you agree to our Terms of Service and Privacy Policy.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
    }
}

/** Six rounded boxes showing the OTP digits; a hidden text field captures input. */
@Composable
private fun OtpBoxes(value: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value, onValueChange = onChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.focusRequester(focus),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                repeat(6) { i ->
                    val ch = value.getOrNull(i)?.toString() ?: ""
                    val active = i == value.length
                    Box(
                        Modifier.size(46.dp, 54.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (ch.isNotEmpty()) BrandSoft else Field)
                            .border(1.dp, if (active) Brand else Line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(ch, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink) }
                }
            }
        },
    )
}
