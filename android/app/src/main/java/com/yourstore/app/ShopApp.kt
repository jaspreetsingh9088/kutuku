package com.yourstore.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.yourstore.app.data.AppContainer

class ShopApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("orders", "Order updates", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Order confirmation, dispatch and delivery updates"
        })
        nm.createNotificationChannel(NotificationChannel("promotions", "Offers & promotions", NotificationManager.IMPORTANCE_DEFAULT))

        // Push is optional: it only works once google-services.json has been added.
        runCatching {
            if (FirebaseApp.getApps(this).isNotEmpty()) FirebaseMessaging.getInstance().subscribeToTopic("promotions")
        }
    }

    companion object {
        lateinit var instance: ShopApp
            private set
        val container: AppContainer get() = instance.container
    }
}
