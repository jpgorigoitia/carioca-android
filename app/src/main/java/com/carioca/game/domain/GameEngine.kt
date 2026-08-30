package com.carioca.game.domain

import kotlin.random.Random

enum class TurnPhase { DRAW, ACTION, ROUND_OVER, GAME_OVER }

data class Meld(val type: MeldType, val cards: List<Card>)

data class PlayerState(
    val name: String,
    val isHuman: Boolean,
    val hand: List<Card> = emptyList(),
    val melds: List<Meld> = emptyList(),
    val score: Int = 0,
    val roundPoints: Int = 0,
    val steals: Int = 0
)

data class GameState(
    val mode: GameMode,
    val difficulty: Difficulty,
    val roundIndex: Int,
    val players: List<PlayerState>,
    val drawPile: List<Card>,
    val discardPile: List<Card>,
    val currentPlayer: Int = 0,
    val phase: TurnPhase = TurnPhase.DRAW,
    val selected: Set<Card> = emptySet(),
    val message: String = "Draw from the deck or steal the discard.",
    val roundWinner: Int? = null,
    val gameWinner: Int? = null
) {
    val roundRule: RoundRule get() = GameRules.rounds[roundIndex]
}

object GameEngine {
    fun newGame(
        mode: GameMode,
        totalPlayers: Int,
        difficulty: Difficulty,
        seed: Int = Random.nextInt()
    ): GameState {
        require(totalPlayers in 2..4)
        val players = (0 until totalPlayers).map { index ->
            PlayerState(
                name = if (index == 0) "You" else "AI $index",
                isHuman = index == 0
            )
        }
        return dealRound(mode, difficulty, 0, players, seed)
    }

    fun nextRound(state: GameState, seed: Int = Random.nextInt()): GameState {
        if (state.phase != TurnPhase.ROUND_OVER) return state
        val next = state.roundIndex + 1
        if (next >= state.mode.rounds) return state
        return dealRound(state.mode, state.difficulty, next, state.players, seed)
    }

    fun requiredTypes(rule: RoundRule): List<MeldType> {
        rule.special?.let { return listOf(it) }
        return buildList {
            repeat(rule.legs) { add(MeldType.LEG) }
            repeat(rule.straights) { add(MeldType.STRAIGHT) }
        }
    }

    fun remainingRequirements(player: PlayerState, rule: RoundRule): List<MeldType> {
        val remaining = requiredTypes(rule).toMutableList()
        player.melds.forEach { remaining.remove(it.type) }
        return remaining
    }

    fun contractComplete(player: PlayerState, rule: RoundRule): Boolean =
        remainingRequirements(player, rule).isEmpty()

