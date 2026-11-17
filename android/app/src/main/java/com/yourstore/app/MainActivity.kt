package com.yourstore.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.yourstore.app.payment.PaymentBus
import com.yourstore.app.payment.PaymentResult
import com.yourstore.app.ui.nav.AppNav
import com.yourstore.app.ui.nav.DeepLink
import com.yourstore.app.ui.theme.AppTheme

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private val deepLink = mutableStateOf<DeepLink?>(null)
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

        setContent {
            AppTheme {
                AppNav(deepLink = deepLink.value, onDeepLinkHandled = { deepLink.value = null })
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
        if (orderId != null || productId != null) deepLink.value = DeepLink(orderId, productId)
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
