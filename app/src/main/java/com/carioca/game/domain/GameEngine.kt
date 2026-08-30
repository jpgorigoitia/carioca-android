package com.carioca.game.domain

import kotlin.random.Random

enum class TurnPhase { DRAW, ACTION, STEAL_WINDOW, ROUND_OVER, GAME_OVER }

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
    val gameWinner: Int? = null,
    val pendingNextPlayer: Int? = null,
    val stealQueue: List<Int> = emptyList()
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
            PlayerState(name = if (index == 0) "You" else "AI $index", isHuman = index == 0)
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

    fun contractComplete(player: PlayerState, rule: RoundRule): Boolean = remainingRequirements(player, rule).isEmpty()

    fun contractReady(player: PlayerState, rule: RoundRule): Boolean {
        val remaining = remainingRequirements(player, rule)
        if (remaining.isEmpty()) return true
        return GameRules.findMeldPlan(player.hand, remaining, useAllCards = false) != null
    }

    fun nextMeldReady(player: PlayerState, rule: RoundRule): Boolean =
        remainingRequirements(player, rule).any { type -> findMeld(player.hand, type) != null }

    fun cardsStillRequired(player: PlayerState, rule: RoundRule): Int =
        remainingRequirements(player, rule).sumOf(GameRules::minimumCards)

    fun selectionPlan(state: GameState): List<Meld>? {
        if (state.selected.isEmpty()) return null
        val player = state.players.first()
        val cards = player.hand.filter { it in state.selected }
        return GameRules.findPartialMeldPlan(cards, remainingRequirements(player, state.roundRule))
    }

    fun toggleSelection(state: GameState, card: Card): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        if (card !in state.players.first().hand) return state
        val next = state.selected.toMutableSet()
        if (!next.add(card)) next.remove(card)
        return state.copy(selected = next)
    }

    fun drawFromDeck(state: GameState): GameState = drawHuman(state, fromDiscard = false)

    /** Normal draw choice: taking the top discard on your own turn is not a +2 steal. */
    fun takeDiscard(state: GameState): GameState = drawHuman(state, fromDiscard = true)

    /** Compatibility alias used by older UI. This is now a normal discard draw, not a steal. */
    fun stealDiscard(state: GameState): GameState = takeDiscard(state)

    fun canHumanSteal(state: GameState): Boolean =
        state.phase == TurnPhase.STEAL_WINDOW && state.currentPlayer == 0 && state.discardPile.isNotEmpty()

    /** Out-of-turn steal. The +2 penalty is recorded here and only here. */
    fun stealAvailableDiscard(state: GameState): GameState {
        if (!canHumanSteal(state)) return state
        return continueLocalTurns(applySteal(state, 0))
    }

    fun passSteal(state: GameState): GameState {
        if (state.phase != TurnPhase.STEAL_WINDOW || state.currentPlayer != 0) return state
        return continueLocalTurns(passCurrentStealCandidate(state))
    }

    fun createMeld(state: GameState): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        if (state.selected.isEmpty()) return state.copy(message = "Select the cards for this round's goal first.")

        val human = state.players.first()
        val cards = human.hand.filter { it in state.selected }
        if (cards.size != state.selected.size) return state.copy(selected = emptySet())

        val remaining = remainingRequirements(human, state.roundRule)
        if (remaining.isNotEmpty()) {
            val fullPlan = GameRules.findMeldPlan(cards, remaining, useAllCards = true)
            if (fullPlan == null) {
                val partial = GameRules.findPartialMeldPlan(cards, remaining)
                return state.copy(
                    message = if (partial != null) {
                        "${partial.size}/${remaining.size} required melds selected. Select the rest of the round goal before lowering."
                    } else if (contractReady(human, state.roundRule)) {
                        "Your full round goal is available. Select every required Trio/Straight, then drag the selection to the table."
                    } else {
                        "That selection does not match this round's required melds."
                    }
                )
            }

            val used = fullPlan.flatMap { it.cards }.toSet()
            val updated = human.copy(
                hand = sortHand(human.hand.filterNot { it in used }),
                melds = human.melds + fullPlan,
                roundPoints = 0
            )
            var next = state.copy(
                players = state.players.toMutableList().also { it[0] = updated },
                selected = emptySet(),
                message = "ROUND GOAL LOWERED. You may add cards to legal table melds, then discard."
            )
            if (updated.hand.isEmpty()) next = finishRound(next, 0)
            return next
        }

        val added = addCardsToAnyMeld(state, actorIndex = 0, cards = cards)
            ?: return state.copy(message = "Drop those cards directly on a compatible table meld.")

        val next = added.copy(selected = emptySet(), message = "Cards added. Drag one card to DISCARD when finished.")
        return if (next.players.first().hand.isEmpty()) finishRound(next, 0) else next
    }

    fun laySelected(state: GameState): GameState = createMeld(state)

    fun addSelectedToMeld(state: GameState, ownerIndex: Int, meldIndex: Int): GameState {
        if (state.phase != TurnPhase.ACTION || state.currentPlayer != 0) return state
        val human = state.players.first()
        if (!contractComplete(human, state.roundRule)) {
            return state.copy(message = "Lower the complete round goal before adding cards to existing melds.")
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
            return state.copy(message = "You must lower this round's complete goal before going out.")
        }

        val card = state.selected.first()
        val updated = human.copy(hand = sortHand(human.hand - card))
        val discarded = state.copy(
            players = state.players.toMutableList().also { it[0] = updated },
            discardPile = state.discardPile + card,
            selected = emptySet()
        )

        if (updated.hand.isEmpty() && contractComplete(updated, state.roundRule)) return finishRound(discarded, 0)
        return continueLocalTurns(openStealWindow(discarded, discarder = 0))
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
            ?: return state.copy(message = if (fromDiscard) "The discard pile is empty." else "The draw pile is empty.")

        val human = state.players.first()
        val updated = human.copy(hand = sortHand(human.hand + card))
        val fullGoalReady = contractReady(updated, state.roundRule)

        return state.copy(
            players = state.players.toMutableList().also { it[0] = updated },
            drawPile = if (fromDiscard) draw else draw.drop(1),
            discardPile = if (fromDiscard) discard.dropLast(1) else discard,
            phase = TurnPhase.ACTION,
            selected = emptySet(),
            pendingNextPlayer = null,
            stealQueue = emptyList(),
            message = when {
                fullGoalReady && !contractComplete(updated, state.roundRule) -> "ROUND GOAL READY — select all required melds and drag them to the table."
                fromDiscard -> "Discard taken. Lower if ready, then discard."
                else -> "Card drawn. Lower if ready, then discard."
            }
        )
    }

    private fun continueLocalTurns(start: GameState): GameState {
        var state = start
        while (true) {
            if (state.phase == TurnPhase.ROUND_OVER || state.phase == TurnPhase.GAME_OVER) return state

            if (state.phase == TurnPhase.STEAL_WINDOW) {
                state = resolveAiStealCandidates(state)
                if (state.phase == TurnPhase.STEAL_WINDOW && state.currentPlayer == 0) return state
                continue
            }

            if (state.phase == TurnPhase.DRAW) {
                if (state.currentPlayer == 0) {
                    return state.copy(message = "Your turn. Drag DRAW or the top DISCARD into your hand.")
                }
                state = playAiTurn(state, state.currentPlayer)
                continue
            }

            return state
        }
    }

    private fun playAiTurn(start: GameState, index: Int): GameState {
        var state = aiDraw(start, index)
        state = aiLowerFullGoal(state, index)
        state = aiShedCards(state, index)

        if (state.players[index].hand.isEmpty() && contractComplete(state.players[index], state.roundRule)) {
            return finishRound(state, index)
        }

        val player = state.players[index]
        if (player.hand.isEmpty()) return state
        val discardCard = chooseAiDiscard(player.hand, state.difficulty)
        val updated = player.copy(hand = sortHand(player.hand - discardCard))
        val discarded = state.copy(
            players = state.players.toMutableList().also { it[index] = updated },
            discardPile = state.discardPile + discardCard,
            selected = emptySet()
        )

        return if (updated.hand.isEmpty() && contractComplete(updated, state.roundRule)) {
            finishRound(discarded, index)
        } else {
            openStealWindow(discarded, discarder = index)
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
        val strategicTake = topDiscard != null && shouldAiTakeDiscard(player, topDiscard, state)
        val useDiscard = topDiscard != null && (strategicTake || draw.isEmpty())
        val card = when {
            useDiscard -> topDiscard!!
            draw.isNotEmpty() -> draw.first()
            else -> return state
        }
        val updated = player.copy(hand = sortHand(player.hand + card))

        return state.copy(
            players = state.players.toMutableList().also { it[index] = updated },
            drawPile = if (useDiscard) draw else draw.drop(1),
            discardPile = if (useDiscard) discard.dropLast(1) else discard,
            phase = TurnPhase.ACTION,
            pendingNextPlayer = null,
            stealQueue = emptyList()
        )
    }

    private fun shouldAiTakeDiscard(player: PlayerState, card: Card, state: GameState): Boolean {
        if (state.difficulty == Difficulty.EASY) return false
        val remaining = remainingRequirements(player, state.roundRule)
        val improvesGoal = GameRules.findMeldPlan(player.hand + card, remaining, useAllCards = false) != null &&
            GameRules.findMeldPlan(player.hand, remaining, useAllCards = false) == null
        if (improvesGoal) return true
        if (state.difficulty == Difficulty.HARD) {
            return player.hand.any { existing ->
                !existing.isJoker && !card.isJoker && (existing.rank == card.rank || existing.suit == card.suit)
            }
        }
        return false
    }

    private fun shouldAiStealOutOfTurn(player: PlayerState, card: Card, state: GameState): Boolean {
        if (state.difficulty == Difficulty.EASY) return false
        if (!contractComplete(player, state.roundRule)) {
            val remaining = remainingRequirements(player, state.roundRule)
            val before = GameRules.findMeldPlan(player.hand, remaining, useAllCards = false)
            val after = GameRules.findMeldPlan(player.hand + card, remaining, useAllCards = false)
            if (before == null && after != null) return true
        }
        if (state.difficulty == Difficulty.HARD) {
            return player.hand.any { existing ->
                !existing.isJoker && !card.isJoker && (existing.rank == card.rank || existing.suit == card.suit)
            }
        }
        return false
    }

    private fun aiLowerFullGoal(start: GameState, index: Int): GameState {
        val player = start.players[index]
        if (contractComplete(player, start.roundRule)) return start
        val required = remainingRequirements(player, start.roundRule)
        val plan = GameRules.findMeldPlan(player.hand, required, useAllCards = false) ?: return start
        val used = plan.flatMap { it.cards }.toSet()
        val updated = player.copy(
            hand = sortHand(player.hand.filterNot { it in used }),
            melds = player.melds + plan
        )
        return start.copy(players = start.players.toMutableList().also { it[index] = updated })
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

    private fun openStealWindow(state: GameState, discarder: Int): GameState {
        val count = state.players.size
        if (count <= 2) {
            val next = (discarder + 1) % count
            return state.copy(
                currentPlayer = next,
                phase = TurnPhase.DRAW,
                pendingNextPlayer = null,
                stealQueue = emptyList(),
                message = "${state.players[next].name}'s turn."
            )
        }

        val next = (discarder + 1) % count
        val candidates = mutableListOf<Int>()
        var seat = (next + 1) % count
        while (seat != discarder) {
            candidates += seat
            seat = (seat + 1) % count
        }

        if (candidates.isEmpty()) {
            return state.copy(currentPlayer = next, phase = TurnPhase.DRAW, pendingNextPlayer = null, stealQueue = emptyList())
        }

        val first = candidates.first()
        return state.copy(
            currentPlayer = first,
            phase = TurnPhase.STEAL_WINDOW,
            pendingNextPlayer = next,
            stealQueue = candidates,
            selected = emptySet(),
            message = if (first == 0) "Available to steal · +2 points. Drag DISCARD into your hand or PASS." else "${state.players[first].name} may steal the discard."
        )
    }

    private fun resolveAiStealCandidates(start: GameState): GameState {
        var state = start
        while (state.phase == TurnPhase.STEAL_WINDOW && state.currentPlayer != 0) {
            val candidate = state.currentPlayer
            val card = state.discardPile.lastOrNull() ?: return finishStealWindow(state)
            state = if (shouldAiStealOutOfTurn(state.players[candidate], card, state)) {
                applySteal(state, candidate)
            } else {
                passCurrentStealCandidate(state)
            }
        }
        return state
    }

    private fun applySteal(state: GameState, seat: Int): GameState {
        if (state.phase != TurnPhase.STEAL_WINDOW || seat != state.currentPlayer) return state
        val card = state.discardPile.lastOrNull() ?: return finishStealWindow(state)
        val player = state.players[seat]
        val updated = player.copy(hand = sortHand(player.hand + card), steals = player.steals + 1)
        val stolen = state.copy(
            players = state.players.toMutableList().also { it[seat] = updated },
            discardPile = state.discardPile.dropLast(1),
            message = "${updated.name} stole the discard · +2 points."
        )
        return finishStealWindow(stolen)
    }

    private fun passCurrentStealCandidate(state: GameState): GameState {
        if (state.phase != TurnPhase.STEAL_WINDOW) return state
        val remaining = state.stealQueue.drop(1)
        if (remaining.isEmpty()) return finishStealWindow(state)
        val nextCandidate = remaining.first()
        return state.copy(
            currentPlayer = nextCandidate,
            stealQueue = remaining,
            message = if (nextCandidate == 0) "Available to steal · +2 points. Drag DISCARD into your hand or PASS." else "${state.players[nextCandidate].name} may steal the discard."
        )
    }

    private fun finishStealWindow(state: GameState): GameState {
        val next = state.pendingNextPlayer ?: return state.copy(phase = TurnPhase.DRAW, stealQueue = emptyList())
        return state.copy(
            currentPlayer = next,
            phase = TurnPhase.DRAW,
            pendingNextPlayer = null,
            stealQueue = emptyList(),
            selected = emptySet(),
            message = "${state.players[next].name}'s turn."
        )
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

    private fun findTrio(hand: List<Card>): List<Card>? {
        val joker = hand.firstOrNull { it.isJoker }
        val groups = hand.filterNot { it.isJoker }.groupBy { it.rank }
        for (cards in groups.values.sortedByDescending { it.mapNotNull { card -> card.suit }.distinct().size }) {
            val distinct = cards.distinctBy { it.suit }
            if (distinct.size >= 3) return distinct.take(3)
            if (distinct.size >= 2 && joker != null) return distinct.take(2) + joker
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
            pendingNextPlayer = null,
            stealQueue = emptyList(),
            message = if (gameOver) "Game complete. ${scored[gameWinner ?: winner].name} wins with the lowest score." else "${scored[winner].name} went out. Round complete."
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
        repeat(rule.deal) { hands.indices.forEach { playerIndex -> hands[playerIndex].add(deck.removeAt(0)) } }
        val firstDiscard = deck.removeAt(0)
        val players = previousPlayers.mapIndexed { index, player ->
            player.copy(hand = sortHand(hands[index]), melds = emptyList(), roundPoints = 0, steals = 0)
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
            message = "Round ${rule.number}. Drag DRAW or the top DISCARD into your hand."
        )
    }

    private fun sortHand(cards: List<Card>): List<Card> = cards.sortedWith(
        compareBy<Card>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy })
    )
}
