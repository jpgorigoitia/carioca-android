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
import androidx.compose.foundation.verticalScroll
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
import com.carioca.game.domain.GameEngine
import com.carioca.game.domain.GameRules
import com.carioca.game.domain.GameState
import com.carioca.game.domain.Meld
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

private val OnlineHandWidth = 50.dp
private val OnlineHandHeight = 73.dp
private val OnlinePileWidth = 58.dp
private val OnlinePileHeight = 80.dp
private data class OnlineMeldTarget(val viewOwner: Int, val meld: Int)

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
            delay(650)
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
                    Spacer(Modifier.height(10.dp))
                    Text("Loading table…", color = Color.White)
                    error?.let { Text(it, color = OnlineDanger, fontSize = 10.sp) }
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
                onAdd = { owner, meldIndex, cards -> submit { s, seat -> NetworkGameEngine.addToMeld(s, seat, owner, meldIndex, cards) } },
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
    onAdd: (Int, Int, Set<GameCard>) -> Unit,
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
    var meldBounds by remember { mutableStateOf<Map<OnlineMeldTarget, Rect>>(emptyMap()) }
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
        val group = if (card in selected && selected.isNotEmpty()) selected else setOf(card)
        val target = meldBounds.entries.firstOrNull { it.value.contains(point) }?.key
        when {
            discardBounds != Rect.Zero && discardBounds.contains(point) -> { selected = emptySet(); onDiscard(card) }
            target != null -> {
                selected = emptySet()
                val originalOwner = order[target.viewOwner]
                onAdd(originalOwner, target.meld, group)
            }
            tableBounds != Rect.Zero && tableBounds.contains(point) -> { selected = emptySet(); onLay(group) }
        }
        dragPoint = null; floating = null
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(OnlineNavy, OnlineDeep)))) {
        OnlineBackgroundTexture()
        Column(Modifier.fillMaxSize().padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            OnlineHud(view, roomCode, busy, sfx, onExit) { sfx = !sfx }
            OnlineOpponents(view)
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .background(Brush.radialGradient(listOf(OnlineFelt, OnlineFeltDark)), RoundedCornerShape(24.dp))
                    .border(2.dp, OnlineGold.copy(alpha = .24f), RoundedCornerShape(24.dp))
            ) {
                OnlineFeltTexture()
                if (view.phase == TurnPhase.ROUND_OVER || view.phase == TurnPhase.GAME_OVER) {
                    OnlineRoundSummary(view, onNextRound, onExit)
                } else {
                    Box(Modifier.fillMaxSize().padding(7.dp).onGloballyPositioned { tableBounds = it.onlineBounds() }) {
                        OnlineMeldTable(
                            state = view,
                            dragPoint = dragPoint,
                            onMeldBounds = { target, bounds -> meldBounds = meldBounds + (target to bounds) },
                            modifier = Modifier.fillMaxSize().padding(start = 162.dp, end = 86.dp, top = 4.dp, bottom = 31.dp)
                        )
                        OnlineGoalInfo(view, Modifier.align(Alignment.TopStart).width(154.dp))
                        OnlinePileDock(
                            state = view,
                            busy = busy,
                            handBounds = handBounds,
                            setDiscardBounds = { discardBounds = it },
                            onDrag = { dragPoint = it },
                            onDraw = onDraw,
                            onSteal = onSteal,
                            modifier = Modifier.align(Alignment.CenterEnd).width(78.dp)
                        )
                        Surface(color = OnlineInk.copy(alpha = .42f), shape = RoundedCornerShape(10.dp), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(.72f)) {
                            Text(view.message, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = OnlineSoft, fontSize = 9.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            OnlineHand(
                state = view,
                selected = selected,
                busy = busy,
                setHandBounds = { handBounds = it },
                onToggle = { card -> if (view.currentPlayer == 0 && view.phase == TurnPhase.ACTION && !busy) selected = selected.toMutableSet().also { if (!it.add(card)) it.remove(card) } },
                onDragPoint = { dragPoint = it },
                onFloating = { floating = it },
                onDrop = ::drop,
                modifier = Modifier.fillMaxWidth().height(104.dp)
            )
            error?.let { Text(it, color = OnlineDanger, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
        if (floating != null && dragPoint != null) OnlineFloatingCard(floating!!, dragPoint!!)
    }
}

@Composable
private fun OnlineHud(state: GameState, code: String, busy: Boolean, sfx: Boolean, onExit: () -> Unit, onSfx: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExit, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("← Exit", color = OnlineMuted, fontSize = 11.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TABLE $code · ROUND ${state.roundIndex + 1}/${state.mode.rounds}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(state.roundRule.onlineDescription().uppercase(), color = OnlineGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnlineGold)
        IconButton(onClick = onSfx, modifier = Modifier.size(34.dp)) { Text(if (sfx) "🔊" else "🔇", fontSize = 15.sp) }
    }
}

@Composable
private fun OnlineOpponents(state: GameState) {
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        state.players.drop(1).forEachIndexed { index, player ->
            val active = state.currentPlayer == index + 1
            Surface(color = Color.White.copy(alpha = .07f), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(if (active) 2.dp else 1.dp, if (active) OnlineGold else Color.White.copy(alpha = .08f))) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OnlineAvatar(index + 1, active, 34.dp); Spacer(Modifier.width(6.dp))
                    Column {
                        Text(player.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${player.hand.size} cards · ${player.score} pts", color = OnlineMuted, fontSize = 7.sp)
                        if (player.melds.isNotEmpty()) Text("${player.melds.size} melds", color = OnlineGold, fontSize = 7.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineGoalInfo(state: GameState, modifier: Modifier) {
    val me = state.players.first()
    val remaining = GameEngine.remainingRequirements(me, state.roundRule)
    val complete = GameEngine.contractComplete(me, state.roundRule)
    val ready = !complete && GameEngine.contractReady(me, state.roundRule)
    Surface(modifier, color = OnlineInk.copy(alpha = .80f), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (ready || complete) OnlineLegal.copy(alpha = .8f) else Color.White.copy(alpha = .09f))) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ROUND GOAL", color = OnlineGold, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(state.roundRule.onlineDescription(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${GameRules.requiredCardCount(state.roundRule)} contract cards", color = OnlineMuted, fontSize = 8.sp)
            Text(
                when {
                    complete -> "✓ MELDED"
                    ready -> "✓ GOAL READY"
                    else -> "Need ${GameEngine.cardsStillRequired(me, state.roundRule)} cards in ${remaining.size} meld${if (remaining.size == 1) "" else "s"}"
                },
                color = if (ready || complete) OnlineLegal else OnlineSoft,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            if (remaining.isNotEmpty()) Text(remaining.joinToString(" + ") { it.onlineLabel() }, color = OnlineSoft, fontSize = 7.sp, lineHeight = 9.sp)
        }
    }
}

@Composable
private fun OnlinePileDock(
    state: GameState,
    busy: Boolean,
    handBounds: Rect,
    setDiscardBounds: (Rect) -> Unit,
    onDrag: (Offset?) -> Unit,
    onDraw: () -> Unit,
    onSteal: () -> Unit,
    modifier: Modifier
) {
    val canDraw = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW && !busy
    val canSteal = canDraw && state.discardPile.isNotEmpty()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("DRAG", color = OnlineMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        OnlinePileBack(state.drawPile.size, canDraw, handBounds, onDrag, onDraw)
        val top = state.discardPile.lastOrNull()
        Box(Modifier.size(OnlinePileWidth, OnlinePileHeight).onGloballyPositioned { setDiscardBounds(it.onlineBounds()) }, contentAlignment = Alignment.Center) {
            if (top != null) OnlineDiscard(top, canSteal, handBounds, onDrag, onSteal)
            else Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Text("DISCARD", color = Color.White.copy(alpha = .35f), fontSize = 6.sp) }
        }
    }
}

@Composable
private fun OnlineMeldTable(state: GameState, dragPoint: Offset?, onMeldBounds: (OnlineMeldTarget, Rect) -> Unit, modifier: Modifier) {
    val totalCards = state.players.sumOf { player -> player.melds.sumOf { it.cards.size } }
    val width = when {
        totalCards >= 36 -> 27.dp
        totalCards >= 24 -> 30.dp
        else -> 34.dp
    }
    val height = width * 1.47f
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (state.players.all { it.melds.isEmpty() }) {
            Box(Modifier.fillMaxWidth().height(128.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MELD AREA", color = Color.White.copy(alpha = .42f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text("Select the complete round contract and drag it here", color = Color.White.copy(alpha = .48f), fontSize = 9.sp)
                }
            }
        }
        state.players.forEachIndexed { ownerIndex, player ->
            if (player.melds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        OnlineAvatar(ownerIndex, state.currentPlayer == ownerIndex, 28.dp)
                        Text(player.name, color = Color.White, fontSize = 7.sp, maxLines = 1)
                    }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        player.melds.forEachIndexed { meldIndex, meld ->
                            val target = OnlineMeldTarget(ownerIndex, meldIndex)
                            val boundsHolder = remember { mutableStateOf(Rect.Zero) }
                            val over = dragPoint?.let { boundsHolder.value != Rect.Zero && boundsHolder.value.contains(it) } == true
                            OnlineMeldFan(
                                meld = meld,
                                cardWidth = width,
                                cardHeight = height,
                                highlight = over,
                                modifier = Modifier.onGloballyPositioned {
                                    val bounds = it.onlineBounds(); boundsHolder.value = bounds; onMeldBounds(target, bounds)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineMeldFan(meld: Meld, cardWidth: androidx.compose.ui.unit.Dp, cardHeight: androidx.compose.ui.unit.Dp, highlight: Boolean, modifier: Modifier) {
    Surface(modifier, color = OnlineInk.copy(alpha = .40f), shape = RoundedCornerShape(11.dp), border = androidx.compose.foundation.BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) OnlineLegal else Color.White.copy(alpha = .08f))) {
        Column(Modifier.padding(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(meld.type.onlineLabel().uppercase(), color = OnlineGold, fontSize = 6.sp, fontWeight = FontWeight.Black)
                Text("${meld.cards.size}", color = OnlineMuted, fontSize = 6.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(-(cardWidth * .56f))) {
                meld.cards.forEach { card -> OnlineCardFace(card, false, Modifier.size(cardWidth, cardHeight), compact = true) }
            }
        }
    }
}

@Composable
private fun OnlineHand(
    state: GameState,
    selected: Set<GameCard>,
    busy: Boolean,
    setHandBounds: (Rect) -> Unit,
    onToggle: (GameCard) -> Unit,
    onDragPoint: (Offset?) -> Unit,
    onFloating: (GameCard?) -> Unit,
    onDrop: (GameCard, Offset) -> Unit,
    modifier: Modifier
) {
    val cards = state.players.first().hand.sortedWith(compareBy<GameCard>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy }))
    Box(modifier.onGloballyPositioned { setHandBounds(it.onlineBounds()) }.background(OnlineInk.copy(alpha = .76f), RoundedCornerShape(18.dp)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(108.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OnlineAvatar(0, state.currentPlayer == 0, 30.dp); Spacer(Modifier.width(5.dp))
                    Column {
                        Text("YOUR HAND", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("${cards.size} cards", color = OnlineMuted, fontSize = 7.sp)
                    }
                }
                Text(if (state.currentPlayer == 0) if (state.phase == TurnPhase.DRAW) "Drag a pile here" else "Drag cards to meld/discard" else "Waiting for ${state.players.getOrNull(state.currentPlayer)?.name ?: "player"}", color = OnlineMuted, fontSize = 6.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((-9).dp), verticalAlignment = Alignment.CenterVertically) {
                cards.forEach { card ->
                    OnlineDraggableCard(
                        card,
                        card in selected,
                        state.currentPlayer == 0 && state.phase == TurnPhase.ACTION && !busy,
                        { onToggle(card) },
                        onDragPoint,
                        onFloating
                    ) { p -> onDrop(card, p) }
                }
            }
            Text(if (state.phase == TurnPhase.DRAW && state.currentPlayer == 0) "DRAG\nA PILE\nHERE" else "DRAG\nTO MELD\nOR DISCARD", Modifier.width(68.dp), color = if (state.phase == TurnPhase.DRAW && state.currentPlayer == 0) OnlineGold else OnlineMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
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
    OnlineCardFace(
        card,
        selected,
        Modifier.padding(horizontal = 1.dp).size(OnlineHandWidth, OnlineHandHeight).offset(y = if (selected && !dragging) (-5).dp else 0.dp)
            .onGloballyPositioned { measured = it.size; if (!dragging) origin = it.positionInRoot() }
            .graphicsLayer { alpha = if (dragging) .25f else 1f }
            .pointerInput(enabled, card) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true; delta = Offset.Zero; onFloating(card); onDrag(center()) },
                    onDragCancel = { dragging = false; delta = Offset.Zero; onFloating(null); onDrag(null) },
                    onDragEnd = { val p = center(); dragging = false; onDrop(p); delta = Offset.Zero; onFloating(null); onDrag(null) }
                ) { change, amount -> change.consume(); delta += amount; onDrag(center()) }
            }.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun OnlineFloatingCard(card: GameCard, center: Offset) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { OnlineHandWidth.toPx() }; val h = with(density) { OnlineHandHeight.toPx() }
    OnlineCardFace(card, true, Modifier.offset { IntOffset((center.x - w / 2).roundToInt(), (center.y - h / 2).roundToInt()) }.size(OnlineHandWidth, OnlineHandHeight).zIndex(100f).graphicsLayer { scaleX = 1.10f; scaleY = 1.10f; shadowElevation = 16.dp.toPx() })
}

@Composable
private fun OnlinePileBack(count: Int, enabled: Boolean, handBounds: Rect, onDrag: (Offset?) -> Unit, onDrop: () -> Unit) {
    OnlineDraggablePile(enabled, handBounds, onDrag, onDrop) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(OnlineBack).border(2.dp, OnlineSoft, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CARIOCA", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Text(count.toString(), color = OnlineGold, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("DRAW", color = OnlineSoft, fontSize = 6.sp)
            }
        }
    }
}

@Composable
private fun OnlineDiscard(card: GameCard, enabled: Boolean, handBounds: Rect, onDrag: (Offset?) -> Unit, onDrop: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "onlineSteal")
    val glow by transition.animateFloat(.3f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "onlineGlow")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OnlineDraggablePile(enabled, handBounds, onDrag, onDrop) { OnlineCardFace(card, false, Modifier.fillMaxSize().border(if (enabled) 3.dp else 1.dp, if (enabled) OnlineGold.copy(alpha = glow) else Color.LightGray, RoundedCornerShape(9.dp))) }
        if (enabled) Text("STEAL +2", color = OnlineGold, fontSize = 6.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun OnlineDraggablePile(enabled: Boolean, handBounds: Rect, onDrag: (Offset?) -> Unit, onDrop: () -> Unit, content: @Composable () -> Unit) {
    var delta by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    fun center() = origin + delta + Offset(measured.width / 2f, measured.height / 2f)
    Box(
        Modifier.size(OnlinePileWidth, OnlinePileHeight).onGloballyPositioned { measured = it.size; if (!dragging) origin = it.positionInRoot() }
            .zIndex(if (dragging) 20f else 1f).graphicsLayer { translationX = delta.x; translationY = delta.y; scaleX = if (dragging) 1.08f else 1f; scaleY = if (dragging) 1.08f else 1f }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true; delta = Offset.Zero; onDrag(center()) },
                    onDragCancel = { dragging = false; delta = Offset.Zero; onDrag(null) },
                    onDragEnd = { val p = center(); dragging = false; if (handBounds != Rect.Zero && handBounds.contains(p)) onDrop(); delta = Offset.Zero; onDrag(null) }
                ) { change, amount -> change.consume(); delta += amount; onDrag(center()) }
            }
    ) { content() }
}

@Composable
private fun OnlineCardFace(card: GameCard, selected: Boolean, modifier: Modifier, compact: Boolean = false) {
    val ink = if (card.suit?.red == true) OnlineRed else OnlineInk
    Card(modifier = modifier, shape = RoundedCornerShape(if (compact) 6.dp else 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)), elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 1.dp else 2.dp)) {
        Box(Modifier.fillMaxSize().border(if (selected) 3.dp else 1.dp, if (selected) OnlineGold else Color(0xFFD7D9DC), RoundedCornerShape(if (compact) 6.dp else 8.dp)).padding(if (compact) 3.dp else 4.dp)) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.rank.onlineRank(), color = ink, fontSize = if (compact) 8.sp else 11.sp, fontWeight = FontWeight.Black, lineHeight = if (compact) 8.sp else 11.sp)
                Text(if (card.isJoker) "★" else card.suit?.onlineSymbol().orEmpty(), color = if (card.isJoker) OnlineTeal else ink, fontSize = if (compact) 7.sp else 8.sp, lineHeight = if (compact) 7.sp else 8.sp)
            }
            Text(if (card.isJoker) "★" else card.suit?.onlineSymbol().orEmpty(), Modifier.align(Alignment.Center), color = if (card.isJoker) OnlineTeal else ink, fontSize = if (compact) 16.sp else 21.sp, fontWeight = FontWeight.Bold)
            if (card.isJoker) Text("J", Modifier.align(Alignment.BottomCenter), color = OnlineTeal, fontSize = if (compact) 5.sp else 6.sp, fontWeight = FontWeight.Black)
            else Text(card.suit?.onlineSymbol().orEmpty(), Modifier.align(Alignment.BottomEnd), color = ink, fontSize = if (compact) 7.sp else 9.sp)
        }
    }
}

@Composable
private fun OnlineRoundSummary(state: GameState, onNext: () -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = OnlineInk.copy(alpha = .93f), shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 440.dp).padding(12.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(state.message, color = OnlineGold, textAlign = TextAlign.Center, fontSize = 11.sp)
                state.players.sortedBy { it.score }.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${i + 1}. ${p.name}", color = Color.White, fontSize = 10.sp)
                        Text("+${p.roundPoints} · ${p.score}", color = if (i == 0) OnlineGold else OnlineSoft, fontSize = 10.sp)
                    }
                }
                if (state.phase == TurnPhase.ROUND_OVER) Button(onClick = onNext, enabled = state.currentPlayer == 0, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text(if (state.currentPlayer == 0) "Deal Next Round" else "Waiting for round winner") }
                else Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text("Return") }
            }
        }
    }
}

