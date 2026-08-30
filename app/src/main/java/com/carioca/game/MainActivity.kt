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
        setContent { CariocaGameApp(onExit = { finish() }) }
    }
}

private val Ink = Color(0xFF0D2438)
private val Navy = Color(0xFF071A29)
private val DeepBlue = Color(0xFF0C3148)
private val Teal = Color(0xFF1D8B83)
private val Felt = Color(0xFF0D5C58)
private val FeltDark = Color(0xFF073F3E)
private val Gold = Color(0xFFFFC857)
private val SoftWhite = Color(0xFFF2FAFB)
private val Muted = Color(0xFF9FC3CC)
private val RedSuit = Color(0xFFC93645)
private val Danger = Color(0xFFFF7B79)
private val Legal = Color(0xFF73E0C1)
private val CardBack = Color(0xFF155B7A)

private enum class GameScreen { SETUP, TABLE, RULES }
private enum class HandSort { SUIT, RANK }
private data class MeldTarget(val owner: Int, val meld: Int)

private val HandCardWidth = 50.dp
private val HandCardHeight = 73.dp
private val PileWidth = 58.dp
private val PileHeight = 80.dp

@Composable
fun CariocaGameApp(onExit: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(GameScreen.SETUP) }
    var mode by rememberSaveable { mutableStateOf(GameMode.REGULAR) }
    var players by rememberSaveable { mutableIntStateOf(4) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }

    BackHandler {
        when (screen) {
            GameScreen.TABLE, GameScreen.RULES -> screen = GameScreen.SETUP
            GameScreen.SETUP -> onExit()
        }
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
            GameScreen.SETUP -> PracticeSetup(
                mode = mode,
                players = players,
                difficulty = difficulty,
                setMode = { mode = it },
                setPlayers = { players = it },
                setDifficulty = { difficulty = it },
                start = { screen = GameScreen.TABLE },
                rules = { screen = GameScreen.RULES },
                exit = onExit
            )
            GameScreen.TABLE -> GameTable(mode, players, difficulty) { screen = GameScreen.SETUP }
            GameScreen.RULES -> RulesScreen { screen = GameScreen.SETUP }
        }
    }
}

@Composable
private fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, DeepBlue)))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.minDimension / 8f
            repeat(14) { i ->
                drawCircle(
                    Color.White.copy(alpha = .014f),
                    step * (.25f + (i % 3) * .07f),
                    Offset((i * step * 1.65f) % size.width, (i * step * 2.1f) % size.height)
                )
            }
        }
        content()
    }
}

