package com.carioca.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun newRegularGameDealsElevenCardsAndStartsOnHumanDraw() {
        val state = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 4, difficulty = Difficulty.MEDIUM, seed = 1234)
        assertEquals(4, state.players.size)
        assertTrue(state.players.all { it.hand.size == 11 })
        assertEquals(63, state.drawPile.size)
        assertEquals(1, state.discardPile.size)
        assertEquals(0, state.currentPlayer)
        assertEquals(TurnPhase.DRAW, state.phase)
    }

    @Test
    fun drawingMovesHumanToActionPhase() {
        val initial = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 2, difficulty = Difficulty.EASY, seed = 7)
        val drawn = GameEngine.drawFromDeck(initial)
        assertEquals(12, drawn.players.first().hand.size)
        assertEquals(TurnPhase.ACTION, drawn.phase)
        assertEquals(initial.drawPile.size - 1, drawn.drawPile.size)
    }

    @Test
    fun stealingDiscardAddsTwoPointPenaltyHook() {
        val initial = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 2, difficulty = Difficulty.MEDIUM, seed = 9)
        val stolen = GameEngine.stealDiscard(initial)
        assertEquals(1, stolen.players.first().steals)
        assertEquals(12, stolen.players.first().hand.size)
        assertEquals(0, stolen.discardPile.size)
        assertEquals(TurnPhase.ACTION, stolen.phase)
    }

    @Test
    fun oneThreeCardTrioCanBeMeldedWithoutSelectingTheOtherRoundOneTrio() {
        val initial = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 2, difficulty = Difficulty.MEDIUM, seed = 11)
        val trio = listOf(
            Card(Rank.SEVEN, Suit.CLUBS, deck = 0),
            Card(Rank.SEVEN, Suit.DIAMONDS, deck = 0),
            Card(Rank.SEVEN, Suit.HEARTS, deck = 0)
        )
        val filler = listOf(Card(Rank.TWO, Suit.CLUBS, deck = 1), Card(Rank.THREE, Suit.CLUBS, deck = 1))
        val human = initial.players.first().copy(hand = trio + filler)
        val ready = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = trio.toSet()
        )

        val laid = GameEngine.createMeld(ready)

        assertEquals(1, laid.players.first().melds.size)
        assertEquals(MeldType.LEG, laid.players.first().melds.first().type)
        assertEquals(2, laid.players.first().hand.size)
        assertEquals(1, GameEngine.remainingRequirements(laid.players.first(), laid.roundRule).size)
    }

    @Test
    fun duplicateSuitCopiesFromTwoDecksStillFormATrio() {
        val initial = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 2, difficulty = Difficulty.MEDIUM, seed = 21)
        val trio = listOf(
            Card(Rank.EIGHT, Suit.CLUBS, deck = 0),
            Card(Rank.EIGHT, Suit.CLUBS, deck = 1),
            Card(Rank.EIGHT, Suit.HEARTS, deck = 0)
        )
        val human = initial.players.first().copy(hand = trio + Card(Rank.TWO, Suit.SPADES, deck = 1))
        val state = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = trio.toSet()
        )

        val laid = GameEngine.createMeld(state)

        assertEquals(1, laid.players.first().melds.size)
        assertEquals(trio.toSet(), laid.players.first().melds.first().cards.toSet())
    }

    @Test
    fun fourSameRankCardsCannotBeUsedAsOneInitialTrio() {
        val initial = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 2, difficulty = Difficulty.MEDIUM, seed = 22)
        val four = listOf(
            Card(Rank.FIVE, Suit.CLUBS, deck = 0),
            Card(Rank.FIVE, Suit.DIAMONDS, deck = 0),
            Card(Rank.FIVE, Suit.HEARTS, deck = 0),
            Card(Rank.FIVE, Suit.SPADES, deck = 0)
        )
        val human = initial.players.first().copy(hand = four + Card(Rank.TWO, Suit.SPADES, deck = 1))
        val state = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = four.toSet()
        )

        val result = GameEngine.createMeld(state)

        assertTrue(result.players.first().melds.isEmpty())
        assertEquals(5, result.players.first().hand.size)
    }

    @Test
    fun roundOneDetectsCompleteTwoTrioGoalInHand() {
        val initial = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 2, difficulty = Difficulty.MEDIUM, seed = 12)
        val goal = twoTrioGoal()
        val human = initial.players.first().copy(hand = goal + Card(Rank.TWO, Suit.SPADES, deck = 1))

        assertEquals(6, GameRules.requiredCardCount(initial.roundRule))
        assertTrue(GameEngine.contractReady(human, initial.roundRule))
        assertFalse(GameEngine.contractComplete(human, initial.roundRule))
    }

    @Test
    fun selectingBothRoundOneTriosCanStillMeldBothAtOnce() {
        val initial = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 2, difficulty = Difficulty.MEDIUM, seed = 13)
        val goal = twoTrioGoal()
        val filler = Card(Rank.TWO, Suit.SPADES, deck = 1)
        val human = initial.players.first().copy(hand = goal + filler)
        val ready = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = goal.toSet()
        )

        val laid = GameEngine.createMeld(ready)

        assertEquals(2, laid.players.first().melds.size)
        assertTrue(laid.players.first().melds.all { it.type == MeldType.LEG })
        assertTrue(GameEngine.contractComplete(laid.players.first(), laid.roundRule))
        assertEquals(listOf(filler), laid.players.first().hand)
    }

    @Test
    fun completedPlayerCanDragCardOntoSpecificExistingTrio() {
        val initial = GameEngine.newGame(GameMode.REGULAR, totalPlayers = 2, difficulty = Difficulty.MEDIUM, seed = 14)
        val goal = twoTrioGoal()
        val extraSeven = Card(Rank.SEVEN, Suit.SPADES, deck = 1)
        val filler = Card(Rank.TWO, Suit.SPADES, deck = 1)
        val human = initial.players.first().copy(hand = goal + extraSeven + filler)
        val ready = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = goal.toSet()
        )
        val laid = GameEngine.createMeld(ready)

        val extended = GameEngine.addSelectedToMeld(
            laid.copy(phase = TurnPhase.ACTION, selected = setOf(extraSeven)),
            ownerIndex = 0,
            meldIndex = 0
        )

        assertEquals(4, extended.players.first().melds.first().cards.size)
        assertFalse(extraSeven in extended.players.first().hand)
        assertEquals(listOf(filler), extended.players.first().hand)
    }

    private fun twoTrioGoal(): List<Card> = listOf(
        Card(Rank.SEVEN, Suit.CLUBS, deck = 0),
        Card(Rank.SEVEN, Suit.DIAMONDS, deck = 0),
        Card(Rank.SEVEN, Suit.HEARTS, deck = 0),
        Card(Rank.NINE, Suit.CLUBS, deck = 0),
        Card(Rank.NINE, Suit.DIAMONDS, deck = 0),
        Card(Rank.NINE, Suit.HEARTS, deck = 0)
    )
}
