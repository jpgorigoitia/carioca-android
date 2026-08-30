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
import androidx.compose.ui.draw.shadow
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
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import com.carioca.game.domain.Card as GameCard
import com.carioca.game.domain.Difficulty
import com.carioca.game.domain.GameEngine
import com.carioca.game.domain.GameMode
import com.carioca.game.domain.GameState
import com.carioca.game.domain.Meld
import com.carioca.game.domain.MeldType
import com.carioca.game.domain.Rank
import com.carioca.game.domain.RoundRule
import com.carioca.game.domain.Suit
import com.carioca.game.domain.TurnPhase

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
            GameScreen.TABLE -> GameTable(
                mode = mode,
                players = players,
                difficulty = difficulty,
                exit = { screen = GameScreen.SETUP }
            )
            GameScreen.RULES -> RulesScreen { screen = GameScreen.SETUP }
        }
    }
}

@Composable
private fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy, DeepBlue)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.minDimension / 9f
            for (i in 0..12) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.018f),
                    radius = step * (0.35f + (i % 3) * 0.12f),
                    center = Offset((i * step * 1.63f) % size.width, (i * step * 2.11f) % size.height)
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SetupHero(Modifier.weight(1f))
                    SetupControls(
                        modifier = Modifier.weight(1.05f),
                        mode = mode,
                        players = players,
                        difficulty = difficulty,
                        setMode = setMode,
                        setPlayers = setPlayers,
                        setDifficulty = setDifficulty,
                        start = start,
                        rules = rules,
                        exit = exit
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    SetupHero(Modifier.fillMaxWidth())
                    SetupControls(
                        modifier = Modifier.fillMaxWidth(),
                        mode = mode,
                        players = players,
                        difficulty = difficulty,
                        setMode = setMode,
                        setPlayers = setPlayers,
                        setDifficulty = setDifficulty,
                        start = start,
                        rules = rules,
                        exit = exit
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SetupHero(modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 250.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.radialGradient(listOf(Teal.copy(alpha = 0.45f), FeltDark)))
            .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(30.dp))
            .padding(24.dp)
    ) {
        DecorativeCards(Modifier.align(Alignment.CenterEnd).size(210.dp, 180.dp))
        Column(Modifier.align(Alignment.CenterStart).widthIn(max = 330.dp)) {
            Text("CARIOCA", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("JUST THE GAME", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(22.dp))
            Text(
                "A real card table: rotate your phone, grab cards, drag them to the table, and drop one on the discard pile.",
                color = SoftWhite,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniAvatar(0, active = true)
                MiniAvatar(1, active = false)
                MiniAvatar(2, active = false)
                MiniAvatar(3, active = false)
            }
        }
    }
}

