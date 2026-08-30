package com.carioca.game

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlin.math.abs
import kotlin.math.roundToInt

private val POnlineNavy = Color(0xFF071A29)
private val POnlineDeep = Color(0xFF0C3148)
private val POnlineFelt = Color(0xFF0D5C58)
private val POnlineFeltDark = Color(0xFF073F3E)
private val POnlineInk = Color(0xFF0D2438)
private val POnlineTeal = Color(0xFF1D8B83)
private val POnlineGold = Color(0xFFFFC857)
private val POnlineSoft = Color(0xFFF2FAFB)
private val POnlineMuted = Color(0xFF9FC3CC)
private val POnlineLegal = Color(0xFF73E0C1)
private val POnlineDanger = Color(0xFFFF7B79)
private val POnlineRed = Color(0xFFC93645)
private val POnlineBack = Color(0xFF155B7A)
private val POnlineShadow = Shadow(Color.Black.copy(alpha = .95f), Offset(2.2f, 2.2f), 4f)
private val POnlineHandW = 50.dp
private val POnlineHandH = 73.dp
private val POnlinePileW = 58.dp
private val POnlinePileH = 80.dp

private data class POnlineMeldTarget(val ownerSeat: Int, val meldIndex: Int)

@Composable
fun OnlinePlayableSession(roomId: String, token: String, onExit: () -> Unit) {
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
                withContext(Dispatchers.IO) {
                    MultiplayerClient.updateGame(snapshot.roomId, token, snapshot.version, next)
                }
            }.onSuccess {
                room = it
                error = null
            }.onFailure {
                error = it.message ?: "Move could not be synchronized"
                room = runCatching { withContext(Dispatchers.IO) { MultiplayerClient.snapshot(roomId, token) } }.getOrNull() ?: room
            }
            busy = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = POnlineTeal, secondary = POnlineGold)) {
        val snapshot = room
        val state = snapshot?.gameState
        if (snapshot == null || state == null) {
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(POnlineNavy, POnlineDeep))),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = POnlineGold)
                    Spacer(Modifier.height(8.dp))
                    Text("Loading table…", color = Color.White)
                    error?.let { Text(it, color = POnlineDanger, fontSize = 9.sp) }
                }
            }
        } else {
            POnlineTable(
                state = state,
                localSeat = snapshot.mySeat,
                roomCode = snapshot.code,
                busy = busy,
                error = error,
                onDraw = { submit { s, seat -> NetworkGameEngine.drawFromDeck(s, seat) } },
                onTakeDiscard = { submit { s, seat -> NetworkGameEngine.takeDiscard(s, seat) } },
                onSteal = { submit { s, seat -> NetworkGameEngine.stealAvailableDiscard(s, seat) } },
                onPass = { submit { s, seat -> NetworkGameEngine.passSteal(s, seat) } },
                onLower = { cards -> submit { s, seat -> NetworkGameEngine.layCards(s, seat, cards) } },
                onAdd = { owner, meld, cards -> submit { s, seat -> NetworkGameEngine.addToMeld(s, seat, owner, meld, cards) } },
                onDiscard = { card -> submit { s, seat -> NetworkGameEngine.discard(s, seat, card) } },
                onNextRound = { submit { s, seat -> NetworkGameEngine.nextRound(s, seat) } },
                onExit = onExit
            )
        }
    }
}

