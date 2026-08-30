package com.carioca.game

import com.carioca.game.domain.Card
import com.carioca.game.domain.Difficulty
import com.carioca.game.domain.GameEngine
import com.carioca.game.domain.GameMode
import com.carioca.game.domain.GameRules
import com.carioca.game.domain.GameState
import com.carioca.game.domain.PlayerState
import com.carioca.game.domain.TurnPhase
import kotlin.random.Random

object NetworkGameEngine {
    fun newGame(mode: GameMode, playerNames: List<String>, seed: Int = Random.nextInt()): GameState {
        require(playerNames.size in 2..4)
        val players = playerNames.map { name -> PlayerState(name = name, isHuman = true) }
        return dealRound(mode, 0, players, seed)
    }

    fun drawFromDeck(state: GameState, seat: Int): GameState = draw(state, seat, fromDiscard = false)
    fun takeDiscard(state: GameState, seat: Int): GameState = draw(state, seat, fromDiscard = true)
    fun stealDiscard(state: GameState, seat: Int): GameState = takeDiscard(state, seat)

    fun stealAvailableDiscard(state: GameState, seat: Int): GameState {
        if (state.phase != TurnPhase.STEAL_WINDOW || state.currentPlayer != seat) return state.copy(message = "It is not your steal decision.")
        val card = state.discardPile.lastOrNull() ?: return finishStealWindow(state)
        val player = state.players[seat]
        val updated = player.copy(hand = sortHand(player.hand + card), steals = player.steals + 1)
        return finishStealWindow(
            state.copy(
                players = state.players.toMutableList().also { it[seat] = updated },
                discardPile = state.discardPile.dropLast(1),
                selected = emptySet(),
                message = "${updated.name} stole the discard · +2 points."
            )
        )
    }

    fun passSteal(state: GameState, seat: Int): GameState {
        if (state.phase != TurnPhase.STEAL_WINDOW || state.currentPlayer != seat) return state.copy(message = "It is not your steal decision.")
        val remaining = state.stealQueue.drop(1)
        if (remaining.isEmpty()) return finishStealWindow(state)
        val nextCandidate = remaining.first()
        return state.copy(
            currentPlayer = nextCandidate,
            stealQueue = remaining,
            selected = emptySet(),
            message = "${state.players[nextCandidate].name} may steal the discard · +2."
        )
    }

    fun layCards(state: GameState, seat: Int, chosen: Set<Card>): GameState {
        if (!validTurn(state, seat, TurnPhase.ACTION)) return state.copy(message = "It is not your action phase.")
        if (chosen.isEmpty()) return state.copy(message = "Select the complete round goal before lowering.")

        val player = state.players[seat]
        val cards = player.hand.filter { it in chosen }
        if (cards.size != chosen.size) return state.copy(message = "Those cards are no longer in your hand.")

        val remaining = GameEngine.remainingRequirements(player, state.roundRule)
        if (remaining.isNotEmpty()) {
            val fullPlan = GameRules.findMeldPlan(cards, remaining, useAllCards = true)
            if (fullPlan == null) {
                val partial = GameRules.findPartialMeldPlan(cards, remaining)
                return state.copy(
                    message = if (partial != null) {
                        "${partial.size}/${remaining.size} required melds selected. Select the rest before lowering."
                    } else if (GameEngine.contractReady(player, state.roundRule)) {
                        "Your full round goal is available. Select every required Trio/Straight, then drag them to the table."
                    } else {
                        "That selection does not match this round's goal."
                    }
                )
            }

            val used = fullPlan.flatMap { it.cards }.toSet()
            val updated = player.copy(
                hand = sortHand(player.hand.filterNot { it in used }),
                melds = player.melds + fullPlan,
                roundPoints = 0
            )
            var next = state.copy(
                players = state.players.toMutableList().also { it[seat] = updated },
                selected = emptySet(),
                message = "ROUND GOAL LOWERED. Add cards to compatible table melds, then discard."
            )
            if (updated.hand.isEmpty()) next = finishRound(next, seat)
            return next
        }

        val added = addCardsToAnyMeld(state, seat, cards)
            ?: return state.copy(message = "Drop those cards directly on a compatible meld.")
        return if (added.players[seat].hand.isEmpty()) finishRound(added, seat)
        else added.copy(message = "Cards added to a meld. Drag one card to DISCARD when finished.")
    }

