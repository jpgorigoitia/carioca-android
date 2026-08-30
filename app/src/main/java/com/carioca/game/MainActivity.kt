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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.carioca.game.domain.Card as GameCard
import com.carioca.game.domain.Difficulty
import com.carioca.game.domain.GameEngine
import com.carioca.game.domain.GameMode
import com.carioca.game.domain.GameRules
import com.carioca.game.domain.GameState
import com.carioca.game.domain.Meld
import com.carioca.game.domain.MeldType
import com.carioca.game.domain.Rank
import com.carioca.game.domain.RoundRule
import com.carioca.game.domain.Suit
import com.carioca.game.domain.TurnPhase
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CariocaGameApp { finish() } }
    }
}

private val Ink = Color(0xFF0D2438)
private val Navy = Color(0xFF071A29)
private val DeepBlue = Color(0xFF0C3148)
private val Felt = Color(0xFF0D5C58)
private val FeltDark = Color(0xFF073F3E)
private val Teal = Color(0xFF1D8B83)
private val Gold = Color(0xFFFFC857)
private val SoftWhite = Color(0xFFF2FAFB)
private val Muted = Color(0xFF9FC3CC)
private val RedSuit = Color(0xFFC93645)
private val Legal = Color(0xFF73E0C1)
private val CardBack = Color(0xFF155B7A)

private val HandCardWidth = 50.dp
private val HandCardHeight = 73.dp
private val PileWidth = 58.dp
private val PileHeight = 80.dp

private enum class Screen { SETUP, TABLE, RULES }
private enum class HandSort { SUIT, RANK }
private data class MeldTarget(val owner: Int, val meldIndex: Int)

@Composable
fun CariocaGameApp(onExit: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.SETUP) }
    var mode by rememberSaveable { mutableStateOf(GameMode.REGULAR) }
    var players by rememberSaveable { mutableIntStateOf(4) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }

    BackHandler {
        if (screen == Screen.SETUP) onExit() else screen = Screen.SETUP
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            secondary = Gold,
            surface = DeepBlue,
            onPrimary = Color.White,
            onSurface = Color.White
        )
    ) {
        when (screen) {
            Screen.SETUP -> SetupScreen(
                mode = mode,
                players = players,
                difficulty = difficulty,
                setMode = { mode = it },
                setPlayers = { players = it },
                setDifficulty = { difficulty = it },
                start = { screen = Screen.TABLE },
                rules = { screen = Screen.RULES },
                exit = onExit
            )
            Screen.TABLE -> GameTable(mode, players, difficulty) { screen = Screen.SETUP }
            Screen.RULES -> RulesScreen { screen = Screen.SETUP }
        }
    }
}

@Composable
private fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, DeepBlue)))) {
        content()
    }
}

@Composable
private fun SetupScreen(
    mode: GameMode,
    players: Int,
    difficulty: Difficulty,
    setMode: (GameMode) -> Unit,
    setPlayers: (Int) -> Unit,
    setDifficulty: (Difficulty) -> Unit,
    start: () -> Unit,
    rules: () -> Unit,
    exit: () -> Unit
) {
    Backdrop {
        Row(
            Modifier.fillMaxSize().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                Modifier.weight(1f).fillMaxHeight(),
                color = FeltDark,
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .25f))
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.radialGradient(listOf(Teal.copy(alpha = .45f), FeltDark)))
                        .padding(22.dp)
                ) {
                    Column(Modifier.align(Alignment.CenterStart).widthIn(max = 360.dp)) {
                        Text("CARIOCA", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                        Text("JUST THE GAME", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Horizontal-only table play. Drag piles into your hand, drag contracts to the meld area, and drag one card to discard.",
                            color = SoftWhite,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                    DecorativeFan(Modifier.align(Alignment.CenterEnd).size(190.dp, 150.dp))
                }
            }

            Surface(
                Modifier.weight(1.05f),
                color = Color.White.copy(alpha = .075f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .12f))
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Practice", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        TextButton(onClick = exit) { Text("Home", color = Muted) }
                    }
                    Text("Game mode", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        GameMode.entries.forEach { item ->
                            FilterChip(
                                selected = mode == item,
                                onClick = { setMode(item) },
                                label = { Text("${item.name.pretty()} · ${item.rounds}") }
                            )
                        }
                    }
                    Text("Players", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        (2..4).forEach { count ->
                            FilterChip(selected = players == count, onClick = { setPlayers(count) }, label = { Text(count.toString()) })
                        }
                    }
                    Text("AI difficulty", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Difficulty.entries.forEach { level ->
                            FilterChip(selected = difficulty == level, onClick = { setDifficulty(level) }, label = { Text(level.name.pretty()) })
                        }
                    }
                    Button(onClick = start, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Deal Cards", fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(onClick = rules, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text("Rules") }
                }
            }
        }
    }
}

