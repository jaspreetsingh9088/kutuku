package com.yourstore.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.yourstore.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore("session")
private val TOKEN = stringPreferencesKey("token")

/** Simple manual dependency container (one per process). */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var token: String? = runBlocking { context.dataStore.data.map { it[TOKEN] }.first() }
    private val _loggedIn = MutableStateFlow(token != null)
    val loggedIn = _loggedIn.asStateFlow()

    /** Cart badge count shared across screens. */
    private val _cartCount = MutableStateFlow(0)
    val cartCount = _cartCount.asStateFlow()
    fun setCartCount(n: Int) { _cartCount.value = n }

    /** One-off events (e.g. session expired) for the UI. */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder().apply {
            token?.let { header("Authorization", "Bearer $it") }
            header("Accept", "application/json")
        }.build()
        val res = chain.proceed(req)
        if (res.code == 401 && token != null) scope.launch { clearSession(); _events.emit("Your session has expired. Please log in again.") }
        res
    }

    val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)) }
        .build()

    val api: Api = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(Api::class.java)

    fun currentToken() = token

    suspend fun saveSession(newToken: String) {
        token = newToken
        context.dataStore.edit { it[TOKEN] = newToken }
        _loggedIn.value = true
    }

    suspend fun clearSession() {
        token = null
        context.dataStore.edit { it.remove(TOKEN) }
        _cartCount.value = 0
        _loggedIn.value = false
    }

    /** Refresh the cart badge in the background. */
    fun refreshCartCount() {
        if (token == null) return
        scope.launch { runCatching { api.cart() }.onSuccess { _cartCount.value = it.summary.itemCount } }
    }
}

/** Run an API call and convert failures into a user-friendly message. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    val msg = runCatching {
        JsonParser.parseString(e.response()?.errorBody()?.string() ?: "").asJsonObject.get("message")?.asString
    }.getOrNull()
    Result.failure(Exception(msg ?: "Something went wrong (${e.code()})"))
} catch (e: IOException) {
    Result.failure(Exception("No internet connection. Please check your network and try again."))
} catch (e: Exception) {
    Result.failure(Exception(e.message ?: "Something went wrong"))
}
