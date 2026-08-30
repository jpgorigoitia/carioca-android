package com.carioca.game

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.carioca.game.domain.Card as GameCard
import com.carioca.game.domain.GameState
import com.carioca.game.domain.MeldType
import com.carioca.game.domain.Rank
import com.carioca.game.domain.RoundRule
import com.carioca.game.domain.Suit
import com.carioca.game.domain.TurnPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val OnlineNavy = Color(0xFF071A29)
private val OnlineDeep = Color(0xFF0C3148)
private val OnlineFelt = Color(0xFF0D5C58)
private val OnlineFeltDark = Color(0xFF073F3E)
private val OnlineInk = Color(0xFF0D2438)
private val OnlineTeal = Color(0xFF1D8B83)
private val OnlineGold = Color(0xFFFFC857)
private val OnlineSoft = Color(0xFFF2FAFB)
private val OnlineMuted = Color(0xFF9FC3CC)
private val OnlineRed = Color(0xFFC93645)
private val OnlineLegal = Color(0xFF73E0C1)
private val OnlineDanger = Color(0xFFFF7B79)
private val OnlineBack = Color(0xFF155B7A)

class OnlineGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val roomId = intent.getStringExtra("room_id").orEmpty()
        val token = intent.getStringExtra("token").orEmpty()
        if (roomId.isBlank() || token.isBlank()) return finish()
        setContent { OnlineGameSession(roomId, token) { finish() } }
    }
}

@Composable
private fun OnlineGameSession(roomId: String, token: String, onExit: () -> Unit) {
    var room by remember { mutableStateOf<OnlineRoom?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    LaunchedEffect(roomId, token) {
        while (true) {
            runCatching { withContext(Dispatchers.IO) { MultiplayerClient.snapshot(roomId, token) } }
                .onSuccess { room = it; error = null }
                .onFailure { error = it.message ?: "Connection lost" }
            delay(700)
        }
    }

    fun submit(transform: (GameState, Int) -> GameState) {
        val snapshot = room ?: return
        val current = snapshot.gameState ?: return
        if (busy) return
        busy = true
        scope.launch {
            runCatching {
                val next = transform(current, snapshot.mySeat)
                withContext(Dispatchers.IO) { MultiplayerClient.updateGame(snapshot.roomId, token, snapshot.version, next) }
            }.onSuccess { room = it; error = null }
                .onFailure {
                    error = it.message ?: "Move could not be synchronized"
                    room = runCatching { withContext(Dispatchers.IO) { MultiplayerClient.snapshot(roomId, token) } }.getOrNull() ?: room
                }
            busy = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = OnlineTeal, secondary = OnlineGold)) {
        val snapshot = room
        if (snapshot?.gameState == null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(OnlineNavy, OnlineDeep))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = OnlineGold)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading table…", color = Color.White)
                    error?.let { Text(it, color = OnlineDanger, fontSize = 11.sp) }
                }
            }
        } else {
            OnlineTable(
                state = snapshot.gameState,
                localSeat = snapshot.mySeat,
                roomCode = snapshot.code,
                busy = busy,
                error = error,
                onDraw = { submit { s, seat -> NetworkGameEngine.drawFromDeck(s, seat) } },
                onSteal = { submit { s, seat -> NetworkGameEngine.stealDiscard(s, seat) } },
                onLay = { cards -> submit { s, seat -> NetworkGameEngine.layCards(s, seat, cards) } },
                onDiscard = { card -> submit { s, seat -> NetworkGameEngine.discard(s, seat, card) } },
                onNextRound = { submit { s, seat -> NetworkGameEngine.nextRound(s, seat) } },
                onExit = onExit
            )
        }
    }
}

