package com.yourstore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.yourstore.app.data.model.Product
import com.yourstore.app.ui.theme.Discount
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }
fun inr(v: Double) = inrFormat.format(v)

fun prettyStatus(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }

fun formatDate(iso: String, withTime: Boolean = true): String = runCatching {
    val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val d = p.parse(iso.take(19))!!
    SimpleDateFormat(if (withTime) "d MMM yyyy, h:mm a" else "d MMM yyyy", Locale("en", "IN")).format(d)
}.getOrDefault(iso)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, null, tint = Color.LightGray) } },
    )
}

@Composable
fun PriceRow(price: Double, mrp: Double?, big: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(inr(price), fontWeight = FontWeight.Bold, fontSize = if (big) 22.sp else 15.sp)
        if (mrp != null && mrp > price) {
            Text(inr(mrp), textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = if (big) 14.sp else 12.sp)
            Text("${(((mrp - price) / mrp) * 100).toInt()}% off", color = Discount, fontWeight = FontWeight.SemiBold, fontSize = if (big) 14.sp else 12.sp)
        }
    }
}

@Composable
fun ProductCard(p: Product, onClick: () -> Unit, onWishlist: ((Product) -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            NetImage(p.image, Modifier.fillMaxWidth().aspectRatio(1f))
            if (!p.inStock) {
                Text("Out of stock", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (onWishlist != null) {
                IconButton(onClick = { onWishlist(p) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)) {
                    Icon(if (p.wishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Wishlist",
                        tint = if (p.wishlisted) Color(0xFFE11D48) else Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, minLines = 2)
            p.variants.firstOrNull()?.let { Text(it.label + if (p.variants.size > 1) " +${p.variants.size - 1} more" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            if (p.price != null) PriceRow(p.price, p.mrp)
        }
    }
}

@Composable
fun QuantityStepper(qty: Int, onChange: (Int) -> Unit, max: Int = 50, enabled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
    ) {
        IconButton(onClick = { onChange(qty - 1) }, enabled = enabled, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Remove, "Decrease", tint = MaterialTheme.colorScheme.primary) }
        Text("$qty", fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange(qty + 1) }, enabled = enabled && qty < max, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Add, "Increase", tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (onRetry != null) { Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onRetry) { Text("Try again") } }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) { Spacer(Modifier.height(16.dp)); Button(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(action, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onAction).padding(4.dp))
    }
}

@Composable
fun StatusChip(status: String) {
    val (bg, fg) = when (status) {
        "delivered", "paid" -> Color(0xFFE3F5EA) to Color(0xFF116B35)
        "cancelled", "failed" -> Color(0xFFFDE8E1) to Color(0xFFB13A0B)
        "shipped", "out_for_delivery", "packed" -> Color(0xFFEFEAFD) to Color(0xFF5B33C6)
        "refunded", "returned", "partially_refunded" -> Color(0xFFEEF0F3) to Color(0xFF4B5260)
        else -> Color(0xFFE6EFFC) to Color(0xFF1D4ED8)
    }
    Text(prettyStatus(status), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
}
