package com.carioca.game

import com.carioca.game.domain.GameMode
import com.carioca.game.domain.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkGameEngineTest {
    @Test
    fun hostDrawAndDiscardPassesTurnToSecondSeat() {
        var state = NetworkGameEngine.newGame(GameMode.REGULAR, listOf("Host", "Guest"), seed = 101)
        assertEquals(0, state.currentPlayer)
        assertEquals(TurnPhase.DRAW, state.phase)

        state = NetworkGameEngine.drawFromDeck(state, 0)
        assertEquals(TurnPhase.ACTION, state.phase)
        assertEquals(12, state.players[0].hand.size)

        val discard = state.players[0].hand.first()
        state = NetworkGameEngine.discard(state, 0, discard)
        assertEquals(1, state.currentPlayer)
        assertEquals(TurnPhase.DRAW, state.phase)
        assertEquals(11, state.players[0].hand.size)
    }

    @Test
    fun wrongSeatCannotDraw() {
        val state = NetworkGameEngine.newGame(GameMode.REGULAR, listOf("Host", "Guest"), seed = 202)
        val attempted = NetworkGameEngine.drawFromDeck(state, 1)
        assertEquals(0, attempted.currentPlayer)
        assertEquals(TurnPhase.DRAW, attempted.phase)
        assertEquals(state.drawPile.size, attempted.drawPile.size)
        assertTrue(attempted.message.contains("not your", ignoreCase = true))
    }
}
