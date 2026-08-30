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
    val message: String = "Draw from the deck or take the discard.",
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

    fun requiredTypes(rule: RoundRule): List<MeldType> = GameRules.requiredTypes(rule)

    fun remainingRequirements(player: PlayerState, rule: RoundRule): List<MeldType> {
        val remaining = requiredTypes(rule).toMutableList()
        player.melds.forEach { remaining.remove(it.type) }
        return remaining
    }

    fun contractComplete(player: PlayerState, rule: RoundRule): Boolean =
        remainingRequirements(player, rule).isEmpty()

    /** True when every still-required contract meld can be formed from the current hand. */
    fun contractReady(player: PlayerState, rule: RoundRule): Boolean {
        val remaining = remainingRequirements(player, rule)
        if (remaining.isEmpty()) return true
        return GameRules.findMeldPlan(player.hand, remaining, useAllCards = false) != null
    }

    /** True when at least one still-required Trio/Straight can be placed now. */
    fun nextMeldReady(player: PlayerState, rule: RoundRule): Boolean =
        remainingRequirements(player, rule).any { type -> findMeld(player.hand, type) != null }

    fun cardsStillRequired(player: PlayerState, rule: RoundRule): Int =
        remainingRequirements(player, rule).sumOf(GameRules::minimumCards)

    fun toggleSelection(state: GameState, card: Card): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        if (card !in state.players.first().hand) return state
        val next = state.selected.toMutableSet()
        if (!next.add(card)) next.remove(card)
        return state.copy(selected = next)
    }

    fun drawFromDeck(state: GameState): GameState = drawHuman(state, fromDiscard = false)

    fun stealDiscard(state: GameState): GameState = drawHuman(state, fromDiscard = true)

    /**
     * Creates one exact-size required meld (Trio = 3, Straight = 4), or multiple
     * exact-size melds when the selected cards cleanly contain several requirements.
     */
    fun createMeld(state: GameState): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        if (state.selected.isEmpty()) return state.copy(message = "Select cards for a Trio or Straight first.")

        val human = state.players.first()
        val cards = human.hand.filter { it in state.selected }
        if (cards.size != state.selected.size) return state.copy(selected = emptySet())

        val remaining = remainingRequirements(human, state.roundRule)
        if (remaining.isNotEmpty()) {
            val completePlan = GameRules.findMeldPlan(cards, remaining, useAllCards = true)
            val singlePlan = if (completePlan == null) {
                remaining.firstNotNullOfOrNull { type ->
                    if (GameRules.validInitial(type, cards)) listOf(Meld(type, cards)) else null
                }
            } else null
            val meldsToCreate = completePlan ?: singlePlan
                ?: return state.copy(
                    message = when {
                        cards.size == 3 && remaining.contains(MeldType.LEG) -> "A Trio is 3 cards of the same rank."
                        cards.size == 4 && remaining.contains(MeldType.STRAIGHT) -> "A Straight is 4 consecutive cards of the same suit."
                        nextMeldReady(human, state.roundRule) -> "A required meld is available in your hand. Select exactly that Trio or Straight."
                        else -> "Those selected cards do not form a required meld for this round."
                    }
                )

            val updated = human.copy(
                hand = sortHand(human.hand.filterNot { it in state.selected }),
                melds = human.melds + meldsToCreate,
                roundPoints = 0
            )
            val nowRemaining = remainingRequirements(updated, state.roundRule)
            var next = state.copy(
                players = state.players.toMutableList().also { it[0] = updated },
                selected = emptySet(),
                message = if (nowRemaining.isEmpty()) {
                    "ROUND GOAL COMPLETE. You may now add cards to compatible table melds, then discard."
                } else {
                    "Meld placed. ${nowRemaining.size} required meld${if (nowRemaining.size == 1) "" else "s"} remaining."
                }
            )
            if (updated.hand.isEmpty() && contractComplete(updated, state.roundRule)) {
                next = finishRound(next, 0)
            }
            return next
        }

        val added = addCardsToAnyMeld(state, actorIndex = 0, cards = cards)
            ?: return state.copy(message = "Drop those cards directly on a compatible table meld.")

        val next = added.copy(
            selected = emptySet(),
            message = "Cards added. Drag one card to DISCARD when finished."
        )
        return if (next.players.first().hand.isEmpty()) finishRound(next, 0) else next
    }

    fun laySelected(state: GameState): GameState = createMeld(state)

    fun addSelectedToMeld(state: GameState, ownerIndex: Int, meldIndex: Int): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        val human = state.players.first()
        if (!contractComplete(human, state.roundRule)) {
            return state.copy(message = "Complete the round goal before adding cards to existing melds.")
        }
        if (state.selected.isEmpty()) return state
        val cards = human.hand.filter { it in state.selected }
        if (cards.size != state.selected.size) return state.copy(selected = emptySet())
        val added = addCardsToMeld(state, 0, ownerIndex, meldIndex, cards)
            ?: return state.copy(message = "Those cards do not fit that meld.")
        val next = added.copy(selected = emptySet(), message = "Cards added to ${added.players[ownerIndex].name}'s meld.")
        return if (next.players.first().hand.isEmpty()) finishRound(next, 0) else next
    }

    fun discardSelected(state: GameState): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        if (state.selected.size != 1) return state.copy(message = "Drag exactly one card to the discard pile.")

        val human = state.players.first()
        if (human.hand.size == 1 && !contractComplete(human, state.roundRule)) {
            return state.copy(message = "Complete this round's goal before going out.")
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

        val card = (if (fromDiscard) discard.lastOrNull() else draw.firstOrNull())
            ?: return state.copy(
                message = if (fromDiscard) "The discard pile is empty." else "The draw pile is empty."
            )

        val human = state.players.first()
        val updated = human.copy(
            hand = sortHand(human.hand + card),
            steals = human.steals + if (fromDiscard) 1 else 0
        )
        val fullGoalReady = contractReady(updated, state.roundRule)
        val oneMeldReady = nextMeldReady(updated, state.roundRule)

        return state.copy(
            players = state.players.toMutableList().also { it[0] = updated },
            drawPile = if (fromDiscard) draw else draw.drop(1),
            discardPile = if (fromDiscard) discard.dropLast(1) else discard,
            phase = TurnPhase.ACTION,
            selected = emptySet(),
            message = when {
                fullGoalReady && !contractComplete(updated, state.roundRule) -> "ROUND GOAL READY — select and meld the required groups."
                oneMeldReady -> "A required Trio or Straight is ready to meld."
                fromDiscard -> "Discard taken: +2 points. Meld if possible, then discard."
                else -> "Card drawn. Meld if possible, then discard."
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
            message = "Your turn. Drag DRAW or DISCARD into your hand."
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
        val strategicSteal = topDiscard != null && shouldAiSteal(player, topDiscard, state)
        val useDiscard = topDiscard != null && (strategicSteal || draw.isEmpty())
        val card = when {
            useDiscard -> topDiscard!!
            draw.isNotEmpty() -> draw.first()
            else -> return state
        }
        val updated = player.copy(
            hand = sortHand(player.hand + card),
            steals = player.steals + if (useDiscard) 1 else 0
        )

        return state.copy(
            players = state.players.toMutableList().also { it[index] = updated },
            drawPile = if (useDiscard) draw else draw.drop(1),
            discardPile = if (useDiscard) discard.dropLast(1) else discard,
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

    private fun addCardsToMeld(
        state: GameState,
        actorIndex: Int,
        ownerIndex: Int,
        meldIndex: Int,
        cards: List<Card>
    ): GameState? {
        if (cards.isEmpty()) return null
        if (actorIndex !in state.players.indices || ownerIndex !in state.players.indices) return null
        val target = state.players[ownerIndex]
        val meld = target.melds.getOrNull(meldIndex) ?: return null
        val combined = meld.cards + cards
        if (!GameRules.valid(meld.type, combined)) return null

        val players = state.players.toMutableList()
        val targetMelds = target.melds.toMutableList().also { it[meldIndex] = meld.copy(cards = combined) }
        players[ownerIndex] = target.copy(melds = targetMelds)
        val actor = players[actorIndex]
        players[actorIndex] = actor.copy(hand = sortHand(actor.hand.filterNot { it in cards }))
        return state.copy(players = players)
    }

    private fun addCardsToAnyMeld(state: GameState, actorIndex: Int, cards: List<Card>): GameState? {
        if (cards.isEmpty()) return null
        for (playerIndex in state.players.indices) {
            for (meldIndex in state.players[playerIndex].melds.indices) {
                val result = addCardsToMeld(state, actorIndex, playerIndex, meldIndex, cards)
                if (result != null) return result
            }
        }
        return null
    }

    private fun findMeld(hand: List<Card>, type: MeldType): List<Card>? = when (type) {
        MeldType.LEG -> findTrio(hand)
        MeldType.STRAIGHT -> findStraight(hand)
        MeldType.CRAZY_STRAIGHT,
        MeldType.COLOUR_STRAIGHT,
        MeldType.ROYAL_STRAIGHT -> combinations(hand, 13).firstOrNull { GameRules.validInitial(type, it) }
    }

    /** Two decks are used, therefore matching-rank cards may repeat a suit. */
    private fun findTrio(hand: List<Card>): List<Card>? {
        val joker = hand.firstOrNull { it.isJoker }
        val groups = hand.filterNot { it.isJoker }.groupBy { it.rank }
        for (cards in groups.values.sortedByDescending { it.size }) {
            if (cards.size >= 3) return cards.take(3)
            if (cards.size >= 2 && joker != null) return cards.take(2) + joker
        }
        return null
    }

    private fun findStraight(hand: List<Card>): List<Card>? {
        if (hand.size < 4) return null
        return combinations(hand, 4).firstOrNull { GameRules.validInitial(MeldType.STRAIGHT, it) }
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
            message = "Round ${rule.number}. Drag DRAW or DISCARD into your hand."
        )
    }

    private fun sortHand(cards: List<Card>): List<Card> = cards.sortedWith(
        compareBy<Card>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy })
    )
}
