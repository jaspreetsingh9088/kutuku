package com.yourstore.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.yourstore.app.data.model.Product
import com.yourstore.app.data.model.Store
import com.yourstore.app.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------- formatting
private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2; minimumFractionDigits = 2 }
fun inr(v: Double): String = inrFormat.format(v)
fun prettyStatus(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun parseIso(iso: String): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso.take(19))
}.getOrNull()

fun formatDate(iso: String, withTime: Boolean = true): String =
    parseIso(iso)?.let { SimpleDateFormat(if (withTime) "d MMM yyyy, h:mm a" else "d MMM yyyy", Locale("en", "IN")).format(it) } ?: iso

fun formatTime(iso: String): String = parseIso(iso)?.let { SimpleDateFormat("hh.mm a", Locale.US).format(it) } ?: ""

/** "2 m ago" / "40 min" relative times used in Notification and Message lists. */
fun timeAgo(iso: String?, short: Boolean = false): String {
    val d = iso?.let { parseIso(it) } ?: return ""
    val mins = ((System.currentTimeMillis() - d.time) / 60000).coerceAtLeast(0)
    return when {
        mins < 1 -> "now"
        mins < 60 -> if (short) "$mins min" else "$mins m ago"
        mins < 60 * 24 -> if (short) "${mins / 60} hr" else "${mins / 60} h ago"
        else -> SimpleDateFormat("d MMM", Locale.US).format(d)
    }
}

/** 1300 -> "1.3k". */
fun compact(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fm", n / 1_000_000.0).replace(".0m", "m")
    n >= 1000 -> String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k")
    else -> n.toString()
}

fun parseHex(hex: String?): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

// ---------------------------------------------------------------- layout primitives
val PagePad = 24.dp

/** Kutuku top bar: 56dp with centred 15sp title. Shopping screens use "<", settings screens use "←". */
@Composable
fun KTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    arrowBack: Boolean = false,
    divider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.background(Color.White).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                Icon(if (arrowBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowBackIos, "Back",
                    Modifier.size(if (arrowBack) 24.dp else 18.dp), tint = Ink)
            }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.align(Alignment.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (divider) HorizontalDivider(color = Line)
    }
}

@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    SubcomposeAsyncImage(
        model = url, contentDescription = null, contentScale = contentScale,
        modifier = modifier.background(Tile),
        error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, null, tint = InkHint) } },
    )
}

@Composable
fun Avatar(url: String?, name: String, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false) {
    val inner = if (ring) size - 8.dp else size
    Box(modifier.size(size).then(if (ring) Modifier.border(1.5.dp, Brand, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) Box(Modifier.size(inner).clip(CircleShape).background(BrandLight), contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Brand, fontWeight = FontWeight.SemiBold, fontSize = (inner.value * 0.4f).sp)
        } else NetImage(url, Modifier.size(inner).clip(CircleShape))
    }
}

// ---------------------------------------------------------------- buttons
@Composable
fun KButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false,
    icon: ImageVector? = null, color: Color = Brand, height: Dp = 56.dp, fontSize: Int = 16,
) {
    Button(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier.height(height), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.5f), disabledContentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        else {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, fontSize = fontSize.sp, fontWeight = FontWeight.Medium, fontFamily = Outfit)
        }
    }
}

@Composable
fun KOutlinedButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Ink,
    height: Dp = 56.dp, leading: (@Composable () -> Unit)? = null, borderColor: Color = Line, fontSize: Int = 15,
) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(height), shape = CircleShape,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, containerColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Text(text, fontSize = fontSize.sp, fontWeight = FontWeight.Medium, color = color, fontFamily = Outfit)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Brand, fontSize: Int = 14, weight: FontWeight = FontWeight.Medium) {
    Text(text, color = color, fontSize = fontSize.sp, fontWeight = weight,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(2.dp))
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, badge: Boolean = false, tint: Color = Ink, size: Dp = 24.dp) {
    Box {
        IconButton(onClick = onClick) { Icon(icon, desc, Modifier.size(size), tint = tint) }
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 11.dp).size(9.dp).clip(CircleShape)
            .background(Warning).border(1.5.dp, Color.White, CircleShape))
    }
}

