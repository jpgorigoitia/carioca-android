package com.carioca.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carioca.game.domain.GameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NavyHome = Color(0xFF071A29)
private val DeepBlueHome = Color(0xFF0C3148)
private val TealHome = Color(0xFF1D8B83)
private val FeltHome = Color(0xFF0D5C58)
private val GoldHome = Color(0xFFFFC857)
private val SoftWhiteHome = Color(0xFFF2FAFB)
private val MutedHome = Color(0xFF9FC3CC)
private val TitleShadowHome = Shadow(Color.Black.copy(alpha = .95f), Offset(2.5f, 2.5f), 4f)

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CariocaHome(this) }
    }
}

private enum class HomeScreen { HOME, ONLINE, LOBBY }

@Composable
private fun CariocaHome(context: Context) {
    var screen by remember { mutableStateOf(HomeScreen.HOME) }
    var room by remember { mutableStateOf<OnlineRoom?>(null) }
    var token by remember { mutableStateOf("") }
    MaterialTheme(colorScheme = darkColorScheme(primary = TealHome, secondary = GoldHome)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NavyHome, DeepBlueHome)))) {
            HomeTexture()
            when (screen) {
                HomeScreen.HOME -> HomeMenu(
                    playAi = { context.startActivity(Intent(context, AiGameActivity::class.java)) },
                    playOnline = { screen = HomeScreen.ONLINE }
                )
                HomeScreen.ONLINE -> OnlineMenu(
                    back = { screen = HomeScreen.HOME },
                    onRoom = { newRoom, newToken -> room = newRoom; token = newToken; screen = HomeScreen.LOBBY }
                )
                HomeScreen.LOBBY -> OnlineLobby(
                    initial = room ?: return@Box,
                    token = token,
                    back = { screen = HomeScreen.ONLINE; room = null }
                )
            }
        }
    }
}

@Composable
private fun HomeTexture() {
    Canvas(Modifier.fillMaxSize()) {
        val step = size.minDimension / 7f
        repeat(15) { i ->
            drawCircle(Color.White.copy(alpha = .014f), step * (.25f + (i % 3) * .08f), Offset((i * step * 1.7f) % size.width, (i * step * 2.15f) % size.height))
        }
    }
}

@Composable
private fun HomeMenu(playAi: () -> Unit, playOnline: () -> Unit) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeHero(Modifier.weight(1.08f).fillMaxHeight())
        HomeActions(Modifier.weight(.92f).fillMaxHeight(), playOnline, playAi)
    }
}

@Composable
private fun HomeHero(modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = FeltHome,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldHome.copy(alpha = .28f))
    ) {
        Row(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(TealHome.copy(alpha = .55f), FeltHome, NavyHome)))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1.25f)) {
                Text("CARIOCA", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, style = TextStyle(shadow = TitleShadowHome))
                Text("JUST THE GAME", color = GoldHome, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = TextStyle(shadow = TitleShadowHome))
                Spacer(Modifier.height(10.dp))
                Text("Play the table, not an economy.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = TitleShadowHome))
                Spacer(Modifier.height(5.dp))
                Text(
                    "Play Carioca with friends or practice against AI. No coins, gems, betting, ad walls, or pay-to-play mechanics.",
                    color = SoftWhiteHome,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { repeat(4) { HomeAvatar(it) } }
            }
            Spacer(Modifier.width(8.dp))
            HomeCardFan(Modifier.weight(.75f).fillMaxHeight().padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun HomeActions(modifier: Modifier, playOnline: () -> Unit, playAi: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose a table", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = TitleShadowHome))
        HeroCard("PLAY ONLINE", "Create or join a 2–4 player table.", "2–4 PLAYERS", playOnline, Modifier.weight(1f))
        HeroCard("PLAY VS AI", "Choose mode, players and AI difficulty, then start immediately.", "AI PRACTICE", playAi, Modifier.weight(1f))
        Surface(color = Color.Black.copy(alpha = .28f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Horizontal-only · Touch, grab, drag and drop cards", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = SoftWhiteHome, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, badge: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = .09f),
        shape = RoundedCornerShape(19.dp),
        modifier = modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(19.dp)).clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(CircleShape).background(TealHome.copy(alpha = .38f)), contentAlignment = Alignment.Center) {
                Text(if (title.contains("ONLINE")) "♣" else "♠", color = GoldHome, fontSize = 23.sp, style = TextStyle(shadow = TitleShadowHome))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(badge, color = GoldHome, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, style = TextStyle(shadow = TitleShadowHome))
                Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = TitleShadowHome))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = SoftWhiteHome, fontSize = 10.sp, lineHeight = 13.sp)
            }
            Text("›", color = GoldHome, fontSize = 29.sp, style = TextStyle(shadow = TitleShadowHome))
        }
    }
}