@Composable
private fun OnlineTable(
    state: GameState,
    localSeat: Int,
    roomCode: String,
    busy: Boolean,
    error: String?,
    onDraw: () -> Unit,
    onSteal: () -> Unit,
    onLay: (Set<GameCard>) -> Unit,
    onDiscard: (GameCard) -> Unit,
    onNextRound: () -> Unit,
    onExit: () -> Unit
) {
    val order = remember(state.players.size, localSeat) { listOf(localSeat) + state.players.indices.filter { it != localSeat } }
    fun remap(index: Int?): Int? = index?.let { old -> order.indexOf(old).takeIf { it >= 0 } }
    var selected by remember { mutableStateOf<Set<GameCard>>(emptySet()) }
    val localHand = state.players.getOrNull(localSeat)?.hand.orEmpty()
    LaunchedEffect(state, localSeat) { selected = selected.filter { it in localHand }.toSet() }
    val view = state.copy(
        players = order.map { state.players[it] },
        currentPlayer = remap(state.currentPlayer) ?: 0,
        roundWinner = remap(state.roundWinner),
        gameWinner = remap(state.gameWinner),
        selected = selected
    )

    var tableBounds by remember { mutableStateOf(Rect.Zero) }
    var discardBounds by remember { mutableStateOf(Rect.Zero) }
    var handBounds by remember { mutableStateOf(Rect.Zero) }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var floating by remember { mutableStateOf<GameCard?>(null) }
    var sfx by remember { mutableStateOf(true) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 42) }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    val canSteal = view.currentPlayer == 0 && view.phase == TurnPhase.DRAW && view.discardPile.isNotEmpty() && !busy
    LaunchedEffect(view.currentPlayer, view.phase, view.discardPile.size, sfx) {
        if (canSteal && sfx) tone.startTone(ToneGenerator.TONE_PROP_ACK, 100)
    }

    fun drop(card: GameCard, point: Offset) {
        if (busy || view.currentPlayer != 0 || view.phase != TurnPhase.ACTION) return
        when {
            discardBounds != Rect.Zero && discardBounds.contains(point) -> { selected = emptySet(); onDiscard(card) }
            tableBounds != Rect.Zero && tableBounds.contains(point) -> {
                val group = if (card in selected && selected.isNotEmpty()) selected else setOf(card)
                selected = emptySet(); onLay(group)
            }
        }
        dragPoint = null; floating = null
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(OnlineNavy, OnlineDeep)))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize().padding(if (landscape) 10.dp else 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OnlineHud(view, roomCode, busy, sfx, onExit) { sfx = !sfx }
                OnlineOpponents(view)
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .background(Brush.radialGradient(listOf(OnlineFelt, OnlineFeltDark)), RoundedCornerShape(26.dp))
                        .border(2.dp, OnlineGold.copy(alpha = .24f), RoundedCornerShape(26.dp))
                ) {
                    OnlineFeltTexture()
                    if (view.phase == TurnPhase.ROUND_OVER || view.phase == TurnPhase.GAME_OVER) {
                        OnlineRoundSummary(view, onNextRound, onExit)
                    } else if (landscape) {
                        Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OnlineContract(view, Modifier.width(160.dp).fillMaxHeight())
                            OnlineCenter(
                                view, busy, dragPoint, tableBounds, discardBounds, handBounds,
                                { tableBounds = it }, { discardBounds = it }, { dragPoint = it },
                                onDraw, onSteal, Modifier.weight(1f).fillMaxHeight()
                            )
                            OnlineActions(view, selected, busy,
                                onLay = { if (selected.isNotEmpty()) { val c = selected; selected = emptySet(); onLay(c) } },
                                onDiscard = { selected.singleOrNull()?.let { c -> selected = emptySet(); onDiscard(c) } },
                                modifier = Modifier.width(150.dp).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OnlineContract(view, Modifier.fillMaxWidth().height(64.dp))
                            OnlineCenter(
                                view, busy, dragPoint, tableBounds, discardBounds, handBounds,
                                { tableBounds = it }, { discardBounds = it }, { dragPoint = it },
                                onDraw, onSteal, Modifier.weight(1f).fillMaxWidth()
                            )
                            OnlineActions(view, selected, busy,
                                onLay = { if (selected.isNotEmpty()) { val c = selected; selected = emptySet(); onLay(c) } },
                                onDiscard = { selected.singleOrNull()?.let { c -> selected = emptySet(); onDiscard(c) } },
                                modifier = Modifier.fillMaxWidth().height(54.dp))
                        }
                    }
                }
                OnlineHand(
                    view, selected, busy,
                    setHandBounds = { handBounds = it },
                    onToggle = { card -> if (view.currentPlayer == 0 && view.phase == TurnPhase.ACTION && !busy) selected = selected.toMutableSet().also { if (!it.add(card)) it.remove(card) } },
                    onDragPoint = { dragPoint = it }, onFloating = { floating = it }, onDrop = ::drop,
                    modifier = Modifier.fillMaxWidth().height(if (landscape) 132.dp else 142.dp)
                )
                error?.let { Text(it, color = OnlineDanger, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
        if (floating != null && dragPoint != null) OnlineFloatingCard(floating!!, dragPoint!!)
    }
}