// ---------------------------------------------------------------- inputs
@Composable
fun KField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    label: String? = null, icon: ImageVector? = null, isPassword: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null, trailing: (@Composable () -> Unit)? = null, readOnly: Boolean = false,
    imeAction: ImeAction = ImeAction.Next, onIme: (() -> Unit)? = null, singleLine: Boolean = true, minHeight: Dp = 52.dp,
) {
    var visible by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(modifier) {
        if (label != null) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink); Spacer(Modifier.height(12.dp)) }
        BasicTextField(
            value = value, onValueChange = onChange, readOnly = readOnly, singleLine = singleLine, interactionSource = source,
            textStyle = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = Ink),
            cursorBrush = SolidColor(Brand),
            visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onIme?.invoke() }),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(12.dp))
                        .background(if (focused || value.isNotEmpty()) Color.White else Field)
                        .border(1.dp, when { error != null -> Warning; focused -> Brand; else -> Color(0xFFF1F1F5) }, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (icon != null) { Icon(icon, null, Modifier.size(22.dp), tint = if (focused || value.isNotEmpty()) Brand else InkHint); Spacer(Modifier.width(12.dp)) }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = InkHint, maxLines = 1)
                        inner()
                    }
                    if (isPassword) Icon(if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, "Show password",
                        Modifier.size(22.dp).clickable { visible = !visible }, tint = InkHint)
                    trailing?.invoke()
                }
            },
        )
        if (error != null) Text(error, color = Warning, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
    }
}

@Composable
fun KSearchBar(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search something...",
    onFilter: (() -> Unit)? = null, onSubmit: (() -> Unit)? = null, focusRequester: FocusRequester? = null,
    filled: Boolean = false, readOnly: Boolean = false, onClick: (() -> Unit)? = null, height: Dp = 48.dp,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(if (filled) 14.dp else 12.dp)
    val box = @Composable { inner: @Composable () -> Unit ->
        Row(
            Modifier.fillMaxWidth().height(height).clip(shape).background(if (filled) Field else Color.White)
                .border(if (focused) 1.5.dp else 1.dp, if (focused) Brand else if (filled) Field else Line, shape).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(22.dp), tint = if (filled) InkHint else Ink)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = InkHint)
                inner()
            }
            if (onFilter != null) Icon(Icons.Outlined.Tune, "Filter", Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onFilter), tint = Ink)
        }
    }
    if (onClick != null) {
        Box(modifier.clip(shape).clickable(onClick = onClick)) { box { Text(value, fontSize = 13.sp, color = Ink) } }
        return
    }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true, interactionSource = source, readOnly = readOnly,
        textStyle = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = Ink), cursorBrush = SolidColor(Brand),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner -> box(inner) },
    )
}

/** Rounded boxes for verification codes. */
@Composable
fun OtpBoxes(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, length: Int = 6) {
    BasicTextField(
        value = value, onValueChange = { v -> onChange(v.filter(Char::isDigit).take(length)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = modifier,
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                repeat(length) { i ->
                    val ch = value.getOrNull(i)?.toString() ?: ""
                    val active = i == value.length
                    Box(
                        Modifier.weight(1f).aspectRatio(0.85f).clip(RoundedCornerShape(12.dp))
                            .background(if (ch.isEmpty() && !active) Field else Color.White)
                            .border(1.dp, if (active) Brand else if (ch.isEmpty()) Field else Line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (active) "|" else ch, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = if (active) Brand else Ink, fontFamily = Outfit) }
                }
            }
        },
    )
}

// ---------------------------------------------------------------- selection controls
@Composable
fun KCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, size: Dp = 20.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(5.dp)).background(if (checked) Brand else Color.White)
            .border(1.5.dp, if (checked) Brand else Color(0xFFCDD0DA), RoundedCornerShape(5.dp)).clickable { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) { if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.7f)) }
}

@Composable
fun KSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Brand, checkedBorderColor = Brand,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD9DBE3), uncheckedBorderColor = Color(0xFFD9DBE3),
        ),
    )
}

