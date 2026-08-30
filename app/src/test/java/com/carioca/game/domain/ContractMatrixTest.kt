package com.carioca.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractMatrixTest {
    @Test
    fun everyRegularRoundHasAPlayableFullObjective() {
        val contracts = listOf(
            listOf(trio(Rank.THREE), trio(Rank.SEVEN)).flatten(),
            listOf(trio(Rank.FOUR), straight(Suit.CLUBS, 2)).flatten(),
            listOf(straight(Suit.CLUBS, 2), straight(Suit.HEARTS, 7)).flatten(),
            listOf(trio(Rank.THREE), trio(Rank.SEVEN), trio(Rank.JACK)).flatten(),
            listOf(trio(Rank.THREE), trio(Rank.SEVEN), straight(Suit.SPADES, 9)).flatten(),
            listOf(trio(Rank.FIVE), straight(Suit.CLUBS, 2), straight(Suit.HEARTS, 8)).flatten(),
            listOf(trio(Rank.THREE), trio(Rank.SIX), trio(Rank.NINE), trio(Rank.QUEEN)).flatten(),
            listOf(straight(Suit.CLUBS, 2), straight(Suit.HEARTS, 6), straight(Suit.SPADES, 10)).flatten()
        )

        contracts.forEachIndexed { index, cards ->
            val rule = GameRules.rounds[index]
            val plan = GameRules.findMeldPlan(cards, GameRules.requiredTypes(rule), useAllCards = true)
            assertNotNull("Round ${index + 1} should have a legal full objective", plan)
            assertEquals(GameRules.requiredTypes(rule).size, plan!!.size)
            assertEquals(GameRules.requiredCardCount(rule), cards.size)
            assertTrue(plan.all { GameRules.validInitial(it.type, it.cards) })
        }
    }

    @Test
    fun specialRoundNineCrazyStraightIsPlayableWithOneJoker() {
        val naturals = Rank.entries.filter { it != Rank.JOKER && it != Rank.SIX }.mapIndexed { index, rank ->
            Card(rank, Suit.entries[index % Suit.entries.size], deck = 0)
        }
        val cards = naturals + Card(Rank.JOKER, deck = 0, copy = 0)
        val plan = GameRules.findMeldPlan(cards, GameRules.requiredTypes(GameRules.rounds[8]), useAllCards = true)
        assertNotNull(plan)
        assertEquals(MeldType.CRAZY_STRAIGHT, plan!!.single().type)
    }

    @Test
    fun specialRoundTenColourStraightIsPlayableWithoutJoker() {
        val redSuits = listOf(Suit.HEARTS, Suit.DIAMONDS)
        val cards = Rank.entries.filter { it != Rank.JOKER }.mapIndexed { index, rank ->
            Card(rank, redSuits[index % 2], deck = 0)
        }
        val plan = GameRules.findMeldPlan(cards, GameRules.requiredTypes(GameRules.rounds[9]), useAllCards = true)
        assertNotNull(plan)
        assertEquals(MeldType.COLOUR_STRAIGHT, plan!!.single().type)
    }

    @Test
    fun specialRoundElevenRoyalStraightIsPlayableWithoutJoker() {
        val cards = Rank.entries.filter { it != Rank.JOKER }.map { rank -> Card(rank, Suit.SPADES, deck = 0) }
        val plan = GameRules.findMeldPlan(cards, GameRules.requiredTypes(GameRules.rounds[10]), useAllCards = true)
        assertNotNull(plan)
        assertEquals(MeldType.ROYAL_STRAIGHT, plan!!.single().type)
    }

    @Test
    fun jokerCanCompleteATrioWhenNaturalSuitsDiffer() {
        val oneJoker = listOf(
            Card(Rank.TEN, Suit.CLUBS, deck = 0),
            Card(Rank.TEN, Suit.HEARTS, deck = 1),
            Card(Rank.JOKER, deck = 0, copy = 0)
        )
        assertTrue(GameRules.validInitial(MeldType.LEG, oneJoker))
    }

    private fun trio(rank: Rank): List<Card> = listOf(
        Card(rank, Suit.CLUBS, deck = 0),
        Card(rank, Suit.HEARTS, deck = 0),
        Card(rank, Suit.SPADES, deck = 1)
    )

    private fun straight(suit: Suit, start: Int): List<Card> {
        val ranks = Rank.entries.filter { it != Rank.JOKER }.associateBy { it.order }
        return (start until start + 4).map { order -> Card(ranks.getValue(order), suit, deck = 0) }
    }
}