@Composable
private fun SetupControls(
    modifier: Modifier,
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
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.075f),
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("AI Practice", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                TextButton(onClick = exit) { Text("Home", color = Muted) }
            }
            Text("Game mode", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { setMode(item) },
                        label = { Text("${item.name.pretty()} · ${item.rounds}") }
                    )
                }
            }
            Text("Players", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..4).forEach { count ->
                    FilterChip(selected = players == count, onClick = { setPlayers(count) }, label = { Text(count.toString()) })
                }
            }
            Text("AI difficulty", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { level ->
                    FilterChip(
                        selected = difficulty == level,
                        onClick = { setDifficulty(level) },
                        label = { Text(level.name.pretty()) }
                    )
                }
            }
            Button(
                onClick = start,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Deal Cards", fontWeight = FontWeight.Black) }
            OutlinedButton(onClick = rules, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Rules") }
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

    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 44) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    val canSteal = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW && state.discardPile.isNotEmpty()
    LaunchedEffect(state.roundIndex, state.currentPlayer, state.phase, state.discardPile.size, sfx) {
        if (canSteal && sfx) tone.startTone(ToneGenerator.TONE_PROP_ACK, 105)
    }

    fun inside(bounds: Rect, point: Offset): Boolean = bounds != Rect.Zero && bounds.contains(point)

    fun dropHandCard(card: GameCard, point: Offset) {
        when {
            inside(discardBounds, point) && state.phase == TurnPhase.ACTION -> {
                state = GameEngine.discardSelected(state.copy(selected = setOf(card)))
            }
            inside(tableBounds, point) && state.phase == TurnPhase.ACTION -> {
                val group = if (card in state.selected && state.selected.isNotEmpty()) state.selected else setOf(card)
                state = GameEngine.laySelected(state.copy(selected = group))
            }
        }
        dragPosition = null
    }

    AppBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                LandscapeTable(
                    state = state,
                    sort = sort,
                    sfx = sfx,
                    dragPosition = dragPosition,
                    tableBounds = tableBounds,
                    discardBounds = discardBounds,
                    handBounds = handBounds,
                    setTableBounds = { tableBounds = it },
                    setDiscardBounds = { discardBounds = it },
                    setHandBounds = { handBounds = it },
                    onDragPosition = { dragPosition = it },
                    onDraggingCard = { draggedCard = it },
                    onDropCard = ::dropHandCard,
                    onToggleCard = { state = GameEngine.toggleSelection(state, it) },
                    onDraw = { state = GameEngine.drawFromDeck(state) },
                    onSteal = { state = GameEngine.stealDiscard(state) },
                    onLay = { state = GameEngine.laySelected(state) },
                    onDiscard = { state = GameEngine.discardSelected(state) },
                    onNextRound = { state = GameEngine.nextRound(state) },
                    onSort = { sort = it },
                    onSfx = { sfx = !sfx },
                    onExit = exit
                )
            } else {
                PortraitTable(
                    state = state,
                    sort = sort,
                    sfx = sfx,
                    dragPosition = dragPosition,
                    tableBounds = tableBounds,
                    discardBounds = discardBounds,
                    handBounds = handBounds,
                    setTableBounds = { tableBounds = it },
                    setDiscardBounds = { discardBounds = it },
                    setHandBounds = { handBounds = it },
                    onDragPosition = { dragPosition = it },
                    onDraggingCard = { draggedCard = it },
                    onDropCard = ::dropHandCard,
                    onToggleCard = { state = GameEngine.toggleSelection(state, it) },
                    onDraw = { state = GameEngine.drawFromDeck(state) },
                    onSteal = { state = GameEngine.stealDiscard(state) },
                    onLay = { state = GameEngine.laySelected(state) },
                    onDiscard = { state = GameEngine.discardSelected(state) },
                    onNextRound = { state = GameEngine.nextRound(state) },
                    onSort = { sort = it },
                    onSfx = { sfx = !sfx },
                    onExit = exit
                )
            }
        }
        if (draggedCard != null && dragPosition != null) {
            FloatingDraggedCard(draggedCard!!, dragPosition!!)
        }
    }
}

@Composable
private fun LandscapeTable(
    state: GameState,
    sort: HandSort,
    sfx: Boolean,
    dragPosition: Offset?,
    tableBounds: Rect,
    discardBounds: Rect,
    handBounds: Rect,
    setTableBounds: (Rect) -> Unit,
    setDiscardBounds: (Rect) -> Unit,
    setHandBounds: (Rect) -> Unit,
    onDragPosition: (Offset?) -> Unit,
    onDraggingCard: (GameCard?) -> Unit,
    onDropCard: (GameCard, Offset) -> Unit,
    onToggleCard: (GameCard) -> Unit,
    onDraw: () -> Unit,
    onSteal: () -> Unit,
    onLay: () -> Unit,
    onDiscard: () -> Unit,
    onNextRound: () -> Unit,
    onSort: (HandSort) -> Unit,
    onSfx: () -> Unit,
    onExit: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        TableHud(state, sfx, onSfx, onExit)
        OpponentSeats(state, compact = true)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Brush.radialGradient(listOf(Felt, FeltDark)), RoundedCornerShape(28.dp))
                .border(2.dp, Gold.copy(alpha = 0.26f), RoundedCornerShape(28.dp))
        ) {
            FeltPattern()
            if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) {
                RoundSummaryOverlay(state, onNextRound, onExit)
            } else {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContractRail(state, Modifier.width(165.dp).fillMaxHeight())
                    TableCenter(
                        state = state,
                        dragPosition = dragPosition,
                        tableBounds = tableBounds,
                        discardBounds = discardBounds,
                        handBounds = handBounds,
                        setTableBounds = setTableBounds,
                        setDiscardBounds = setDiscardBounds,
                        onDragPosition = onDragPosition,
                        onDraw = onDraw,
                        onSteal = onSteal,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ActionRail(state, onLay, onDiscard, Modifier.width(165.dp).fillMaxHeight())
                }
            }
        }
        HumanHandDock(
            state = state,
            sort = sort,
            handBounds = handBounds,
            setHandBounds = setHandBounds,
            onSort = onSort,
            onToggle = onToggleCard,
            onDragPosition = onDragPosition,
            onDraggingCard = onDraggingCard,
            onDrop = onDropCard,
            modifier = Modifier.fillMaxWidth().height(132.dp)
        )
    }
}