@Composable
private fun OnlineHud(state: GameState, code: String, busy: Boolean, sfx: Boolean, onExit: () -> Unit, onSfx: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExit) { Text("← Exit", color = OnlineMuted) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TABLE $code · ROUND ${state.roundIndex + 1}/${state.mode.rounds}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(state.roundRule.onlineDescription().uppercase(), color = OnlineGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnlineGold)
        IconButton(onClick = onSfx) { Text(if (sfx) "🔊" else "🔇", fontSize = 17.sp) }
    }
}

@Composable
private fun OnlineOpponents(state: GameState) {
    Row(Modifier.fillMaxWidth().height(65.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        state.players.drop(1).forEachIndexed { index, player ->
            val active = state.currentPlayer == index + 1
            Surface(color = Color.White.copy(alpha = .07f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(if (active) 2.dp else 1.dp, if (active) OnlineGold else Color.White.copy(alpha = .08f))) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    OnlineAvatar(index + 1, active); Spacer(Modifier.width(6.dp))
                    Column { Text(player.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("${player.hand.size} cards · ${player.score} pts", color = OnlineMuted, fontSize = 8.sp) }
                }
            }
        }
    }
}

@Composable
private fun OnlineAvatar(index: Int, active: Boolean) {
    val accents = listOf(Color(0xFFFF8DA1), Color(0xFF86D7E8), Color(0xFFB4A0FF), Color(0xFF7EE0BD))
    val accent = accents[index % accents.size]
    Box(Modifier.size(40.dp).clip(CircleShape).background(if (active) OnlineGold else accent.copy(alpha = .35f)).padding(3.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF49352F), size.minDimension * .43f, Offset(center.x, center.y - size.height * .07f))
            drawCircle(Color(0xFFFFD3B8), size.minDimension * .34f, Offset(center.x, center.y + size.height * .04f))
            drawCircle(OnlineInk, size.minDimension * .035f, Offset(center.x - size.width * .11f, center.y))
            drawCircle(OnlineInk, size.minDimension * .035f, Offset(center.x + size.width * .11f, center.y))
            drawCircle(accent.copy(alpha = .6f), size.minDimension * .045f, Offset(center.x - size.width * .19f, center.y + size.height * .09f))
            drawCircle(accent.copy(alpha = .6f), size.minDimension * .045f, Offset(center.x + size.width * .19f, center.y + size.height * .09f))
        }
    }
}