@Composable
private fun OnlineAvatar(index: Int, active: Boolean, avatarSize: androidx.compose.ui.unit.Dp) {
    val accents = listOf(Color(0xFFFF8DA1), Color(0xFF86D7E8), Color(0xFFB4A0FF), Color(0xFF7EE0BD))
    val accent = accents[index % accents.size]
    Box(Modifier.size(avatarSize).clip(CircleShape).background(if (active) OnlineGold else accent.copy(alpha = .38f)).padding(3.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val hair = listOf(Color(0xFF49352F), Color(0xFF263A57), Color(0xFF70432D), Color(0xFF302D2B))[index % 4]
            drawCircle(hair, size.minDimension * .43f, Offset(center.x, center.y - size.height * .07f))
            drawCircle(Color(0xFFFFD3B8), size.minDimension * .34f, Offset(center.x, center.y + size.height * .04f))
            drawCircle(OnlineInk, size.minDimension * .035f, Offset(center.x - size.width * .11f, center.y))
            drawCircle(OnlineInk, size.minDimension * .035f, Offset(center.x + size.width * .11f, center.y))
        }
    }
}

@Composable
private fun OnlineFeltTexture() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 31.dp.toPx(); var x = -size.height
        while (x < size.width + size.height) { drawLine(Color.White.copy(alpha = .014f), Offset(x, 0f), Offset(x + size.height, size.height), 1.dp.toPx()); x += step }
    }
}

