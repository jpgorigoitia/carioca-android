package com.carioca.game

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carioca.game.domain.Card as PlayingCardModel
import com.carioca.game.domain.Difficulty
import com.carioca.game.domain.GameEngine
import com.carioca.game.domain.GameMode
import com.carioca.game.domain.GameState
import com.carioca.game.domain.MeldType
import com.carioca.game.domain.Rank
import com.carioca.game.domain.RoundRule
import com.carioca.game.domain.Suit
import com.carioca.game.domain.TurnPhase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CariocaApp() }
    }
}

private val Navy = Color(0xFF0D2438)
private val DeepBlue = Color(0xFF163E56)
private val Teal = Color(0xFF167D79)
private val Gold = Color(0xFFFFC857)
private val Cream = Color(0xFFFFFBF1)
private val SoftWhite = Color(0xFFEAF4F7)
private val RedSuit = Color(0xFFC73E3A)

@Composable
fun CariocaApp() {
    var mode by remember { mutableStateOf(GameMode.REGULAR) }
    var screen by remember { mutableStateOf("menu") }
    var players by remember { mutableIntStateOf(4) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            secondary = Gold,
            surface = Cream
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Navy, DeepBlue)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        ) {
            when (screen) {
                "menu" -> MainMenu(
                    mode = mode,
                    setMode = { mode = it },
                    onPractice = { screen = "practice" },
                    onRules = { screen = "rules" }
                )
                "practice" -> PracticeSetup(
                    mode = mode,
                    players = players,
                    difficulty = difficulty,
                    setPlayers = { players = it },
                    setDifficulty = { difficulty = it },
                    start = { screen = "table" },
                    back = { screen = "menu" }
                )
                "rules" -> RulesScreen { screen = "menu" }
                else -> GameTable(
                    mode = mode,
                    totalPlayers = players,
                    difficulty = difficulty,
                    exit = { screen = "menu" }
                )
            }
        }
    }
}

@Composable
private fun MainMenu(
    mode: GameMode,
    setMode: (GameMode) -> Unit,
    onPractice: () -> Unit,
    onRules: () -> Unit
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text("CARIOCA", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text("A playable card table, not a rules mockup.", color = SoftWhite, textAlign = TextAlign.Center)

        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Game mode", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameMode.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { setMode(item) },
                            label = { Text("${item.name.pretty()} · ${item.rounds}") }
                        )
                    }
                }
            }
        }

        MenuButton("Play Against AI", onPractice)
        MenuButton("Private Table · Coming later") { }
        OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Rules")
        }
        Spacer(Modifier.weight(1f))
        Text("2–4 players · 3 AI levels · ad-free", color = Color(0xFFA9C7D4))
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PracticeSetup(
    mode: GameMode,
    players: Int,
    difficulty: Difficulty,
    setPlayers: (Int) -> Unit,
    setDifficulty: (Difficulty) -> Unit,
    start: () -> Unit,
    back: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = back) { Text("← Back", color = Color.White) }
        Text("Practice Setup", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${mode.name.pretty()} game · ${mode.rounds} rounds", color = Gold, fontWeight = FontWeight.Bold)

        SetupPanel("Total players") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..4).forEach { count ->
                    FilterChip(
                        selected = players == count,
                        onClick = { setPlayers(count) },
                        label = { Text("$count") }
                    )
                }
            }
        }

        SetupPanel("AI difficulty") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { level ->
                    FilterChip(
                        selected = difficulty == level,
                        onClick = { setDifficulty(level) },
                        label = { Text(level.name.pretty()) }
                    )
                }
            }
        }

        Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp)) {
            Text(
                "On your turn: draw or steal the glowing discard, select cards to lay your contract, then select one card to discard. AI turns resolve automatically.",
                modifier = Modifier.padding(14.dp),
                color = SoftWhite,
                lineHeight = 20.sp
            )
        }

        Spacer(Modifier.weight(1f))
        MenuButton("Deal Cards", start)
    }
}