@Composable
private fun POnlineTable(
    state: GameState,
    localSeat: Int,
    roomCode: String,
    busy: Boolean,
    error: String?,
    onDraw: () -> Unit,
    onTakeDiscard: () -> Unit,
    onSteal: () -> Unit,
    onPass: () -> Unit,
    onLower: (Set<GameCard>) -> Unit,
    onAdd: (Int, Int, Set<GameCard>) -> Unit,
    onDiscard: (GameCard) -> Unit,
    onNextRound: () -> Unit,
    onExit: () -> Unit
) {
    val me = state.players.getOrNull(localSeat) ?: return
    var selected by remember { mutableStateOf<Set<GameCard>>(emptySet()) }
    var handOrder by remember { mutableStateOf<List<GameCard>>(emptyList()) }
    var handCardBounds by remember { mutableStateOf<Map<GameCard, Rect>>(emptyMap()) }
    var handBounds by remember { mutableStateOf(Rect.Zero) }
    var discardBounds by remember { mutableStateOf(Rect.Zero) }
    var tableBounds by remember { mutableStateOf(Rect.Zero) }
    var meldBounds by remember { mutableStateOf<Map<POnlineMeldTarget, Rect>>(emptyMap()) }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var floating by remember { mutableStateOf<GameCard?>(null) }
    var sfx by rememberSaveableCompat { mutableStateOf(true) }

    LaunchedEffect(me.hand) {
        selected = selected.filter { it in me.hand }.toSet()
        val current = me.hand.toSet()
        val kept = handOrder.filter { it in current }
        handOrder = kept + me.hand.filter { it !in kept }
    }

    val myTurn = state.currentPlayer == localSeat
    val stealWindow = myTurn && state.phase == TurnPhase.STEAL_WINDOW
    val selectedCards = me.hand.filter { it in selected }
    val remaining = GameEngine.remainingRequirements(me, state.roundRule)
    val fullPlan = if (selectedCards.isNotEmpty() && remaining.isNotEmpty()) GameRules.findMeldPlan(selectedCards, remaining, useAllCards = true) else null
    val partialPlan = if (selectedCards.isNotEmpty() && remaining.isNotEmpty()) GameRules.findPartialMeldPlan(selectedCards, remaining) else null
    val selectedGroups = partialPlan?.size ?: 0
    val canLower = myTurn && state.phase == TurnPhase.ACTION && fullPlan != null && !busy

    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 42) }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    LaunchedEffect(stealWindow, sfx) {
        if (stealWindow && sfx) tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
    }

    fun reorder(card: GameCard, point: Offset) {
        val ordered = handOrder.filter { it in me.hand }.toMutableList()
        if (card !in ordered) return
        ordered.remove(card)
        val nearest = handCardBounds.filterKeys { it != card && it in ordered }
            .minByOrNull { (_, rect) -> abs(rect.center.x - point.x) }?.key
        val target = nearest?.let { ordered.indexOf(it) } ?: ordered.size
        ordered.add(target.coerceIn(0, ordered.size), card)
        handOrder = ordered
    }

    fun drop(card: GameCard, point: Offset) {
        if (!myTurn || busy || state.phase == TurnPhase.STEAL_WINDOW) return
        if (handBounds != Rect.Zero && handBounds.contains(point)) {
            reorder(card, point)
            dragPoint = null
            floating = null
            return
        }
        if (state.phase != TurnPhase.ACTION) return

        val group = if (card in selected && selected.isNotEmpty()) selected else setOf(card)
        val target = meldBounds.entries.firstOrNull { it.value.contains(point) }?.key
        when {
            discardBounds != Rect.Zero && discardBounds.contains(point) -> {
                selected = emptySet()
                onDiscard(card)
            }
            target != null -> onAdd(target.ownerSeat, target.meldIndex, group)
            tableBounds != Rect.Zero && tableBounds.contains(point) -> onLower(group)
        }
        dragPoint = null
        floating = null
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(POnlineNavy, POnlineDeep)))) {
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            POnlineHud(state, localSeat, roomCode, busy, sfx, onExit) { sfx = !sfx }
            POnlineOpponents(state, localSeat)
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .background(Brush.radialGradient(listOf(POnlineFelt, POnlineFeltDark)), RoundedCornerShape(22.dp))
                    .border(2.dp, POnlineGold.copy(alpha = .24f), RoundedCornerShape(22.dp))
            ) {
                if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) {
                    POnlineRoundSummary(state, localSeat, onNextRound, onExit)
                } else {
                    POnlineGoal(
                        state, localSeat, selectedCards.size, selectedGroups, canLower,
                        onLower = { if (canLower) onLower(selected) },
                        modifier = Modifier.align(Alignment.TopStart).padding(7.dp).width(164.dp)
                    )
                    POnlineMeldArea(
                        state = state,
                        dragPoint = dragPoint,
                        canLower = canLower,
                        setBounds = { tableBounds = it },
                        onMeldBounds = { target, rect -> meldBounds = meldBounds + (target to rect) },
                        modifier = Modifier.fillMaxSize().padding(start = 174.dp, end = 158.dp, top = 6.dp, bottom = 26.dp)
                    )
                    POnlinePiles(
                        state = state,
                        localSeat = localSeat,
                        busy = busy,
                        handBounds = handBounds,
                        dragPoint = dragPoint,
                        setDiscardBounds = { discardBounds = it },
                        onDrag = { dragPoint = it },
                        onDraw = onDraw,
                        onTakeDiscard = onTakeDiscard,
                        onSteal = onSteal,
                        onPass = onPass,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(146.dp)
                    )
                    Surface(
                        color = POnlineInk.copy(alpha = .60f),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(.64f).padding(bottom = 4.dp)
                    ) {
                        Text(state.message.pOnlineFriendly(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = POnlineSoft, fontSize = 8.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            POnlineHand(
                state = state,
                localSeat = localSeat,
                cards = handOrder.filter { it in me.hand } + me.hand.filter { it !in handOrder },
                selected = selected,
                busy = busy,
                setHandBounds = { handBounds = it },
                onCardBounds = { card, rect -> handCardBounds = handCardBounds + (card to rect) },
                onToggle = { card ->
                    if (myTurn && state.phase == TurnPhase.ACTION && !busy) {
                        selected = selected.toMutableSet().also { if (!it.add(card)) it.remove(card) }
                    }
                },
                onDrag = { dragPoint = it },
                onFloating = { floating = it },
                onDrop = ::drop,
                modifier = Modifier.fillMaxWidth().height(101.dp)
            )
            error?.let { Text(it, color = POnlineDanger, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }

        if (floating != null && dragPoint != null) {
            val count = if (floating in selected) selected.size.coerceAtLeast(1) else 1
            POnlineFloatingCard(floating!!, dragPoint!!, count)
        }
    }
}

@Composable
private fun POnlineHud(state: GameState, localSeat: Int, code: String, busy: Boolean, sfx: Boolean, onExit: () -> Unit, onSfx: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExit, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("← Exit", color = POnlineMuted, fontSize = 10.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TABLE $code · ROUND ${state.roundIndex + 1}/${state.mode.rounds}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = POnlineShadow))
            Text(state.roundRule.pOnlineGoal().uppercase(), color = POnlineGold, fontSize = 7.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = POnlineShadow))
        }
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = POnlineGold)
        IconButton(onClick = onSfx, modifier = Modifier.size(30.dp)) { Text(if (sfx) "🔊" else "🔇", fontSize = 13.sp) }
        val active = state.players.getOrNull(state.currentPlayer)?.name ?: ""
        Text(if (state.currentPlayer == localSeat) "YOU" else active.take(5), color = if (state.currentPlayer == localSeat) POnlineLegal else POnlineMuted, fontSize = 7.sp, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun POnlineOpponents(state: GameState, localSeat: Int) {
    val seats = state.players.indices.filter { it != localSeat }
    Row(Modifier.fillMaxWidth().height(45.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        seats.forEach { seat ->
            val player = state.players[seat]
            val active = state.currentPlayer == seat
            Surface(
                color = Color.White.copy(alpha = .07f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(if (active) 2.dp else 1.dp, if (active) POnlineGold else Color.White.copy(alpha = .08f))
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(if (active) POnlineGold else POnlineTeal.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                        Text((seat + 1).toString(), color = POnlineInk, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Text(player.name, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("${player.hand.size} cards · ${player.score} pts", color = POnlineMuted, fontSize = 6.sp)
                        if (player.melds.isNotEmpty()) Text("${player.melds.size} melds", color = POnlineGold, fontSize = 6.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun POnlineGoal(
    state: GameState,
    localSeat: Int,
    selectedCount: Int,
    selectedGroups: Int,
    canLower: Boolean,
    onLower: () -> Unit,
    modifier: Modifier
) {
    val me = state.players[localSeat]
    val remaining = GameEngine.remainingRequirements(me, state.roundRule)
    val complete = GameEngine.contractComplete(me, state.roundRule)
    val ready = !complete && GameEngine.contractReady(me, state.roundRule)
    val border = when {
        complete || canLower -> POnlineLegal
        selectedGroups > 0 -> POnlineGold
        else -> Color.White.copy(alpha = .10f)
    }
    Surface(modifier, color = POnlineInk.copy(alpha = .88f), shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(if (complete || canLower || selectedGroups > 0) 2.dp else 1.dp, border)) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ROUND GOAL", color = POnlineGold, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(state.roundRule.pOnlineGoal(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = POnlineShadow))
            Text(state.roundRule.pOnlineExplanation(), color = POnlineSoft, fontSize = 7.sp, lineHeight = 9.sp)
            HorizontalDivider(color = Color.White.copy(alpha = .10f))
            Text(
                when {
                    complete -> "✓ ROUND GOAL LOWERED"
                    canLower -> "✓ COMPLETE SELECTION READY"
                    selectedGroups > 0 -> "$selectedGroups/${remaining.size} required groups selected"
                    ready -> "Goal is available in your hand"
                    else -> "Select each required group"
                },
                color = if (complete || canLower) POnlineLegal else if (selectedGroups > 0) POnlineGold else POnlineSoft,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            if (!complete && remaining.isNotEmpty()) Text("Required: ${remaining.joinToString(" + ") { it.pOnlineLabel() }}", color = POnlineGold, fontSize = 7.sp)
            Text("Selected cards: $selectedCount", color = if (selectedCount > 0) Color.White else POnlineMuted, fontSize = 7.sp)
            Button(onClick = onLower, enabled = canLower, modifier = Modifier.fillMaxWidth().height(34.dp), contentPadding = PaddingValues(4.dp), shape = RoundedCornerShape(9.dp)) {
                Text(if (canLower) "LOWER ROUND GOAL" else if (selectedGroups > 0) "KEEP SELECTING" else if (complete) "GOAL LOWERED" else "SELECT FULL GOAL", fontSize = 7.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun POnlinePiles(
    state: GameState,
    localSeat: Int,
    busy: Boolean,
    handBounds: Rect,
    dragPoint: Offset?,
    setDiscardBounds: (Rect) -> Unit,
    onDrag: (Offset?) -> Unit,
    onDraw: () -> Unit,
    onTakeDiscard: () -> Unit,
    onSteal: () -> Unit,
    onPass: () -> Unit,
    modifier: Modifier
) {
    val myTurn = state.currentPlayer == localSeat
    val normalDraw = myTurn && state.phase == TurnPhase.DRAW && !busy
    val stealWindow = myTurn && state.phase == TurnPhase.STEAL_WINDOW && !busy
    val discardDraggable = (normalDraw || stealWindow) && state.discardPile.isNotEmpty()
    var discardRect by remember { mutableStateOf(Rect.Zero) }
    val hover = dragPoint?.let { discardRect != Rect.Zero && discardRect.contains(it) } == true
    val pulse by rememberInfiniteTransition(label = "onlineStealGlow").animateFloat(
        .42f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "onlineStealPulse"
    )
    val accent = if (stealWindow) POnlineLegal.copy(alpha = pulse) else if (hover) POnlineLegal else POnlineGold.copy(alpha = .75f)

    Surface(modifier, color = POnlineInk.copy(alpha = .78f), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(2.dp, if (stealWindow) POnlineLegal.copy(alpha = pulse) else Color.White.copy(alpha = .12f))) {
        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(if (stealWindow) "STEAL AVAILABLE · +2" else "DRAW / DISCARD", color = if (stealWindow) POnlineLegal else POnlineGold, fontSize = 8.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = POnlineShadow))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.border(if (normalDraw) 2.dp else 0.dp, if (normalDraw) POnlineLegal else Color.Transparent, RoundedCornerShape(9.dp))) {
                        POnlineDraggablePile(normalDraw, handBounds, onDrag, onDraw) { POnlineDrawBack(state.drawPile.size) }
                    }
                    Text("DRAW", color = if (normalDraw) POnlineLegal else POnlineMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(POnlinePileW, POnlinePileH)
                            .onGloballyPositioned {
                                discardRect = it.pOnlineBounds()
                                setDiscardBounds(discardRect)
                            }
                            .border(if (stealWindow || hover) 3.dp else 2.dp, accent, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val top = state.discardPile.lastOrNull()
                        if (top == null) {
                            Box(Modifier.fillMaxSize().background(POnlineInk.copy(alpha = .55f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Text("DISCARD", color = POnlineGold, fontSize = 7.sp, fontWeight = FontWeight.Black)
                            }
                        } else {
                            POnlineDraggablePile(discardDraggable, handBounds, onDrag, if (stealWindow) onSteal else onTakeDiscard) {
                                POnlineCardFace(top, false, Modifier.fillMaxSize())
                            }
                        }
                    }
                    Text(if (stealWindow) "STEAL +2" else "DISCARD", color = if (stealWindow) POnlineLegal else POnlineGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            if (stealWindow) {
                OutlinedButton(onClick = onPass, enabled = !busy, modifier = Modifier.fillMaxWidth().height(30.dp), contentPadding = PaddingValues(2.dp)) {
                    Text("PASS", fontSize = 7.sp, fontWeight = FontWeight.Black)
                }
                Text("Drag DISCARD into your hand to steal, or PASS", color = POnlineLegal, fontSize = 6.sp, textAlign = TextAlign.Center)
            } else {
                Text(if (normalDraw) "Drag DRAW or top DISCARD into your hand" else "Discard stays visible here", color = POnlineSoft, fontSize = 6.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun POnlineMeldArea(
    state: GameState,
    dragPoint: Offset?,
    canLower: Boolean,
    setBounds: (Rect) -> Unit,
    onMeldBounds: (POnlineMeldTarget, Rect) -> Unit,
    modifier: Modifier
) {
    val total = state.players.sumOf { p -> p.melds.sumOf { it.cards.size } }
    val cardW = when {
        total >= 36 -> 25.dp
        total >= 24 -> 28.dp
        else -> 32.dp
    }
    val cardH = cardW * 1.46f
    var area by remember { mutableStateOf(Rect.Zero) }
    val hover = dragPoint?.let { area != Rect.Zero && area.contains(it) } == true

    Surface(
        modifier = modifier.onGloballyPositioned { area = it.pOnlineBounds(); setBounds(area) },
        color = if (hover && canLower) POnlineLegal.copy(alpha = .08f) else Color.White.copy(alpha = .025f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(if (hover && canLower) 2.dp else 1.dp, if (hover && canLower) POnlineLegal else Color.White.copy(alpha = .10f))
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MELD AREA", color = if (canLower) POnlineLegal else Color.White.copy(alpha = .62f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (state.players.all { it.melds.isEmpty() }) {
                Box(Modifier.fillMaxWidth().height(85.dp), contentAlignment = Alignment.Center) {
                    Text("Select each required Trio/Straight. Keep the cards selected.\nDrag the complete round goal here when ready.", color = Color.White.copy(alpha = .58f), fontSize = 8.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)
                }
            }
            state.players.forEachIndexed { owner, player ->
                if (player.melds.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 49.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(player.name, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), maxLines = 1)
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            player.melds.forEachIndexed { meldIndex, meld ->
                                val target = POnlineMeldTarget(owner, meldIndex)
                                var rect by remember(owner, meldIndex, meld.cards.size) { mutableStateOf(Rect.Zero) }
                                val over = dragPoint?.let { rect != Rect.Zero && rect.contains(it) } == true
                                POnlineMeldFan(meld, cardW, cardH, over, Modifier.onGloballyPositioned {
                                    rect = it.pOnlineBounds()
                                    onMeldBounds(target, rect)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun POnlineMeldFan(meld: Meld, cardW: Dp, cardH: Dp, highlight: Boolean, modifier: Modifier) {
    Surface(modifier, color = POnlineInk.copy(alpha = .48f), shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) POnlineLegal else Color.White.copy(alpha = .08f))) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(meld.type.pOnlineLabel().uppercase(), color = POnlineGold, fontSize = 5.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(-(cardW * .50f))) {
                meld.cards.forEach { card -> POnlineCardFace(card, false, Modifier.size(cardW, cardH), compact = true) }
            }
        }
    }
}

@Composable
private fun POnlineHand(
    state: GameState,
    localSeat: Int,
    cards: List<GameCard>,
    selected: Set<GameCard>,
    busy: Boolean,
    setHandBounds: (Rect) -> Unit,
    onCardBounds: (GameCard, Rect) -> Unit,
    onToggle: (GameCard) -> Unit,
    onDrag: (Offset?) -> Unit,
    onFloating: (GameCard?) -> Unit,
    onDrop: (GameCard, Offset) -> Unit,
    modifier: Modifier
) {
    val myTurn = state.currentPlayer == localSeat
    val canTouch = myTurn && state.phase != TurnPhase.STEAL_WINDOW && !busy
    Box(modifier.onGloballyPositioned { setHandBounds(it.pOnlineBounds()) }.background(POnlineInk.copy(alpha = .84f), RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(116.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("YOUR HAND", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${cards.size} cards · Selected ${selected.size}", color = if (selected.isNotEmpty()) POnlineGold else POnlineMuted, fontSize = 6.sp)
                Text(
                    when {
                        myTurn && state.phase == TurnPhase.STEAL_WINDOW -> "Steal or pass above"
                        myTurn && state.phase == TurnPhase.DRAW -> "Drag a pile into your hand"
                        myTurn && state.phase == TurnPhase.ACTION -> "Tap select · drag lower / discard / reorder"
                        else -> "Waiting for ${state.players.getOrNull(state.currentPlayer)?.name ?: "player"}"
                    },
                    color = POnlineMuted, fontSize = 6.sp, textAlign = TextAlign.Center
                )
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((-9).dp), verticalAlignment = Alignment.CenterVertically) {
                cards.forEach { card ->
                    POnlineDraggableCard(
                        card = card,
                        selected = card in selected,
                        enabled = canTouch,
                        onClick = { if (state.phase == TurnPhase.ACTION) onToggle(card) },
                        onBounds = { onCardBounds(card, it) },
                        onDrag = onDrag,
                        onFloating = onFloating,
                        onDrop = { point -> onDrop(card, point) }
                    )
                }
            }
        }
    }
}

@Composable
private fun POnlineDraggableCard(
    card: GameCard,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
    onDrag: (Offset?) -> Unit,
    onFloating: (GameCard?) -> Unit,
    onDrop: (Offset) -> Unit
) {
    var delta by remember(card) { mutableStateOf(Offset.Zero) }
    var origin by remember(card) { mutableStateOf(Offset.Zero) }
    var measured by remember(card) { mutableStateOf(IntSize.Zero) }
    var dragging by remember(card) { mutableStateOf(false) }
    fun center() = origin + delta + Offset(measured.width / 2f, measured.height / 2f)

    POnlineCardFace(
        card,
        selected,
        Modifier.size(POnlineHandW, POnlineHandH)
            .offset(y = if (selected && !dragging) (-6).dp else 0.dp)
            .onGloballyPositioned {
                measured = it.size
                if (!dragging) origin = it.positionInRoot()
                onBounds(it.pOnlineBounds())
            }
            .graphicsLayer { alpha = if (dragging) .24f else 1f }
            .zIndex(if (dragging) 20f else if (selected) 2f else 1f)
            .pointerInput(enabled, card) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true; delta = Offset.Zero; onFloating(card); onDrag(center()) },
                    onDragCancel = { dragging = false; delta = Offset.Zero; onFloating(null); onDrag(null) },
                    onDragEnd = { val p = center(); dragging = false; onDrop(p); delta = Offset.Zero; onFloating(null); onDrag(null) },
                    onDrag = { change, amount -> change.consume(); delta += amount; onDrag(center()) }
                )
            }
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun POnlineDraggablePile(enabled: Boolean, handBounds: Rect, onDrag: (Offset?) -> Unit, onDrop: () -> Unit, content: @Composable () -> Unit) {
    var delta by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    fun center() = origin + delta + Offset(measured.width / 2f, measured.height / 2f)
    Box(
        Modifier.size(POnlinePileW, POnlinePileH)
            .onGloballyPositioned { measured = it.size; if (!dragging) origin = it.positionInRoot() }
            .graphicsLayer { translationX = delta.x; translationY = delta.y; scaleX = if (dragging) 1.08f else 1f; scaleY = if (dragging) 1.08f else 1f }
            .zIndex(if (dragging) 30f else 1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true; delta = Offset.Zero; onDrag(center()) },
                    onDragCancel = { dragging = false; delta = Offset.Zero; onDrag(null) },
                    onDragEnd = { val p = center(); dragging = false; if (handBounds != Rect.Zero && handBounds.contains(p)) onDrop(); delta = Offset.Zero; onDrag(null) },
                    onDrag = { change, amount -> change.consume(); delta += amount; onDrag(center()) }
                )
            }
    ) { content() }
}

@Composable
private fun POnlineDrawBack(count: Int) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(POnlineBack).border(2.dp, POnlineSoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CARIOCA", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Black)
            Text(count.toString(), color = POnlineGold, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun POnlineCardFace(card: GameCard, selected: Boolean, modifier: Modifier, compact: Boolean = false) {
    val ink = if (card.suit?.red == true) POnlineRed else POnlineInk
    Card(modifier = modifier, shape = RoundedCornerShape(if (compact) 5.dp else 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)), elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 1.dp else 2.dp)) {
        Box(Modifier.fillMaxSize().border(if (selected) 3.dp else 1.dp, if (selected) POnlineGold else Color(0xFFD7D9DC), RoundedCornerShape(if (compact) 5.dp else 8.dp)).padding(if (compact) 2.dp else 4.dp)) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.rank.pOnlineRank(), color = ink, fontSize = if (compact) 7.sp else 11.sp, fontWeight = FontWeight.Black)
                Text(if (card.isJoker) "★" else card.suit?.pOnlineSuit().orEmpty(), color = if (card.isJoker) POnlineTeal else ink, fontSize = if (compact) 6.sp else 8.sp)
            }
            Text(if (card.isJoker) "★" else card.suit?.pOnlineSuit().orEmpty(), Modifier.align(Alignment.Center), color = if (card.isJoker) POnlineTeal else ink, fontSize = if (compact) 14.sp else 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun POnlineFloatingCard(card: GameCard, center: Offset, groupCount: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { POnlineHandW.toPx() }
    val h = with(density) { POnlineHandH.toPx() }
    Box(Modifier.offset { IntOffset((center.x - w / 2).roundToInt(), (center.y - h / 2).roundToInt()) }.size(POnlineHandW, POnlineHandH).zIndex(100f)) {
        if (groupCount > 1) {
            repeat(minOf(groupCount - 1, 3)) { index ->
                Box(Modifier.matchParentSize().offset(x = (index + 1).dp, y = (index + 1).dp).background(Color.White.copy(alpha = .88f), RoundedCornerShape(8.dp)).border(1.dp, POnlineGold.copy(alpha = .65f), RoundedCornerShape(8.dp)))
            }
        }
        POnlineCardFace(card, true, Modifier.matchParentSize().graphicsLayer { scaleX = 1.1f; scaleY = 1.1f; shadowElevation = 14.dp.toPx() })
        if (groupCount > 1) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp).size(20.dp).clip(CircleShape).background(POnlineGold), contentAlignment = Alignment.Center) {
                Text(groupCount.toString(), color = POnlineInk, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun POnlineRoundSummary(state: GameState, localSeat: Int, onNext: () -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = POnlineInk.copy(alpha = .94f), shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 430.dp).padding(12.dp)) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = POnlineShadow))
                Text(state.message, color = POnlineGold, fontSize = 10.sp, textAlign = TextAlign.Center)
                state.players.sortedBy { it.score }.forEachIndexed { index, player ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${player.name}", color = Color.White, fontSize = 9.sp)
                        Text("+${player.roundPoints} · ${player.score}", color = if (index == 0) POnlineGold else POnlineSoft, fontSize = 9.sp)
                    }
                }
                if (state.phase == TurnPhase.ROUND_OVER) {
                    Button(onClick = onNext, enabled = state.currentPlayer == localSeat, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        Text(if (state.currentPlayer == localSeat) "Deal Next Round" else "Waiting for round winner")
                    }
                } else {
                    Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("Return") }
                }
            }
        }
    }
}

@Composable
private fun <T> rememberSaveableCompat(calculation: () -> MutableState<T>): MutableState<T> = remember { calculation() }

private fun LayoutCoordinates.pOnlineBounds(): Rect {
    val p = positionInRoot()
    return Rect(p, Size(size.width.toFloat(), size.height.toFloat()))
}

private fun RoundRule.pOnlineGoal(): String {
    special?.let { return it.pOnlineLabel() }
    val parts = mutableListOf<String>()
    if (legs > 0) parts += "$legs ${if (legs == 1) "Trio" else "Trios"}"
    if (straights > 0) parts += "$straights ${if (straights == 1) "Straight" else "Straights"}"
    return parts.joinToString(" + ")
}

private fun RoundRule.pOnlineExplanation(): String = when {
    special == MeldType.CRAZY_STRAIGHT -> "13 ranks, mixed suits allowed, at most one Joker."
    special == MeldType.COLOUR_STRAIGHT -> "13 ranks, all red or all black. No Jokers."
    special == MeldType.ROYAL_STRAIGHT -> "13 ranks, one suit. No Jokers."
    legs > 0 && straights > 0 -> "Trio: 3–4 same-rank cards with different suits. Straight: 4+ consecutive cards of one suit. Lower all required groups together."
    legs > 0 -> "Each Trio: 3–4 same-rank cards with different suits. Lower all required Trios together."
    else -> "Each Straight: 4+ consecutive cards of one suit. Lower all required Straights together."
}

private fun MeldType.pOnlineLabel(): String = when (this) {
    MeldType.LEG -> "Trio"
    MeldType.STRAIGHT -> "Straight"
    MeldType.CRAZY_STRAIGHT -> "Crazy Straight"
    MeldType.COLOUR_STRAIGHT -> "Colour Straight"
    MeldType.ROYAL_STRAIGHT -> "Royal Straight"
}

private fun Rank.pOnlineRank(): String = when (this) {
    Rank.ACE -> "A"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    Rank.JOKER -> "J"
    else -> order.toString()
}

private fun Suit.pOnlineSuit(): String = when (this) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
}

private fun String.pOnlineFriendly(): String = replace("LEG", "Trio", ignoreCase = true).replace("contract", "round goal", ignoreCase = true)
