package com.yourstore.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourstore.app.BuildConfig
import com.yourstore.app.R
import com.yourstore.app.ShopApp
import com.yourstore.app.data.Prefs
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.*
import com.yourstore.app.push.ShopMessagingService
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class VerifyPurpose { REGISTER, RESET, LOGIN }

/** One ViewModel drives every auth screen, so the email/phone typed on one screen carries to the next. */
class AuthViewModel : ViewModel() {
    private val c = ShopApp.container
    var name by mutableStateOf("")
    var identifier by mutableStateOf(if (Prefs.rememberLogin) Prefs.rememberedIdentifier else "")
    var password by mutableStateOf("")
    var otp by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var resetToken by mutableStateOf("")
    var purpose by mutableStateOf(VerifyPurpose.REGISTER)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var registered by mutableStateOf(false)
    var onboarding by mutableStateOf<List<OnboardingSlide>>(emptyList())

    fun loadOnboarding() {
        if (onboarding.isNotEmpty()) return
        viewModelScope.launch { apiCall { c.api.onboarding() }.onSuccess { onboarding = it } }
    }

    private fun validIdentifier(): Boolean {
        val v = identifier.trim()
        val ok = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(v) || Regex("^(\\+?91)?[6-9]\\d{9}$").matches(v.replace(" ", ""))
        if (!ok) error = "Enter a valid email or 10-digit mobile number"
        return ok
    }
    val identifierLooksValid: Boolean get() {
        val v = identifier.trim()
        return Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(v) || Regex("^(\\+?91)?[6-9]\\d{9}$").matches(v.replace(" ", ""))
    }
    private fun cleanId() = identifier.trim().let { if (it.contains('@')) it.lowercase() else it.replace(" ", "").removePrefix("+91").let { p -> if (p.length == 12 && p.startsWith("91")) p.drop(2) else p } }

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch { try { block() } finally { busy = false } }
    }

    private suspend fun finish(auth: AuthResponse) {
        c.saveSession(auth.token)
        if (Prefs.rememberLogin) Prefs.rememberedIdentifier = cleanId()
        c.refreshCartCount()
        password = ""; otp = ""; newPassword = ""; confirmPassword = ""
    }

    private suspend fun fcm() = ShopMessagingService.currentToken(ShopApp.instance)

    fun login(onDone: () -> Unit) {
        if (!validIdentifier()) return
        if (password.isEmpty()) { error = "Enter your password"; return }
        run { apiCall { c.api.login(LoginReq(cleanId(), password, fcm())) }.onSuccess { finish(it); onDone() }.onFailure { error = it.message } }
    }

    fun register(onCodeSent: () -> Unit) {
        if (name.trim().length < 2) { error = "Enter your name"; return }
        if (!validIdentifier()) return
        if (password.length < 8) { error = "Password must be at least 8 characters"; return }
        run {
            apiCall { c.api.register(RegisterReq(name.trim(), cleanId(), password)) }
                .onSuccess { purpose = VerifyPurpose.REGISTER; otp = ""; message = it.message; onCodeSent() }
                .onFailure { error = it.message }
        }
    }

    fun sendLoginCode(onCodeSent: () -> Unit) {
        if (!validIdentifier()) return
        run {
            apiCall { c.api.sendOtp(SendOtpReq(cleanId())) }
                .onSuccess { purpose = VerifyPurpose.LOGIN; otp = ""; message = it.message; onCodeSent() }
                .onFailure { error = it.message }
        }
    }

    fun forgot(onCodeSent: () -> Unit) {
        if (!validIdentifier()) return
        run {
            apiCall { c.api.forgotPassword(IdentifierReq(cleanId())) }
                .onSuccess { purpose = VerifyPurpose.RESET; otp = ""; message = it.message; onCodeSent() }
                .onFailure { error = it.message }
        }
    }

    fun resend() = run {
        val r = when (purpose) {
            VerifyPurpose.REGISTER -> apiCall { c.api.register(RegisterReq(name.trim(), cleanId(), password)) }
            VerifyPurpose.RESET -> apiCall { c.api.forgotPassword(IdentifierReq(cleanId())) }
            VerifyPurpose.LOGIN -> apiCall { c.api.sendOtp(SendOtpReq(cleanId())) }
        }
        r.onSuccess { message = "A new code has been sent" }.onFailure { error = it.message }
    }

    /** onLoggedIn: register/OTP login finished; onResetVerified: show the new-password sheet. */
    fun submitCode(onLoggedIn: () -> Unit, onResetVerified: () -> Unit) {
        if (otp.length != 6) { error = "Enter the 6-digit code"; return }
        run {
            when (purpose) {
                VerifyPurpose.REGISTER -> apiCall { c.api.registerVerify(RegisterVerifyReq(name.trim(), cleanId(), password, otp, fcm())) }
                    .onSuccess { finish(it); registered = true }.onFailure { error = it.message }
                VerifyPurpose.LOGIN -> apiCall { c.api.verifyOtp(VerifyOtpReq(cleanId(), otp, fcm())) }
                    .onSuccess { finish(it); onLoggedIn() }.onFailure { error = it.message }
                VerifyPurpose.RESET -> apiCall { c.api.verifyResetCode(VerifyCodeReq(cleanId(), otp)) }
                    .onSuccess { resetToken = it.resetToken; onResetVerified() }.onFailure { error = it.message }
            }
        }
    }

    fun resetPassword(onDone: () -> Unit) {
        if (newPassword.length < 8) { error = "Password must be at least 8 characters"; return }
        if (newPassword != confirmPassword) { error = "Passwords do not match"; return }
        run {
            apiCall { c.api.resetPassword(ResetPasswordReq(resetToken, newPassword, fcm())) }
                .onSuccess { finish(it); onDone() }.onFailure { error = it.message }
        }
    }
}

