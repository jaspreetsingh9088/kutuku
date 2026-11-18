package com.yourstore.app.data

import android.content.Context
import com.yourstore.app.ShopApp

/** Small device-side settings: onboarding seen, Security toggles, remembered login, language, recent searches. */
object Prefs {
    private fun p() = ShopApp.instance.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var onboardingSeen: Boolean
        get() = p().getBoolean("onboarding_seen", false)
        set(v) = p().edit().putBoolean("onboarding_seen", v).apply()

    /** Security → Remember Password: pre-fill the email/phone on the login screen. */
    var rememberLogin: Boolean
        get() = p().getBoolean("remember_login", true)
        set(v) = p().edit().putBoolean("remember_login", v).apply()
    var rememberedIdentifier: String
        get() = p().getString("remembered_identifier", "").orEmpty()
        set(v) = p().edit().putString("remembered_identifier", v).apply()

    /** Security → Face ID / Touch ID: ask for the device biometric when the app opens. */
    var faceUnlock: Boolean
        get() = p().getBoolean("face_unlock", false)
        set(v) = p().edit().putBoolean("face_unlock", v).apply()
    var fingerprintUnlock: Boolean
        get() = p().getBoolean("fingerprint_unlock", false)
        set(v) = p().edit().putBoolean("fingerprint_unlock", v).apply()
    val biometricLock: Boolean get() = faceUnlock || fingerprintUnlock

    var language: String
        get() = p().getString("language", "en").orEmpty()
        set(v) = p().edit().putString("language", v).apply()

    fun recentSearches(): List<String> = p().getString("recent_searches", "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun addRecentSearch(q: String) {
        val list = (listOf(q.trim()) + recentSearches().filter { !it.equals(q.trim(), true) }).take(8)
        p().edit().putString("recent_searches", list.joinToString("\n")).apply()
    }
    fun removeRecentSearch(q: String) = p().edit().putString("recent_searches", recentSearches().filter { it != q }.joinToString("\n")).apply()
    fun clearRecentSearches() = p().edit().remove("recent_searches").apply()
}
