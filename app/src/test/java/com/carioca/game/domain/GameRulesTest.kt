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
        assertTrue(GameRules.valid(MeldType.STRAIGHT, cards))
    }

    @Test
    fun kingAceTwoDoesNotWrap() {
        val cards = listOf(
            Card(Rank.KING, Suit.CLUBS),
            Card(Rank.ACE, Suit.CLUBS),
            Card(Rank.TWO, Suit.CLUBS),
            Card(Rank.THREE, Suit.CLUBS)
        )
        assertFalse(GameRules.valid(MeldType.STRAIGHT, cards))
    }

    @Test
    fun scoringHasStealPenaltyAndNoGoingOutBonus() {
        assertEquals(4, GameRules.score(emptyList(), steals = 2))
        assertEquals(-10, GameRules.score(emptyList(), perfectCut = true))
    }
}