@Composable
private fun GameTable(mode: GameMode, players: Int, difficulty: Difficulty, exit: () -> Unit) {
    var state by remember(mode, players, difficulty) { mutableStateOf(GameEngine.newGame(mode, players, difficulty)) }
    var sort by rememberSaveable { mutableStateOf(HandSort.SUIT) }
    var sfx by rememberSaveable { mutableStateOf(true) }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var floatingCard by remember { mutableStateOf<GameCard?>(null) }
    var tableBounds by remember { mutableStateOf(Rect.Zero) }
    var discardBounds by remember { mutableStateOf(Rect.Zero) }
    var handBounds by remember { mutableStateOf(Rect.Zero) }
    var meldBounds by remember { mutableStateOf<Map<MeldTarget, Rect>>(emptyMap()) }

    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 44) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    val canSteal = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW && state.discardPile.isNotEmpty()
    LaunchedEffect(state.currentPlayer, state.phase, state.discardPile.size, sfx) {
        if (canSteal && sfx) tone.startTone(ToneGenerator.TONE_PROP_ACK, 105)
    }

    fun dropHandCard(card: GameCard, point: Offset) {
        if (state.currentPlayer != 0 || state.phase != TurnPhase.ACTION) return
        val selected = if (card in state.selected && state.selected.isNotEmpty()) state.selected else setOf(card)
        val target = meldBounds.entries.firstOrNull { it.value.contains(point) }?.key

        state = when {
            discardBounds != Rect.Zero && discardBounds.contains(point) ->
                GameEngine.discardSelected(state.copy(selected = setOf(card)))
            target != null ->
                GameEngine.addSelectedToMeld(state.copy(selected = selected), target.owner, target.meldIndex)
            tableBounds != Rect.Zero && tableBounds.contains(point) ->
                GameEngine.createMeld(state.copy(selected = selected))
            else -> state
        }
        dragPoint = null
    }

    Backdrop {
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TableHud(state, sfx, { sfx = !sfx }, exit)
            OpponentStrip(state)

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .background(Brush.radialGradient(listOf(Felt, FeltDark)), RoundedCornerShape(22.dp))
                    .border(2.dp, Gold.copy(alpha = .24f), RoundedCornerShape(22.dp))
            ) {
                if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) {
                    RoundSummary(state, { state = GameEngine.nextRound(state) }, exit)
                } else {
                    RoundGoalBox(state, Modifier.align(Alignment.TopStart).padding(7.dp).width(150.dp))

                    MeldWorkspace(
                        state = state,
                        dragPoint = dragPoint,
                        setTableBounds = { tableBounds = it },
                        onMeldBounds = { target, bounds -> meldBounds = meldBounds + (target to bounds) },
                        modifier = Modifier.fillMaxSize().padding(start = 164.dp, end = 84.dp, top = 6.dp, bottom = 28.dp)
                    )

                    PileDock(
                        state = state,
                        handBounds = handBounds,
                        setDiscardBounds = { discardBounds = it },
                        onDrag = { dragPoint = it },
                        onDraw = { state = GameEngine.drawFromDeck(state) },
                        onSteal = { state = GameEngine.stealDiscard(state) },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 7.dp).width(70.dp)
                    )

                    Surface(
                        color = Ink.copy(alpha = .46f),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(.70f).padding(bottom = 4.dp)
                    ) {
                        Text(
                            state.message,
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = SoftWhite,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            HandDock(
                state = state,
                sort = sort,
                setHandBounds = { handBounds = it },
                onSort = { sort = it },
                onToggle = { state = GameEngine.toggleSelection(state, it) },
                onDrag = { dragPoint = it },
                onFloating = { floatingCard = it },
                onDrop = ::dropHandCard,
                modifier = Modifier.fillMaxWidth().height(101.dp)
            )
        }

        if (floatingCard != null && dragPoint != null) {
            FloatingCard(floatingCard!!, dragPoint!!)
        }
    }
}