@Composable
private fun PracticeSetup(
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
    AppBackdrop {
        Row(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.radialGradient(listOf(Teal.copy(alpha = .48f), FeltDark)))
                    .border(1.dp, Gold.copy(alpha = .25f), RoundedCornerShape(28.dp))
                    .padding(22.dp)
            ) {
                DecorativeCards(Modifier.align(Alignment.CenterEnd).size(205.dp, 170.dp))
                Column(Modifier.align(Alignment.CenterStart).widthIn(max = 330.dp)) {
                    Text("CARIOCA", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                    Text("JUST THE GAME", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("Landscape card-table play with drag-only draw and discard interaction.", color = SoftWhite, fontSize = 15.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(15.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(4) { MiniAvatar(it, it == 0) } }
                }
            }
            Surface(
                Modifier.weight(1.05f),
                color = Color.White.copy(alpha = .075f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .12f))
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Practice", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        TextButton(onClick = exit) { Text("Home", color = Muted) }
                    }
                    Text("Game mode", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        GameMode.entries.forEach { item -> FilterChip(selected = mode == item, onClick = { setMode(item) }, label = { Text("${item.name.pretty()} · ${item.rounds}") }) }
                    }
                    Text("Players", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { (2..4).forEach { count -> FilterChip(selected = players == count, onClick = { setPlayers(count) }, label = { Text(count.toString()) }) } }
                    Text("AI difficulty", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Difficulty.entries.forEach { level -> FilterChip(selected = difficulty == level, onClick = { setDifficulty(level) }, label = { Text(level.name.pretty()) }) } }
                    Button(onClick = start, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) { Text("Deal Cards", fontWeight = FontWeight.Black) }
                    OutlinedButton(onClick = rules, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Rules") }
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
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var draggedCard by remember { mutableStateOf<GameCard?>(null) }
    var tableBounds by remember { mutableStateOf(Rect.Zero) }
    var discardBounds by remember { mutableStateOf(Rect.Zero) }
    var handBounds by remember { mutableStateOf(Rect.Zero) }
    var meldBounds by remember { mutableStateOf<Map<MeldTarget, Rect>>(emptyMap()) }

    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 44) }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    val canSteal = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW && state.discardPile.isNotEmpty()
    LaunchedEffect(state.roundIndex, state.currentPlayer, state.phase, state.discardPile.size, sfx) {
        if (canSteal && sfx) tone.startTone(ToneGenerator.TONE_PROP_ACK, 105)
    }

    fun inside(bounds: Rect, point: Offset): Boolean = bounds != Rect.Zero && bounds.contains(point)
    fun dropHandCard(card: GameCard, point: Offset) {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return
        val group = if (card in state.selected && state.selected.isNotEmpty()) state.selected else setOf(card)
        val target = meldBounds.entries.firstOrNull { inside(it.value, point) }?.key
        state = when {
            inside(discardBounds, point) -> GameEngine.discardSelected(state.copy(selected = setOf(card)))
            target != null -> GameEngine.addSelectedToMeld(state.copy(selected = group), target.owner, target.meld)
            inside(tableBounds, point) -> GameEngine.createMeld(state.copy(selected = group))
            else -> state
        }
        dragPosition = null
    }

    AppBackdrop {
        Column(Modifier.fillMaxSize().padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TableHud(state, sfx, { sfx = !sfx }, exit)
            OpponentSeats(state)
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .background(Brush.radialGradient(listOf(Felt, FeltDark)), RoundedCornerShape(24.dp))
                    .border(2.dp, Gold.copy(alpha = .24f), RoundedCornerShape(24.dp))
            ) {
                FeltPattern()
                if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) {
                    RoundSummaryOverlay(state, { state = GameEngine.nextRound(state) }, exit)
                } else {
                    Box(
                        Modifier.fillMaxSize().padding(7.dp)
                            .onGloballyPositioned { tableBounds = it.boundsInRootSafe() }
                    ) {
                        MeldTable(
                            state = state,
                            dragPosition = dragPosition,
                            onMeldBounds = { target, bounds -> meldBounds = meldBounds + (target to bounds) },
                            modifier = Modifier.fillMaxSize().padding(start = 162.dp, end = 86.dp, top = 4.dp, bottom = 31.dp)
                        )
                        RoundGoalInfo(state, Modifier.align(Alignment.TopStart).width(154.dp))
                        PileDock(
                            state = state,
                            handBounds = handBounds,
                            discardBounds = discardBounds,
                            setDiscardBounds = { discardBounds = it },
                            onDragPosition = { dragPosition = it },
                            onDraw = { state = GameEngine.drawFromDeck(state) },
                            onSteal = { state = GameEngine.stealDiscard(state) },
                            modifier = Modifier.align(Alignment.CenterEnd).width(78.dp)
                        )
                        Surface(
                            color = Ink.copy(alpha = .42f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(.72f)
                        ) {
                            Text(state.message, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = SoftWhite, fontSize = 9.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            HumanHandDock(
                state = state,
                sort = sort,
                setHandBounds = { handBounds = it },
                onSort = { sort = it },
                onToggle = { state = GameEngine.toggleSelection(state, it) },
                onDragPosition = { dragPosition = it },
                onDraggingCard = { draggedCard = it },
                onDrop = ::dropHandCard,
                modifier = Modifier.fillMaxWidth().height(104.dp)
            )
        }
        if (draggedCard != null && dragPosition != null) FloatingDraggedCard(draggedCard!!, dragPosition!!)
    }
}

@Composable
private fun TableHud(state: GameState, sfx: Boolean, onSfx: () -> Unit, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExit, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("← Exit", color = Muted, fontSize = 11.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROUND ${state.roundIndex + 1} / ${state.mode.rounds}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(state.roundRule.description().uppercase(), color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onSfx, modifier = Modifier.size(34.dp)) { Text(if (sfx) "🔊" else "🔇", fontSize = 15.sp) }
    }
}

@Composable
private fun OpponentSeats(state: GameState) {
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        state.players.drop(1).forEachIndexed { index, player ->
            val active = state.currentPlayer == index + 1
            Surface(
                color = Color.White.copy(alpha = .07f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(if (active) 2.dp else 1.dp, if (active) Gold else Color.White.copy(alpha = .08f))
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniAvatar(index + 1, active, 34.dp)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(player.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${player.hand.size} cards · ${player.score} pts", color = Muted, fontSize = 7.sp)
                        if (player.melds.isNotEmpty()) Text("${player.melds.size} melds", color = Gold, fontSize = 7.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundGoalInfo(state: GameState, modifier: Modifier) {
    val human = state.players.first()
    val remaining = GameEngine.remainingRequirements(human, state.roundRule)
    val complete = GameEngine.contractComplete(human, state.roundRule)
    val ready = !complete && GameEngine.contractReady(human, state.roundRule)
    Surface(modifier, color = Ink.copy(alpha = .80f), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (ready || complete) Legal.copy(alpha = .8f) else Color.White.copy(alpha = .09f))) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ROUND GOAL", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(state.roundRule.description(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${GameRules.requiredCardCount(state.roundRule)} contract cards", color = Muted, fontSize = 8.sp)
            Text(
                when {
                    complete -> "✓ MELDED"
                    ready -> "✓ GOAL READY"
                    else -> "Need ${GameEngine.cardsStillRequired(human, state.roundRule)} cards in ${remaining.size} meld${if (remaining.size == 1) "" else "s"}"
                },
                color = if (ready || complete) Legal else SoftWhite,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            if (remaining.isNotEmpty()) Text(remaining.joinToString(" + ") { it.label() }, color = SoftWhite, fontSize = 7.sp, lineHeight = 9.sp)
        }
    }
}

@Composable
private fun PileDock(
    state: GameState,
    handBounds: Rect,
    discardBounds: Rect,
    setDiscardBounds: (Rect) -> Unit,
    onDragPosition: (Offset?) -> Unit,
    onDraw: () -> Unit,
    onSteal: () -> Unit,
    modifier: Modifier
) {
    val canDraw = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW
    val canSteal = canDraw && state.discardPile.isNotEmpty()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("DRAG", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        DrawPileCard(state.drawPile.size, canDraw, handBounds, onDragPosition, onDraw)
        val top = state.discardPile.lastOrNull()
        Box(
            Modifier.size(PileWidth, PileHeight).onGloballyPositioned { setDiscardBounds(it.boundsInRootSafe()) },
            contentAlignment = Alignment.Center
        ) {
            if (top != null) DiscardPileCard(top, canSteal, handBounds, onDragPosition, onSteal)
            else EmptyPile("DISCARD")
        }
    }
}

@Composable
private fun MeldTable(
    state: GameState,
    dragPosition: Offset?,
    onMeldBounds: (MeldTarget, Rect) -> Unit,
    modifier: Modifier
) {
    val totalCards = state.players.sumOf { player -> player.melds.sumOf { it.cards.size } }
    val cardWidth = when {
        totalCards >= 36 -> 27.dp
        totalCards >= 24 -> 30.dp
        else -> 34.dp
    }
    val cardHeight = cardWidth * 1.47f

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (state.players.all { it.melds.isEmpty() }) {
            Box(Modifier.fillMaxWidth().height(128.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MELD AREA", color = Color.White.copy(alpha = .42f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text("Select the round contract cards and drag them here", color = Color.White.copy(alpha = .48f), fontSize = 9.sp)
                }
            }
        }
        state.players.forEachIndexed { ownerIndex, player ->
            if (player.melds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        MiniAvatar(ownerIndex, state.currentPlayer == ownerIndex, 28.dp)
                        Text(player.name, color = Color.White, fontSize = 7.sp, maxLines = 1)
                    }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        player.melds.forEachIndexed { meldIndex, meld ->
                            val target = MeldTarget(ownerIndex, meldIndex)
                            val over = dragPosition?.let { point -> false } ?: false
                            MeldFan(
                                owner = player.name,
                                meld = meld,
                                cardWidth = cardWidth,
                                cardHeight = cardHeight,
                                modifier = Modifier.onGloballyPositioned { onMeldBounds(target, it.boundsInRootSafe()) },
                                highlight = over
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeldFan(owner: String, meld: Meld, cardWidth: androidx.compose.ui.unit.Dp, cardHeight: androidx.compose.ui.unit.Dp, modifier: Modifier, highlight: Boolean) {
    Surface(
        modifier = modifier,
        color = Ink.copy(alpha = .40f),
        shape = RoundedCornerShape(11.dp),
        border = androidx.compose.foundation.BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) Legal else Color.White.copy(alpha = .08f))
    ) {
        Column(Modifier.padding(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(meld.type.label().uppercase(), color = Gold, fontSize = 6.sp, fontWeight = FontWeight.Black)
                Text("${meld.cards.size}", color = Muted, fontSize = 6.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(-(cardWidth * .56f))) {
                meld.cards.forEach { card -> PlayingCardFace(card, false, Modifier.size(cardWidth, cardHeight), compact = true) }
            }
        }
    }
}

@Composable
private fun HumanHandDock(
    state: GameState,
    sort: HandSort,
    setHandBounds: (Rect) -> Unit,
    onSort: (HandSort) -> Unit,
    onToggle: (GameCard) -> Unit,
    onDragPosition: (Offset?) -> Unit,
    onDraggingCard: (GameCard?) -> Unit,
    onDrop: (GameCard, Offset) -> Unit,
    modifier: Modifier
) {
    val human = state.players.first()
    val cards = when (sort) {
        HandSort.SUIT -> human.hand.sortedWith(compareBy<GameCard>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy }))
        HandSort.RANK -> human.hand.sortedWith(compareBy<GameCard>({ it.isJoker }, { it.rank.order }, { it.suit?.ordinal ?: 9 }, { it.deck }, { it.copy }))
    }
    Box(
        modifier.onGloballyPositioned { setHandBounds(it.boundsInRootSafe()) }
            .background(Ink.copy(alpha = .76f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(106.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniAvatar(0, state.currentPlayer == 0, 30.dp)
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Text("YOUR HAND", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("${human.hand.size} cards", color = Muted, fontSize = 7.sp)
                    }
                }
                Row {
                    TextButton(onClick = { onSort(HandSort.SUIT) }, contentPadding = PaddingValues(2.dp)) { Text("Suit", color = if (sort == HandSort.SUIT) Gold else Muted, fontSize = 7.sp) }
                    TextButton(onClick = { onSort(HandSort.RANK) }, contentPadding = PaddingValues(2.dp)) { Text("Rank", color = if (sort == HandSort.RANK) Gold else Muted, fontSize = 7.sp) }
                }
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((-9).dp), verticalAlignment = Alignment.CenterVertically) {
                cards.forEach { card ->
                    DraggablePlayingCard(
                        card,
                        selected = card in state.selected,
                        enabled = state.phase == TurnPhase.ACTION && state.currentPlayer == 0,
                        onClick = { onToggle(card) },
                        onDragPosition = onDragPosition,
                        onDraggingCard = onDraggingCard,
                        onDrop = { point -> onDrop(card, point) }
                    )
                }
            }
            Text(
                if (state.phase == TurnPhase.DRAW) "DRAG\nA PILE\nHERE" else "DRAG\nTO MELD\nOR DISCARD",
                modifier = Modifier.width(68.dp), color = if (state.phase == TurnPhase.DRAW) Gold else Muted, fontSize = 7.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DraggablePlayingCard(
    card: GameCard,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDragPosition: (Offset?) -> Unit,
    onDraggingCard: (GameCard?) -> Unit,
    onDrop: (Offset) -> Unit
) {
    var drag by remember(card) { mutableStateOf(Offset.Zero) }
    var baseOrigin by remember(card) { mutableStateOf(Offset.Zero) }
    var measured by remember(card) { mutableStateOf(IntSize.Zero) }
    var dragging by remember(card) { mutableStateOf(false) }
    fun centerPoint(): Offset = baseOrigin + drag + Offset(measured.width / 2f, measured.height / 2f)

    PlayingCardFace(
        card,
        selected,
        Modifier.padding(horizontal = 1.dp).size(HandCardWidth, HandCardHeight)
            .offset(y = if (selected && !dragging) (-5).dp else 0.dp)
            .onGloballyPositioned { measured = it.size; if (!dragging) baseOrigin = it.positionInRoot() }
            .zIndex(if (dragging) 20f else if (selected) 2f else 1f)
            .graphicsLayer { alpha = if (dragging) .26f else 1f }
            .pointerInput(enabled, card) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true; drag = Offset.Zero; onDraggingCard(card); onDragPosition(centerPoint()) },
                    onDragCancel = { dragging = false; drag = Offset.Zero; onDraggingCard(null); onDragPosition(null) },
                    onDragEnd = { val point = centerPoint(); dragging = false; onDrop(point); drag = Offset.Zero; onDraggingCard(null); onDragPosition(null) }
                ) { change, amount -> change.consume(); drag += amount; onDragPosition(centerPoint()) }
            }.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun FloatingDraggedCard(card: GameCard, center: Offset) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthPx = with(density) { HandCardWidth.toPx() }
    val heightPx = with(density) { HandCardHeight.toPx() }
    PlayingCardFace(
        card,
        true,
        Modifier.offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(HandCardWidth, HandCardHeight).zIndex(100f)
            .graphicsLayer { scaleX = 1.10f; scaleY = 1.10f; shadowElevation = 16.dp.toPx() }
    )
}

@Composable
private fun DrawPileCard(count: Int, enabled: Boolean, handBounds: Rect, onDragPosition: (Offset?) -> Unit, onDropToHand: () -> Unit) {
    DraggablePile(enabled, handBounds, onDragPosition, onDropToHand) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(CardBack).border(2.dp, SoftWhite.copy(alpha = .9f), RoundedCornerShape(9.dp)).padding(4.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = 8.dp.toPx(); var x = -size.height
                while (x < size.width + size.height) { drawLine(Gold.copy(alpha = .25f), Offset(x, 0f), Offset(x + size.height, size.height), 1.dp.toPx()); x += s }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CARIOCA", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Text(count.toString(), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("DRAW", color = SoftWhite, fontSize = 6.sp)
            }
        }
    }
}

@Composable
private fun DiscardPileCard(card: GameCard, canSteal: Boolean, handBounds: Rect, onDragPosition: (Offset?) -> Unit, onDropToHand: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "steal")
    val glow by transition.animateFloat(.30f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "glow")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DraggablePile(canSteal, handBounds, onDragPosition, onDropToHand) {
            PlayingCardFace(card, false, Modifier.fillMaxSize().border(if (canSteal) 3.dp else 1.dp, if (canSteal) Gold.copy(alpha = glow) else Color.LightGray, RoundedCornerShape(9.dp)))
        }
        if (canSteal) Text("STEAL +2", color = Gold, fontSize = 6.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DraggablePile(enabled: Boolean, handBounds: Rect, onDragPosition: (Offset?) -> Unit, onDropToHand: () -> Unit, content: @Composable () -> Unit) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var baseOrigin by remember { mutableStateOf(Offset.Zero) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    fun centerPoint(): Offset = baseOrigin + drag + Offset(measured.width / 2f, measured.height / 2f)

    Box(
        Modifier.size(PileWidth, PileHeight)
            .onGloballyPositioned { measured = it.size; if (!dragging) baseOrigin = it.positionInRoot() }
            .zIndex(if (dragging) 30f else 1f)
            .graphicsLayer { translationX = drag.x; translationY = drag.y; scaleX = if (dragging) 1.08f else 1f; scaleY = if (dragging) 1.08f else 1f }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true; drag = Offset.Zero; onDragPosition(centerPoint()) },
                    onDragCancel = { dragging = false; drag = Offset.Zero; onDragPosition(null) },
                    onDragEnd = {
                        val point = centerPoint(); dragging = false
                        if (handBounds != Rect.Zero && handBounds.contains(point)) onDropToHand()
                        drag = Offset.Zero; onDragPosition(null)
                    }
                ) { change, amount -> change.consume(); drag += amount; onDragPosition(centerPoint()) }
            }
    ) { content() }
}

@Composable
private fun PlayingCardFace(card: GameCard, selected: Boolean, modifier: Modifier, compact: Boolean = false) {
    val ink = if (card.suit?.red == true) RedSuit else Ink
    Card(modifier = modifier, shape = RoundedCornerShape(if (compact) 6.dp else 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)), elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 1.dp else 2.dp)) {
        Box(Modifier.fillMaxSize().border(if (selected) 3.dp else 1.dp, if (selected) Gold else Color(0xFFD7D9DC), RoundedCornerShape(if (compact) 6.dp else 8.dp)).padding(if (compact) 3.dp else 4.dp)) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.rank.shortLabel(), color = ink, fontSize = if (compact) 8.sp else 11.sp, fontWeight = FontWeight.Black, lineHeight = if (compact) 8.sp else 11.sp)
                Text(if (card.isJoker) "★" else card.suit?.symbol().orEmpty(), color = if (card.isJoker) Teal else ink, fontSize = if (compact) 7.sp else 8.sp, lineHeight = if (compact) 7.sp else 8.sp)
            }
            Text(if (card.isJoker) "★" else card.suit?.symbol().orEmpty(), Modifier.align(Alignment.Center), color = if (card.isJoker) Teal else ink, fontSize = if (compact) 16.sp else 21.sp, fontWeight = FontWeight.Bold)
            if (card.isJoker) Text("J", Modifier.align(Alignment.BottomCenter), color = Teal, fontSize = if (compact) 5.sp else 6.sp, fontWeight = FontWeight.Black)
            else Text(card.suit?.symbol().orEmpty(), Modifier.align(Alignment.BottomEnd), color = ink, fontSize = if (compact) 7.sp else 9.sp)
        }
    }
}

@Composable
private fun EmptyPile(label: String) {
    Box(Modifier.size(PileWidth, PileHeight).border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White.copy(alpha = .35f), fontSize = 6.sp)
    }
}

@Composable
private fun RoundSummaryOverlay(state: GameState, next: () -> Unit, exit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = Ink.copy(alpha = .93f), shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 440.dp).padding(12.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(state.message, color = Gold, textAlign = TextAlign.Center, fontSize = 11.sp)
                state.players.sortedBy { it.score }.forEachIndexed { index, player ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${player.name}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("+${player.roundPoints} · ${player.score}", color = if (index == 0) Gold else SoftWhite, fontSize = 10.sp)
                    }
                }
                if (state.phase == TurnPhase.ROUND_OVER) Button(onClick = next, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text("Deal Next Round") }
                else Button(onClick = exit, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text("Return") }
            }
        }
    }
}

