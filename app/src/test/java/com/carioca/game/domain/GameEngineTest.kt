package com.carioca.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun newRegularGameDealsElevenCardsAndStartsOnHumanDraw() {
        val state = GameEngine.newGame(GameMode.REGULAR, 4, Difficulty.MEDIUM, seed = 1234)
        assertEquals(4, state.players.size)
        assertTrue(state.players.all { it.hand.size == 11 })
        assertEquals(63, state.drawPile.size)
        assertEquals(1, state.discardPile.size)
        assertEquals(TurnPhase.DRAW, state.phase)
    }

    @Test
    fun drawingMovesHumanToActionPhase() {
        val initial = GameEngine.newGame(GameMode.REGULAR, 2, Difficulty.EASY, seed = 7)
        val drawn = GameEngine.drawFromDeck(initial)
        assertEquals(12, drawn.players.first().hand.size)
        assertEquals(TurnPhase.ACTION, drawn.phase)
    }

    @Test
    fun takingTopDiscardOnOwnTurnHasNoStealPenalty() {
        val initial = GameEngine.newGame(GameMode.REGULAR, 2, Difficulty.MEDIUM, seed = 9)
        val taken = GameEngine.takeDiscard(initial)
        assertEquals(0, taken.players.first().steals)
        assertEquals(12, taken.players.first().hand.size)
        assertEquals(0, taken.discardPile.size)
        assertEquals(TurnPhase.ACTION, taken.phase)
    }

    @Test
    fun outOfTurnStealAddsTwoPointPenaltyHook() {
        val card = Card(Rank.KING, Suit.HEARTS, deck = 0)
        val players = listOf(
            PlayerState("You", true, hand = listOf(Card(Rank.TWO, Suit.CLUBS))),
            PlayerState("AI 1", false, hand = listOf(Card(Rank.THREE, Suit.CLUBS))),
            PlayerState("AI 2", false, hand = listOf(Card(Rank.FOUR, Suit.CLUBS))),
            PlayerState("AI 3", false, hand = listOf(Card(Rank.FIVE, Suit.CLUBS)))
        )
        val state = GameState(
            mode = GameMode.REGULAR,
            difficulty = Difficulty.EASY,
            roundIndex = 0,
            players = players,
            drawPile = GameRules.deck().take(20),
            discardPile = listOf(card),
            currentPlayer = 0,
            phase = TurnPhase.STEAL_WINDOW,
            pendingNextPlayer = 0,
            stealQueue = listOf(0)
        )

        val stolen = GameEngine.stealAvailableDiscard(state)

        assertEquals(1, stolen.players.first().steals)
        assertTrue(card in stolen.players.first().hand)
        assertTrue(stolen.discardPile.isEmpty())
    }

    @Test
    fun humanCanPassOutOfTurnSteal() {
        val state = GameState(
            mode = GameMode.REGULAR,
            difficulty = Difficulty.EASY,
            roundIndex = 0,
            players = listOf(
                PlayerState("You", true),
                PlayerState("AI 1", false),
                PlayerState("AI 2", false)
            ),
            drawPile = GameRules.deck().take(20),
            discardPile = listOf(Card(Rank.NINE, Suit.SPADES)),
            currentPlayer = 0,
            phase = TurnPhase.STEAL_WINDOW,
            pendingNextPlayer = 0,
            stealQueue = listOf(0)
        )

        val passed = GameEngine.passSteal(state)

        assertEquals(TurnPhase.DRAW, passed.phase)
        assertEquals(0, passed.currentPlayer)
        assertTrue(passed.stealQueue.isEmpty())
    }

    @Test
    fun oneTrioSelectionIsRecognizedButCannotLowerRoundOneAlone() {
        val initial = GameEngine.newGame(GameMode.REGULAR, 2, Difficulty.MEDIUM, seed = 11)
        val trio = trio(Rank.SEVEN)
        val human = initial.players.first().copy(hand = trio + Card(Rank.TWO, Suit.CLUBS, deck = 1))
        val ready = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = trio.toSet()
        )

        val partial = GameEngine.selectionPlan(ready)
        val result = GameEngine.createMeld(ready)

        assertEquals(1, partial?.size)
        assertTrue(result.players.first().melds.isEmpty())
        assertEquals(4, result.players.first().hand.size)
        assertTrue(result.message.contains("1/2"))
    }

    @Test
    fun duplicateSuitTrioIsRejected() {
        val initial = GameEngine.newGame(GameMode.REGULAR, 2, Difficulty.MEDIUM, seed = 21)
        val bad = listOf(
            Card(Rank.EIGHT, Suit.CLUBS, deck = 0),
            Card(Rank.EIGHT, Suit.CLUBS, deck = 1),
            Card(Rank.EIGHT, Suit.HEARTS, deck = 0)
        )
        val human = initial.players.first().copy(hand = bad + Card(Rank.TWO, Suit.SPADES, deck = 1))
        val state = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = bad.toSet()
        )
        assertTrue(GameEngine.createMeld(state).players.first().melds.isEmpty())
    }

    @Test
    fun fullRoundOneGoalLowersBothTriosTogether() {
        val initial = GameEngine.newGame(GameMode.REGULAR, 2, Difficulty.MEDIUM, seed = 13)
        val goal = twoTrioGoal()
        val filler = Card(Rank.TWO, Suit.SPADES, deck = 1)
        val human = initial.players.first().copy(hand = goal + filler)
        val ready = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = goal.toSet()
        )

        val lowered = GameEngine.createMeld(ready)

        assertEquals(2, lowered.players.first().melds.size)
        assertTrue(lowered.players.first().melds.all { it.type == MeldType.LEG })
        assertTrue(GameEngine.contractComplete(lowered.players.first(), lowered.roundRule))
        assertEquals(listOf(filler), lowered.players.first().hand)
    }

    @Test
    fun fullGoalMayIncludeFourCardTrio() {
        val initial = GameEngine.newGame(GameMode.REGULAR, 2, Difficulty.MEDIUM, seed = 19)
        val fourSevens = Suit.entries.map { suit -> Card(Rank.SEVEN, suit, deck = 0) }
        val threeNines = trio(Rank.NINE)
        val selected = (fourSevens + threeNines).toSet()
        val filler = Card(Rank.TWO, Suit.CLUBS, deck = 1)
        val human = initial.players.first().copy(hand = selected.toList() + filler)
        val ready = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = selected
        )
        val lowered = GameEngine.createMeld(ready)
        assertEquals(2, lowered.players.first().melds.size)
        assertEquals(7, lowered.players.first().melds.sumOf { it.cards.size })
    }

    @Test
    fun completedPlayerCanAddFourthSuitToExistingTrio() {
        val initial = GameEngine.newGame(GameMode.REGULAR, 2, Difficulty.MEDIUM, seed = 14)
        val goal = twoTrioGoal()
        val extraSeven = Card(Rank.SEVEN, Suit.SPADES, deck = 1)
        val filler = Card(Rank.TWO, Suit.SPADES, deck = 1)
        val human = initial.players.first().copy(hand = goal + extraSeven + filler)
        val ready = initial.copy(
            players = initial.players.toMutableList().also { it[0] = human },
            phase = TurnPhase.ACTION,
            selected = goal.toSet()
        )
        val lowered = GameEngine.createMeld(ready)
        val extended = GameEngine.addSelectedToMeld(
            lowered.copy(phase = TurnPhase.ACTION, selected = setOf(extraSeven)), 0, 0
        )
        assertEquals(4, extended.players.first().melds.first().cards.size)
        assertFalse(extraSeven in extended.players.first().hand)
    }

    private fun trio(rank: Rank): List<Card> = listOf(
        Card(rank, Suit.CLUBS, deck = 0),
        Card(rank, Suit.DIAMONDS, deck = 0),
        Card(rank, Suit.HEARTS, deck = 1)
    )

    private fun twoTrioGoal(): List<Card> = trio(Rank.SEVEN) + trio(Rank.NINE)
}