// ============================================================ Splash
@Composable
fun SplashContent() {
    Box(Modifier.fillMaxSize().background(Brand)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Kutuku", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = Outfit)
            Spacer(Modifier.height(12.dp))
            Text("Any shopping just from home", color = Color.White, fontSize = 15.sp, fontFamily = Outfit)
        }
        Text("Version ${BuildConfig.VERSION_NAME}", color = Color.White, fontSize = 13.sp, fontFamily = Outfit,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp))
    }
}

@Composable
fun SplashScreen(onReady: () -> Unit) {
    LaunchedEffect(Unit) { delay(1200); onReady() }
    SplashContent()
}

// ============================================================ Onboarding
private val fallbackSlides = listOf(
    OnboardingSlide(1, "Various Collections Of The Latest Products", "Urna amet, suspendisse ullamcorper ac elit diam facilisis cursus vestibulum.", ""),
    OnboardingSlide(2, "Complete Collection Of Colors And Sizes", "Urna amet, suspendisse ullamcorper ac elit diam facilisis cursus vestibulum.", ""),
    OnboardingSlide(3, "Find The Most Suitable Outfit For You", "Urna amet, suspendisse ullamcorper ac elit diam facilisis cursus vestibulum.", ""),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingContent(slides: List<OnboardingSlide>, onCreateAccount: () -> Unit, onLogin: () -> Unit, initialPage: Int = 0) {
    val list = slides.ifEmpty { fallbackSlides }
    val pager = rememberPagerState(initialPage) { list.size }
    Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding()) {
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val s = list[i]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                NetImage(s.imageUrl, Modifier.padding(horizontal = PagePad).padding(top = 8.dp).fillMaxWidth().weight(1f).clip(RoundedCornerShape(36.dp)))
                Spacer(Modifier.height(28.dp))
                Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, lineHeight = 30.sp,
                    modifier = Modifier.padding(horizontal = 40.dp))
                Spacer(Modifier.height(16.dp))
                Text(s.subtitle.orEmpty(), fontSize = 14.sp, color = InkHint, textAlign = TextAlign.Center, lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 40.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(list.size) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == pager.currentPage) Brand else Color(0xFFDCDCE4))) }
        }
        Spacer(Modifier.height(32.dp))
        KButton("Create Account", onCreateAccount, Modifier.fillMaxWidth().padding(horizontal = PagePad))
        Spacer(Modifier.height(20.dp))
        TextLink("Already Have an Account", onLogin, Modifier.align(Alignment.CenterHorizontally), fontSize = 15)
        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================ shared pieces