/** Rounded-rect chip: purple when selected. */
@Composable
fun KChip(text: String, selected: Boolean, onClick: () -> Unit, big: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(if (big) 10.dp else 8.dp)
    Row(
        Modifier.clip(shape).background(if (selected) Brand else Color.White)
            .border(1.dp, if (selected) Brand else Color(0xFFE6E7EC), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (big) 20.dp else 12.dp, vertical = if (big) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = if (selected) Color.White else InkMuted, fontSize = if (big) 15.sp else 14.sp)
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

/** Underline tabs (Home | Category, My Order | History, store tabs). */
@Composable
fun UnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, underlineWidth: Dp = 90.dp) {
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, t ->
            Column(Modifier.weight(1f).clickable { onSelect(i) }.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t, color = if (i == selected) Ink2 else InkHint, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.width(underlineWidth).height(2.dp).background(if (i == selected) Brand else Color.Transparent))
            }
        }
    }
}

/** "−  1  +" stepper (cart rows, detail). */
@Composable
fun KStepper(qty: Int, onChange: (Int) -> Unit, max: Int = 50, enabled: Boolean = true, darkPlus: Boolean = false, boxed: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (boxed) Modifier.clip(CircleShape).background(Color.White).border(1.dp, Line, CircleShape).padding(horizontal = 6.dp, vertical = 4.dp) else Modifier,
    ) {
        StepBtn(Icons.Filled.Remove, "Decrease", enabled && qty > 1, false) { onChange(qty - 1) }
        Text("$qty", fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.widthIn(min = 30.dp), textAlign = TextAlign.Center)
        StepBtn(Icons.Filled.Add, "Increase", enabled && qty < max, darkPlus) { onChange(qty + 1) }
    }
}

@Composable
private fun StepBtn(icon: ImageVector, desc: String, enabled: Boolean, dark: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).shadow(if (dark) 0.dp else 3.dp, CircleShape).clip(CircleShape).background(if (dark) Ink else Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(14.dp), tint = when { dark -> Color.White; enabled -> Ink; else -> InkHint }) }
}

// ---------------------------------------------------------------- product & store
@Composable
fun ProductCard(p: Product, onClick: () -> Unit, modifier: Modifier = Modifier, onWishlist: ((Product) -> Unit)? = null) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            NetImage(p.image, Modifier.fillMaxWidth().aspectRatio(152f / 168f).clip(RoundedCornerShape(10.dp)))
            if (onWishlist != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape)
                        .background(if (p.wishlisted) Color.White else Color(0x66616161)).clickable { onWishlist(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (p.wishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                        tint = if (p.wishlisted) Color(0xFFE11D2E) else Color.White, modifier = Modifier.size(14.dp))
                }
            }
            if (!p.inStock) Text("Out of stock", color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Ink.copy(alpha = 0.7f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(p.store?.name ?: p.categoryName ?: "", fontSize = 12.sp, color = InkHint, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        if (p.price != null) Text(inr(p.price), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Price with small raised currency symbol, like "$ 67.00" in the kit. */
@Composable
fun PriceTag(value: Double, size: Int = 18, color: Color = Ink, weight: FontWeight = FontWeight.Medium) {
    val s = inr(value)
    val idx = s.indexOfFirst { it.isDigit() }.coerceAtLeast(0)
    Row(verticalAlignment = Alignment.Top) {
        Text(s.substring(0, idx).trim(), fontSize = (size * 0.6).sp, color = color, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 3.dp))
        Text(s.substring(idx), fontSize = size.sp, fontWeight = weight, color = color)
    }
}

@Composable
fun StrikePrice(mrp: Double, price: Double) {
    if (mrp > price) Text(inr(mrp), textDecoration = TextDecoration.LineThrough, color = InkHint, fontSize = 12.sp)
}

@Composable
fun RatingRow(rating: Double, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = Star, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(String.format(Locale.US, "%.1f", rating), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Ink)
        Text(" ($count Review)", fontSize = 11.sp, color = InkMuted)
    }
}

@Composable
fun VerifiedBadge(size: Dp = 16.dp) = Icon(Icons.Filled.Verified, "Verified", tint = Color(0xFF3B6FF6), modifier = Modifier.size(size))

@Composable
fun StoreLogo(url: String?, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Tile), contentAlignment = Alignment.Center) {
        NetImage(url, Modifier.size(size * 0.8f).clip(CircleShape))
    }
}

