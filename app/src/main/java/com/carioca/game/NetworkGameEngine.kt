package com.carioca.game

import com.carioca.game.domain.Card
import com.carioca.game.domain.Difficulty
import com.carioca.game.domain.GameMode
import com.carioca.game.domain.GameRules
import com.carioca.game.domain.GameState
import com.carioca.game.domain.Meld
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

    fun stealDiscard(state: GameState, seat: Int): GameState = draw(state, seat, fromDiscard = true)

    fun layCards(state: GameState, seat: Int, chosen: Set<Card>): GameState {
        if (!validTurn(state, seat, TurnPhase.ACTION)) return state.copy(message = "It is not your action phase.")
        if (chosen.isEmpty()) return state.copy(message = "Select cards before laying them.")

        val player = state.players[seat]
        val cards = player.hand.filter { it in chosen }
        if (cards.size != chosen.size) return state.copy(message = "Those cards are no longer in your hand.")

        val remaining = com.carioca.game.domain.GameEngine.remainingRequirements(player, state.roundRule)
        if (remaining.isNotEmpty()) {
            val type = remaining.firstOrNull { GameRules.valid(it, cards) }
                ?: return state.copy(message = "Those cards do not satisfy a remaining contract meld.")
            val updated = player.copy(
                hand = sortHand(player.hand.filterNot { it in chosen }),
                melds = player.melds + Meld(type, cards),
                roundPoints = 0
            )
            var next = state.copy(
                players = state.players.toMutableList().also { it[seat] = updated },
                selected = emptySet(),
                message = if (remaining.size == 1) "Contract complete. Add to the table or discard." else "Meld laid. ${remaining.size - 1} contract meld(s) remaining."
            )
            if (updated.hand.isEmpty() && com.carioca.game.domain.GameEngine.contractComplete(updated, state.roundRule)) next = finishRound(next, seat)
            return next
        }

        val added = addCardsToAnyMeld(state, seat, cards)
            ?: return state.copy(message = "Those cards cannot be added to a table meld.")
        return if (added.players[seat].hand.isEmpty()) finishRound(added, seat)
        else added.copy(message = "Cards added to the table. Discard when finished.")
    }

    fun discard(state: GameState, seat: Int, card: Card): GameState {
        if (!validTurn(state, seat, TurnPhase.ACTION)) return state.copy(message = "It is not your action phase.")
        val player = state.players[seat]
        if (card !in player.hand) return state.copy(message = "That card is no longer in your hand.")
        if (player.hand.size == 1 && !com.carioca.game.domain.GameEngine.contractComplete(player, state.roundRule)) return state.copy(message = "Complete this round's contract before going out.")

        val updated = player.copy(hand = sortHand(player.hand - card))
        var next = state.copy(
            players = state.players.toMutableList().also { it[seat] = updated },
            discardPile = state.discardPile + card,
            selected = emptySet()
        )
        if (updated.hand.isEmpty() && com.carioca.game.domain.GameEngine.contractComplete(updated, state.roundRule)) return finishRound(next, seat)
        val nextSeat = (seat + 1) % state.players.size
        next = next.copy(currentPlayer = nextSeat, phase = TurnPhase.DRAW, message = "${next.players[nextSeat].name}'s turn. Draw from the deck or steal the discard.")
        return next
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
        val updated = player.copy(hand = sortHand(player.hand + card), steals = player.steals + if (fromDiscard) 1 else 0)
        return state.copy(
            players = state.players.toMutableList().also { it[seat] = updated },
            drawPile = if (fromDiscard) draw else draw.drop(1),
            discardPile = if (fromDiscard) discard.dropLast(1) else discard,
            phase = TurnPhase.ACTION,
            selected = emptySet(),
            message = if (fromDiscard) "Discard stolen: +2 points. Lay cards or discard." else "Card drawn. Lay cards or discard."
        )
    }

    private fun validTurn(state: GameState, seat: Int, phase: TurnPhase): Boolean = seat in state.players.indices && state.currentPlayer == seat && state.phase == phase

    private fun addCardsToAnyMeld(state: GameState, actorSeat: Int, cards: List<Card>): GameState? {
        if (cards.isEmpty()) return null
        for (playerIndex in state.players.indices) {
            val target = state.players[playerIndex]
            for (meldIndex in target.melds.indices) {
                val meld = target.melds[meldIndex]
                val combined = meld.cards + cards
                if (!GameRules.valid(meld.type, combined)) continue
                val players = state.players.toMutableList()
                val targetMelds = target.melds.toMutableList().also { it[meldIndex] = meld.copy(cards = combined) }
                players[playerIndex] = target.copy(melds = targetMelds)
                val actor = players[actorSeat]
                players[actorSeat] = actor.copy(hand = sortHand(actor.hand.filterNot { it in cards }))
                return state.copy(players = players, selected = emptySet())
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
            message = if (gameOver) "Game complete. ${scored[gameWinner ?: winner].name} wins with the lowest score." else "${scored[winner].name} went out. Round complete."
        )
    }

    private fun dealRound(mode: GameMode, roundIndex: Int, previousPlayers: List<PlayerState>, seed: Int): GameState {
        val rule = GameRules.rounds[roundIndex]
        val deck = GameRules.deck().shuffled(Random(seed)).toMutableList()
        val hands = List(previousPlayers.size) { mutableListOf<Card>() }
        repeat(rule.deal) { hands.indices.forEach { seat -> hands[seat].add(deck.removeAt(0)) } }
        val firstDiscard = deck.removeAt(0)
        val players = previousPlayers.mapIndexed { index, player -> player.copy(hand = sortHand(hands[index]), melds = emptyList(), roundPoints = 0, steals = 0, isHuman = true) }
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
            message = "Round ${rule.number}. ${players.first().name} draws first."
        )
    }

    private fun recycleDiscard(discard: List<Card>): Pair<List<Card>, List<Card>>? {
        if (discard.size <= 1) return null
        val top = discard.last()
        return discard.dropLast(1).shuffled() to listOf(top)
    }

    private fun sortHand(cards: List<Card>): List<Card> = cards.sortedWith(compareBy<Card>({ it.isJoker }, { it.suit?.ordinal ?: 9 }, { it.rank.order }, { it.deck }, { it.copy }))
}
