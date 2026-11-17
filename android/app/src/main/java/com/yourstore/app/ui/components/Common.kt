package com.yourstore.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.yourstore.app.data.model.Product
import com.yourstore.app.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2; minimumFractionDigits = 2 }
fun inr(v: Double) = inrFormat.format(v)

fun prettyStatus(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }

fun formatDate(iso: String, withTime: Boolean = true): String = runCatching {
    val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val d = p.parse(iso.take(19))!!
    SimpleDateFormat(if (withTime) "d MMM yyyy, h:mm a" else "d MMM yyyy", Locale("en", "IN")).format(d)
}.getOrDefault(iso)

/** Centered-title top bar used across the Kutuku screens: back chevron left, optional action right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", Modifier.size(20.dp), tint = Ink) }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
    )
}

@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.background(Tile),
        error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, null, tint = InkHint) } },
    )
}

/** Full-width pill button (primary purple), 52dp tall like the kit's "Sign In" / "Checkout". */
@Composable
fun PillButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false,
    icon: ImageVector? = null, outlined: Boolean = false, color: Color = Brand,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = if (outlined) color else Color.White)
        else {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (outlined) OutlinedButton(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier.height(52.dp), shape = CircleShape,
        border = BorderStroke(1.dp, Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = color), content = content,
    ) else Button(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier.height(52.dp), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(alpha = 0.45f), disabledContentColor = Color.White),
        content = content,
    )
}

/** Labelled, softly-filled input with a leading icon (Login / Create Account / Edit Profile fields). */
@Composable
fun KField(
    label: String?, value: String, onChange: (String) -> Unit, placeholder: String = "", icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text, error: String? = null, readOnly: Boolean = false,
    prefix: String? = null, trailing: (@Composable () -> Unit)? = null, singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None, modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (label != null) { Text(label, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp)) }
        OutlinedTextField(
            value = value, onValueChange = onChange, readOnly = readOnly, singleLine = singleLine,
            placeholder = { Text(placeholder, color = InkHint) },
            leadingIcon = icon?.let { { Icon(it, null, tint = InkHint) } },
            prefix = prefix?.let { { Text(it, color = Ink) } },
            trailingIcon = trailing,
            isError = error != null,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Field, focusedContainerColor = Color.White,
                unfocusedBorderColor = Line, focusedBorderColor = Brand,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
    }
}

/** Price with a small currency symbol, as in the kit ("$ 67.00" style). */
@Composable
fun PriceTag(value: Double, size: Int = 18, color: Color = Ink, bold: Boolean = true) {
    val parts = inr(value)
    val symbol = parts.takeWhile { !it.isDigit() }
    Row(verticalAlignment = Alignment.Top) {
        Text(symbol.trim(), fontSize = (size * 0.6).sp, color = color, modifier = Modifier.padding(top = 2.dp, end = 2.dp))
        Text(parts.drop(symbol.length), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = color)
    }
}

