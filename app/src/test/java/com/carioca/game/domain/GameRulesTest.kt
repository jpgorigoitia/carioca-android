package com.carioca.game.domain

import org.junit.Assert.*
import org.junit.Test

class GameRulesTest {
    @Test fun deckHas108CardsAndFourJokers(){ val d=GameRules.deck();assertEquals(108,d.size);assertEquals(4,d.count{it.isJoker}) }
    @Test fun redAcesAreNatural(){ assertFalse(Card(Rank.ACE,Suit.HEARTS).isJoker);assertFalse(Card(Rank.ACE,Suit.DIAMONDS).isJoker) }
    @Test fun twoJokersInvalidateAnyMeld(){ val c=listOf(Card(Rank.SEVEN,Suit.CLUBS),Card(Rank.SEVEN,Suit.HEARTS),Card(Rank.JOKER),Card(Rank.JOKER));assertFalse(GameRules.valid(MeldType.LEG,c)) }
    @Test fun jokerFillsStraightGap(){ val c=listOf(Card(Rank.FOUR,Suit.CLUBS),Card(Rank.FIVE,Suit.CLUBS),Card(Rank.JOKER),Card(Rank.SEVEN,Suit.CLUBS));assertTrue(GameRules.valid(MeldType.STRAIGHT,c)) }
    @Test fun kingAceTwoDoesNotWrap(){ val c=listOf(Card(Rank.KING,Suit.CLUBS),Card(Rank.ACE,Suit.CLUBS),Card(Rank.TWO,Suit.CLUBS),Card(Rank.THREE,Suit.CLUBS));assertFalse(GameRules.valid(MeldType.STRAIGHT,c)) }
    @Test fun scoringHasStealAndNoGoingOutBonus(){ assertEquals(4,GameRules.score(emptyList(),steals=2));assertEquals(-10,GameRules.score(emptyList(),perfectCut=true)) }
}