    fun addToMeld(
        state: GameState,
        seat: Int,
        ownerSeat: Int,
        meldIndex: Int,
        chosen: Set<Card>
    ): GameState {
        if (!validTurn(state, seat, TurnPhase.ACTION)) return state.copy(message = "It is not your action phase.")
        val player = state.players[seat]
        if (!GameEngine.contractComplete(player, state.roundRule)) {
            return state.copy(message = "Lower your complete round goal before adding to existing melds.")
        }
        if (chosen.isEmpty()) return state
        val cards = player.hand.filter { it in chosen }
        if (cards.size != chosen.size) return state.copy(message = "Those cards are no longer in your hand.")
        val added = addCardsToMeld(state, seat, ownerSeat, meldIndex, cards)
            ?: return state.copy(message = "Those cards do not fit that meld.")
        return if (added.players[seat].hand.isEmpty()) finishRound(added, seat)
        else added.copy(message = "Cards added to ${added.players[ownerSeat].name}'s meld.")
    }

    fun discard(state: GameState, seat: Int, card: Card): GameState {
        if (!validTurn(state, seat, TurnPhase.ACTION)) return state.copy(message = "It is not your action phase.")
        val player = state.players[seat]
        if (card !in player.hand) return state.copy(message = "That card is no longer in your hand.")
        if (player.hand.size == 1 && !GameEngine.contractComplete(player, state.roundRule)) {
            return state.copy(message = "Lower this round's complete goal before going out.")
        }

        val updated = player.copy(hand = sortHand(player.hand - card))
        val discarded = state.copy(
            players = state.players.toMutableList().also { it[seat] = updated },
            discardPile = state.discardPile + card,
            selected = emptySet()
        )
        if (updated.hand.isEmpty() && GameEngine.contractComplete(updated, state.roundRule)) return finishRound(discarded, seat)
        return openStealWindow(discarded, discarder = seat)
    }

    fun nextRound(state: GameState, seat: Int, seed: Int = Random.nextInt()): GameState {
        if (state.phase != TurnPhase.ROUND_OVER) return state
        if (state.currentPlayer != seat) return state.copy(message = "The round winner starts the next round.")
        val next = state.roundIndex + 1
        if (next >= state.mode.rounds) return state
        return dealRound(state.mode, next, state.players, seed)
    }

    private fun draw(state: GameState, seat: Int, fromDiscard: Boolean): GameState {
        if (!validTurn(state, seat, TurnPhase.DRAW)) return state.copy(message = "It is not your draw phase.")
        var draw = state.drawPile
        var discard = state.discardPile
        if (!fromDiscard && draw.isEmpty()) {
            val recycled = recycleDiscard(discard) ?: return state.copy(message = "No cards are available to draw.")
            draw = recycled.first
            discard = recycled.second
        }
        val card = (if (fromDiscard) discard.lastOrNull() else draw.firstOrNull())
            ?: return state.copy(message = if (fromDiscard) "The discard pile is empty." else "The draw pile is empty.")

        val player = state.players[seat]
        val updated = player.copy(hand = sortHand(player.hand + card))
        val ready = GameEngine.contractReady(updated, state.roundRule)
        return state.copy(
            players = state.players.toMutableList().also { it[seat] = updated },
            drawPile = if (fromDiscard) draw else draw.drop(1),
            discardPile = if (fromDiscard) discard.dropLast(1) else discard,
            phase = TurnPhase.ACTION,
            selected = emptySet(),
            pendingNextPlayer = null,
            stealQueue = emptyList(),
            message = when {
                ready && !GameEngine.contractComplete(updated, state.roundRule) -> "ROUND GOAL READY — select all required melds and drag them to the table."
                fromDiscard -> "Discard taken. Lower if ready, then discard."
                else -> "Card drawn. Lower if ready, then discard."
            }
        )
    }