@Composable
private fun OnlineContract(state: GameState, modifier: Modifier) {
    val remaining = com.carioca.game.domain.GameEngine.remainingRequirements(state.players.first(), state.roundRule)
    Surface(modifier = modifier, color = OnlineInk.copy(alpha = .30f), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CONTRACT", color = OnlineGold, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(state.roundRule.onlineDescription(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(if (remaining.isEmpty()) "Complete — add to table melds." else "Remaining: ${remaining.joinToString { it.onlineLabel() }}", color = OnlineSoft, fontSize = 9.sp)
            Text("Your score ${state.players.first().score}", color = OnlineMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun OnlineCenter(
    state: GameState, busy: Boolean, drag: Offset?, tableBounds: Rect, discardBounds: Rect, handBounds: Rect,
    setTable: (Rect) -> Unit, setDiscard: (Rect) -> Unit, onDrag: (Offset?) -> Unit,
    onDraw: () -> Unit, onSteal: () -> Unit, modifier: Modifier
) {
    val action = state.currentPlayer == 0 && state.phase == TurnPhase.ACTION && !busy
    val drawPhase = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW && !busy
    val overTable = drag?.let { tableBounds != Rect.Zero && tableBounds.contains(it) } == true
    val overDiscard = drag?.let { discardBounds != Rect.Zero && discardBounds.contains(it) } == true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { setTable(it.onlineBounds()) }
                .background(if (overTable && action) OnlineLegal.copy(alpha = .16f) else Color.Black.copy(alpha = .08f), RoundedCornerShape(17.dp))
                .border(if (overTable && action) 2.dp else 1.dp, if (overTable && action) OnlineLegal else Color.White.copy(alpha = .08f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            OnlineMelds(state)
            if (state.players.all { it.melds.isEmpty() }) Text("DRAG SELECTED CARDS HERE", color = Color.White.copy(alpha = .35f), fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Row(Modifier.fillMaxWidth().height(102.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            OnlinePileBack(state.drawPile.size, drawPhase, handBounds, onDrag, onDraw)
            Spacer(Modifier.width(26.dp))
            Box(Modifier.size(70.dp, 98.dp).onGloballyPositioned { setDiscard(it.onlineBounds()) }.border(if (overDiscard && action) 3.dp else 0.dp, if (overDiscard && action) OnlineDanger else Color.Transparent, RoundedCornerShape(10.dp))) {
                state.discardPile.lastOrNull()?.let { OnlineDiscard(it, drawPhase, handBounds, onDrag, onSteal) }
            }
        }
        Surface(color = OnlineInk.copy(alpha = .28f), shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth()) {
            Text(state.message, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = OnlineSoft, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun OnlineMelds(state: GameState) {
    val melds = state.players.flatMapIndexed { owner, p -> p.melds.map { Triple(owner, p.name, it) } }
    if (melds.isEmpty()) return
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        melds.forEach { (owner, name, meld) ->
            Surface(color = OnlineInk.copy(alpha = .38f), shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.padding(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { OnlineAvatar(owner, false); Spacer(Modifier.width(4.dp)); Text("$name · ${meld.type.onlineLabel()}", color = OnlineGold, fontSize = 7.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy((-22).dp)) { meld.cards.forEach { OnlineCardFace(it, false, Modifier.size(42.dp, 62.dp)) } }
                }
            }
        }
    }
}

@Composable
private fun OnlineActions(state: GameState, selected: Set<GameCard>, busy: Boolean, onLay: () -> Unit, onDiscard: () -> Unit, modifier: Modifier) {
    val action = state.currentPlayer == 0 && state.phase == TurnPhase.ACTION && !busy
    Surface(modifier = modifier, color = OnlineInk.copy(alpha = .30f), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Button(onClick = onLay, enabled = action && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Lay", fontSize = 10.sp) }
            OutlinedButton(onClick = onDiscard, enabled = action && selected.size == 1, modifier = Modifier.fillMaxWidth()) { Text("Discard", fontSize = 10.sp) }
            Text("Tap to select · drag to table/discard", color = OnlineMuted, fontSize = 8.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun OnlineHand(
    state: GameState, selected: Set<GameCard>, busy: Boolean, setHandBounds: (Rect) -> Unit,
    onToggle: (GameCard) -> Unit, onDragPoint: (Offset?) -> Unit, onFloating: (GameCard?) -> Unit,
    onDrop: (GameCard, Offset) -> Unit, modifier: Modifier
) {
    val cards = state.players.first().hand.sortedWith(compareBy<GameCard>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy }))
    Box(modifier.onGloballyPositioned { setHandBounds(it.onlineBounds()) }.background(OnlineInk.copy(alpha = .72f), RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(20.dp))) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OnlineAvatar(0, state.currentPlayer == 0); Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text("YOUR HAND · ${cards.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(if (state.currentPlayer == 0) if (state.phase == TurnPhase.DRAW) "Drag a pile into your hand" else "Select and drag cards" else "Waiting for ${state.players.getOrNull(state.currentPlayer)?.name ?: "player"}", color = OnlineMuted, fontSize = 8.sp)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                cards.forEach { card -> OnlineDraggableCard(card, card in selected, state.currentPlayer == 0 && state.phase == TurnPhase.ACTION && !busy, { onToggle(card) }, onDragPoint, onFloating) { p -> onDrop(card, p) } }
            }
        }
    }
}

@Composable
private fun OnlineDraggableCard(card: GameCard, selected: Boolean, enabled: Boolean, onClick: () -> Unit, onDrag: (Offset?) -> Unit, onFloating: (GameCard?) -> Unit, onDrop: (Offset) -> Unit) {
    var delta by remember(card) { mutableStateOf(Offset.Zero) }
    var origin by remember(card) { mutableStateOf(Offset.Zero) }
    var measured by remember(card) { mutableStateOf(IntSize.Zero) }
    var dragging by remember(card) { mutableStateOf(false) }
    fun center() = origin + delta + Offset(measured.width / 2f, measured.height / 2f)
    OnlineCardFace(card, selected,
        Modifier.padding(horizontal = 2.dp).size(61.dp, 90.dp).offset(y = if (selected && !dragging) (-7).dp else 0.dp)
            .onGloballyPositioned { measured = it.size; if (!dragging) origin = it.positionInRoot() }
            .graphicsLayer { alpha = if (dragging) .25f else 1f }
            .pointerInput(enabled, card) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true; delta = Offset.Zero; onFloating(card); onDrag(center()) },
                    onDragCancel = { dragging = false; delta = Offset.Zero; onFloating(null); onDrag(null) },
                    onDragEnd = { val p = center(); dragging = false; onDrop(p); delta = Offset.Zero; onFloating(null); onDrag(null) }
                ) { change, amount -> change.consume(); delta += amount; onDrag(center()) }
            }.clickable(enabled = enabled, onClick = onClick))
}

@Composable
private fun OnlineFloatingCard(card: GameCard, center: Offset) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { 61.dp.toPx() }; val h = with(density) { 90.dp.toPx() }
    OnlineCardFace(card, true, Modifier.offset { IntOffset((center.x - w / 2).roundToInt(), (center.y - h / 2).roundToInt()) }.size(61.dp, 90.dp).zIndex(100f).graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; shadowElevation = 20.dp.toPx() })
}

@Composable
private fun OnlinePileBack(count: Int, enabled: Boolean, handBounds: Rect, onDrag: (Offset?) -> Unit, onDrop: () -> Unit) {
    OnlineDraggablePile(enabled, handBounds, onDrag, onDrop) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(OnlineBack).border(2.dp, OnlineSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("CARIOCA", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(count.toString(), color = OnlineGold, fontSize = 16.sp, fontWeight = FontWeight.Black); Text("DRAW", color = OnlineSoft, fontSize = 7.sp) }
        }
    }
}

@Composable
private fun OnlineDiscard(card: GameCard, enabled: Boolean, handBounds: Rect, onDrag: (Offset?) -> Unit, onDrop: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "onlineSteal")
    val glow by transition.animateFloat(.3f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "onlineGlow")
    OnlineDraggablePile(enabled, handBounds, onDrag, onDrop) { OnlineCardFace(card, false, Modifier.fillMaxSize().border(if (enabled) 4.dp else 1.dp, if (enabled) OnlineGold.copy(alpha = glow) else Color.LightGray, RoundedCornerShape(10.dp))) }
}

@Composable
private fun OnlineDraggablePile(enabled: Boolean, handBounds: Rect, onDrag: (Offset?) -> Unit, onDrop: () -> Unit, content: @Composable () -> Unit) {
    var delta by remember { mutableStateOf(Offset.Zero) }; var origin by remember { mutableStateOf(Offset.Zero) }; var measured by remember { mutableStateOf(IntSize.Zero) }; var dragging by remember { mutableStateOf(false) }
    fun center() = origin + delta + Offset(measured.width / 2f, measured.height / 2f)
    Box(Modifier.size(70.dp, 98.dp).onGloballyPositioned { measured = it.size; if (!dragging) origin = it.positionInRoot() }.zIndex(if (dragging) 20f else 1f).graphicsLayer { translationX = delta.x; translationY = delta.y; scaleX = if (dragging) 1.08f else 1f; scaleY = if (dragging) 1.08f else 1f }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectDragGestures(
                onDragStart = { dragging = true; delta = Offset.Zero; onDrag(center()) },
                onDragCancel = { dragging = false; delta = Offset.Zero; onDrag(null) },
                onDragEnd = { val p = center(); dragging = false; if (handBounds != Rect.Zero && handBounds.contains(p)) onDrop(); delta = Offset.Zero; onDrag(null) }
            ) { change, amount -> change.consume(); delta += amount; onDrag(center()) }
        }.clickable(enabled = enabled, onClick = onDrop)) { content() }
}

@Composable
private fun OnlineCardFace(card: GameCard, selected: Boolean, modifier: Modifier) {
    val ink = if (card.suit?.red == true) OnlineRed else OnlineInk
    Card(modifier = modifier, shape = RoundedCornerShape(9.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Box(Modifier.fillMaxSize().border(if (selected) 3.dp else 1.dp, if (selected) OnlineGold else Color(0xFFD7D9DC), RoundedCornerShape(9.dp)).padding(5.dp)) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) { Text(card.rank.onlineRank(), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Black, lineHeight = 13.sp); Text(if (card.isJoker) "★" else card.suit?.onlineSymbol().orEmpty(), color = if (card.isJoker) OnlineTeal else ink, fontSize = 10.sp, lineHeight = 10.sp) }
            Text(if (card.isJoker) "★" else card.suit?.onlineSymbol().orEmpty(), Modifier.align(Alignment.Center), color = if (card.isJoker) OnlineTeal else ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            if (card.isJoker) Text("JOKER", Modifier.align(Alignment.BottomCenter), color = OnlineTeal, fontSize = 6.sp, fontWeight = FontWeight.Black) else Text(card.suit?.onlineSymbol().orEmpty(), Modifier.align(Alignment.BottomEnd), color = ink, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OnlineRoundSummary(state: GameState, onNext: () -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = OnlineInk.copy(alpha = .93f), shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(max = 470.dp).padding(16.dp)) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(state.message, color = OnlineGold, textAlign = TextAlign.Center)
                state.players.sortedBy { it.score }.forEachIndexed { i, p -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${i + 1}. ${p.name}", color = Color.White); Text("+${p.roundPoints} · ${p.score}", color = if (i == 0) OnlineGold else OnlineSoft) } }
                if (state.phase == TurnPhase.ROUND_OVER) Button(onClick = onNext, enabled = state.currentPlayer == 0, modifier = Modifier.fillMaxWidth()) { Text(if (state.currentPlayer == 0) "Deal Next Round" else "Waiting for round winner") }
                else Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Return") }
            }
        }
    }
}

@Composable
private fun OnlineFeltTexture() {
    Canvas(Modifier.fillMaxSize()) { val step = 34.dp.toPx(); var x = -size.height; while (x < size.width + size.height) { drawLine(Color.White.copy(alpha = .015f), Offset(x, 0f), Offset(x + size.height, size.height), 1.dp.toPx()); x += step } }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.onlineBounds(): Rect { val p = positionInRoot(); return Rect(p, Size(size.width.toFloat(), size.height.toFloat())) }
private fun RoundRule.onlineDescription(): String { special?.let { return it.onlineLabel() }; val p = mutableListOf<String>(); if (legs > 0) p += "$legs ${if (legs == 1) "leg" else "legs"}"; if (straights > 0) p += "$straights ${if (straights == 1) "straight" else "straights"}"; return p.joinToString(" + ") }
private fun MeldType.onlineLabel(): String = when (this) { MeldType.LEG -> "leg"; MeldType.STRAIGHT -> "straight"; MeldType.CRAZY_STRAIGHT -> "crazy straight"; MeldType.COLOUR_STRAIGHT -> "colour straight"; MeldType.ROYAL_STRAIGHT -> "royal straight" }
private fun Suit.onlineSymbol(): String = when (this) { Suit.CLUBS -> "♣"; Suit.DIAMONDS -> "♦"; Suit.HEARTS -> "♥"; Suit.SPADES -> "♠" }
private fun Rank.onlineRank(): String = when (this) { Rank.ACE -> "A"; Rank.JACK -> "J"; Rank.QUEEN -> "Q"; Rank.KING -> "K"; Rank.JOKER -> "J"; else -> order.toString() }
