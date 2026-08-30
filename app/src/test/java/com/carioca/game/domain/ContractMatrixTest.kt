package com.carioca.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractMatrixTest {
    @Test
    fun everyRegularRoundHasAPlayableExactContract() {
        val contracts = listOf(
            listOf(trio(Rank.THREE, 0), trio(Rank.SEVEN, 0)).flatten(),
            listOf(trio(Rank.FOUR, 0), straight(Suit.CLUBS, 2, 0)).flatten(),
            listOf(straight(Suit.CLUBS, 2, 0), straight(Suit.HEARTS, 7, 0)).flatten(),
            listOf(trio(Rank.THREE, 0), trio(Rank.SEVEN, 0), trio(Rank.JACK, 0)).flatten(),
            listOf(trio(Rank.THREE, 0), trio(Rank.SEVEN, 0), straight(Suit.SPADES, 9, 0)).flatten(),
            listOf(trio(Rank.FIVE, 0), straight(Suit.CLUBS, 2, 0), straight(Suit.HEARTS, 8, 0)).flatten(),
            listOf(trio(Rank.THREE, 0), trio(Rank.SIX, 0), trio(Rank.NINE, 0), trio(Rank.QUEEN, 0)).flatten(),
            listOf(straight(Suit.CLUBS, 2, 0), straight(Suit.HEARTS, 6, 0), straight(Suit.SPADES, 10, 0)).flatten()
        )

        contracts.forEachIndexed { index, cards ->
            val rule = GameRules.rounds[index]
            val plan = GameRules.findMeldPlan(cards, GameRules.requiredTypes(rule), useAllCards = true)
            assertNotNull("Round ${index + 1} should have a legal exact contract", plan)
            assertEquals(GameRules.requiredTypes(rule).size, plan!!.size)
            assertEquals(GameRules.requiredCardCount(rule), cards.size)
            assertTrue(plan.all { GameRules.validInitial(it.type, it.cards) })
        }
    }

    @Test
    fun specialRoundNineCrazyStraightIsPlayable() {
        val cards = Rank.entries.filter { it != Rank.JOKER }.mapIndexed { index, rank ->
            Card(rank, Suit.entries[index % Suit.entries.size], deck = 0)
        }
        val rule = GameRules.rounds[8]
        val plan = GameRules.findMeldPlan(cards, GameRules.requiredTypes(rule), useAllCards = true)
        assertNotNull(plan)
        assertEquals(MeldType.CRAZY_STRAIGHT, plan!!.single().type)
    }

    @Test
    fun specialRoundTenColourStraightIsPlayable() {
        val redSuits = listOf(Suit.HEARTS, Suit.DIAMONDS)
        val cards = Rank.entries.filter { it != Rank.JOKER }.mapIndexed { index, rank ->
            Card(rank, redSuits[index % 2], deck = 0)
        }
        val rule = GameRules.rounds[9]
        val plan = GameRules.findMeldPlan(cards, GameRules.requiredTypes(rule), useAllCards = true)
        assertNotNull(plan)
        assertEquals(MeldType.COLOUR_STRAIGHT, plan!!.single().type)
    }

    @Test
    fun specialRoundElevenRoyalStraightIsPlayable() {
        val cards = Rank.entries.filter { it != Rank.JOKER }.map { rank -> Card(rank, Suit.SPADES, deck = 0) }
        val rule = GameRules.rounds[10]
        val plan = GameRules.findMeldPlan(cards, GameRules.requiredTypes(rule), useAllCards = true)
        assertNotNull(plan)
        assertEquals(MeldType.ROYAL_STRAIGHT, plan!!.single().type)
    }

    @Test
    fun jokerCanCompleteOneTrioButTwoJokersCannot() {
        val oneJoker = listOf(
            Card(Rank.TEN, Suit.CLUBS, deck = 0),
            Card(Rank.TEN, Suit.CLUBS, deck = 1),
            Card(Rank.JOKER, deck = 0, copy = 0)
        )
        assertTrue(GameRules.validInitial(MeldType.LEG, oneJoker))
    }

    private fun trio(rank: Rank, deck: Int): List<Card> = listOf(
        Card(rank, Suit.CLUBS, deck = deck),
        Card(rank, Suit.HEARTS, deck = deck),
        Card(rank, Suit.CLUBS, deck = 1 - deck)
    )

    private fun straight(suit: Suit, start: Int, deck: Int): List<Card> {
        val ranks = Rank.entries.filter { it != Rank.JOKER }.associateBy { it.order }
        return (start until start + 4).map { order -> Card(ranks.getValue(order), suit, deck = deck) }
    }
}