/** Store row: logo, name + tick, "104 Products  1.3k Followers", trailing slot. */
@Composable
fun StoreRow(store: Store, onClick: (() -> Unit)? = null, logoSize: Dp = 56.dp, small: Boolean = false, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoreLogo(store.logoUrl, logoSize)
        Spacer(Modifier.width(if (small) 10.dp else 14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(store.name, fontSize = if (small) 13.sp else 16.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1)
                if (store.isVerified) { Spacer(Modifier.width(5.dp)); VerifiedBadge(if (small) 12.dp else 14.dp) }
            }
            Text("${store.productCount} Products   ${compact(store.followerCount)} Followers", fontSize = if (small) 10.sp else 13.sp,
                color = InkMuted, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

@Composable
fun FollowButton(following: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (following) Color.White else Brand).border(1.dp, Brand, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 7.dp),
    ) { Text(if (following) "Following" else "Follow", color = if (following) Brand else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun ColorDot(hex: String?, selected: Boolean, onClick: () -> Unit, size: Dp = 34.dp, enabled: Boolean = true) {
    val c = parseHex(hex) ?: InkHint
    Box(
        Modifier.size(size).clip(CircleShape).background(if (enabled) c else c.copy(alpha = 0.35f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
}

// ---------------------------------------------------------------- containers
/** White panel with rounded top corners and a grab handle. */
@Composable
fun SheetPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color.White).padding(horizontal = PagePad),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 16.dp).size(48.dp, 4.dp).clip(CircleShape).background(Color(0xFFE6E7EC)))
        content()
    }
}

/** Grey scrim that dims the screen behind a sheet. */
@Composable
fun Scrim(onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99A4A6B3)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick))
}

@Composable
fun HaloIcon(icon: ImageVector, color: Color, outer: Dp = 136.dp) {
    Box(Modifier.size(outer).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(outer * 0.7f).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(outer * 0.24f))
        }
    }
}

@Composable
fun SettingRow(icon: ImageVector, text: String, value: String? = null, tint: Color = Ink, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(Field).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value != null) { Text(value, color = InkHint, fontSize = 13.sp); Spacer(Modifier.width(8.dp)) }
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InkHint)
    }
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(Color(0xFFD9DBE3), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextLink(action, onAction, fontSize = 13)
    }
}

@Composable
fun BillLine(label: String, value: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = InkMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        PriceTag(value, size = if (bold) 18 else 16, weight = FontWeight.Medium)
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
        if (onRetry != null) { Spacer(Modifier.height(16.dp)); KOutlinedButton("Try again", onRetry, Modifier.width(180.dp), height = 48.dp) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        HaloIcon(icon, Brand, 120.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, color = InkMuted, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (action != null && onAction != null) { Spacer(Modifier.height(24.dp)); KButton(action, onAction, Modifier.fillMaxWidth()) }
    }
}

/** Outlined status pill used on order cards ("On Progress", "Completed"). */
@Composable
fun OrderStatusPill(status: String) {
    val (label, color) = when (status) {
        "delivered" -> "Completed" to Success
        "cancelled" -> "Cancelled" to Warning
        "returned" -> "Returned" to InkMuted
        "pending_payment" -> "Awaiting Payment" to NewFg
        else -> "On Progress" to Teal
    }
    Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
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
    Text(prettyStatus(status), color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Snackbar host styled for Kutuku. */
@Composable
fun KSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { Snackbar(it, containerColor = Ink, contentColor = Color.White, shape = RoundedCornerShape(12.dp)) }
}

/** Dims the page and shows a bottom sheet over it, as in the Forgot / New Password / Success frames. */
@Composable
fun SheetOverlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!visible) return
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        SheetPanel(Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding(), content = content)
    }
}

