package com.carioca.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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

class AiGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AiGameRoot { finish() } }
    }
}

private val AiNavy = Color(0xFF071A29)
private val AiDeep = Color(0xFF0C3148)
private val AiFelt = Color(0xFF0D5C58)
private val AiFeltDark = Color(0xFF073F3E)
private val AiInk = Color(0xFF0D2438)
private val AiTeal = Color(0xFF1D8B83)
private val AiGold = Color(0xFFFFC857)
private val AiSoft = Color(0xFFF2FAFB)
private val AiMuted = Color(0xFF9FC3CC)
private val AiLegal = Color(0xFF73E0C1)
private val AiRed = Color(0xFFC93645)
private val AiBack = Color(0xFF155B7A)
private val AiShadow = Shadow(Color.Black.copy(alpha = .95f), Offset(2.4f, 2.4f), 4f)

private val AiHandW = 50.dp
private val AiHandH = 73.dp
private val AiPileW = 58.dp
private val AiPileH = 80.dp

private enum class AiScreen { SETUP, TABLE }
private data class AiMeldTarget(val owner: Int, val meldIndex: Int)

@Composable
private fun AiGameRoot(onExit: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(AiScreen.SETUP) }
    var mode by rememberSaveable { mutableStateOf(GameMode.REGULAR) }
    var players by rememberSaveable { mutableIntStateOf(4) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }

    BackHandler {
        if (screen == AiScreen.TABLE) screen = AiScreen.SETUP else onExit()
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = AiTeal, secondary = AiGold)) {
        when (screen) {
            AiScreen.SETUP -> AiSetup(
                mode = mode,
                players = players,
                difficulty = difficulty,
                setMode = { mode = it },
                setPlayers = { players = it },
                setDifficulty = { difficulty = it },
                onStart = { screen = AiScreen.TABLE },
                onExit = onExit
            )
            AiScreen.TABLE -> AiTable(mode, players, difficulty) { screen = AiScreen.SETUP }
        }
    }
}

@Composable
private fun AiBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AiNavy, AiDeep))), content = content)
}

@Composable
private fun AiSetup(
    mode: GameMode,
    players: Int,
    difficulty: Difficulty,
    setMode: (GameMode) -> Unit,
    setPlayers: (Int) -> Unit,
    setDifficulty: (Difficulty) -> Unit,
    onStart: () -> Unit,
    onExit: () -> Unit
) {
    AiBackdrop {
        Row(
            Modifier.fillMaxSize().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(.95f).fillMaxHeight(),
                color = AiFelt,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AiGold.copy(alpha = .28f))
            ) {
                Column(
                    Modifier.fillMaxSize().background(Brush.radialGradient(listOf(AiTeal.copy(alpha = .42f), AiFeltDark))).padding(18.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("CARIOCA", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = AiShadow))
                    Text("AI PRACTICE", color = AiGold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = TextStyle(shadow = AiShadow))
                    Spacer(Modifier.height(10.dp))
                    Text("Cards are the controls.", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = AiShadow))
                    Text("Drag a pile into your hand to draw. Tap each required Trio or Straight to keep its cards selected. When the complete round goal is selected, drag the group to the table. Drag one card onto DISCARD to end the turn. Drag cards sideways in your hand to rearrange them.", color = AiSoft, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }

            Surface(
                modifier = Modifier.weight(1.15f).fillMaxHeight(),
                color = Color.White.copy(alpha = .075f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .12f))
            ) {
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    Text("Game setup", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = AiShadow))
                    AiOptionRow("Mode") {
                        GameMode.entries.forEach { item ->
                            FilterChip(selected = mode == item, onClick = { setMode(item) }, label = { Text("${item.name.aiPretty()} · ${item.rounds}", fontSize = 10.sp) })
                        }
                    }
                    AiOptionRow("Players") {
                        (2..4).forEach { count -> FilterChip(selected = players == count, onClick = { setPlayers(count) }, label = { Text(count.toString()) }) }
                    }
                    AiOptionRow("AI difficulty") {
                        Difficulty.entries.forEach { level -> FilterChip(selected = difficulty == level, onClick = { setDifficulty(level) }, label = { Text(level.name.aiPretty(), fontSize = 10.sp) }) }
                    }
                }
            }

            Surface(
                modifier = Modifier.width(190.dp).fillMaxHeight(),
                color = AiInk.copy(alpha = .78f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AiGold.copy(alpha = .30f))
            ) {
                Column(
                    Modifier.fillMaxSize().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("READY?", color = AiGold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${mode.name.aiPretty()}\n$players players\n${difficulty.name.aiPretty()} AI", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 21.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp)) {
                        Text("START\nAI GAME", fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("Home") }
                }
            }
        }
    }
}