@Composable
private fun MiniAvatar(index: Int, active: Boolean, size: androidx.compose.ui.unit.Dp = 42.dp) {
    val accents = listOf(Color(0xFFFF8DA1), Color(0xFF86D7E8), Color(0xFFB4A0FF), Color(0xFF7EE0BD))
    val accent = accents[index % accents.size]
    Box(Modifier.size(size).clip(CircleShape).background(if (active) Gold else accent.copy(alpha = .38f)).padding(3.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val hair = listOf(Color(0xFF49352F), Color(0xFF263A57), Color(0xFF70432D), Color(0xFF302D2B))[index % 4]
            drawCircle(hair, size.minDimension * .43f, Offset(center.x, center.y - size.height * .07f))
            drawCircle(Color(0xFFFFD3B8), size.minDimension * .34f, Offset(center.x, center.y + size.height * .04f))
            drawCircle(Ink, size.minDimension * .035f, Offset(center.x - size.width * .11f, center.y))
            drawCircle(Ink, size.minDimension * .035f, Offset(center.x + size.width * .11f, center.y))
        }
    }
}

@Composable
private fun FeltPattern() {
    Canvas(Modifier.fillMaxSize()) {
        val spacing = 31.dp.toPx(); var x = -size.height
        while (x < size.width + size.height) { drawLine(Color.White.copy(alpha = .014f), Offset(x, 0f), Offset(x + size.height, size.height), 1.dp.toPx()); x += spacing }
    }
}