@Composable
fun PriceRow(price: Double, mrp: Double?, big: Boolean = false, center: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = if (center) Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(6.dp)) {
        Text(inr(price), fontWeight = FontWeight.Bold, fontSize = if (big) 26.sp else 15.sp, color = if (big) Brand else Ink)
        if (mrp != null && mrp > price) {
            Text(inr(mrp), textDecoration = TextDecoration.LineThrough, color = InkHint, fontSize = if (big) 14.sp else 11.sp)
            if (big) Text("${(((mrp - price) / mrp) * 100).toInt()}% off", color = Discount, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/** Kutuku product card: rounded grey image tile with a white heart button, centered name, category and price. */
@Composable
fun ProductCard(p: Product, onClick: () -> Unit, onWishlist: ((Product) -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            NetImage(p.image, Modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(12.dp)))
            if (onWishlist != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f)).clickable { onWishlist(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (p.wishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                        tint = if (p.wishlisted) Warning else Ink.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                }
            }
            if (!p.inStock) Text("Out of stock", color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Ink.copy(alpha = 0.75f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(p.variants.firstOrNull()?.label?.let { it + if (p.variants.size > 1) " · ${p.variants.size} options" else "" } ?: (p.categoryName ?: ""),
            style = MaterialTheme.typography.bodySmall, maxLines = 1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        if (p.price != null) PriceRow(p.price, p.mrp, center = true)
    }
}

/** Compact stepper: two small circle buttons around the count (cart rows, detail sheet). */
@Composable
fun QuantityStepper(qty: Int, onChange: (Int) -> Unit, max: Int = 50, enabled: Boolean = true, filledPlus: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape).background(Field).border(1.dp, Line, CircleShape).padding(3.dp),
    ) {
        StepBtn(Icons.Filled.Remove, "Decrease", enabled) { onChange(qty - 1) }
        Text("$qty", fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 26.dp), textAlign = TextAlign.Center)
        StepBtn(Icons.Filled.Add, "Increase", enabled && qty < max, filled = filledPlus) { onChange(qty + 1) }
    }
}

@Composable
private fun StepBtn(icon: ImageVector, desc: String, enabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(if (filled) Ink else Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(14.dp), tint = when { filled -> Color.White; enabled -> Ink; else -> InkHint }) }
}

/** Two-or-more text tabs with a purple underline under the selected one (Home / Category). */
@Composable
fun UnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, t ->
            Column(Modifier.weight(1f).clickable { onSelect(i) }.padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t, color = if (i == selected) Ink else InkHint, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(96.dp).height(2.dp).background(if (i == selected) Brand else Color.Transparent))
            }
        }
    }
}

/** Rounded pill chip: purple when selected, outlined otherwise (search filters, variants). */
@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.clip(CircleShape)
            .background(if (selected) Brand else Color.White)
            .border(1.dp, if (selected) Brand else Line, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = when { selected -> Color.White; enabled -> Ink.copy(alpha = 0.8f); else -> InkHint }, fontSize = 13.sp)
        if (trailing != null) { Spacer(Modifier.width(6.dp)); trailing() }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, badge: Int = 0, tint: Color = Ink) {
    Box {
        IconButton(onClick = onClick) { Icon(icon, desc, tint = tint) }
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(16.dp).clip(CircleShape).background(Warning),
            contentAlignment = Alignment.Center,
        ) { Text(if (badge > 9) "9+" else "$badge", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(Line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
}

/** Light grey rounded row with leading icon and chevron — the Settings list style. */
@Composable
fun SettingRow(icon: ImageVector, text: String, value: String? = null, tint: Color = Ink, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Field).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = tint, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value != null) { Text(value, color = InkHint, fontSize = 13.sp); Spacer(Modifier.width(6.dp)) }
        if (tint == Ink) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InkHint)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Warning, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = InkMuted)
        if (onRetry != null) { Spacer(Modifier.height(16.dp)); PillButton("Try again", onRetry, outlined = true, modifier = Modifier.width(180.dp)) }
    }
}

/** Empty state in the kit's "success" style: big soft circle with an icon, title, subtitle, pill button. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        HaloIcon(icon, Brand)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (action != null && onAction != null) { Spacer(Modifier.height(24.dp)); PillButton(action, onAction, modifier = Modifier.fillMaxWidth()) }
    }
}

/** Two concentric circles with an icon in the middle (Verification / Register Success). */
@Composable
fun HaloIcon(icon: ImageVector, color: Color, outer: Dp = 120.dp) {
    Box(Modifier.size(outer).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(outer * 0.7f).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(outer * 0.28f))
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(action, color = Brand, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(4.dp))
    }
}

@Composable
fun StatusChip(status: String) {
    val (bg, fg) = when (status) {
        "delivered", "paid", "captured" -> PopularBg to Discount
        "cancelled", "failed" -> HotBg to Warning
        "shipped", "out_for_delivery", "packed" -> BrandSoft to Brand
        "refunded", "returned", "partially_refunded" -> Tile to InkMuted
        else -> NewBg to NewFg
    }
    Text(prettyStatus(status), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp))
}
