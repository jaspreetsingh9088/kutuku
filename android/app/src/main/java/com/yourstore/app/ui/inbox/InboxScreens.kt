package com.yourstore.app.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourstore.app.ShopApp
import com.yourstore.app.data.apiCall
import com.yourstore.app.data.model.*
import com.yourstore.app.ui.components.*
import com.yourstore.app.ui.nav.Routes
import com.yourstore.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ============================================================ Notification
class NotificationsViewModel : ViewModel() {
    private val c = ShopApp.container
    var items by mutableStateOf<List<AppNotification>?>(null)
    var error by mutableStateOf<String?>(null)
    fun load() {
        viewModelScope.launch {
            apiCall { c.api.notifications() }.onSuccess { items = it.data; if (it.unread > 0) apiCall { c.api.readAllNotifications() } }
                .onFailure { if (items == null) error = it.message }
        }
    }
}

@Composable
fun NotificationsScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: NotificationsViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    NotificationsContent(vm.items, vm.error, onBack, onSettings = { navigate(Routes.NOTIF_SETTINGS) }, onRetry = { vm.load() }) { n ->
        val d = n.data.orEmpty()
        fun num(k: String) = (d[k] as? Number)?.toInt() ?: d[k]?.toString()?.toDoubleOrNull()?.toInt()
        when {
            num("conversation_id") != null -> navigate(Routes.chat(num("conversation_id")!!))
            num("order_id") != null -> navigate(Routes.order(num("order_id")!!))
            num("product_id") != null -> navigate(Routes.product(num("product_id")!!))
        }
    }
}

