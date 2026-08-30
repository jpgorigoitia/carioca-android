package com.carioca.game.domain

enum class Suit(val red: Boolean) { CLUBS(false), DIAMONDS(true), HEARTS(true), SPADES(false) }
enum class Rank(val order: Int, val points: Int) {
    ACE(1,15), TWO(2,2), THREE(3,3), FOUR(4,4), FIVE(5,5), SIX(6,6), SEVEN(7,7),
    EIGHT(8,8), NINE(9,9), TEN(10,10), JACK(11,10), QUEEN(12,10), KING(13,10), JOKER(0,25)
}

data class Card(
    val rank: Rank,
    val suit: Suit? = null,
    val deck: Int = 0,
    val copy: Int = 0
) {
    val isJoker get() = rank == Rank.JOKER
}

enum class MeldType { LEG, STRAIGHT, CRAZY_STRAIGHT, COLOUR_STRAIGHT, ROYAL_STRAIGHT }
enum class GameMode(val rounds: Int) { REGULAR(8), SPECIAL(11) }
enum class Difficulty { EASY, MEDIUM, HARD }

data class RoundRule(
    val number: Int,
    val legs: Int = 0,
    val straights: Int = 0,
    val special: MeldType? = null,
    val deal: Int = 11
)

object GameRules {
    val rounds = listOf(
        RoundRule(1, legs = 2),
        RoundRule(2, legs = 1, straights = 1),
        RoundRule(3, straights = 2),
        RoundRule(4, legs = 3),
        RoundRule(5, legs = 2, straights = 1),
        RoundRule(6, legs = 1, straights = 2),
        RoundRule(7, legs = 4),
        RoundRule(8, straights = 3, deal = 13),
        RoundRule(9, special = MeldType.CRAZY_STRAIGHT, deal = 13),
        RoundRule(10, special = MeldType.COLOUR_STRAIGHT, deal = 13),
        RoundRule(11, special = MeldType.ROYAL_STRAIGHT, deal = 13)
    )

    fun deck(): List<Card> = (0..1).flatMap { deck ->
        Suit.entries.flatMap { suit ->
            Rank.entries.filter { it != Rank.JOKER }.map { rank -> Card(rank, suit, deck) }
        } + listOf(
            Card(Rank.JOKER, deck = deck, copy = 0),
            Card(Rank.JOKER, deck = deck, copy = 1)
        )
    }

    fun valid(type: MeldType, cards: List<Card>): Boolean {
        val jokers = cards.count { it.isJoker }
        if (jokers > 1) return false
        val natural = cards.filterNot { it.isJoker }

        return when (type) {
            MeldType.LEG ->
                cards.size >= 3 &&
                    natural.map { it.rank }.distinct().size == 1 &&
                    natural.map { it.suit }.distinct().size == natural.size

            MeldType.STRAIGHT ->
                cards.size >= 4 &&
                    natural.isNotEmpty() &&
                    natural.map { it.suit }.distinct().size == 1 &&
                    sequenceFits(natural.map { it.rank.order }, jokers)

            MeldType.CRAZY_STRAIGHT ->
                cards.size == 13 && natural.map { it.rank }.distinct().size == natural.size

            MeldType.COLOUR_STRAIGHT ->
                cards.size == 13 && jokers == 0 &&
                    natural.map { it.rank }.distinct().size == 13 &&
                    natural.map { it.suit?.red }.distinct().size == 1

            MeldType.ROYAL_STRAIGHT ->
                cards.size == 13 && jokers == 0 &&
                    natural.map { it.rank }.distinct().size == 13 &&
                    natural.map { it.suit }.distinct().size == 1
        }
    }

    private fun sequenceFits(values: List<Int>, jokers: Int): Boolean {
        if (values.isEmpty() || values.distinct().size != values.size) return false

        fun gaps(valuesToCheck: List<Int>): Int =
            valuesToCheck.sorted().zipWithNext().sumOf { (a, b) -> b - a - 1 }

        val aceLow = gaps(values)
        val aceHigh = gaps(values.map { if (it == 1) 14 else it })
        return minOf(aceLow, aceHigh) <= jokers
    }

    fun score(hand: List<Card>, steals: Int = 0, perfectCut: Boolean = false): Int =
        hand.sumOf { it.rank.points } + steals * 2 - if (perfectCut) 10 else 0
}