@Composable
private fun PortraitTable(
    state: GameState,
    sort: HandSort,
    sfx: Boolean,
    dragPosition: Offset?,
    tableBounds: Rect,
    discardBounds: Rect,
    handBounds: Rect,
    setTableBounds: (Rect) -> Unit,
    setDiscardBounds: (Rect) -> Unit,
    setHandBounds: (Rect) -> Unit,
    onDragPosition: (Offset?) -> Unit,
    onDraggingCard: (GameCard?) -> Unit,
    onDropCard: (GameCard, Offset) -> Unit,
    onToggleCard: (GameCard) -> Unit,
    onDraw: () -> Unit,
    onSteal: () -> Unit,
    onLay: () -> Unit,
    onDiscard: () -> Unit,
    onNextRound: () -> Unit,
    onSort: (HandSort) -> Unit,
    onSfx: () -> Unit,
    onExit: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TableHud(state, sfx, onSfx, onExit)
        OpponentSeats(state, compact = true)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Brush.radialGradient(listOf(Felt, FeltDark)), RoundedCornerShape(24.dp))
                .border(2.dp, Gold.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
        ) {
            FeltPattern()
            if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) {
                RoundSummaryOverlay(state, onNextRound, onExit)
            } else {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContractRail(state, Modifier.fillMaxWidth().heightIn(min = 64.dp))
                    TableCenter(
                        state = state,
                        dragPosition = dragPosition,
                        tableBounds = tableBounds,
                        discardBounds = discardBounds,
                        handBounds = handBounds,
                        setTableBounds = setTableBounds,
                        setDiscardBounds = setDiscardBounds,
                        onDragPosition = onDragPosition,
                        onDraw = onDraw,
                        onSteal = onSteal,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    ActionRail(state, onLay, onDiscard, Modifier.fillMaxWidth().heightIn(min = 54.dp))
                }
            }
        }
        HumanHandDock(
            state = state,
            sort = sort,
            handBounds = handBounds,
            setHandBounds = setHandBounds,
            onSort = onSort,
            onToggle = onToggleCard,
            onDragPosition = onDragPosition,
            onDraggingCard = onDraggingCard,
            onDrop = onDropCard,
            modifier = Modifier.fillMaxWidth().height(142.dp)
        )
    }
}

@Composable
private fun TableHud(state: GameState, sfx: Boolean, onSfx: () -> Unit, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(45.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExit) { Text("← Exit", color = Muted) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROUND ${state.roundIndex + 1} / ${state.mode.rounds}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(state.roundRule.description().uppercase(), color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onSfx) { Text(if (sfx) "🔊" else "🔇", fontSize = 18.sp) }
    }
}

@Composable
private fun OpponentSeats(state: GameState, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(if (compact) 68.dp else 82.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.players.drop(1).forEachIndexed { index, player ->
            PlayerSeat(
                name = player.name,
                cardCount = player.hand.size,
                score = player.score,
                avatar = index + 1,
                active = state.currentPlayer == index + 1,
                meldCount = player.melds.size
            )
        }
    }
}