    private fun openStealWindow(state: GameState, discarder: Int): GameState {
        val count = state.players.size
        val next = (discarder + 1) % count
        if (count <= 2) {
            return state.copy(
                currentPlayer = next,
                phase = TurnPhase.DRAW,
                pendingNextPlayer = null,
                stealQueue = emptyList(),
                message = "${state.players[next].name}'s turn."
            )
        }

        val candidates = mutableListOf<Int>()
        var candidate = (next + 1) % count
        while (candidate != discarder) {
            candidates += candidate
            candidate = (candidate + 1) % count
        }
        if (candidates.isEmpty()) return state.copy(currentPlayer = next, phase = TurnPhase.DRAW)

        val first = candidates.first()
        return state.copy(
            currentPlayer = first,
            phase = TurnPhase.STEAL_WINDOW,
            pendingNextPlayer = next,
            stealQueue = candidates,
            selected = emptySet(),
            message = "${state.players[first].name} may steal the discard · +2."
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

    private fun validTurn(state: GameState, seat: Int, phase: TurnPhase): Boolean =
        seat in state.players.indices && state.currentPlayer == seat && state.phase == phase

    private fun addCardsToMeld(state: GameState, actorSeat: Int, ownerSeat: Int, meldIndex: Int, cards: List<Card>): GameState? {
        if (cards.isEmpty()) return null
        if (actorSeat !in state.players.indices || ownerSeat !in state.players.indices) return null
        val target = state.players[ownerSeat]
        val meld = target.melds.getOrNull(meldIndex) ?: return null
        val combined = meld.cards + cards
        if (!GameRules.valid(meld.type, combined)) return null
        val players = state.players.toMutableList()
        val targetMelds = target.melds.toMutableList().also { it[meldIndex] = meld.copy(cards = combined) }
        players[ownerSeat] = target.copy(melds = targetMelds)
        val actor = players[actorSeat]
        players[actorSeat] = actor.copy(hand = sortHand(actor.hand.filterNot { it in cards }))
        return state.copy(players = players, selected = emptySet())
    }

    private fun addCardsToAnyMeld(state: GameState, actorSeat: Int, cards: List<Card>): GameState? {
        if (cards.isEmpty()) return null
        for (playerIndex in state.players.indices) {
            for (meldIndex in state.players[playerIndex].melds.indices) {
                val result = addCardsToMeld(state, actorSeat, playerIndex, meldIndex, cards)
                if (result != null) return result
            }
        }
        return null
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
            currentPlayer = winner,
            phase = if (gameOver) TurnPhase.GAME_OVER else TurnPhase.ROUND_OVER,
            selected = emptySet(),
            roundWinner = winner,
            gameWinner = gameWinner,
            pendingNextPlayer = null,
            stealQueue = emptyList(),
            message = if (gameOver) "Game complete. ${scored[gameWinner ?: winner].name} wins with the lowest score." else "${scored[winner].name} went out. Round complete."
        )
    }

    private fun dealRound(mode: GameMode, roundIndex: Int, previousPlayers: List<PlayerState>, seed: Int): GameState {
        val rule = GameRules.rounds[roundIndex]
        val deck = GameRules.deck().shuffled(Random(seed)).toMutableList()
        val hands = List(previousPlayers.size) { mutableListOf<Card>() }
        repeat(rule.deal) { hands.indices.forEach { seat -> hands[seat].add(deck.removeAt(0)) } }
        val firstDiscard = deck.removeAt(0)
        val players = previousPlayers.mapIndexed { index, player ->
            player.copy(hand = sortHand(hands[index]), melds = emptyList(), roundPoints = 0, steals = 0, isHuman = true)
        }
        return GameState(
            mode = mode,
            difficulty = Difficulty.MEDIUM,
            roundIndex = roundIndex,
            players = players,
            drawPile = deck.toList(),
            discardPile = listOf(firstDiscard),
            currentPlayer = 0,
            phase = TurnPhase.DRAW,
            selected = emptySet(),
            message = "Round ${rule.number}. ${players.first().name} drags DRAW or the top DISCARD into the hand first."
        )
    }

    private fun recycleDiscard(discard: List<Card>): Pair<List<Card>, List<Card>>? {
        if (discard.size <= 1) return null
        val top = discard.last()
        return discard.dropLast(1).shuffled() to listOf(top)
    }

    private fun sortHand(cards: List<Card>): List<Card> = cards.sortedWith(
        compareBy<Card>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy })
    )
}