@Composable
private fun SetupPanel(title: String, content: @Composable () -> Unit) {
    Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun GameTable(
    mode: GameMode,
    totalPlayers: Int,
    difficulty: Difficulty,
    exit: () -> Unit
) {
    var state by remember(mode, totalPlayers, difficulty) {
        mutableStateOf(GameEngine.newGame(mode, totalPlayers, difficulty))
    }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 48) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    val canSteal = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW && state.discardPile.isNotEmpty()
    LaunchedEffect(state.roundIndex, state.discardPile.size, state.phase, state.currentPlayer) {
        if (canSteal) tone.startTone(ToneGenerator.TONE_PROP_ACK, 100)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TableHeader(state, exit)
        ScoreStrip(state)
        ContractPanel(state)

        if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) {
            RoundSummary(
                state = state,
                onNext = { state = GameEngine.nextRound(state) },
                exit = exit
            )
        } else {
            OpponentStrip(state)
            MeldTable(state)
            DrawArea(
                state = state,
                canSteal = canSteal,
                onDraw = { state = GameEngine.drawFromDeck(state) },
                onSteal = { state = GameEngine.stealDiscard(state) }
            )
            StatusBar(state.message)
            ActionStrip(
                enabled = state.phase == TurnPhase.ACTION,
                selectedCount = state.selected.size,
                onLay = { state = GameEngine.laySelected(state) },
                onDiscard = { state = GameEngine.discardSelected(state) }
            )
            HandArea(
                state = state,
                onCard = { card -> state = GameEngine.toggleSelection(state, card) }
            )
        }
    }
}