@Composable
private fun PlayerSeat(name: String, cardCount: Int, score: Int, avatar: Int, active: Boolean, meldCount: Int) {
    val transition = rememberInfiniteTransition(label = "turnGlow")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "seatGlow"
    )
    Surface(
        color = Color.White.copy(alpha = 0.07f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (active) 2.dp else 1.dp,
            if (active) Gold.copy(alpha = glow) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniAvatar(avatar, active)
            Spacer(Modifier.width(7.dp))
            Column {
                Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("$cardCount cards · $score pts", color = Muted, fontSize = 9.sp)
                if (meldCount > 0) Text("$meldCount meld${if (meldCount == 1) "" else "s"}", color = Gold, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun MiniAvatar(index: Int, active: Boolean) {
    val palettes = listOf(
        Triple(Color(0xFFFFD2B8), Color(0xFF4B2E2A), Color(0xFFFF8DA1)),
        Triple(Color(0xFFFFD7C7), Color(0xFF253653), Color(0xFF86D7E8)),
        Triple(Color(0xFFF5C8A9), Color(0xFF713F2A), Color(0xFFB4A0FF)),
        Triple(Color(0xFFFFD6AE), Color(0xFF332F2A), Color(0xFF7EE0BD))
    )
    val (skin, hair, accent) = palettes[index % palettes.size]
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Gold.copy(alpha = 0.9f) else accent.copy(alpha = 0.35f))
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            drawCircle(hair, radius = size.minDimension * 0.43f, center = Offset(c.x, c.y - size.height * 0.08f))
            drawCircle(skin, radius = size.minDimension * 0.34f, center = Offset(c.x, c.y + size.height * 0.03f))
            drawCircle(hair, radius = size.minDimension * 0.08f, center = Offset(c.x - size.width * 0.22f, c.y - size.height * 0.20f))
            drawCircle(hair, radius = size.minDimension * 0.08f, center = Offset(c.x + size.width * 0.22f, c.y - size.height * 0.20f))
            drawCircle(Ink, radius = size.minDimension * 0.035f, center = Offset(c.x - size.width * 0.105f, c.y))
            drawCircle(Ink, radius = size.minDimension * 0.035f, center = Offset(c.x + size.width * 0.105f, c.y))
            drawCircle(accent.copy(alpha = 0.55f), radius = size.minDimension * 0.045f, center = Offset(c.x - size.width * 0.19f, c.y + size.height * 0.09f))
            drawCircle(accent.copy(alpha = 0.55f), radius = size.minDimension * 0.045f, center = Offset(c.x + size.width * 0.19f, c.y + size.height * 0.09f))
            drawArc(
                color = Ink,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(c.x - size.width * 0.09f, c.y + size.height * 0.02f),
                size = Size(size.width * 0.18f, size.height * 0.13f)
            )
        }
    }
}

@Composable
private fun FeltPattern() {
    Canvas(Modifier.fillMaxSize()) {
        val spacing = 34.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = Color.White.copy(alpha = 0.015f),
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = 1.dp.toPx()
            )
            x += spacing
        }
    }
}