@Composable
private fun RulesScreen(back: () -> Unit) {
    AppBackdrop {
        Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Column(Modifier.width(220.dp)) {
                TextButton(onClick = back) { Text("← Back", color = Color.White) }
                Text("Carioca Rules", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("The small corner goal box always shows the current contract and required card count.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Rule("Deck", "Two standard decks plus four Jokers: 108 cards. Red aces are ordinary natural cards.")
                Rule("Wildcards", "Jokers are the only wildcards. At most one Joker per meld, and it cannot be replaced after it is laid.")
                Rule("Meld", "Complete the round contract by collecting the required legs/straights. The game detects when the complete contract exists in your hand. Select the contract cards and drag them into the middle meld area; multiple required melds can be created in one drop.")
                Rule("Draw / acquire", "Acquiring a card is drag-only. Drag DRAW or the glowing top discard into your hand. Tapping the pile does not draw.")
                Rule("Discard", "Discarding is drag-only. Drag one card from your hand onto the discard pile to finish the turn.")
                Rule("Adding", "After your round contract has been melded, select extra card(s) and drag them directly onto a compatible meld already on the table.")
                Rule("Rounds", "Regular mode has 8 rounds. Special mode adds crazy straight, colour straight, and royal straight as rounds 9–11.")
                Rule("Going out", "Complete the round contract before going out. There is no −30 going-out bonus.")
                Rule("Scoring", "Cards left in hand and steal penalties count against you. Lowest cumulative score wins.")
            }
        }
    }
}

@Composable
private fun Rule(title: String, text: String) {
    Surface(color = Color.White.copy(alpha = .07f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(text, color = SoftWhite, lineHeight = 16.sp, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DecorativeCards(modifier: Modifier) {
    Box(modifier) {
        listOf(Triple("A", "♥", RedSuit), Triple("K", "♠", Ink), Triple("J", "★", Teal)).forEachIndexed { index, item ->
            Surface(
                color = Color(0xFFFFFEFB), shape = RoundedCornerShape(14.dp), shadowElevation = 8.dp,
                modifier = Modifier.size(76.dp, 108.dp).align(Alignment.Center).graphicsLayer {
                    translationX = (index - 1) * 38.dp.toPx(); translationY = kotlin.math.abs(index - 1) * 9.dp.toPx(); rotationZ = (index - 1) * 12f
                }
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(item.first, color = item.third, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text(item.second, color = item.third, fontSize = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(item.second, color = item.third, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.boundsInRootSafe(): Rect {
    val p = positionInRoot()
    return Rect(p, Size(size.width.toFloat(), size.height.toFloat()))
}

private fun RoundRule.description(): String {
    special?.let { return it.label() }
    val parts = mutableListOf<String>()
    if (legs > 0) parts += "$legs ${if (legs == 1) "leg" else "legs"}"
    if (straights > 0) parts += "$straights ${if (straights == 1) "straight" else "straights"}"
    return parts.joinToString(" + ")
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