@Composable
private fun AuthHeader(title: String, subtitle: String) {
    Spacer(Modifier.height(40.dp))
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, fontSize = 14.sp, color = InkHint)
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun SocialButtons(verb: String, onSocial: (String) -> Unit) {
    Text("Or using other method", color = InkHint, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    KOutlinedButton("$verb with Google", { onSocial("Google") }, Modifier.fillMaxWidth(), borderColor = Color(0xFFF1F1F5), fontSize = 16,
        leading = { Icon(painterResource(R.drawable.ic_google), null, Modifier.size(24.dp), tint = Color.Unspecified) })
    Spacer(Modifier.height(16.dp))
    KOutlinedButton("$verb with Facebook", { onSocial("Facebook") }, Modifier.fillMaxWidth(), borderColor = Color(0xFFF1F1F5), fontSize = 16,
        leading = { Icon(painterResource(R.drawable.ic_facebook), null, Modifier.size(24.dp), tint = Color.Unspecified) })
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) Text(error, color = Warning, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
}

// ============================================================ Login
enum class LoginSheet { NONE, FORGOT, NEW_PASSWORD }

@Composable
fun LoginContent(
    identifier: String, onIdentifier: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    busy: Boolean, error: String?, otpMode: Boolean,
    onSignIn: () -> Unit, onForgot: () -> Unit, onToggleOtp: () -> Unit, onRegister: () -> Unit, onSocial: (String) -> Unit,
    sheet: LoginSheet = LoginSheet.NONE, sheetContent: @Composable ColumnScope.() -> Unit = {}, onDismissSheet: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = PagePad)) {
            AuthHeader(if (otpMode) "Login with Code" else "Login Account",
                if (otpMode) "We will send a one-time code to your email or phone" else "Please login with registered account")
            KField(identifier, onIdentifier, "Enter your email or phone number", label = "Email or Phone Number", icon = Icons.Outlined.Email,
                keyboardType = KeyboardType.Email, imeAction = if (otpMode) ImeAction.Done else ImeAction.Next, onIme = if (otpMode) onSignIn else null)
            if (!otpMode) {
                Spacer(Modifier.height(24.dp))
                KField(password, onPassword, "Create your password", label = "Password", icon = Icons.Outlined.Lock, isPassword = true,
                    imeAction = ImeAction.Done, onIme = onSignIn)
                Spacer(Modifier.height(16.dp))
                TextLink("Forgot Password?", onForgot, Modifier.align(Alignment.End), fontSize = 13)
            }
            ErrorText(error)
            Spacer(Modifier.height(40.dp))
            KButton(if (otpMode) "Send Code" else "Sign In", onSignIn, Modifier.fillMaxWidth(), loading = busy && sheet == LoginSheet.NONE)
            Spacer(Modifier.height(24.dp))
            SocialButtons("Sign In", onSocial)
            Spacer(Modifier.height(16.dp))
            KOutlinedButton(if (otpMode) "Sign In with Password" else "Sign In with OTP", onToggleOtp, Modifier.fillMaxWidth(),
                borderColor = Color(0xFFF1F1F5), fontSize = 16,
                leading = { Icon(if (otpMode) Icons.Outlined.Lock else Icons.Outlined.Key, null, Modifier.size(22.dp), tint = Brand) })
            Spacer(Modifier.height(24.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account? ", color = InkHint, fontSize = 14.sp)
                TextLink("Create Account", onRegister)
            }
            Spacer(Modifier.height(24.dp))
        }
        SheetOverlay(sheet != LoginSheet.NONE, onDismissSheet, sheetContent)
    }
}

