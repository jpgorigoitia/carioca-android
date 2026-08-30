package com.carioca.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    @Test
    fun deckHas108UniqueCardsAndFourJokers() {
        val deck = GameRules.deck()
        assertEquals(108, deck.size)
        assertEquals(108, deck.toSet().size)
        assertEquals(4, deck.count { it.isJoker })
    }

    @Test
    fun redAcesAreNatural() {
        assertFalse(Card(Rank.ACE, Suit.HEARTS).isJoker)
        assertFalse(Card(Rank.ACE, Suit.DIAMONDS).isJoker)
    }

    @Test
    fun trioIsThreeSameRankDifferentSuitCards() {
        val trio = listOf(
            Card(Rank.SEVEN, Suit.CLUBS, deck = 0),
            Card(Rank.SEVEN, Suit.HEARTS, deck = 0),
            Card(Rank.SEVEN, Suit.SPADES, deck = 1)
        )
        assertTrue(GameRules.validInitial(MeldType.LEG, trio))
    }

    @Test
    fun duplicateNaturalSuitIsRejectedInTrio() {
        val trio = listOf(
            Card(Rank.NINE, Suit.SPADES, deck = 0),
            Card(Rank.NINE, Suit.SPADES, deck = 1),
            Card(Rank.NINE, Suit.DIAMONDS, deck = 0)
        )
        assertFalse(GameRules.validInitial(MeldType.LEG, trio))
    }

    @Test
    fun naturalTrioMayContainFourDifferentSuits() {
        val four = Suit.entries.map { suit -> Card(Rank.QUEEN, suit, deck = 0) }
        assertTrue(GameRules.validInitial(MeldType.LEG, four))
        assertTrue(GameRules.valid(MeldType.LEG, four))
    }

    @Test
    fun trioCannotGrowBeyondFourCards() {
        val five = Suit.entries.map { suit -> Card(Rank.QUEEN, suit, deck = 0) } + Card(Rank.JOKER)
        assertFalse(GameRules.valid(MeldType.LEG, five))
    }

    @Test
    fun twoJokersInvalidateAnyMeld() {
        val cards = listOf(
            Card(Rank.SEVEN, Suit.CLUBS),
            Card(Rank.SEVEN, Suit.HEARTS),
            Card(Rank.JOKER, copy = 0),
            Card(Rank.JOKER, copy = 1)
        )
        assertFalse(GameRules.valid(MeldType.LEG, cards))
    }

    @Test
    fun jokerFillsStraightGap() {
        val cards = listOf(
            Card(Rank.FOUR, Suit.CLUBS),
            Card(Rank.FIVE, Suit.CLUBS),
            Card(Rank.JOKER),
            Card(Rank.SEVEN, Suit.CLUBS)
        )
        assertTrue(GameRules.validInitial(MeldType.STRAIGHT, cards))
    }

    @Test
    fun fiveCardStraightCanBeLowered() {
        val cards = listOf(
            Card(Rank.FOUR, Suit.CLUBS),
            Card(Rank.FIVE, Suit.CLUBS),
            Card(Rank.SIX, Suit.CLUBS),
            Card(Rank.SEVEN, Suit.CLUBS),
            Card(Rank.EIGHT, Suit.CLUBS)
        )
        assertTrue(GameRules.validInitial(MeldType.STRAIGHT, cards))
    }

    @Test
    fun kingAceTwoDoesNotWrap() {
        val cards = listOf(
            Card(Rank.KING, Suit.CLUBS),
            Card(Rank.ACE, Suit.CLUBS),
            Card(Rank.TWO, Suit.CLUBS),
            Card(Rank.THREE, Suit.CLUBS)
        )
        assertFalse(GameRules.validInitial(MeldType.STRAIGHT, cards))
    }

    @Test
    fun colourAndRoyalStraightsRejectJokers() {
        val ranks = Rank.entries.filter { it != Rank.JOKER }
        val colourWithJoker = ranks.take(12).mapIndexed { i, rank -> Card(rank, if (i % 2 == 0) Suit.HEARTS else Suit.DIAMONDS) } + Card(Rank.JOKER)
        val royalWithJoker = ranks.take(12).map { rank -> Card(rank, Suit.SPADES) } + Card(Rank.JOKER)
        assertFalse(GameRules.valid(MeldType.COLOUR_STRAIGHT, colourWithJoker))
        assertFalse(GameRules.valid(MeldType.ROYAL_STRAIGHT, royalWithJoker))
    }

    @Test
    fun establishedRoundSequenceIsPreserved() {
        assertEquals(listOf(MeldType.LEG, MeldType.LEG), GameRules.requiredTypes(GameRules.rounds[0]))
        assertEquals(listOf(MeldType.LEG, MeldType.STRAIGHT), GameRules.requiredTypes(GameRules.rounds[1]))
        assertEquals(4, GameRules.rounds[6].legs)
        assertEquals(3, GameRules.rounds[7].straights)
        assertEquals(13, GameRules.rounds[7].deal)
    }

    @Test
    fun scoringHasStealPenaltyAndNoGoingOutBonus() {
        assertEquals(4, GameRules.score(emptyList(), steals = 2))
        assertEquals(-10, GameRules.score(emptyList(), perfectCut = true))
    }
}