@Composable
private fun HomeCardFan(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        listOf(Triple("A", "♥", Color(0xFFC93645)), Triple("K", "♠", Color(0xFF0D2438)), Triple("J", "★", TealHome)).forEachIndexed { i, card ->
            Surface(
                color = Color(0xFFFFFEFB), shape = RoundedCornerShape(13.dp), shadowElevation = 8.dp,
                modifier = Modifier.size(70.dp, 100.dp).graphicsLayer {
                    translationX = (i - 1) * 34.dp.toPx(); translationY = kotlin.math.abs(i - 1) * 8.dp.toPx(); rotationZ = (i - 1) * 12f
                }
            ) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(card.first, color = card.third, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(card.second, color = card.third, fontSize = 29.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(card.second, color = card.third, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeAvatar(index: Int) {
    val accents = listOf(Color(0xFFFF8DA1), Color(0xFF86D7E8), Color(0xFFB4A0FF), Color(0xFF7EE0BD)); val accent = accents[index]
    Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = .42f)).padding(3.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val hair = listOf(Color(0xFF49352F), Color(0xFF263A57), Color(0xFF70432D), Color(0xFF302D2B))[index]
            drawCircle(hair, size.minDimension * .43f, Offset(center.x, center.y - size.height * .07f))
            drawCircle(Color(0xFFFFD3B8), size.minDimension * .34f, Offset(center.x, center.y + size.height * .04f))
            drawCircle(Color(0xFF0D2438), size.minDimension * .035f, Offset(center.x - size.width * .11f, center.y))
            drawCircle(Color(0xFF0D2438), size.minDimension * .035f, Offset(center.x + size.width * .11f, center.y))
        }
    }
}

@Composable
private fun OnlineMenu(back: () -> Unit, onRoom: (OnlineRoom, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("REGULAR") }; var visibility by remember { mutableStateOf("PRIVATE") }; var maxPlayers by remember { mutableIntStateOf(4) }
    var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var publicRooms by remember { mutableStateOf<List<PublicRoom>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun network(block: suspend () -> Unit) { if (loading) return; loading = true; error = null; scope.launch { runCatching { block() }.onFailure { error = it.message ?: "Could not connect." }; loading = false } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = back) { Text("← Back", color = Color.White) }
        Text("Play Online", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = TitleShadowHome))
        Text("No account required. Choose a display name for this table.", color = SoftWhiteHome)
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 24) name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Section("Game") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Regular · 8", mode == "REGULAR") { mode = "REGULAR" }
                ChoiceChip("Special · 11", mode == "SPECIAL") { mode = "SPECIAL" }
            }
        }
        Section("Players") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..4).forEach { count -> ChoiceChip(count.toString(), maxPlayers == count) { maxPlayers = count } }
            }
        }
        Section("Table") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Private", visibility == "PRIVATE") { visibility = "PRIVATE" }
                ChoiceChip("Public", visibility == "PUBLIC") { visibility = "PUBLIC" }
            }
            Text(if (visibility == "PRIVATE") "Friends join using your 6-character code." else "Your waiting table appears publicly.", color = SoftWhiteHome, fontSize = 12.sp)
        }
        Button(onClick = {
            if (name.isBlank()) error = "Enter a display name first." else network {
                val t = MultiplayerClient.newPlayerToken()
                val r = withContext(Dispatchers.IO) { MultiplayerClient.createRoom(name, t, mode, visibility, maxPlayers) }
                onRoom(r, t)
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (loading) "Connecting…" else "Create Table", fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color.White.copy(alpha = .15f))
        Text("Join with code", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = code, onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) }, label = { Text("Room code") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            if (name.isBlank() || code.length != 6) error = "Enter your name and a 6-character room code." else network {
                val t = MultiplayerClient.newPlayerToken()
                val r = withContext(Dispatchers.IO) { MultiplayerClient.joinRoom(code, name, t) }
                onRoom(r, t)
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Join Table") }
        HorizontalDivider(color = Color.White.copy(alpha = .15f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Public tables", color = Color.White, fontWeight = FontWeight.Bold)
            TextButton(onClick = { network { publicRooms = withContext(Dispatchers.IO) { MultiplayerClient.publicRooms() } } }) { Text("Refresh") }
        }
        if (publicRooms.isEmpty()) Text("No public tables loaded yet.", color = MutedHome, fontSize = 12.sp)
        publicRooms.forEach { publicRoom ->
            Surface(color = Color.White.copy(alpha = .08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${publicRoom.mode.lowercase().replaceFirstChar { it.uppercase() }} · ${publicRoom.playerCount}/${publicRoom.maxPlayers}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(publicRoom.code, color = GoldHome, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        if (name.isBlank()) error = "Enter a display name first." else network {
                            val t = MultiplayerClient.newPlayerToken()
                            val r = withContext(Dispatchers.IO) { MultiplayerClient.joinRoom(publicRoom.code, name, t) }
                            onRoom(r, t)
                        }
                    }) { Text("Join") }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnlineLobby(initial: OnlineRoom, token: String, back: () -> Unit) {
    var room by remember { mutableStateOf(initial) }; var error by remember { mutableStateOf<String?>(null) }; var starting by remember { mutableStateOf(false) }; var launched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current

    LaunchedEffect(initial.roomId, token) {
        while (true) {
            delay(850)
            runCatching { withContext(Dispatchers.IO) { MultiplayerClient.snapshot(initial.roomId, token) } }.onSuccess { room = it }.onFailure { error = it.message }
        }
    }
    LaunchedEffect(room.status, room.gameState, launched) {
        if (room.status == "PLAYING" && room.gameState != null && !launched) {
            launched = true
            context.startActivity(Intent(context, OnlineGameActivity::class.java).putExtra("room_id", room.roomId).putExtra("token", token))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = back) { Text("← Leave lobby", color = Color.White) }
        Text("Table ${room.code}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = TitleShadowHome))
        Text("${room.mode.lowercase().replaceFirstChar { it.uppercase() }} · ${room.visibility.lowercase().replaceFirstChar { it.uppercase() }} · ${room.players.size}/${room.maxPlayers}", color = GoldHome, fontWeight = FontWeight.Bold)
        Surface(color = GoldHome.copy(alpha = .12f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Share this code with friends: ${room.code}", Modifier.padding(16.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Text("Players", color = Color.White, fontWeight = FontWeight.Bold)
        repeat(room.maxPlayers) { seat ->
            val player = room.players.firstOrNull { it.seat == seat }
            Surface(color = Color.White.copy(alpha = .08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) { HomeAvatar(seat); Spacer(Modifier.width(10.dp)); Text(player?.name ?: "Waiting for player…", color = if (player == null) MutedHome else Color.White) }
                    if (player?.isHost == true) Text("HOST", color = GoldHome, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (room.isHost && room.status == "WAITING") {
            Button(onClick = {
                if (room.players.size < 2 || starting) return@Button
                starting = true; error = null
                scope.launch {
                    runCatching {
                        val names = room.players.sortedBy { it.seat }.map { it.name }
                        val initialState = NetworkGameEngine.newGame(GameMode.valueOf(room.mode), names)
                        withContext(Dispatchers.IO) { MultiplayerClient.startGame(room.roomId, token, initialState) }
                    }.onSuccess { room = it }.onFailure { error = it.message ?: "Could not start game" }
                    starting = false
                }
            }, enabled = room.players.size >= 2 && !starting, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(17.dp)) {
                Text(if (starting) "Dealing…" else if (room.players.size < 2) "Waiting for another player" else "Start Game", fontWeight = FontWeight.Black)
            }
        } else {
            Surface(color = TealHome.copy(alpha = .16f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(if (room.status == "PLAYING") "Game started — opening table…" else "Connected. Waiting for the host to start the game.", Modifier.padding(16.dp), color = SoftWhiteHome, fontSize = 13.sp)
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White.copy(alpha = .07f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun RowScope.ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}