@Composable
private fun OnlineBackgroundTexture() {
    Canvas(Modifier.fillMaxSize()) {
        val step = size.minDimension / 8f
        repeat(14) { i -> drawCircle(Color.White.copy(alpha = .014f), step * (.25f + (i % 3) * .07f), Offset((i * step * 1.65f) % size.width, (i * step * 2.1f) % size.height)) }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.onlineBounds(): Rect {
    val p = positionInRoot()
    return Rect(p, Size(size.width.toFloat(), size.height.toFloat()))
}

private fun RoundRule.onlineDescription(): String {
    special?.let { return it.onlineLabel() }
    val p = mutableListOf<String>()
    if (legs > 0) p += "$legs ${if (legs == 1) "leg" else "legs"}"
    if (straights > 0) p += "$straights ${if (straights == 1) "straight" else "straights"}"
    return p.joinToString(" + ")
}

private fun MeldType.onlineLabel(): String = when (this) {
    MeldType.LEG -> "leg"
    MeldType.STRAIGHT -> "straight"
    MeldType.CRAZY_STRAIGHT -> "crazy straight"
    MeldType.COLOUR_STRAIGHT -> "colour straight"
    MeldType.ROYAL_STRAIGHT -> "royal straight"
}

private fun Suit.onlineSymbol(): String = when (this) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
}

private fun Rank.onlineRank(): String = when (this) {
    Rank.ACE -> "A"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    Rank.JOKER -> "J"
    else -> order.toString()
}
