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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carioca.game.domain.Card as GameCard
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
private val SoftWhite = Color(0xFFEAF4F7)
private val RedSuit = Color(0xFFC73E3A)

@Composable
fun CariocaApp() {
    var screen by remember { mutableStateOf("menu") }
    var mode by remember { mutableStateOf(GameMode.REGULAR) }
    var players by remember { mutableIntStateOf(4) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Gold)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Navy, DeepBlue)))
                .padding(16.dp)
        ) {
            when (screen) {
                "menu" -> MainMenu(
                    mode = mode,
                    setMode = { mode = it },
                    practice = { screen = "setup" },
                    rules = { screen = "rules" }
                )
                "setup" -> PracticeSetup(
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
                    players = players,
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
    practice: () -> Unit,
    rules: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text("CARIOCA", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
        Text("Playable practice table", color = SoftWhite)
        Panel {
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
        FullButton("Play Against AI", practice)
        OutlinedButton(onClick = rules, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Rules")
        }
        Text("2–4 players · Easy / Medium / Hard", color = Color(0xFFA9C7D4), fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = back) { Text("← Back", color = Color.White) }
        Text("Practice Setup", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("${mode.name.pretty()} · ${mode.rounds} rounds", color = Gold, fontWeight = FontWeight.Bold)
        Panel {
            Text("Total players", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..4).forEach { count ->
                    FilterChip(
                        selected = players == count,
                        onClick = { setPlayers(count) },
                        label = { Text(count.toString()) }
                    )
                }
            }
        }
        Panel {
            Text("AI difficulty", color = Color.White, fontWeight = FontWeight.Bold)
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
        Panel {
            Text(
                "Draw from the deck or steal the glowing discard. Select cards to lay your contract, then select exactly one card to discard. AI turns run automatically.",
                color = SoftWhite,
                lineHeight = 20.sp
            )
        }
        FullButton("Deal Cards", start)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.09f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun FullButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GameTable(mode: GameMode, players: Int, difficulty: Difficulty, exit: () -> Unit) {
    var state by remember(mode, players, difficulty) {
        mutableStateOf(GameEngine.newGame(mode, players, difficulty))
    }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 48) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    val canSteal = state.currentPlayer == 0 && state.phase == TurnPhase.DRAW && state.discardPile.isNotEmpty()
    LaunchedEffect(state.roundIndex, state.currentPlayer, state.phase, state.discardPile.size) {
        if (canSteal) tone.startTone(ToneGenerator.TONE_PROP_ACK, 100)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = exit) { Text("Exit", color = Color.White) }
            Column(horizontalAlignment = Alignment.End) {
                Text("Round ${state.roundIndex + 1}/${state.mode.rounds}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${state.difficulty.name.pretty()} AI", color = Gold, fontSize = 11.sp)
            }
        }
        ScoreStrip(state)
        ContractPanel(state)

        if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) {
            RoundSummary(state, next = { state = GameEngine.nextRound(state) }, exit = exit)
        } else {
            Opponents(state)
            TableMelds(state)
            DrawPiles(
                state = state,
                canSteal = canSteal,
                draw = { state = GameEngine.drawFromDeck(state) },
                steal = { state = GameEngine.stealDiscard(state) }
            )
            Surface(color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(state.message, modifier = Modifier.padding(9.dp), color = SoftWhite, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { state = GameEngine.laySelected(state) },
                    enabled = state.phase == TurnPhase.ACTION && state.selected.isNotEmpty(),
                    modifier = Modifier.width(155.dp)
                ) { Text("Lay selected") }
                OutlinedButton(
                    onClick = { state = GameEngine.discardSelected(state) },
                    enabled = state.phase == TurnPhase.ACTION && state.selected.size == 1,
                    modifier = Modifier.width(155.dp)
                ) { Text("Discard") }
            }
            HumanHand(state) { card -> state = GameEngine.toggleSelection(state, card) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ScoreStrip(state: GameState) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        state.players.forEach { player ->
            Surface(color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "${player.name}: ${player.score}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = if (player.isHuman) Gold else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ContractPanel(state: GameState) {
    val remaining = GameEngine.remainingRequirements(state.players.first(), state.roundRule)
    Surface(color = Gold.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Contract · ${state.roundRule.description()}", color = Gold, fontWeight = FontWeight.Bold)
            Text(
                if (remaining.isEmpty()) "Complete — you may add to table melds."
                else "Remaining: ${remaining.joinToString { it.label() }}",
                color = SoftWhite,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Opponents(state: GameState) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.players.drop(1).forEach { player ->
            Surface(color = Color(0xFF244F67), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(player.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${player.hand.size} cards", color = SoftWhite, fontSize = 11.sp)
                    if (player.melds.isNotEmpty()) Text("${player.melds.size} melds", color = Gold, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun TableMelds(state: GameState) {
    val melds = state.players.flatMap { owner -> owner.melds.map { owner.name to it } }
    if (melds.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Table melds", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
private fun DrawPiles(state: GameState, canSteal: Boolean, draw: () -> Unit, steal: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(76.dp, 106.dp)
                .background(Color(0xFF2F6D98), RoundedCornerShape(10.dp))
                .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                .clickable(enabled = state.phase == TurnPhase.DRAW, onClick = draw),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DRAW", color = Color.White, fontWeight = FontWeight.Black)
                Text(state.drawPile.size.toString(), color = SoftWhite, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(26.dp))
        val top = state.discardPile.lastOrNull()
        if (top != null) GlowingDiscard(top, canSteal, steal)
        else Box(Modifier.size(76.dp, 106.dp).border(2.dp, Color.Gray, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text("EMPTY", color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun GlowingDiscard(card: GameCard, active: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shine")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        Modifier
            .size(76.dp, 106.dp)
            .border(if (active) 5.dp else 2.dp, if (active) Gold.copy(alpha = alpha) else Color.Gray, RoundedCornerShape(10.dp))
            .clickable(enabled = active, onClick = onClick)
    ) {
        CardFace(card, Modifier.fillMaxSize(), selected = false)
    }
}

@Composable
private fun HumanHand(state: GameState, select: (GameCard) -> Unit) {
    val human = state.players.first()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "Your hand · ${human.hand.size} cards${if (human.steals > 0) " · +${human.steals * 2} steal points" else ""}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            human.hand.forEach { card ->
                CardFace(
                    card = card,
                    modifier = Modifier.size(58.dp, 86.dp),
                    selected = card in state.selected,
                    enabled = state.phase == TurnPhase.ACTION,
                    onClick = { select(card) }
                )
            }
        }
    }
}

@Composable
private fun CardFace(card: GameCard, modifier: Modifier, selected: Boolean, enabled: Boolean = false, onClick: () -> Unit = {}) {
    val ink = if (card.suit?.red == true) RedSuit else Navy
    Card(
        modifier = modifier
            .border(if (selected) 4.dp else 1.dp, if (selected) Gold else Color.LightGray, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(card.rank.shortLabel(), color = ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(
                if (card.isJoker) "★" else card.suit?.symbol().orEmpty(),
                modifier = Modifier.fillMaxWidth(),
                color = if (card.isJoker) Teal else ink,
                textAlign = TextAlign.Center,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(if (card.isJoker) "JOKER" else card.suit?.symbol().orEmpty(), color = ink, fontSize = 8.sp)
        }
    }
}

@Composable
private fun RoundSummary(state: GameState, next: () -> Unit, exit: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(18.dp))
        Text(
            if (state.phase == TurnPhase.GAME_OVER) "Game Complete" else "Round Complete",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
        Text(state.message, color = Gold, textAlign = TextAlign.Center)
        Panel {
            state.players.sortedBy { it.score }.forEachIndexed { index, player ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${index + 1}. ${player.name}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("+${player.roundPoints} · ${player.score} total", color = if (index == 0) Gold else SoftWhite)
                }
            }
        }
        if (state.phase == TurnPhase.ROUND_OVER) FullButton("Deal Next Round", next)
        else FullButton("Return to Menu", exit)
    }
}

@Composable
private fun RulesScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = back) { Text("← Back", color = Color.White) }
        Text("Carioca Rules", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Rule("Deck", "Two standard decks plus four Jokers: 108 cards. Red aces are natural cards.")
        Rule("Wildcards", "Jokers are the only wildcards. At most one Joker per meld, and it cannot be replaced after it is laid.")
        Rule("Turn", "Draw from the deck or steal the glowing top discard. A steal adds 2 points. Lay eligible cards, then discard one card.")
        Rule("Rounds", "Regular mode has 8 rounds. Special mode adds crazy straight, colour straight, and royal straight as rounds 9–11.")
        Rule("Going out", "Complete the round contract before going out. There is no −30 going-out bonus.")
        Rule("Scoring", "Cards left in hand and steal penalties count against you. Lowest cumulative score wins.")
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Rule(title: String, text: String) {
    Panel {
        Text(title, color = Gold, fontWeight = FontWeight.Bold)
        Text(text, color = SoftWhite, lineHeight = 20.sp)
    }
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

private fun GameCard.label(): String = if (isJoker) "JOKER" else "${rank.shortLabel()}${suit?.symbol().orEmpty()}"
private fun String.pretty(): String = lowercase().replaceFirstChar { it.uppercase() }
