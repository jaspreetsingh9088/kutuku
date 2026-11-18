package com.yourstore.app.payment

import android.app.Activity
import com.razorpay.Checkout
import com.yourstore.app.data.model.PlacedOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

sealed interface PaymentResult {
    data class Success(val paymentId: String, val orderId: String, val signature: String) : PaymentResult
    data class Failure(val code: Int, val description: String) : PaymentResult
}

/**
 * Razorpay delivers results to the Activity (PaymentResultWithDataListener).
 * MainActivity forwards them here so the checkout ViewModel can react.
 */
object PaymentBus {
    private val _results = MutableSharedFlow<PaymentResult>(extraBufferCapacity = 1)
    val results = _results.asSharedFlow()
    fun emit(r: PaymentResult) { _results.tryEmit(r) }

    fun open(activity: Activity, order: PlacedOrder, storeName: String) {
        val co = Checkout()
        co.setKeyID(order.razorpayKeyId)
        val options = JSONObject().apply {
            put("name", storeName)
            put("description", "Order ${order.orderNumber}")
            put("order_id", order.razorpayOrderId)
            put("currency", order.currency)
            put("amount", order.amountPaise)
            put("prefill", JSONObject().apply {
                put("contact", order.prefill.contact)
                if (order.prefill.email.isNotBlank()) put("email", order.prefill.email)
                if (order.prefill.name.isNotBlank()) put("name", order.prefill.name)
                // Opens Razorpay on the method chosen in "Change Payment Method"
                order.preferredMethod?.let { put("method", it) }
            })
            put("theme", JSONObject().put("color", "#514EB7"))
            put("retry", JSONObject().put("enabled", true).put("max_count", 3))
        }
        co.open(activity, options)
    }
}