@Composable
fun ForgotSheetContent(identifier: String, onIdentifier: (String) -> Unit, valid: Boolean, busy: Boolean, error: String?, onSend: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text("Forgot Password", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text("Enter your mail or phone number", fontSize = 14.sp, color = InkHint)
    Spacer(Modifier.height(32.dp))
    KField(identifier, onIdentifier, "Enter your email or phone number", label = "Email or Phone Number", icon = Icons.Outlined.Email,
        keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, onIme = onSend,
        trailing = { if (valid) Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(22.dp)) })
    ErrorText(error)
    Spacer(Modifier.height(48.dp))
    KButton("Send Code", onSend, Modifier.fillMaxWidth(), loading = busy)
    Spacer(Modifier.height(32.dp))
}

@Composable
fun NewPasswordSheetContent(pw: String, onPw: (String) -> Unit, confirm: String, onConfirm: (String) -> Unit, busy: Boolean, error: String?, onSubmit: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text("Create New Password", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text("Enter your new password", fontSize = 14.sp, color = InkHint)
    Spacer(Modifier.height(32.dp))
    KField(pw, onPw, "Create your password", label = "Password", icon = Icons.Outlined.Lock, isPassword = true)
    Spacer(Modifier.height(24.dp))
    KField(confirm, onConfirm, "Confirm your password", label = "Confirm Password", icon = Icons.Outlined.Lock, isPassword = true,
        imeAction = ImeAction.Done, onIme = onSubmit)
    ErrorText(error)
    Spacer(Modifier.height(48.dp))
    KButton("Change Password", onSubmit, Modifier.fillMaxWidth(), loading = busy)
    Spacer(Modifier.height(32.dp))
}

@Composable
fun LoginScreen(vm: AuthViewModel, startSheet: LoginSheet, onLoggedIn: () -> Unit, onRegister: () -> Unit, onVerify: () -> Unit, snack: (String) -> Unit) {
    var sheet by remember { mutableStateOf(startSheet) }
    var otpMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startSheet) { sheet = startSheet }
    LoginContent(
        identifier = vm.identifier, onIdentifier = { vm.identifier = it; vm.error = null },
        password = vm.password, onPassword = { vm.password = it; vm.error = null },
        busy = vm.busy, error = if (sheet == LoginSheet.NONE) vm.error else null, otpMode = otpMode,
        onSignIn = { if (otpMode) vm.sendLoginCode(onVerify) else vm.login(onLoggedIn) },
        onForgot = { vm.error = null; sheet = LoginSheet.FORGOT },
        onToggleOtp = { otpMode = !otpMode; vm.error = null },
        onRegister = { vm.error = null; onRegister() },
        onSocial = { snack("$it sign-in will be available once it is set up for this store. Please use email or phone.") },
        sheet = sheet, onDismissSheet = { sheet = LoginSheet.NONE; vm.error = null },
        sheetContent = {
            when (sheet) {
                LoginSheet.FORGOT -> ForgotSheetContent(vm.identifier, { vm.identifier = it; vm.error = null }, vm.identifierLooksValid, vm.busy, vm.error) {
                    vm.forgot { sheet = LoginSheet.NONE; onVerify() }
                }
                LoginSheet.NEW_PASSWORD -> NewPasswordSheetContent(vm.newPassword, { vm.newPassword = it; vm.error = null }, vm.confirmPassword,
                    { vm.confirmPassword = it; vm.error = null }, vm.busy, vm.error) { vm.resetPassword(onLoggedIn) }
                LoginSheet.NONE -> {}
            }
        },
    )
}

