package com.carioca.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NavyHome = Color(0xFF0D2438)
private val DeepBlueHome = Color(0xFF163E56)
private val TealHome = Color(0xFF167D79)
private val GoldHome = Color(0xFFFFC857)
private val SoftWhiteHome = Color(0xFFEAF4F7)

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
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavyHome, DeepBlueHome)))
                .padding(18.dp)
        ) {
            when (screen) {
                HomeScreen.HOME -> HomeMenu(
                    playAi = { context.startActivity(Intent(context, MainActivity::class.java)) },
                    playOnline = { screen = HomeScreen.ONLINE }
                )
                HomeScreen.ONLINE -> OnlineMenu(
                    back = { screen = HomeScreen.HOME },
                    onRoom = { newRoom, newToken ->
                        room = newRoom
                        token = newToken
                        screen = HomeScreen.LOBBY
                    }
                )
                HomeScreen.LOBBY -> OnlineLobby(
                    initial = room ?: return@Box,
                    token = token,
                    back = {
                        screen = HomeScreen.ONLINE
                        room = null
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeMenu(playAi: () -> Unit, playOnline: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(42.dp))
        Text("CARIOCA", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
        Text("JUST THE GAME", color = GoldHome, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))
        HeroCard("PLAY ONLINE", "Create a table or join friends", playOnline)
        HeroCard("PLAY VS AI", "Practice with Easy, Medium or Hard opponents", playAi)
        Spacer(Modifier.weight(1f))
        Text(
            "Free to play · No coins · No gems · No betting · No ads",
            color = SoftWhiteHome.copy(alpha = 0.78f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.09f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = SoftWhiteHome, fontSize = 14.sp)
        }
    }
}

@Composable
private fun OnlineMenu(back: () -> Unit, onRoom: (OnlineRoom, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("REGULAR") }
    var visibility by remember { mutableStateOf("PRIVATE") }
    var maxPlayers by remember { mutableIntStateOf(4) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var publicRooms by remember { mutableStateOf<List<PublicRoom>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun withNetwork(block: suspend () -> Unit) {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            runCatching { block() }
                .onFailure { error = it.message ?: "Could not connect." }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = back) { Text("← Back", color = Color.White) }
        Text("Play Online", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text("No account required. Choose a display name for this table.", color = SoftWhiteHome)

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 24) name = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

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
            Text(
                if (visibility == "PRIVATE") "Friends join using your 6-character code."
                else "Your waiting table appears in the public room list.",
                color = SoftWhiteHome,
                fontSize = 12.sp
            )
        }

        Button(
            onClick = {
                if (name.isBlank()) {
                    error = "Enter a display name first."
                } else withNetwork {
                    val newToken = MultiplayerClient.newPlayerToken()
                    val newRoom = withContext(Dispatchers.IO) {
                        MultiplayerClient.createRoom(name, newToken, mode, visibility, maxPlayers)
                    }
                    onRoom(newRoom, newToken)
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text(if (loading) "Connecting…" else "Create Table", fontWeight = FontWeight.Bold) }

        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        Text("Join with code", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) },
            label = { Text("Room code") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = {
                if (name.isBlank() || code.length != 6) {
                    error = "Enter your name and a 6-character room code."
                } else withNetwork {
                    val newToken = MultiplayerClient.newPlayerToken()
                    val newRoom = withContext(Dispatchers.IO) { MultiplayerClient.joinRoom(code, name, newToken) }
                    onRoom(newRoom, newToken)
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Join Table") }

        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Public tables", color = Color.White, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                withNetwork { publicRooms = withContext(Dispatchers.IO) { MultiplayerClient.publicRooms() } }
            }) { Text("Refresh") }
        }
        if (publicRooms.isEmpty()) {
            Text("No public tables loaded yet.", color = SoftWhiteHome, fontSize = 12.sp)
        } else {
            publicRooms.forEach { publicRoom ->
                Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${publicRoom.mode.lowercase().replaceFirstChar { it.uppercase() }} · ${publicRoom.playerCount}/${publicRoom.maxPlayers}", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(publicRoom.code, color = GoldHome, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            if (name.isBlank()) error = "Enter a display name first."
                            else withNetwork {
                                val newToken = MultiplayerClient.newPlayerToken()
                                val newRoom = withContext(Dispatchers.IO) { MultiplayerClient.joinRoom(publicRoom.code, name, newToken) }
                                onRoom(newRoom, newToken)
                            }
                        }) { Text("Join") }
                    }
                }
            }
        }

        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnlineLobby(initial: OnlineRoom, token: String, back: () -> Unit) {
    var room by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initial.roomId, token) {
        while (true) {
            delay(1800)
            runCatching {
                withContext(Dispatchers.IO) { MultiplayerClient.snapshot(initial.roomId, token) }
            }.onSuccess { room = it }.onFailure { error = it.message }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = back) { Text("← Leave lobby", color = Color.White) }
        Text("Table ${room.code}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(
            "${room.mode.lowercase().replaceFirstChar { it.uppercase() }} · ${room.visibility.lowercase().replaceFirstChar { it.uppercase() }} · ${room.players.size}/${room.maxPlayers}",
            color = GoldHome,
            fontWeight = FontWeight.Bold
        )
        Surface(color = GoldHome.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Share this code with friends: ${room.code}",
                modifier = Modifier.padding(16.dp),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Text("Players", color = Color.White, fontWeight = FontWeight.Bold)
        repeat(room.maxPlayers) { seat ->
            val player = room.players.firstOrNull { it.seat == seat }
            Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(player?.name ?: "Waiting for player…", color = if (player == null) SoftWhiteHome.copy(alpha = 0.5f) else Color.White)
                    if (player?.isHost == true) Text("HOST", color = GoldHome, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Surface(color = TealHome.copy(alpha = 0.16f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                if (room.isHost) "Online room connectivity is active. The host sees players join this table automatically."
                else "Connected. Waiting for the host and remaining players.",
                modifier = Modifier.padding(16.dp),
                color = SoftWhiteHome,
                fontSize = 13.sp
            )
        }
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White.copy(alpha = 0.07f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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
