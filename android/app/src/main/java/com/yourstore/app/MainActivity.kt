package com.yourstore.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yourstore.app.data.Prefs
import com.yourstore.app.ui.components.KButton
import com.yourstore.app.ui.theme.Brand
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.yourstore.app.payment.PaymentBus
import com.yourstore.app.payment.PaymentResult
import com.yourstore.app.ui.nav.AppNav
import com.yourstore.app.ui.nav.DeepLink
import com.yourstore.app.ui.theme.AppTheme

class MainActivity : FragmentActivity(), PaymentResultWithDataListener {

    private val deepLink = mutableStateOf<DeepLink?>(null)
    /** Security → Face ID / Touch ID: the app stays locked until the phone's biometric check passes. */
    private val locked = mutableStateOf(false)
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Checkout.preload(applicationContext)
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        locked.value = savedInstanceState == null && Prefs.biometricLock && ShopApp.container.loggedIn.value && biometricAvailable()

        setContent {
            AppTheme {
                if (locked.value) LockScreen()
                else AppNav(deepLink = deepLink.value, onDeepLinkHandled = { deepLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val orderId = intent?.getIntExtra("order_id", -1)?.takeIf { it > 0 }
        val productId = intent?.getIntExtra("product_id", -1)?.takeIf { it > 0 }
        val conversationId = intent?.getIntExtra("conversation_id", -1)?.takeIf { it > 0 }
        if (orderId != null || productId != null || conversationId != null) deepLink.value = DeepLink(orderId, productId, conversationId)
    }

    private fun biometricAvailable() = BiometricManager.from(this)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun promptUnlock() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked.value = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kutuku")
                .setSubtitle(if (Prefs.faceUnlock) "Use Face ID to continue" else "Use your fingerprint to continue")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        LaunchedEffect(Unit) { promptUnlock() }
        Column(Modifier.fillMaxSize().background(Color.White).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Fingerprint, null, tint = Brand, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text("Kutuku is locked", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Unlock with your face or fingerprint to continue.", fontSize = 14.sp, color = Color(0xFF8A8FA0))
            Spacer(Modifier.height(32.dp))
            KButton("Unlock", { promptUnlock() }, Modifier.fillMaxWidth())
        }
    }

    // ---- Razorpay callbacks ----
    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        PaymentBus.emit(PaymentResult.Success(
            paymentId = razorpayPaymentId ?: data?.paymentId.orEmpty(),
            orderId = data?.orderId.orEmpty(),
            signature = data?.signature.orEmpty(),
        ))
    }

    override fun onPaymentError(code: Int, description: String?, data: PaymentData?) {
        val msg = description?.let { runCatching { org.json.JSONObject(it).getJSONObject("error").getString("description") }.getOrDefault(it) } ?: "Payment failed"
        PaymentBus.emit(PaymentResult.Failure(code, msg))
    }
}