@Composable
private fun ContractRail(state: GameState, modifier: Modifier) {
    val human = state.players.first()
    val remaining = GameEngine.remainingRequirements(human, state.roundRule)
    Surface(modifier = modifier, color = Ink.copy(alpha = 0.28f), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("CONTRACT", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(state.roundRule.description(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                if (remaining.isEmpty()) "Complete — add to any legal table meld."
                else "Remaining: ${remaining.joinToString { it.label() }}",
                color = SoftWhite,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Text("${state.difficulty.name.pretty()} AI", color = Muted, fontSize = 9.sp)
            Text("Your score ${human.score}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (human.steals > 0) Text("Steals +${human.steals * 2}", color = Gold, fontSize = 9.sp)
        }
    }
}

@Composable
private fun TableCenter(
    state: GameState,
    dragPosition: Offset?,
    tableBounds: Rect,
    discardBounds: Rect,
    handBounds: Rect,
    setTableBounds: (Rect) -> Unit,
    setDiscardBounds: (Rect) -> Unit,
    onDragPosition: (Offset?) -> Unit,
    onDraw: () -> Unit,
    onSteal: () -> Unit,
    modifier: Modifier
) {
    val overTable = dragPosition?.let { tableBounds != Rect.Zero && tableBounds.contains(it) } == true
    val overDiscard = dragPosition?.let { discardBounds != Rect.Zero && discardBounds.contains(it) } == true
    val canAction = state.phase == TurnPhase.ACTION && state.currentPlayer == 0
    val canSteal = state.phase == TurnPhase.DRAW && state.currentPlayer == 0 && state.discardPile.isNotEmpty()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { setTableBounds(it.boundsInRootSafe()) }
                .clip(RoundedCornerShape(18.dp))
                .background(if (overTable && canAction) Legal.copy(alpha = 0.17f) else Color.Black.copy(alpha = 0.09f))
                .border(
                    if (overTable && canAction) 2.dp else 1.dp,
                    if (overTable && canAction) Legal else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            TableMeldField(state)
            if (state.players.all { it.melds.isEmpty() }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TABLE", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Drag selected cards here", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(106.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            DrawPileCard(
                count = state.drawPile.size,
                enabled = state.phase == TurnPhase.DRAW && state.currentPlayer == 0,
                handBounds = handBounds,
                onDragPosition = onDragPosition,
                onDropToHand = onDraw,
                onClick = onDraw
            )
            Spacer(Modifier.width(28.dp))
            val top = state.discardPile.lastOrNull()
            Box(
                Modifier
                    .size(72.dp, 100.dp)
                    .onGloballyPositioned { setDiscardBounds(it.boundsInRootSafe()) }
                    .border(
                        if (overDiscard && canAction) 3.dp else 0.dp,
                        if (overDiscard && canAction) Danger else Color.Transparent,
                        RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (top != null) {
                    DiscardPileCard(
                        card = top,
                        canSteal = canSteal,
                        handBounds = handBounds,
                        onDragPosition = onDragPosition,
                        onDropToHand = onSteal,
                        onClick = onSteal
                    )
                } else {
                    EmptyPile("DISCARD")
                }
            }
        }
        Surface(color = Ink.copy(alpha = 0.26f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(state.message, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = SoftWhite, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActionRail(state: GameState, onLay: () -> Unit, onDiscard: () -> Unit, modifier: Modifier) {
    val action = state.phase == TurnPhase.ACTION && state.currentPlayer == 0
    Surface(modifier = modifier, color = Ink.copy(alpha = 0.28f), shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TOUCH CONTROLS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onLay, enabled = action && state.selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Lay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onDiscard, enabled = action && state.selected.size == 1, modifier = Modifier.fillMaxWidth()) {
                Text("Discard", fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("Tap to select.\nDrag selection to TABLE.\nDrag one card to DISCARD.", color = SoftWhite, fontSize = 9.sp, lineHeight = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HumanHandDock(
    state: GameState,
    sort: HandSort,
    handBounds: Rect,
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
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .onGloballyPositioned { setHandBounds(it.boundsInRootSafe()) }
            .background(Ink.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiniAvatar(0, state.currentPlayer == 0)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("YOUR HAND · ${human.hand.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(
                        when (state.phase) {
                            TurnPhase.DRAW -> "Drag DRAW or glowing DISCARD into your hand."
                            TurnPhase.ACTION -> "Tap cards to select, then drag them onto the table."
                            else -> "Round finished."
                        },
                        color = Muted,
                        fontSize = 9.sp
                    )
                }
                TextButton(onClick = { onSort(HandSort.SUIT) }) { Text("Suit", color = if (sort == HandSort.SUIT) Gold else Muted, fontSize = 10.sp) }
                TextButton(onClick = { onSort(HandSort.RANK) }) { Text("Rank", color = if (sort == HandSort.RANK) Gold else Muted, fontSize = 10.sp) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(scroll).padding(top = 3.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy((-12).dp)
            ) {
                cards.forEach { card ->
                    DraggablePlayingCard(
                        card = card,
                        selected = card in state.selected,
                        enabled = state.phase == TurnPhase.ACTION && state.currentPlayer == 0,
                        onClick = { onToggle(card) },
                        onDragPosition = onDragPosition,
                        onDraggingCard = onDraggingCard,
                        onDrop = { point -> onDrop(card, point) }
                    )
                }
            }
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
        card = card,
        selected = selected,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(62.dp, 91.dp)
            .offset(y = if (selected && !dragging) (-7).dp else 0.dp)
            .onGloballyPositioned {
                measured = it.size
                if (!dragging) baseOrigin = it.positionInRoot()
            }
            .zIndex(if (dragging) 20f else if (selected) 2f else 1f)
            .graphicsLayer {
                scaleX = if (dragging) 0.96f else 1f
                scaleY = if (dragging) 0.96f else 1f
                alpha = if (dragging) 0.28f else 1f
            }
            .pointerInput(enabled, card) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        drag = Offset.Zero
                        onDraggingCard(card)
                        onDragPosition(centerPoint())
                    },
                    onDragCancel = {
                        dragging = false
                        drag = Offset.Zero
                        onDraggingCard(null)
                        onDragPosition(null)
                    },
                    onDragEnd = {
                        val point = centerPoint()
                        dragging = false
                        onDrop(point)
                        drag = Offset.Zero
                        onDraggingCard(null)
                        onDragPosition(null)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                        onDragPosition(centerPoint())
                    }
                )
            }
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun FloatingDraggedCard(card: GameCard, center: Offset) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthPx = with(density) { 62.dp.toPx() }
    val heightPx = with(density) { 91.dp.toPx() }
    PlayingCardFace(
        card = card,
        selected = true,
        modifier = Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(62.dp, 91.dp)
            .zIndex(100f)
            .graphicsLayer {
                scaleX = 1.12f
                scaleY = 1.12f
                shadowElevation = 20.dp.toPx()
            }
    )
}

@Composable
private fun DrawPileCard(
    count: Int,
    enabled: Boolean,
    handBounds: Rect,
    onDragPosition: (Offset?) -> Unit,
    onDropToHand: () -> Unit,
    onClick: () -> Unit
) {
    DraggablePile(
        enabled = enabled,
        handBounds = handBounds,
        onDragPosition = onDragPosition,
        onDropToHand = onDropToHand,
        onClick = onClick
    ) { dragging ->
        Box(
            Modifier
                .fillMaxSize()
                .shadow(if (dragging) 16.dp else 4.dp, RoundedCornerShape(11.dp))
                .clip(RoundedCornerShape(11.dp))
                .background(CardBack)
                .border(2.dp, SoftWhite.copy(alpha = 0.9f), RoundedCornerShape(11.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val s = 10.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(Gold.copy(alpha = 0.25f), Offset(x, 0f), Offset(x + size.height, size.height), 1.dp.toPx())
                    x += s
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CARIOCA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(count.toString(), color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("DRAW", color = SoftWhite, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun DiscardPileCard(
    card: GameCard,
    canSteal: Boolean,
    handBounds: Rect,
    onDragPosition: (Offset?) -> Unit,
    onDropToHand: () -> Unit,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "stealShine")
    val glow by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "stealGlow"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DraggablePile(
            enabled = canSteal,
            handBounds = handBounds,
            onDragPosition = onDragPosition,
            onDropToHand = onDropToHand,
            onClick = onClick
        ) { dragging ->
            PlayingCardFace(
                card = card,
                selected = false,
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(if (dragging) 16.dp else 4.dp, RoundedCornerShape(11.dp))
                    .border(
                        if (canSteal) 4.dp else 1.dp,
                        if (canSteal) Gold.copy(alpha = glow) else Color.LightGray,
                        RoundedCornerShape(11.dp)
                    )
            )
        }
        if (canSteal) Text("STEAL +2", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DraggablePile(
    enabled: Boolean,
    handBounds: Rect,
    onDragPosition: (Offset?) -> Unit,
    onDropToHand: () -> Unit,
    onClick: () -> Unit,
    content: @Composable (Boolean) -> Unit
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var baseOrigin by remember { mutableStateOf(Offset.Zero) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    fun centerPoint(): Offset = baseOrigin + drag + Offset(measured.width / 2f, measured.height / 2f)

    Box(
        Modifier
            .size(72.dp, 100.dp)
            .onGloballyPositioned {
                measured = it.size
                if (!dragging) baseOrigin = it.positionInRoot()
            }
            .zIndex(if (dragging) 30f else 1f)
            .graphicsLayer {
                translationX = drag.x
                translationY = drag.y
                scaleX = if (dragging) 1.08f else 1f
                scaleY = if (dragging) 1.08f else 1f
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        drag = Offset.Zero
                        onDragPosition(centerPoint())
                    },
                    onDragCancel = {
                        dragging = false
                        drag = Offset.Zero
                        onDragPosition(null)
                    },
                    onDragEnd = {
                        val point = centerPoint()
                        dragging = false
                        if (handBounds != Rect.Zero && handBounds.contains(point)) onDropToHand()
                        drag = Offset.Zero
                        onDragPosition(null)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                        onDragPosition(centerPoint())
                    }
                )
            }
            .clickable(enabled = enabled, onClick = onClick)
    ) { content(dragging) }
}

@Composable
private fun TableMeldField(state: GameState) {
    val melds = state.players.flatMapIndexed { index, player -> player.melds.map { Triple(index, player.name, it) } }
    if (melds.isEmpty()) return
    Row(
        Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        melds.forEach { (ownerIndex, owner, meld) ->
            MeldFan(ownerIndex, owner, meld)
        }
    }
}

@Composable
private fun MeldFan(ownerIndex: Int, owner: String, meld: Meld) {
    Surface(color = Ink.copy(alpha = 0.38f), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniAvatar(ownerIndex, active = false)
                Spacer(Modifier.width(5.dp))
                Column {
                    Text(owner, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text(meld.type.label().uppercase(), color = Gold, fontSize = 7.sp)
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-23).dp)) {
                meld.cards.forEach { card ->
                    PlayingCardFace(card, false, Modifier.size(43.dp, 63.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayingCardFace(card: GameCard, selected: Boolean, modifier: Modifier) {
    val ink = if (card.suit?.red == true) RedSuit else Ink
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(if (selected) 3.dp else 1.dp, if (selected) Gold else Color(0xFFD7D9DC), RoundedCornerShape(10.dp))
                .padding(5.dp)
        ) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.rank.shortLabel(), color = ink, fontSize = 14.sp, fontWeight = FontWeight.Black, lineHeight = 14.sp)
                Text(if (card.isJoker) "★" else card.suit?.symbol().orEmpty(), color = if (card.isJoker) Teal else ink, fontSize = 11.sp, lineHeight = 11.sp)
            }
            Text(
                if (card.isJoker) "★" else card.suit?.symbol().orEmpty(),
                modifier = Modifier.align(Alignment.Center),
                color = if (card.isJoker) Teal else ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            if (card.isJoker) {
                Text("JOKER", modifier = Modifier.align(Alignment.BottomCenter), color = Teal, fontSize = 7.sp, fontWeight = FontWeight.Black)
            } else {
                Text(card.suit?.symbol().orEmpty(), modifier = Modifier.align(Alignment.BottomEnd), color = ink, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyPile(label: String) {
    Box(
        Modifier.size(72.dp, 100.dp).border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White.copy(alpha = 0.35f), fontSize = 8.sp) }
}

@Composable
private fun RoundSummaryOverlay(state: GameState, next: () -> Unit, exit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = Ink.copy(alpha = 0.92f), shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 480.dp).padding(16.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text(state.message, color = Gold, textAlign = TextAlign.Center)
                state.players.sortedBy { it.score }.forEachIndexed { index, player ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${player.name}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("+${player.roundPoints} · ${player.score}", color = if (index == 0) Gold else SoftWhite)
                    }
                }
                if (state.phase == TurnPhase.ROUND_OVER) Button(onClick = next, modifier = Modifier.fillMaxWidth()) { Text("Deal Next Round") }
                else Button(onClick = exit, modifier = Modifier.fillMaxWidth()) { Text("Return") }
            }
        }
    }
}

@Composable
private fun RulesScreen(back: () -> Unit) {
    AppBackdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = back) { Text("← Back", color = Color.White) }
            Text("Carioca Rules", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Rule("Deck", "Two standard decks plus four Jokers: 108 cards. Red aces are ordinary natural cards.")
            Rule("Wildcards", "Jokers are the only wildcards. At most one Joker per meld, and it cannot be replaced after it is laid.")
            Rule("Turn", "Draw from the deck or steal the glowing top discard. A steal adds 2 points. Lay eligible cards, then discard one card.")
            Rule("Touch", "Tap cards to select them. Drag the selected group onto the table to lay it. Drag a single card onto the discard pile to finish your turn. During draw phase, drag the deck or glowing discard into your hand.")
            Rule("Rounds", "Regular mode has 8 rounds. Special mode adds crazy straight, colour straight, and royal straight as rounds 9–11.")
            Rule("Going out", "Complete the round contract before going out. There is no −30 going-out bonus.")
            Rule("Scoring", "Cards left in hand and steal penalties count against you. Lowest cumulative score wins.")
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Rule(title: String, text: String) {
    Surface(color = Color.White.copy(alpha = 0.07f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold)
            Text(text, color = SoftWhite, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun DecorativeCards(modifier: Modifier) {
    Box(modifier) {
        listOf(
            Triple("A", "♥", RedSuit),
            Triple("K", "♠", Ink),
            Triple("J", "★", Teal)
        ).forEachIndexed { index, item ->
            Surface(
                color = Color(0xFFFFFEFB),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(82.dp, 118.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = (index - 1) * 42.dp.toPx()
                        translationY = kotlin.math.abs(index - 1) * 11.dp.toPx()
                        rotationZ = (index - 1) * 12f
                    },
                shadowElevation = 10.dp
            ) {
                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(item.first, color = item.third, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(item.second, color = item.third, fontSize = 34.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(item.second, color = item.third, fontSize = 14.sp)
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