@Composable
fun NotificationsContent(items: List<AppNotification>?, error: String?, onBack: () -> Unit, onSettings: () -> Unit, onRetry: () -> Unit, onOpen: (AppNotification) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        KTopBar("Notification", onBack = onBack) { IconBtn(Icons.Outlined.Settings, "Notification settings", onSettings) }
        when {
            items == null && error != null -> ErrorState(error, onRetry)
            items == null -> LoadingBox()
            items.isEmpty() -> EmptyState(Icons.Outlined.NotificationsNone, "No notifications yet", "Order updates, messages and offers will appear here.")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = PagePad, vertical = 8.dp)) {
                item { Text("Recent", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(bottom = 8.dp)) }
                items(items, key = { it.id }) { n ->
                    NotificationRow(n) { onOpen(n) }
                    HorizontalDivider(color = Line)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp)) {
        if (n.type == "message" && !n.imageUrl.isNullOrBlank()) Avatar(n.imageUrl, n.title, 44.dp)
        else Box(Modifier.size(44.dp).clip(CircleShape).background(Field), contentAlignment = Alignment.Center) {
            Icon(
                when (n.type) {
                    "order" -> if (n.title.contains("sent", true) || n.title.contains("ship", true) || n.title.contains("deliver", true)) Icons.Outlined.LocalShipping else Icons.Outlined.ShoppingCart
                    "promo" -> Icons.Outlined.Percent
                    "message" -> Icons.AutoMirrored.Outlined.Chat
                    else -> Icons.Outlined.NotificationsNone
                }, null, tint = Ink, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeAgo(n.createdAt), fontSize = 11.sp, color = InkHint)
            }
            if (!n.body.isNullOrBlank()) Text(n.body, fontSize = 14.sp, color = InkHint, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
            if (n.type == "message") Text("Reply the message", fontSize = 14.sp, color = Brand, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ============================================================ Message list
class MessagesViewModel : ViewModel() {
    private val c = ShopApp.container
    var conversations by mutableStateOf<List<Conversation>?>(null)
    var stores by mutableStateOf<List<Store>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    fun load() {
        viewModelScope.launch {
            apiCall { c.api.conversations() }.onSuccess { conversations = it }.onFailure { if (conversations == null) error = it.message }
            apiCall { c.api.stores(followed = 1) }.onSuccess { stores = it }
        }
    }
    fun open(storeId: Int, onOpen: (Int) -> Unit) = viewModelScope.launch {
        apiCall { c.api.openConversation(ConversationReq(storeId)) }.onSuccess { onOpen(it.id) }.onFailure { error = it.message }
    }
}

@Composable
fun MessagesScreen(navigate: (String) -> Unit, onBack: () -> Unit, vm: MessagesViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    MessagesContent(
        vm.conversations, vm.stores, vm.error, vm.query, { vm.query = it }, onBack, onRetry = { vm.load() },
        onStore = { s -> vm.open(s.id) { navigate(Routes.chat(it)) } }, onConversation = { navigate(Routes.chat(it.id)) },
        onSupport = { vm.open(0) { navigate(Routes.chat(it)) } },
    )
}

@Composable
fun MessagesContent(
    conversations: List<Conversation>?, stores: List<Store>, error: String?, query: String, onQuery: (String) -> Unit,
    onBack: () -> Unit, onRetry: () -> Unit, onStore: (Store) -> Unit, onConversation: (Conversation) -> Unit, onSupport: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        KTopBar("Message", onBack = onBack) { IconBtn(Icons.Outlined.SupportAgent, "Customer support", onSupport) }
        KSearchBar(query, onQuery, Modifier.padding(horizontal = PagePad), placeholder = "Search message...", filled = true)
        when {
            conversations == null && error != null -> ErrorState(error, onRetry)
            conversations == null -> LoadingBox()
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
                if (stores.isNotEmpty()) {
                    item { Text("Activities", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = PagePad)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = PagePad, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(stores, key = { it.id }) { s ->
                                Column(Modifier.width(64.dp).clickable { onStore(s) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Avatar(s.logoUrl, s.name, 60.dp, ring = true)
                                    Text(s.name.substringBefore(' '), fontSize = 12.sp, color = Ink, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
                item { Text("Messages", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = PagePad, vertical = 4.dp)) }
                val shown = conversations.filter { query.isBlank() || it.name.contains(query, true) || it.lastMessage.orEmpty().contains(query, true) }
                if (shown.isEmpty()) item {
                    Text(if (query.isBlank()) "No conversations yet. Open a store and tap the chat button, or contact support from the top right." else "No messages match \"$query\".",
                        fontSize = 14.sp, color = InkHint, modifier = Modifier.padding(PagePad))
                }
                items(shown, key = { it.id }) { c -> ConversationRow(c) { onConversation(c) } }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PagePad, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(c.avatarUrl, c.name, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
                if (c.isVerified) { Spacer(Modifier.width(4.dp)); VerifiedBadge(13.dp) }
            }
            Text(c.lastMessage ?: "Say hello 👋", fontSize = 13.sp, color = if (c.unread > 0) Ink else InkHint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(timeAgo(c.lastMessageAt, short = true), fontSize = 11.sp, color = InkHint)
            Spacer(Modifier.height(6.dp))
            if (c.unread > 0) Box(Modifier.size(20.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) {
                Text("${c.unread.coerceAtMost(9)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            } else Spacer(Modifier.height(20.dp))
        }
    }
}

// ============================================================ Message detail (chat)
class ChatViewModel(private val id: Int) : ViewModel() {
    private val c = ShopApp.container
    var conversation by mutableStateOf<Conversation?>(null)
    val messages = mutableStateListOf<ChatMessage>()
    var draft by mutableStateOf("")
    var sending by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            while (isActive) { poll(); delay(4000) }
        }
    }

    private suspend fun poll() {
        apiCall { c.api.messages(id, messages.lastOrNull()?.id) }.onSuccess { r ->
            conversation = r.conversation
            r.data.forEach { m -> if (messages.none { it.id == m.id }) messages.add(m) }
        }.onFailure { if (conversation == null) error = it.message }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true
        viewModelScope.launch {
            apiCall { c.api.sendMessage(id, SendMessageReq(text)) }.onSuccess { m -> draft = ""; if (messages.none { it.id == m.id }) messages.add(m) }
                .onFailure { error = it.message }
            sending = false
        }
    }
}

@Composable
fun ChatScreen(id: Int, navigate: (String) -> Unit, onBack: () -> Unit, snack: (String) -> Unit) {
    val vm: ChatViewModel = viewModel(key = "chat-$id") { ChatViewModel(id) }
    LaunchedEffect(vm.error) { vm.error?.let { if (vm.conversation != null) { snack(it); vm.error = null } } }
    ChatContent(
        vm.conversation, vm.messages, vm.error, vm.draft, { vm.draft = it }, vm.sending, onSend = { vm.send() }, onBack = onBack,
        onProduct = { navigate(Routes.product(it)) },
        onUnsupported = { snack("$it isn't available in chat yet") },
    )
}

@Composable
fun ChatContent(
    conversation: Conversation?, messages: List<ChatMessage>, error: String?, draft: String, onDraft: (String) -> Unit, sending: Boolean,
    onSend: () -> Unit, onBack: () -> Unit, onProduct: (Int) -> Unit, onUnsupported: (String) -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1) }
    Column(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        // Header
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", Modifier.size(18.dp), tint = Ink) }
                Avatar(conversation?.avatarUrl, conversation?.name ?: "", 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(conversation?.name ?: "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
                        if (conversation?.isVerified == true) { Spacer(Modifier.width(4.dp)); VerifiedBadge(13.dp) }
                    }
                    Text("Usually replies within a day", fontSize = 11.sp, color = InkHint)
                }
                IconBtn(Icons.Outlined.Videocam, "Video call", { onUnsupported("Video call") })
                IconBtn(Icons.Outlined.Call, "Call", { onUnsupported("Calling") })
            }
            HorizontalDivider(color = Line)
        }
        when {
            conversation == null && error != null -> ErrorState(error, null, Modifier.weight(1f).fillMaxWidth())
            conversation == null -> LoadingBox(Modifier.weight(1f).fillMaxWidth())
            else -> LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(PagePad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (messages.isEmpty()) item {
                    Text("Ask ${conversation.name} anything about products, sizes or your order.", fontSize = 13.sp, color = InkHint,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                items(messages, key = { it.id }) { m -> Bubble(m, onProduct) }
            }
        }
        // Input
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = PagePad, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Field).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) Text("Type a message here...", fontSize = 13.sp, color = InkHint)
                    BasicTextField(draft, onDraft, singleLine = true, textStyle = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = Ink),
                        cursorBrush = SolidColor(Brand), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.fillMaxWidth())
                }
                Icon(Icons.Outlined.CameraAlt, "Camera", Modifier.padding(start = 8.dp).size(20.dp).clickable { onUnsupported("Sending photos") }, tint = InkMuted)
                Icon(Icons.Outlined.Mic, "Voice", Modifier.padding(start = 10.dp).size(20.dp).clickable { onUnsupported("Voice messages") }, tint = InkMuted)
                Icon(Icons.Outlined.AttachFile, "Attach", Modifier.padding(start = 10.dp).size(20.dp).clickable { onUnsupported("Attachments") }, tint = InkMuted)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (draft.isBlank()) BrandLight else Brand).clickable(enabled = draft.isNotBlank() && !sending, onClick = onSend),
                contentAlignment = Alignment.Center) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, onProduct: (Int) -> Unit) {
    val mine = m.sender == "customer"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        m.product?.let { p ->
            Row(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Line, RoundedCornerShape(14.dp)).background(Color.White)
                .clickable { onProduct(p.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(p.image, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    p.price?.let { PriceTag(it, size = 14) }
                }
            }
            if (!m.body.isNullOrBlank()) Spacer(Modifier.height(6.dp))
        }
        if (!m.body.isNullOrBlank()) Text(
            m.body, fontSize = 14.sp, lineHeight = 21.sp, color = if (mine) Color.White else Ink,
            modifier = Modifier.widthIn(max = 270.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp))
                .background(if (mine) Brand else Field).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Text(formatTime(m.createdAt), fontSize = 10.sp, color = InkHint, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
    }
}