@Composable
private fun TableHeader(state: GameState, exit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = exit) { Text("Exit", color = Color.White) }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "Round ${state.roundIndex + 1}/${state.mode.rounds}",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(state.difficulty.name.pretty() + " AI", color = Gold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScoreStrip(state: GameState) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.players.forEach { player ->
            Surface(color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "${player.name}  ${player.score} pts",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (player.isHuman) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ContractPanel(state: GameState) {
    val human = state.players.first()
    val remaining = GameEngine.remainingRequirements(human, state.roundRule)
    Surface(color = Gold.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Contract · ${state.roundRule.description()}", color = Gold, fontWeight = FontWeight.Bold)
            Text(
                if (remaining.isEmpty()) "Contract complete — you may add cards to any table meld."
                else "Remaining: ${remaining.joinToString { it.label() }}",
                color = SoftWhite,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun OpponentStrip(state: GameState) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        state.players.drop(1).forEach { player ->
            Surface(color = Color(0xFF244F67), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(player.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${player.hand.size} cards", color = SoftWhite, fontSize = 11.sp)
                    if (player.melds.isNotEmpty()) Text("${player.melds.size} melds down", color = Gold, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MeldTable(state: GameState) {
    val melds = state.players.flatMap { owner -> owner.melds.map { owner.name to it } }
    if (melds.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Table melds", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            melds.forEach { (owner, meld) ->
                Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(7.dp)) {
                        Text("$owner · ${meld.type.label()}", color = Gold, fontSize = 10.sp)
                        Text(meld.cards.joinToString(" ") { it.label() }, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawArea(
    state: GameState,
    canSteal: Boolean,
    onDraw: () -> Unit,
    onSteal: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardBack(
            count = state.drawPile.size,
            enabled = state.phase == TurnPhase.DRAW,
            onClick = onDraw
        )
        Spacer(Modifier.width(26.dp))
        val top = state.discardPile.lastOrNull()
        if (top != null) {
            GlowingDiscard(
                card = top,
                active = canSteal,
                onClick = onSteal
            )
        } else {
            EmptyPile()
        }
    }
}

@Composable
private fun CardBack(count: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp, 106.dp)
            .background(Color(0xFF2F6D98), RoundedCornerShape(10.dp))
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DRAW", color = Color.White, fontWeight = FontWeight.Black)
            Text("$count", color = SoftWhite, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GlowingDiscard(card: PlayingCardModel, active: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "discard-shine")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "discard-alpha"
    )
    Box(
        Modifier
            .size(76.dp, 106.dp)
            .border(
                if (active) 5.dp else 2.dp,
                if (active) Gold.copy(alpha = alpha) else Color.Gray,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = active, onClick = onClick)
    ) {
        CardFace(card = card, modifier = Modifier.fillMaxSize(), selected = false)
    }
}

@Composable
private fun EmptyPile() {
    Box(
        Modifier.size(76.dp, 106.dp).border(2.dp, Color.Gray, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("EMPTY", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun StatusBar(message: String) {
    Surface(color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(9.dp), color = SoftWhite, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActionStrip(
    enabled: Boolean,
    selectedCount: Int,
    onLay: () -> Unit,
    onDiscard: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onLay,
            enabled = enabled && selectedCount > 0,
            modifier = Modifier.weight(1f)
        ) { Text("Lay selected") }
        OutlinedButton(
            onClick = onDiscard,
            enabled = enabled && selectedCount == 1,
            modifier = Modifier.weight(1f)
        ) { Text("Discard") }
    }
}

@Composable
private fun HandArea(state: GameState, onCard: (PlayingCardModel) -> Unit) {
    val human = state.players.first()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Your hand · ${human.hand.size} cards", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (human.steals > 0) Text("Steal penalty: +${human.steals * 2}", color = Gold, fontSize = 11.sp)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            human.hand.forEach { card ->
                CardFace(
                    card = card,
                    modifier = Modifier.size(58.dp, 86.dp),
                    selected = card in state.selected,
                    enabled = state.phase == TurnPhase.ACTION,
                    onClick = { onCard(card) }
                )
            }
        }
    }
}

@Composable
private fun CardFace(
    card: PlayingCardModel,
    modifier: Modifier,
    selected: Boolean,
    enabled: Boolean = false,
    onClick: () -> Unit = { }
) {
    val ink = if (card.suit?.red == true) RedSuit else Navy
    Card(
        modifier = modifier
            .border(if (selected) 4.dp else 1.dp, if (selected) Gold else Color.LightGray, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            Modifier.fillMaxSize().padding(5.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(card.rank.shortLabel(), color = ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(
                if (card.isJoker) "★" else card.suit?.symbol().orEmpty(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = if (card.isJoker) Teal else ink,
                fontSize = if (card.isJoker) 22.sp else 25.sp,
                fontWeight = FontWeight.Bold
            )
            Text(if (card.isJoker) "JOKER" else card.suit?.symbol().orEmpty(), color = ink, fontSize = 8.sp)
        }
    }
}

@Composable
private fun RoundSummary(state: GameState, onNext: () -> Unit, exit: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black
        )
        Text(state.message, color = Gold, textAlign = TextAlign.Center)

        Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.players.sortedBy { it.score }.forEachIndexed { index, player ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${player.name}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("+${player.roundPoints}  ·  ${player.score} total", color = if (index == 0) Gold else SoftWhite)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (state.phase == TurnPhase.ROUND_OVER) {
            MenuButton("Deal Next Round", onNext)
        } else {
            MenuButton("Return to Menu", exit)
        }
    }
}

@Composable
private fun RulesScreen(back: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        TextButton(onClick = back) { Text("← Back", color = Color.White) }
        Text("Carioca Rules", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Rule("Deck", "Two standard decks plus four Jokers: 108 cards total. Red aces are ordinary natural cards.")
        Rule("Wild cards", "Jokers are the only wildcards. A meld can contain at most one Joker, and a Joker already laid cannot be replaced.")
        Rule("Turn", "Draw from the deck or steal the highlighted top discard. A steal adds 2 points. After drawing, lay eligible cards and finish by discarding one card.")
        Rule("Contracts", "Regular mode has 8 rounds. Special mode adds crazy straight, colour straight, and royal straight as rounds 9–11.")
        Rule("Going out", "Complete the round contract before going out. There is no −30 bonus for laying the contract and emptying your hand in the same action.")
        Rule("Scoring", "Cards left in hand count against you, steal penalties are added, and the lowest cumulative score wins.")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Rule(title: String, body: String) {
    Surface(color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold)
            Text(body, color = SoftWhite, lineHeight = 20.sp)
        }
    }
}

private fun RoundRule.description(): String {
    special?.let { return it.label() }
    val parts = buildList {
        if (legs > 0) add("$legs ${if (legs == 1) "leg" else "legs"}")
        if (straights > 0) add("$straights ${if (straights == 1) "straight" else "straights"}")
    }
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

private fun PlayingCardModel.label(): String =
    if (isJoker) "JOKER" else "${rank.shortLabel()}${suit?.symbol().orEmpty()}"

private fun String.pretty(): String = lowercase().replaceFirstChar { it.uppercase() }