@Composable
private fun AiOptionRow(label: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = AiMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun AiTable(mode: GameMode, players: Int, difficulty: Difficulty, onExit: () -> Unit) {
    var state by remember(mode, players, difficulty) { mutableStateOf(GameEngine.newGame(mode, players, difficulty)) }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var floating by remember { mutableStateOf<GameCard?>(null) }
    var tableBounds by remember { mutableStateOf(Rect.Zero) }
    var handBounds by remember { mutableStateOf(Rect.Zero) }
    var discardBounds by remember { mutableStateOf(Rect.Zero) }
    var meldBounds by remember { mutableStateOf<Map<AiMeldTarget, Rect>>(emptyMap()) }
    var handCardBounds by remember { mutableStateOf<Map<GameCard, Rect>>(emptyMap()) }
    var handOrder by remember { mutableStateOf<List<GameCard>>(emptyList()) }

    val human = state.players.first()
    LaunchedEffect(human.hand) {
        val current = human.hand.toSet()
        val kept = handOrder.filter { it in current }
        handOrder = kept + human.hand.filter { it !in kept }
    }

    val selectedCards = human.hand.filter { it in state.selected }
    val remaining = GameEngine.remainingRequirements(human, state.roundRule)
    val fullPlan = if (selectedCards.isNotEmpty() && remaining.isNotEmpty()) GameRules.findMeldPlan(selectedCards, remaining, useAllCards = true) else null
    val partialPlan = if (selectedCards.isNotEmpty() && remaining.isNotEmpty()) GameRules.findPartialMeldPlan(selectedCards, remaining) else null
    val selectedGroups = partialPlan?.size ?: 0
    val canLowerGoal = state.currentPlayer == 0 && state.phase == TurnPhase.ACTION && fullPlan != null

    fun reorderInHand(card: GameCard, point: Offset) {
        val ordered = handOrder.filter { it in human.hand }.toMutableList()
        if (card !in ordered) return
        val from = ordered.indexOf(card)
        ordered.removeAt(from)
        val nearest = handCardBounds
            .filterKeys { it != card && it in ordered }
            .minByOrNull { (_, rect) -> kotlin.math.abs(rect.center.x - point.x) }
            ?.key
        val target = nearest?.let { ordered.indexOf(it) } ?: ordered.size
        ordered.add(target.coerceIn(0, ordered.size), card)
        handOrder = ordered
    }

    fun lowerSelectedGoal() {
        if (canLowerGoal) state = GameEngine.createMeld(state)
    }

    fun dropCard(card: GameCard, point: Offset) {
        if (state.currentPlayer != 0) return

        if (handBounds != Rect.Zero && handBounds.contains(point)) {
            reorderInHand(card, point)
            dragPoint = null
            floating = null
            return
        }

        if (state.phase != TurnPhase.ACTION) {
            dragPoint = null
            floating = null
            return
        }

        val group = if (card in state.selected && state.selected.isNotEmpty()) state.selected else setOf(card)
        val target = meldBounds.entries.firstOrNull { it.value.contains(point) }?.key
        state = when {
            discardBounds != Rect.Zero && discardBounds.contains(point) -> GameEngine.discardSelected(state.copy(selected = setOf(card)))
            target != null -> GameEngine.addSelectedToMeld(state.copy(selected = group), target.owner, target.meldIndex)
            tableBounds != Rect.Zero && tableBounds.contains(point) -> GameEngine.createMeld(state.copy(selected = group))
            else -> state
        }
        dragPoint = null
        floating = null
    }

    AiBackdrop {
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AiHud(state, onExit)
            AiOpponents(state)
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .background(Brush.radialGradient(listOf(AiFelt, AiFeltDark)), RoundedCornerShape(22.dp))
                    .border(2.dp, AiGold.copy(alpha = .24f), RoundedCornerShape(22.dp))
            ) {
                if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) {
                    AiRoundSummary(state, { state = GameEngine.nextRound(state) }, onExit)
                } else {
                    AiGoalPanel(
                        state = state,
                        selectedCount = selectedCards.size,
                        selectedGroups = selectedGroups,
                        canLower = canLowerGoal,
                        onLower = ::lowerSelectedGoal,
                        modifier = Modifier.align(Alignment.TopStart).padding(7.dp).width(164.dp)
                    )

                    AiMeldArea(
                        state = state,
                        dragPoint = dragPoint,
                        canCreateMeld = canLowerGoal,
                        setTableBounds = { tableBounds = it },
                        onMeldBounds = { target, bounds -> meldBounds = meldBounds + (target to bounds) },
                        modifier = Modifier.fillMaxSize().padding(start = 174.dp, end = 158.dp, top = 6.dp, bottom = 26.dp)
                    )

                    AiPileStation(
                        state = state,
                        handBounds = handBounds,
                        dragPoint = dragPoint,
                        setDiscardBounds = { discardBounds = it },
                        onDrag = { dragPoint = it },
                        onDraw = { state = GameEngine.drawFromDeck(state) },
                        onSteal = { state = GameEngine.stealDiscard(state) },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(146.dp)
                    )

                    Surface(
                        color = AiInk.copy(alpha = .58f),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(.64f).padding(bottom = 4.dp)
                    ) {
                        Text(aiFriendlyMessage(state.message), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = AiSoft, fontSize = 8.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            AiHandDock(
                state = state,
                cards = handOrder.filter { it in human.hand } + human.hand.filter { it !in handOrder },
                selectedCount = selectedCards.size,
                setHandBounds = { handBounds = it },
                onCardBounds = { card, bounds -> handCardBounds = handCardBounds + (card to bounds) },
                onToggle = { state = GameEngine.toggleSelection(state, it) },
                onDrag = { dragPoint = it },
                onFloating = { floating = it },
                onDrop = ::dropCard,
                modifier = Modifier.fillMaxWidth().height(101.dp)
            )
        }

        if (floating != null && dragPoint != null) {
            val groupCount = if (floating in state.selected) state.selected.size.coerceAtLeast(1) else 1
            AiFloatingCard(floating!!, dragPoint!!, groupCount)
        }
    }
}

@Composable
private fun AiHud(state: GameState, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExit, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("← Setup", color = AiMuted, fontSize = 10.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROUND ${state.roundIndex + 1} / ${state.mode.rounds}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = AiShadow))
            Text(state.roundRule.aiGoalTitle().uppercase(), color = AiGold, fontSize = 7.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = AiShadow))
        }
        Spacer(Modifier.width(55.dp))
    }
}