    fun toggleSelection(state: GameState, card: Card): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        if (card !in state.players.first().hand) return state
        val next = state.selected.toMutableSet()
        if (!next.add(card)) next.remove(card)
        return state.copy(selected = next)
    }

    fun drawFromDeck(state: GameState): GameState = drawHuman(state, fromDiscard = false)

    fun stealDiscard(state: GameState): GameState = drawHuman(state, fromDiscard = true)

    fun laySelected(state: GameState): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        if (state.selected.isEmpty()) return state.copy(message = "Select the cards you want to lay down.")

        val human = state.players.first()
        val cards = human.hand.filter { it in state.selected }
        if (cards.size != state.selected.size) return state.copy(selected = emptySet())

        val remaining = remainingRequirements(human, state.roundRule)
        if (remaining.isNotEmpty()) {
            val type = remaining.firstOrNull { GameRules.valid(it, cards) }
                ?: return state.copy(message = "Those cards do not satisfy a remaining contract meld.")

            val updated = human.copy(
                hand = sortHand(human.hand.filterNot { it in state.selected }),
                melds = human.melds + Meld(type, cards),
                roundPoints = 0
            )
            var next = state.copy(
                players = state.players.toMutableList().also { it[0] = updated },
                selected = emptySet(),
                message = if (remaining.size == 1) {
                    "Contract complete. Add to table melds or discard."
                } else {
                    "Meld laid. ${remaining.size - 1} contract meld(s) remaining."
                }
            )
            if (updated.hand.isEmpty() && contractComplete(updated, state.roundRule)) {
                next = finishRound(next, 0)
            }
            return next
        }

        val added = addCardsToAnyMeld(state, actorIndex = 0, cards = cards)
            ?: return state.copy(message = "Those cards cannot be added to any meld on the table.")

        val next = added.copy(
            selected = emptySet(),
            message = "Cards added to the table. Discard when you are finished."
        )
        return if (next.players.first().hand.isEmpty()) finishRound(next, 0) else next
    }

    fun discardSelected(state: GameState): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        if (state.selected.size != 1) return state.copy(message = "Select exactly one card to discard.")

        val human = state.players.first()
        if (human.hand.size == 1 && !contractComplete(human, state.roundRule)) {
            return state.copy(message = "You must complete this round's contract before going out.")
        }

        val card = state.selected.first()
        val updated = human.copy(hand = sortHand(human.hand - card))
        var next = state.copy(
            players = state.players.toMutableList().also { it[0] = updated },
            discardPile = state.discardPile + card,
            selected = emptySet(),
            message = "AI players are taking their turns."
        )

        if (updated.hand.isEmpty() && contractComplete(updated, state.roundRule)) {
            return finishRound(next, 0)
        }

        next = next.copy(currentPlayer = if (state.players.size > 1) 1 else 0, phase = TurnPhase.DRAW)
        return runAiTurns(next)
    }

    private fun drawHuman(state: GameState, fromDiscard: Boolean): GameState {
        if (state.phase != TurnPhase.DRAW || state.currentPlayer != 0) return state

        var draw = state.drawPile
        var discard = state.discardPile
        if (!fromDiscard && draw.isEmpty()) {
            val recycled = recycleDiscard(discard)
                ?: return state.copy(message = "No cards are available to draw.")
            draw = recycled.first
            discard = recycled.second
        }

        val card = if (fromDiscard) discard.lastOrNull() else draw.firstOrNull()
            ?: return state.copy(
                message = if (fromDiscard) "The discard pile is empty." else "The draw pile is empty."
            )

        val human = state.players.first()
        val updated = human.copy(
            hand = sortHand(human.hand + card),
            steals = human.steals + if (fromDiscard) 1 else 0
        )

        return state.copy(
            players = state.players.toMutableList().also { it[0] = updated },
            drawPile = if (fromDiscard) draw else draw.drop(1),
            discardPile = if (fromDiscard) discard.dropLast(1) else discard,
            phase = TurnPhase.ACTION,
            selected = emptySet(),
            message = if (fromDiscard) {
                "Discard stolen: +2 points. Lay melds or discard."
            } else {
                "Card drawn. Lay melds or discard."
            }
        )
    }

    private fun runAiTurns(start: GameState): GameState {
        var state = start
        while (state.phase == TurnPhase.DRAW && state.currentPlayer != 0) {
            state = playAiTurn(state, state.currentPlayer)
            if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) return state
            val next = (state.currentPlayer + 1) % state.players.size
            state = state.copy(currentPlayer = next, phase = TurnPhase.DRAW)
        }
        return state.copy(
            currentPlayer = 0,
            phase = TurnPhase.DRAW,
            message = "Your turn. Draw from the deck or steal the glowing discard."
        )
    }

    private fun playAiTurn(start: GameState, index: Int): GameState {
        var state = aiDraw(start, index)
        state = aiLayContracts(state, index)
        state = aiShedCards(state, index)

        if (state.players[index].hand.isEmpty() && contractComplete(state.players[index], state.roundRule)) {
            return finishRound(state, index)
        }

        val player = state.players[index]
        if (player.hand.isEmpty()) return state
        val discardCard = chooseAiDiscard(player.hand, state.difficulty)
        val updated = player.copy(hand = sortHand(player.hand - discardCard))
        state = state.copy(
            players = state.players.toMutableList().also { it[index] = updated },
            discardPile = state.discardPile + discardCard
        )

        return if (updated.hand.isEmpty() && contractComplete(updated, state.roundRule)) {
            finishRound(state, index)
        } else {
            state.copy(phase = TurnPhase.DRAW)
        }
    }

    private fun aiDraw(start: GameState, index: Int): GameState {
        var state = start
        var draw = state.drawPile
        var discard = state.discardPile
        if (draw.isEmpty()) {
            recycleDiscard(discard)?.let {
                draw = it.first
                discard = it.second
                state = state.copy(drawPile = draw, discardPile = discard)
            }
        }

        val player = state.players[index]
        val topDiscard = discard.lastOrNull()
        val takeDiscard = topDiscard != null && shouldAiSteal(player, topDiscard, state)
        val card = if (takeDiscard) topDiscard else draw.firstOrNull() ?: topDiscard ?: return state
        val updated = player.copy(
            hand = sortHand(player.hand + card),
            steals = player.steals + if (takeDiscard) 1 else 0
        )

        return state.copy(
            players = state.players.toMutableList().also { it[index] = updated },
            drawPile = if (takeDiscard) draw else draw.drop(1),
            discardPile = if (takeDiscard) discard.dropLast(1) else discard,
            phase = TurnPhase.ACTION
        )
    }

    private fun shouldAiSteal(player: PlayerState, card: Card, state: GameState): Boolean {
        if (state.difficulty == Difficulty.EASY) return false
        val remaining = remainingRequirements(player, state.roundRule)
        if (remaining.any { type ->
                findMeld(player.hand + card, type) != null && findMeld(player.hand, type) == null
            }
        ) return true

        if (state.difficulty == Difficulty.HARD) {
            return player.hand.any { existing ->
                !existing.isJoker && !card.isJoker &&
                    (existing.rank == card.rank || existing.suit == card.suit)
            }
        }
        return false
    }

    private fun aiLayContracts(start: GameState, index: Int): GameState {
        var state = start
        while (true) {
            val player = state.players[index]
            val remaining = remainingRequirements(player, state.roundRule)
            if (remaining.isEmpty()) return state

            var typeFound: MeldType? = null
            var cardsFound: List<Card>? = null
            for (type in remaining) {
                val candidate = findMeld(player.hand, type)
                if (candidate != null) {
                    typeFound = type
                    cardsFound = candidate
                    break
                }
            }
            val type = typeFound ?: return state
            val cards = cardsFound ?: return state

            val updated = player.copy(
                hand = sortHand(player.hand.filterNot { it in cards }),
                melds = player.melds + Meld(type, cards)
            )
            state = state.copy(players = state.players.toMutableList().also { it[index] = updated })
            if (updated.hand.isEmpty()) return state
        }
    }

    private fun aiShedCards(start: GameState, index: Int): GameState {
        var state = start
        if (!contractComplete(state.players[index], state.roundRule)) return state

        var changed = true
        while (changed && state.players[index].hand.isNotEmpty()) {
            changed = false
            val hand = state.players[index].hand.toList()
            for (card in hand) {
                val added = addCardsToAnyMeld(state, index, listOf(card))
                if (added != null) {
                    state = added
                    changed = true
                    break
                }
            }
        }
        return state
    }

    private fun addCardsToAnyMeld(state: GameState, actorIndex: Int, cards: List<Card>): GameState? {
        if (cards.isEmpty()) return null
        for (playerIndex in state.players.indices) {
            val target = state.players[playerIndex]
            for (meldIndex in target.melds.indices) {
                val meld = target.melds[meldIndex]
                val combined = meld.cards + cards
                if (!GameRules.valid(meld.type, combined)) continue

                val players = state.players.toMutableList()
                val targetMelds = target.melds.toMutableList().also {
                    it[meldIndex] = meld.copy(cards = combined)
                }
                players[playerIndex] = target.copy(melds = targetMelds)

                val actor = players[actorIndex]
                players[actorIndex] = actor.copy(
                    hand = sortHand(actor.hand.filterNot { it in cards })
                )
                return state.copy(players = players)
            }
        }
        return null
    }

    private fun findMeld(hand: List<Card>, type: MeldType): List<Card>? = when (type) {
        MeldType.LEG -> findLeg(hand)
        MeldType.STRAIGHT -> findStraight(hand)
        MeldType.CRAZY_STRAIGHT,
        MeldType.COLOUR_STRAIGHT,
        MeldType.ROYAL_STRAIGHT -> combinations(hand, 13).firstOrNull { GameRules.valid(type, it) }
    }

    private fun findLeg(hand: List<Card>): List<Card>? {
        val joker = hand.firstOrNull { it.isJoker }
        val groups = hand.filterNot { it.isJoker }.groupBy { it.rank }
        for (cards in groups.values.sortedByDescending { it.size }) {
            val uniqueSuits = cards.distinctBy { it.suit }
            if (uniqueSuits.size >= 3) return uniqueSuits.take(3)
            if (uniqueSuits.size >= 2 && joker != null) return uniqueSuits.take(2) + joker
        }
        return null
    }

    private fun findStraight(hand: List<Card>): List<Card>? {
        if (hand.size < 4) return null
        for (size in 4..minOf(6, hand.size)) {
            val match = combinations(hand, size).firstOrNull { GameRules.valid(MeldType.STRAIGHT, it) }
            if (match != null) return match
        }
        return null
    }

    private fun <T> combinations(items: List<T>, size: Int): List<List<T>> {
        if (size <= 0 || size > items.size) return emptyList()
        val result = mutableListOf<List<T>>()
        val picked = mutableListOf<T>()

        fun walk(start: Int) {
            if (picked.size == size) {
                result += picked.toList()
                return
            }
            val needed = size - picked.size
            val lastStart = items.size - needed
            for (index in start..lastStart) {
                picked += items[index]
                walk(index + 1)
                picked.removeAt(picked.lastIndex)
            }
        }

        walk(0)
        return result
    }

    private fun chooseAiDiscard(hand: List<Card>, difficulty: Difficulty): Card {
        if (difficulty == Difficulty.EASY) return hand.random()
        if (difficulty == Difficulty.MEDIUM) return hand.maxBy { it.rank.points }
        return hand.maxBy { card -> card.rank.points - if (card.isJoker) 40 else 0 }
    }

    private fun recycleDiscard(discard: List<Card>): Pair<List<Card>, List<Card>>? {
        if (discard.size <= 1) return null
        val top = discard.last()
        return discard.dropLast(1).shuffled() to listOf(top)
    }

    private fun finishRound(state: GameState, winner: Int): GameState {
        val scored = state.players.map { player ->
            val points = GameRules.score(player.hand, steals = player.steals)
            player.copy(score = player.score + points, roundPoints = points)
        }
        val gameOver = state.roundIndex + 1 >= state.mode.rounds
        val gameWinner = if (gameOver) scored.indices.minByOrNull { scored[it].score } else null
        return state.copy(
            players = scored,
            phase = if (gameOver) TurnPhase.GAME_OVER else TurnPhase.ROUND_OVER,
            selected = emptySet(),
            roundWinner = winner,
            gameWinner = gameWinner,
            message = if (gameOver) {
                "Game complete. ${scored[gameWinner ?: winner].name} wins with the lowest score."
            } else {
                "${scored[winner].name} went out. Round complete."
            }
        )
    }

    private fun dealRound(
        mode: GameMode,
        difficulty: Difficulty,
        roundIndex: Int,
        previousPlayers: List<PlayerState>,
        seed: Int
    ): GameState {
        val rule = GameRules.rounds[roundIndex]
        val deck = GameRules.deck().shuffled(Random(seed)).toMutableList()
        val hands = List(previousPlayers.size) { mutableListOf<Card>() }

        repeat(rule.deal) {
            hands.indices.forEach { playerIndex -> hands[playerIndex].add(deck.removeAt(0)) }
        }
        val firstDiscard = deck.removeAt(0)
        val players = previousPlayers.mapIndexed { index, player ->
            player.copy(
                hand = sortHand(hands[index]),
                melds = emptyList(),
                roundPoints = 0,
                steals = 0
            )
        }

        return GameState(
            mode = mode,
            difficulty = difficulty,
            roundIndex = roundIndex,
            players = players,
            drawPile = deck.toList(),
            discardPile = listOf(firstDiscard),
            currentPlayer = 0,
            phase = TurnPhase.DRAW,
            message = "Round ${rule.number}. Draw from the deck or steal the glowing discard."
        )
    }

    private fun sortHand(cards: List<Card>): List<Card> = cards.sortedWith(
        compareBy<Card>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy })
    )
}
