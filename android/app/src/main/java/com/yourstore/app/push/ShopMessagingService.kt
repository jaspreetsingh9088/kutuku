package com.yourstore.app.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yourstore.app.MainActivity
import com.yourstore.app.R
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.TokenReq
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ShopMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val c = ShopApp.container
        if (c.currentToken() != null) c.scope.launch { apiCall { c.api.updateFcmToken(TokenReq(token)) } }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: msg.data["title"] ?: return
        val body = msg.notification?.body ?: msg.data["body"].orEmpty()
        val isOrder = msg.data["type"] == "order"

        // Deep link data is read by MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            msg.data["order_id"]?.let { putExtra("order_id", it.toIntOrNull()) }
            msg.data["product_id"]?.let { putExtra("product_id", it.toIntOrNull()) }
            msg.data["conversation_id"]?.let { putExtra("conversation_id", it.toIntOrNull()) }
        }
        val pi = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(this, if (isOrder) "orders" else "promotions")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), n) }
    }

    companion object {
        /** Current FCM token, or null if Firebase isn't configured. */
        suspend fun currentToken(context: android.content.Context): String? = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseMessaging.getInstance().token.await()
        }.getOrNull()
    }
}