@Composable
private fun AiOpponents(state: GameState) {
    Row(Modifier.fillMaxWidth().height(45.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        state.players.drop(1).forEachIndexed { i, player ->
            val active = state.currentPlayer == i + 1
            Surface(color = Color.White.copy(alpha = .07f), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(if (active) 2.dp else 1.dp, if (active) AiGold else Color.White.copy(alpha = .08f))) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(if (active) AiGold else AiTeal.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                        Text((i + 1).toString(), color = AiInk, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Text(player.name, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("${player.hand.size} cards · ${player.score} pts", color = AiMuted, fontSize = 6.sp)
                        if (player.melds.isNotEmpty()) Text("${player.melds.size} melds", color = AiGold, fontSize = 6.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiGoalPanel(
    state: GameState,
    selectedCount: Int,
    selectedGroups: Int,
    canLower: Boolean,
    onLower: () -> Unit,
    modifier: Modifier
) {
    val player = state.players.first()
    val all = GameRules.requiredTypes(state.roundRule)
    val remaining = GameEngine.remainingRequirements(player, state.roundRule)
    val complete = GameEngine.contractComplete(player, state.roundRule)
    val ready = !complete && GameEngine.contractReady(player, state.roundRule)
    val borderColor = when {
        complete || canLower -> AiLegal
        selectedGroups > 0 -> AiGold
        else -> Color.White.copy(alpha = .10f)
    }

    Surface(modifier, color = AiInk.copy(alpha = .88f), shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(if (complete || canLower || selectedGroups > 0) 2.dp else 1.dp, borderColor)) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ROUND GOAL", color = AiGold, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(state.roundRule.aiGoalTitle(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = AiShadow))
            Text(state.roundRule.aiGoalExplanation(), color = AiSoft, fontSize = 7.sp, lineHeight = 9.sp)
            HorizontalDivider(color = Color.White.copy(alpha = .10f))
            Text(
                when {
                    complete -> "✓ ROUND GOAL LOWERED"
                    canLower -> "✓ COMPLETE SELECTION READY"
                    selectedGroups > 0 -> "$selectedGroups/${remaining.size} required groups selected"
                    ready -> "Goal is available in your hand"
                    else -> "Select each required group"
                },
                color = if (complete || canLower) AiLegal else if (selectedGroups > 0) AiGold else AiSoft,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            if (!complete && remaining.isNotEmpty()) Text("Required: ${remaining.joinToString(" + ") { it.aiLabel() }}", color = AiGold, fontSize = 7.sp)
            Text("Selected cards: $selectedCount", color = if (selectedCount > 0) Color.White else AiMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = onLower,
                enabled = canLower,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = PaddingValues(horizontal = 5.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    when {
                        canLower -> "LOWER ROUND GOAL"
                        selectedGroups > 0 -> "KEEP SELECTING (${selectedGroups}/${remaining.size})"
                        complete -> "GOAL LOWERED"
                        else -> "SELECT FULL GOAL"
                    },
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AiPileStation(
    state: GameState,
    handBounds: Rect,
    dragPoint: Offset?,
    setDiscardBounds: (Rect) -> Unit,
    onDrag: (Offset?) -> Unit,
    onDraw: () -> Unit,
    onSteal: () -> Unit,
    modifier: Modifier
) {
    val canDraw = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW
    val canSteal = canDraw && state.discardPile.isNotEmpty()
    var localDiscardBounds by remember { mutableStateOf(Rect.Zero) }
    val discardHover = dragPoint?.let { localDiscardBounds != Rect.Zero && localDiscardBounds.contains(it) } == true

    Surface(modifier, color = AiInk.copy(alpha = .76f), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(2.dp, if (discardHover) AiLegal else Color.White.copy(alpha = .12f))) {
        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("DRAW / DISCARD", color = AiGold, fontSize = 8.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = AiShadow))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.border(if (canDraw) 2.dp else 0.dp, if (canDraw) AiLegal else Color.Transparent, RoundedCornerShape(9.dp))) {
                        AiDraggablePile(canDraw, handBounds, onDrag, onDraw) { AiDrawPile(state.drawPile.size) }
                    }
                    Text("DRAW", color = if (canDraw) AiLegal else AiSoft, fontSize = 7.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(AiPileW, AiPileH)
                            .onGloballyPositioned {
                                localDiscardBounds = it.aiRootBounds()
                                setDiscardBounds(localDiscardBounds)
                            }
                            .border(if (discardHover || canSteal) 3.dp else 2.dp, if (discardHover || canSteal) AiLegal else AiGold.copy(alpha = .75f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val top = state.discardPile.lastOrNull()
                        if (top == null) {
                            Box(Modifier.fillMaxSize().background(AiInk.copy(alpha = .55f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Text("DROP\nHERE", color = AiGold, fontSize = 7.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            }
                        } else {
                            AiDraggablePile(canSteal, handBounds, onDrag, onSteal) { AiCardFace(top, false, Modifier.fillMaxSize()) }
                        }
                    }
                    Text("DISCARD", color = AiGold, fontSize = 8.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = AiShadow))
                }
            }
            Text(if (canDraw) "Drag DRAW or available DISCARD into your hand" else "Drag one hand card onto DISCARD", color = AiSoft, fontSize = 6.sp, lineHeight = 8.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AiMeldArea(
    state: GameState,
    dragPoint: Offset?,
    canCreateMeld: Boolean,
    setTableBounds: (Rect) -> Unit,
    onMeldBounds: (AiMeldTarget, Rect) -> Unit,
    modifier: Modifier
) {
    val totalCards = state.players.sumOf { p -> p.melds.sumOf { it.cards.size } }
    val cardW = when {
        totalCards >= 36 -> 25.dp
        totalCards >= 24 -> 28.dp
        else -> 32.dp
    }
    val cardH = cardW * 1.46f
    var localBounds by remember { mutableStateOf(Rect.Zero) }
    val tableHover = dragPoint?.let { localBounds != Rect.Zero && localBounds.contains(it) } == true

    Surface(
        modifier = modifier.onGloballyPositioned {
            localBounds = it.aiRootBounds()
            setTableBounds(localBounds)
        },
        color = if (tableHover && canCreateMeld) AiLegal.copy(alpha = .08f) else Color.White.copy(alpha = .025f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(if (tableHover && canCreateMeld) 2.dp else 1.dp, if (tableHover && canCreateMeld) AiLegal else Color.White.copy(alpha = .10f))
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MELD AREA", color = if (canCreateMeld) AiLegal else Color.White.copy(alpha = .62f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (state.players.all { it.melds.isEmpty() }) {
                Box(Modifier.fillMaxWidth().height(85.dp), contentAlignment = Alignment.Center) {
                    Text("Build the round goal by selecting each required Trio/Straight.\nKeep those cards selected. When the full goal is ready, drag the selected group here.", color = Color.White.copy(alpha = .58f), fontSize = 8.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)
                }
            }
            state.players.forEachIndexed { owner, player ->
                if (player.melds.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 49.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(player.name, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(45.dp))
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            player.melds.forEachIndexed { meldIndex, meld ->
                                val target = AiMeldTarget(owner, meldIndex)
                                var bounds by remember(owner, meldIndex, meld.cards.size) { mutableStateOf(Rect.Zero) }
                                val over = dragPoint?.let { bounds != Rect.Zero && bounds.contains(it) } == true
                                AiMeldFan(
                                    meld = meld,
                                    cardW = cardW,
                                    cardH = cardH,
                                    highlight = over,
                                    modifier = Modifier.onGloballyPositioned {
                                        bounds = it.aiRootBounds()
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
}

@Composable
private fun AiMeldFan(meld: Meld, cardW: Dp, cardH: Dp, highlight: Boolean, modifier: Modifier) {
    Surface(modifier, color = AiInk.copy(alpha = .48f), shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) AiLegal else Color.White.copy(alpha = .08f))) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(meld.type.aiLabel().uppercase(), color = AiGold, fontSize = 5.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(-(cardW * .50f))) {
                meld.cards.forEach { card -> AiCardFace(card, false, Modifier.size(cardW, cardH), compact = true) }
            }
        }
    }
}

@Composable
private fun AiHandDock(
    state: GameState,
    cards: List<GameCard>,
    selectedCount: Int,
    setHandBounds: (Rect) -> Unit,
    onCardBounds: (GameCard, Rect) -> Unit,
    onToggle: (GameCard) -> Unit,
    onDrag: (Offset?) -> Unit,
    onFloating: (GameCard?) -> Unit,
    onDrop: (GameCard, Offset) -> Unit,
    modifier: Modifier
) {
    val humanCanTouch = state.currentPlayer == 0
    Box(modifier.onGloballyPositioned { setHandBounds(it.aiRootBounds()) }.background(AiInk.copy(alpha = .84f), RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(116.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("YOUR HAND", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${cards.size} cards · Selected $selectedCount", color = if (selectedCount > 0) AiGold else AiMuted, fontSize = 6.sp)
                Text(
                    when {
                        state.phase == TurnPhase.DRAW && humanCanTouch -> "Draw first · cards can still be rearranged"
                        state.phase == TurnPhase.ACTION && humanCanTouch -> "Tap to select · drag to lower / discard / reorder"
                        else -> "AI turn"
                    },
                    color = AiMuted,
                    fontSize = 6.sp,
                    textAlign = TextAlign.Center
                )
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((-9).dp), verticalAlignment = Alignment.CenterVertically) {
                cards.forEach { card ->
                    AiDraggableCard(
                        card = card,
                        selected = card in state.selected,
                        enabled = humanCanTouch,
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
private fun AiDraggableCard(
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

    AiCardFace(
        card,
        selected,
        Modifier.size(AiHandW, AiHandH)
            .offset(y = if (selected && !dragging) (-6).dp else 0.dp)
            .onGloballyPositioned {
                measured = it.size
                if (!dragging) origin = it.positionInRoot()
                onBounds(it.aiRootBounds())
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
private fun AiDraggablePile(enabled: Boolean, handBounds: Rect, onDrag: (Offset?) -> Unit, onDrop: () -> Unit, content: @Composable () -> Unit) {
    var delta by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    fun center() = origin + delta + Offset(measured.width / 2f, measured.height / 2f)

    Box(
        Modifier.size(AiPileW, AiPileH)
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
private fun AiDrawPile(count: Int) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(AiBack).border(2.dp, AiSoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CARIOCA", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Black)
            Text(count.toString(), color = AiGold, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AiCardFace(card: GameCard, selected: Boolean, modifier: Modifier, compact: Boolean = false) {
    val ink = if (card.suit?.red == true) AiRed else AiInk
    Card(modifier = modifier, shape = RoundedCornerShape(if (compact) 5.dp else 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)), elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 1.dp else 2.dp)) {
        Box(Modifier.fillMaxSize().border(if (selected) 3.dp else 1.dp, if (selected) AiGold else Color(0xFFD7D9DC), RoundedCornerShape(if (compact) 5.dp else 8.dp)).padding(if (compact) 2.dp else 4.dp)) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.rank.aiRank(), color = ink, fontSize = if (compact) 7.sp else 11.sp, fontWeight = FontWeight.Black)
                Text(if (card.isJoker) "★" else card.suit?.aiSuit().orEmpty(), color = if (card.isJoker) AiTeal else ink, fontSize = if (compact) 6.sp else 8.sp)
            }
            Text(if (card.isJoker) "★" else card.suit?.aiSuit().orEmpty(), Modifier.align(Alignment.Center), color = if (card.isJoker) AiTeal else ink, fontSize = if (compact) 14.sp else 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AiFloatingCard(card: GameCard, center: Offset, groupCount: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { AiHandW.toPx() }
    val h = with(density) { AiHandH.toPx() }
    Box(
        Modifier.offset { IntOffset((center.x - w / 2).roundToInt(), (center.y - h / 2).roundToInt()) }
            .size(AiHandW, AiHandH)
            .zIndex(100f)
    ) {
        if (groupCount > 1) {
            repeat(minOf(groupCount - 1, 3)) { index ->
                Box(
                    Modifier.matchParentSize()
                        .offset(x = (index + 1).dp, y = (index + 1).dp)
                        .background(Color.White.copy(alpha = .88f), RoundedCornerShape(8.dp))
                        .border(1.dp, AiGold.copy(alpha = .65f), RoundedCornerShape(8.dp))
                )
            }
        }
        AiCardFace(card, true, Modifier.matchParentSize().graphicsLayer { scaleX = 1.1f; scaleY = 1.1f; shadowElevation = 14.dp.toPx() })
        if (groupCount > 1) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp).size(20.dp).clip(CircleShape).background(AiGold), contentAlignment = Alignment.Center) {
                Text(groupCount.toString(), color = AiInk, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun AiRoundSummary(state: GameState, onNext: () -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = AiInk.copy(alpha = .94f), shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 430.dp).padding(12.dp)) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = AiShadow))
                Text(state.message, color = AiGold, fontSize = 10.sp, textAlign = TextAlign.Center)
                state.players.sortedBy { it.score }.forEachIndexed { index, player ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${player.name}", color = Color.White, fontSize = 9.sp)
                        Text("+${player.roundPoints} · ${player.score}", color = if (index == 0) AiGold else AiSoft, fontSize = 9.sp)
                    }
                }
                if (state.phase == TurnPhase.ROUND_OVER) Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("Deal Next Round") }
                else Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("Return") }
            }
        }
    }
}

private fun LayoutCoordinates.aiRootBounds(): Rect {
    val p = positionInRoot()
    return Rect(p, Size(size.width.toFloat(), size.height.toFloat()))
}

private fun RoundRule.aiGoalTitle(): String {
    special?.let { return it.aiLabel() }
    val parts = mutableListOf<String>()
    if (legs > 0) parts += "$legs ${if (legs == 1) "Trio" else "Trios"}"
    if (straights > 0) parts += "$straights ${if (straights == 1) "Straight" else "Straights"}"
    return parts.joinToString(" + ")
}

private fun RoundRule.aiGoalExplanation(): String = when {
    special == MeldType.CRAZY_STRAIGHT -> "13 ranks, mixed suits allowed, at most one Joker."
    special == MeldType.COLOUR_STRAIGHT -> "13 ranks, all red or all black. No Jokers."
    special == MeldType.ROYAL_STRAIGHT -> "13 ranks, all the same suit. No Jokers."
    legs > 0 && straights > 0 -> "Trio: 3–4 same-rank cards with different suits. Straight: 4+ consecutive cards of one suit. Lower every required group together."
    legs > 0 -> "Each Trio: 3–4 cards of the same rank with different suits. Lower all required Trios together."
    else -> "Each Straight: 4+ consecutive cards of one suit. Lower all required Straights together."
}

private fun MeldType.aiLabel(): String = when (this) {
    MeldType.LEG -> "Trio"
    MeldType.STRAIGHT -> "Straight"
    MeldType.CRAZY_STRAIGHT -> "Crazy Straight"
    MeldType.COLOUR_STRAIGHT -> "Colour Straight"
    MeldType.ROYAL_STRAIGHT -> "Royal Straight"
}

private fun Rank.aiRank(): String = when (this) {
    Rank.ACE -> "A"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    Rank.JOKER -> "J"
    else -> order.toString()
}

private fun Suit.aiSuit(): String = when (this) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
}

private fun String.aiPretty(): String = lowercase().replaceFirstChar { it.uppercase() }

private fun aiFriendlyMessage(message: String): String = message
    .replace("LEG", "Trio", ignoreCase = true)
    .replace("contract", "round goal", ignoreCase = true)