// ============================================================ Register
@Composable
fun RegisterContent(
    name: String, onName: (String) -> Unit, identifier: String, onIdentifier: (String) -> Unit, password: String, onPassword: (String) -> Unit,
    busy: Boolean, error: String?, onCreate: () -> Unit, onLogin: () -> Unit, onSocial: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = PagePad)) {
        AuthHeader("Create Account", "Start learning with create your account")
        KField(name, onName, "Create your username", label = "Username", icon = Icons.Outlined.Person)
        Spacer(Modifier.height(24.dp))
        KField(identifier, onIdentifier, "Enter your email or phone number", label = "Email or Phone Number", icon = Icons.Outlined.Email, keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(24.dp))
        KField(password, onPassword, "Create your password", label = "Password", icon = Icons.Outlined.Lock, isPassword = true, imeAction = ImeAction.Done, onIme = onCreate)
        ErrorText(error)
        Spacer(Modifier.height(48.dp))
        KButton("Create Account", onCreate, Modifier.fillMaxWidth(), loading = busy)
        Spacer(Modifier.height(24.dp))
        SocialButtons("Sign Up", onSocial)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account? ", color = InkHint, fontSize = 14.sp)
            TextLink("Sign In", onLogin)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RegisterScreen(vm: AuthViewModel, onVerify: () -> Unit, onLogin: () -> Unit, snack: (String) -> Unit) {
    RegisterContent(
        vm.name, { vm.name = it; vm.error = null }, vm.identifier, { vm.identifier = it; vm.error = null }, vm.password, { vm.password = it; vm.error = null },
        vm.busy, vm.error, onCreate = { vm.register(onVerify) }, onLogin = { vm.error = null; onLogin() },
        onSocial = { snack("$it sign-up will be available once it is set up for this store. Please use email or phone.") },
    )
}

// ============================================================ Verification (+ Register Success sheet)
@Composable
fun VerificationContent(
    sentTo: String, code: String, onCode: (String) -> Unit, busy: Boolean, error: String?,
    onSubmit: () -> Unit, onResend: () -> Unit, onBack: () -> Unit,
    success: Boolean = false, onGoHome: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            KTopBar("Verification", onBack = onBack, arrowBack = true, divider = true)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePad), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                HaloIcon(Icons.Filled.MarkEmailRead, Brand, 136.dp)
                Spacer(Modifier.height(40.dp))
                Text("Verification Code", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(12.dp))
                Text("We have sent the verification code to", fontSize = 14.sp, color = InkHint, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(sentTo, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(32.dp))
                OtpBoxes(code, onCode, Modifier.fillMaxWidth())
                ErrorText(error)
                Spacer(Modifier.height(48.dp))
                KButton("Submit", onSubmit, Modifier.fillMaxWidth(), loading = busy && !success)
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Didn't receive the code? ", color = InkHint, fontSize = 14.sp)
                    TextLink("Resend", onResend)
                }
            }
        }
        SheetOverlay(success, onGoHome) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(32.dp))
                HaloIcon(Icons.Filled.Check, Success, 136.dp)
                Spacer(Modifier.height(36.dp))
                Text("Register Success", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(12.dp))
                Text("Congratulation! your account already created.\nPlease login to get amazing experience.", fontSize = 14.sp, color = InkHint,
                    textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(56.dp))
                KButton("Go to Homepage", onGoHome, Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun VerificationScreen(vm: AuthViewModel, onBack: () -> Unit, onLoggedIn: () -> Unit, onResetVerified: () -> Unit, snack: (String) -> Unit) {
    LaunchedEffect(vm.message) { vm.message?.let { snack(it); vm.message = null } }
    VerificationContent(
        sentTo = vm.identifier.trim(), code = vm.otp, onCode = { vm.otp = it; vm.error = null; if (it.length == 6 && !vm.busy) vm.submitCode(onLoggedIn, onResetVerified) },
        busy = vm.busy, error = vm.error,
        onSubmit = { vm.submitCode(onLoggedIn, onResetVerified) }, onResend = { vm.resend() }, onBack = { vm.error = null; onBack() },
        success = vm.registered, onGoHome = { vm.registered = false; onLoggedIn() },
    )
}