@Composable
private fun TableHud(state: GameState, sfx: Boolean, onSfx: () -> Unit, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExit, contentPadding = PaddingValues(horizontal = 7.dp)) {
            Text("← Exit", color = Muted, fontSize = 10.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROUND ${state.roundIndex + 1} / ${state.mode.rounds}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(state.roundRule.description().uppercase(), color = Gold, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onSfx, modifier = Modifier.size(30.dp)) {
            Text(if (sfx) "🔊" else "🔇", fontSize = 13.sp)
        }
    }
}

@Composable
private fun OpponentStrip(state: GameState) {
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.players.drop(1).forEachIndexed { index, player ->
            val active = state.currentPlayer == index + 1
            Surface(
                color = Color.White.copy(alpha = .07f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(if (active) 2.dp else 1.dp, if (active) Gold else Color.White.copy(alpha = .08f))
            ) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(index + 1, active, 29.dp)
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Text(player.name, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("${player.hand.size} cards · ${player.score} pts", color = Muted, fontSize = 6.sp)
                        if (player.melds.isNotEmpty()) Text("${player.melds.size} melds", color = Gold, fontSize = 6.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundGoalBox(state: GameState, modifier: Modifier) {
    val player = state.players.first()
    val remaining = GameEngine.remainingRequirements(player, state.roundRule)
    val complete = GameEngine.contractComplete(player, state.roundRule)
    val ready = !complete && GameEngine.contractReady(player, state.roundRule)

    Surface(
        modifier,
        color = Ink.copy(alpha = .84f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (ready || complete) Legal else Color.White.copy(alpha = .10f))
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ROUND GOAL", color = Gold, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(state.roundRule.description(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${GameRules.requiredCardCount(state.roundRule)} contract cards", color = Muted, fontSize = 7.sp)
            Text(
                when {
                    complete -> "✓ MELDED"
                    ready -> "✓ GOAL READY"
                    else -> "Need ${GameEngine.cardsStillRequired(player, state.roundRule)} cards"
                },
                color = if (ready || complete) Legal else SoftWhite,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            if (remaining.isNotEmpty()) {
                Text(remaining.joinToString(" + ") { it.label() }, color = SoftWhite, fontSize = 6.sp, lineHeight = 8.sp)
            }
        }
    }
}

@Composable
private fun MeldWorkspace(
    state: GameState,
    dragPoint: Offset?,
    setTableBounds: (Rect) -> Unit,
    onMeldBounds: (MeldTarget, Rect) -> Unit,
    modifier: Modifier
) {
    val totalMeldCards = state.players.sumOf { player -> player.melds.sumOf { meld -> meld.cards.size } }
    val dimensions = when {
        totalMeldCards >= 36 -> 26.dp to 38.dp
        totalMeldCards >= 24 -> 29.dp to 43.dp
        else -> 33.dp to 49.dp
    }

    Column(
        modifier
            .onGloballyPositioned { setTableBounds(it.rootBounds()) }
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.players.all { it.melds.isEmpty() }) {
            Box(Modifier.fillMaxWidth().height(116.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MELD AREA", color = Color.White.copy(alpha = .40f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("Select the round contract and drag it here", color = Color.White.copy(alpha = .48f), fontSize = 8.sp)
                }
            }
        }

        state.players.forEachIndexed { ownerIndex, player ->
            if (player.melds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 51.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayerAvatar(ownerIndex, state.currentPlayer == ownerIndex, 25.dp)
                        Text(player.name, color = Color.White, fontSize = 6.sp, maxLines = 1)
                    }
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        player.melds.forEachIndexed { meldIndex, meld ->
                            val target = MeldTarget(ownerIndex, meldIndex)
                            var bounds by remember(ownerIndex, meldIndex, meld.cards.size) { mutableStateOf(Rect.Zero) }
                            val highlight = dragPoint?.let { bounds != Rect.Zero && bounds.contains(it) } == true
                            MeldFan(
                                meld = meld,
                                cardWidth = dimensions.first,
                                cardHeight = dimensions.second,
                                highlight = highlight,
                                modifier = Modifier.onGloballyPositioned {
                                    bounds = it.rootBounds()
                                    onMeldBounds(target, bounds)
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
private fun MeldFan(meld: Meld, cardWidth: Dp, cardHeight: Dp, highlight: Boolean, modifier: Modifier) {
    Surface(
        modifier,
        color = Ink.copy(alpha = .42f),
        shape = RoundedCornerShape(9.dp),
        border = androidx.compose.foundation.BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) Legal else Color.White.copy(alpha = .08f))
    ) {
        Column(Modifier.padding(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(meld.type.label().uppercase(), color = Gold, fontSize = 5.sp, fontWeight = FontWeight.Black)
                Text(meld.cards.size.toString(), color = Muted, fontSize = 5.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-15).dp)) {
                meld.cards.forEach { card -> CardFace(card, false, Modifier.size(cardWidth, cardHeight), compact = true) }
            }
        }
    }
}

@Composable
private fun PileDock(
    state: GameState,
    handBounds: Rect,
    setDiscardBounds: (Rect) -> Unit,
    onDrag: (Offset?) -> Unit,
    onDraw: () -> Unit,
    onSteal: () -> Unit,
    modifier: Modifier
) {
    val canDraw = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW
    val canSteal = canDraw && state.discardPile.isNotEmpty()

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DRAG", color = Muted, fontSize = 6.sp, fontWeight = FontWeight.Black)
        DraggablePile(canDraw, handBounds, onDrag, onDraw) { DrawPileFace(state.drawPile.size) }
        Box(Modifier.size(PileWidth, PileHeight).onGloballyPositioned { setDiscardBounds(it.rootBounds()) }) {
            val top = state.discardPile.lastOrNull()
            if (top == null) {
                EmptyPile()
            } else {
                val transition = rememberInfiniteTransition(label = "steal")
                val glow by transition.animateFloat(
                    initialValue = .30f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                    label = "stealGlow"
                )
                DraggablePile(canSteal, handBounds, onDrag, onSteal) {
                    CardFace(
                        top,
                        false,
                        Modifier.fillMaxSize().border(
                            if (canSteal) 3.dp else 1.dp,
                            if (canSteal) Gold.copy(alpha = glow) else Color.LightGray,
                            RoundedCornerShape(8.dp)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HandDock(
    state: GameState,
    sort: HandSort,
    setHandBounds: (Rect) -> Unit,
    onSort: (HandSort) -> Unit,
    onToggle: (GameCard) -> Unit,
    onDrag: (Offset?) -> Unit,
    onFloating: (GameCard?) -> Unit,
    onDrop: (GameCard, Offset) -> Unit,
    modifier: Modifier
) {
    val player = state.players.first()
    val cards = when (sort) {
        HandSort.SUIT -> player.hand.sortedWith(compareBy<GameCard>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy }))
        HandSort.RANK -> player.hand.sortedWith(compareBy<GameCard>({ it.isJoker }, { it.rank.order }, { it.suit?.ordinal ?: 9 }, { it.deck }, { it.copy }))
    }

    Box(
        modifier
            .onGloballyPositioned { setHandBounds(it.rootBounds()) }
            .background(Ink.copy(alpha = .80f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(102.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(0, state.currentPlayer == 0, 28.dp)
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text("YOUR HAND", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                        Text("${player.hand.size} cards", color = Muted, fontSize = 6.sp)
                    }
                }
                Row {
                    TextButton(onClick = { onSort(HandSort.SUIT) }, contentPadding = PaddingValues(1.dp), modifier = Modifier.height(24.dp)) {
                        Text("Suit", color = if (sort == HandSort.SUIT) Gold else Muted, fontSize = 6.sp)
                    }
                    TextButton(onClick = { onSort(HandSort.RANK) }, contentPadding = PaddingValues(1.dp), modifier = Modifier.height(24.dp)) {
                        Text("Rank", color = if (sort == HandSort.RANK) Gold else Muted, fontSize = 6.sp)
                    }
                }
            }

            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy((-9).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cards.forEach { card ->
                    DraggableHandCard(
                        card = card,
                        selected = card in state.selected,
                        enabled = state.currentPlayer == 0 && state.phase == TurnPhase.ACTION,
                        onClick = { onToggle(card) },
                        onDrag = onDrag,
                        onFloating = onFloating,
                        onDrop = { point -> onDrop(card, point) }
                    )
                }
            }

            Text(
                if (state.phase == TurnPhase.DRAW) "DRAG\nPILE\nHERE" else "DRAG\nTO MELD\nOR PILE",
                Modifier.width(60.dp),
                color = if (state.phase == TurnPhase.DRAW) Gold else Muted,
                fontSize = 6.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DraggableHandCard(
    card: GameCard,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDrag: (Offset?) -> Unit,
    onFloating: (GameCard?) -> Unit,
    onDrop: (Offset) -> Unit
) {
    var delta by remember(card) { mutableStateOf(Offset.Zero) }
    var origin by remember(card) { mutableStateOf(Offset.Zero) }
    var measured by remember(card) { mutableStateOf(IntSize.Zero) }
    var dragging by remember(card) { mutableStateOf(false) }

    fun centerPoint(): Offset = origin + delta + Offset(measured.width / 2f, measured.height / 2f)

    CardFace(
        card,
        selected,
        Modifier
            .size(HandCardWidth, HandCardHeight)
            .offset(y = if (selected && !dragging) (-5).dp else 0.dp)
            .onGloballyPositioned {
                measured = it.size
                if (!dragging) origin = it.positionInRoot()
            }
            .zIndex(if (dragging) 20f else if (selected) 2f else 1f)
            .graphicsLayer { alpha = if (dragging) .25f else 1f }
            .pointerInput(enabled, card) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        delta = Offset.Zero
                        onFloating(card)
                        onDrag(centerPoint())
                    },
                    onDragCancel = {
                        dragging = false
                        delta = Offset.Zero
                        onFloating(null)
                        onDrag(null)
                    },
                    onDragEnd = {
                        val point = centerPoint()
                        dragging = false
                        onDrop(point)
                        delta = Offset.Zero
                        onFloating(null)
                        onDrag(null)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        delta += amount
                        onDrag(centerPoint())
                    }
                )
            }
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun DraggablePile(
    enabled: Boolean,
    handBounds: Rect,
    onDrag: (Offset?) -> Unit,
    onDropToHand: () -> Unit,
    content: @Composable () -> Unit
) {
    var delta by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }

    fun centerPoint(): Offset = origin + delta + Offset(measured.width / 2f, measured.height / 2f)

    Box(
        Modifier
            .size(PileWidth, PileHeight)
            .onGloballyPositioned {
                measured = it.size
                if (!dragging) origin = it.positionInRoot()
            }
            .zIndex(if (dragging) 30f else 1f)
            .graphicsLayer {
                translationX = delta.x
                translationY = delta.y
                scaleX = if (dragging) 1.08f else 1f
                scaleY = if (dragging) 1.08f else 1f
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        delta = Offset.Zero
                        onDrag(centerPoint())
                    },
                    onDragCancel = {
                        dragging = false
                        delta = Offset.Zero
                        onDrag(null)
                    },
                    onDragEnd = {
                        val point = centerPoint()
                        dragging = false
                        if (handBounds != Rect.Zero && handBounds.contains(point)) onDropToHand()
                        delta = Offset.Zero
                        onDrag(null)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        delta += amount
                        onDrag(centerPoint())
                    }
                )
            }
    ) { content() }
}

@Composable
private fun DrawPileFace(count: Int) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(CardBack)
            .border(2.dp, SoftWhite.copy(alpha = .9f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CARIOCA", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
            Text(count.toString(), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text("DRAW", color = SoftWhite, fontSize = 6.sp)
        }
    }
}

@Composable
private fun EmptyPile() {
    Box(
        Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("DISCARD", color = Color.White.copy(alpha = .35f), fontSize = 6.sp)
    }
}

@Composable
private fun FloatingCard(card: GameCard, center: Offset) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthPx = with(density) { HandCardWidth.toPx() }
    val heightPx = with(density) { HandCardHeight.toPx() }
    CardFace(
        card,
        true,
        Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(HandCardWidth, HandCardHeight)
            .zIndex(100f)
            .graphicsLayer { scaleX = 1.10f; scaleY = 1.10f; shadowElevation = 14.dp.toPx() }
    )
}

@Composable
private fun CardFace(card: GameCard, selected: Boolean, modifier: Modifier, compact: Boolean = false) {
    val ink = if (card.suit?.red == true) RedSuit else Ink
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 5.dp else 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 1.dp else 2.dp)
    ) {
        Box(
            Modifier.fillMaxSize()
                .border(if (selected) 3.dp else 1.dp, if (selected) Gold else Color(0xFFD7D9DC), RoundedCornerShape(if (compact) 5.dp else 8.dp))
                .padding(if (compact) 2.dp else 4.dp)
        ) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.rank.shortLabel(), color = ink, fontSize = if (compact) 7.sp else 11.sp, fontWeight = FontWeight.Black)
                Text(if (card.isJoker) "★" else card.suit?.symbol().orEmpty(), color = if (card.isJoker) Teal else ink, fontSize = if (compact) 6.sp else 8.sp)
            }
            Text(
                if (card.isJoker) "★" else card.suit?.symbol().orEmpty(),
                Modifier.align(Alignment.Center),
                color = if (card.isJoker) Teal else ink,
                fontSize = if (compact) 14.sp else 21.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RoundSummary(state: GameState, next: () -> Unit, exit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = Ink.copy(alpha = .94f), shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 430.dp).padding(12.dp)) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(state.message, color = Gold, fontSize = 10.sp, textAlign = TextAlign.Center)
                state.players.sortedBy { it.score }.forEachIndexed { index, player ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${player.name}", color = Color.White, fontSize = 9.sp)
                        Text("+${player.roundPoints} · ${player.score}", color = if (index == 0) Gold else SoftWhite, fontSize = 9.sp)
                    }
                }
                if (state.phase == TurnPhase.ROUND_OVER) {
                    Button(onClick = next, modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("Deal Next Round") }
                } else {
                    Button(onClick = exit, modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("Return") }
                }
            }
        }
    }
}

@Composable
private fun RulesScreen(back: () -> Unit) {
    Backdrop {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(215.dp)) {
                TextButton(onClick = back) { Text("← Back", color = Color.White) }
                Text("Carioca Rules", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text("The game is horizontal-only. The corner box shows the current round contract without covering the meld table.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Rule("Meld", "Collect the complete round contract. When the required cards exist in your hand, GOAL READY appears. Select the contract cards and drag them into the middle meld area.")
                Rule("Multiple melds", "A full contract such as 2 legs is split into the required legal melds by the rules engine when you drop the complete selected contract onto the table.")
                Rule("Draw / acquire", "Drag the draw pile or glowing discard pile into your hand. Pressing a pile does not draw a card.")
                Rule("Discard", "Drag exactly one card from your hand onto the discard pile. There is no discard button.")
                Rule("Add to meld", "After your round contract is complete, select extra cards and drag them directly onto the compatible meld you want to extend.")
                Rule("Wildcards", "Jokers are the only wildcards. At most one Joker per meld, and it cannot be replaced after it is laid.")
                Rule("Rounds", "Regular mode has 8 rounds. Special mode adds crazy straight, colour straight, and royal straight as rounds 9–11.")
            }
        }
    }
}

@Composable
private fun Rule(title: String, text: String) {
    Surface(color = Color.White.copy(alpha = .07f), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(text, color = SoftWhite, fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun PlayerAvatar(index: Int, active: Boolean, avatarSize: Dp) {
    val accents = listOf(Color(0xFFFF8DA1), Color(0xFF86D7E8), Color(0xFFB4A0FF), Color(0xFF7EE0BD))
    Box(
        Modifier.size(avatarSize).clip(CircleShape)
            .background(if (active) Gold else accents[index % accents.size].copy(alpha = .40f)),
        contentAlignment = Alignment.Center
    ) {
        Text(if (index == 0) "Y" else index.toString(), color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DecorativeFan(modifier: Modifier) {
    Box(modifier) {
        listOf(Triple("A", "♥", RedSuit), Triple("K", "♠", Ink), Triple("J", "★", Teal)).forEachIndexed { index, item ->
            Surface(
                color = Color(0xFFFFFEFB),
                shape = RoundedCornerShape(13.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.size(72.dp, 104.dp).align(Alignment.Center).graphicsLayer {
                    translationX = (index - 1) * 36.dp.toPx()
                    translationY = kotlin.math.abs(index - 1) * 8.dp.toPx()
                    rotationZ = (index - 1) * 11f
                }
            ) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(item.first, color = item.third, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(item.second, color = item.third, fontSize = 29.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(item.second, color = item.third, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun LayoutCoordinates.rootBounds(): Rect {
    val position = positionInRoot()
    return Rect(position, Size(size.width.toFloat(), size.height.toFloat()))
}

private fun RoundRule.description(): String {
    special?.let { return it.label() }
    val pieces = mutableListOf<String>()
    if (legs > 0) pieces += "$legs ${if (legs == 1) "leg" else "legs"}"
    if (straights > 0) pieces += "$straights ${if (straights == 1) "straight" else "straights"}"
    return pieces.joinToString(" + ")
}

private fun MeldType.label(): String = when (this) {
    MeldType.LEG -> "leg"
    MeldType.STRAIGHT -> "straight"
    MeldType.CRAZY_STRAIGHT -> "crazy straight"
    MeldType.COLOUR_STRAIGHT -> "colour straight"
    MeldType.ROYAL_STRAIGHT -> "royal straight"
}

private fun Suit.symbol(): String = when (this) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
}

private fun Rank.shortLabel(): String = when (this) {
    Rank.ACE -> "A"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    Rank.JOKER -> "J"
    else -> order.toString()
}

private fun String.pretty(): String = lowercase().replaceFirstChar { it.uppercase() }
