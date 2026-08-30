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
    /*
     * Internal enum LEG is retained only for save/network compatibility.
     * Player-facing terminology is Trio.
     * Regular mode follows the 8-round Carioca sequence established for this app.
     */
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

    /** Exact size required when a new contract meld is first placed on the table. */
    fun minimumCards(type: MeldType): Int = when (type) {
        MeldType.LEG -> 3
        MeldType.STRAIGHT -> 4
        MeldType.CRAZY_STRAIGHT,
        MeldType.COLOUR_STRAIGHT,
        MeldType.ROYAL_STRAIGHT -> 13
    }

    /**
     * Validates a newly-created contract meld.
     * A Trio starts with exactly 3 cards. A Straight starts with exactly 4.
     * They may be extended only after they are on the table.
     */
    fun validInitial(type: MeldType, cards: List<Card>): Boolean =
        cards.size == minimumCards(type) && validCore(type, cards, allowExtended = false)

    /** Validates a meld already on the table after cards have been added to it. */
    fun valid(type: MeldType, cards: List<Card>): Boolean = validCore(type, cards, allowExtended = true)

    private fun validCore(type: MeldType, cards: List<Card>, allowExtended: Boolean): Boolean {
        if (cards.isEmpty()) return false
        val jokers = cards.count { it.isJoker }
        if (jokers > 1) return false
        val natural = cards.filterNot { it.isJoker }

        return when (type) {
            MeldType.LEG -> {
                if (cards.size < 3) false
                else if (!allowExtended && cards.size != 3) false
                else natural.isNotEmpty() && natural.map { it.rank }.distinct().size == 1
                // Two decks are in play: duplicate suits are legal in a Trio.
            }

            MeldType.STRAIGHT -> {
                if (cards.size < 4) false
                else if (!allowExtended && cards.size != 4) false
                else natural.isNotEmpty() &&
                    natural.map { it.suit }.distinct().size == 1 &&
                    sequenceFits(natural.map { it.rank.order }, jokers, cards.size)
            }

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

    fun requiredTypes(rule: RoundRule): List<MeldType> {
        rule.special?.let { return listOf(it) }
        return buildList {
            repeat(rule.legs) { add(MeldType.LEG) }
            repeat(rule.straights) { add(MeldType.STRAIGHT) }
        }
    }

    fun requiredCardCount(rule: RoundRule): Int = requiredTypes(rule).sumOf(::minimumCards)

    /** Finds disjoint exact-size contract melds from a hand/selection. */
    fun findMeldPlan(
        cards: List<Card>,
        required: List<MeldType>,
        useAllCards: Boolean = false
    ): List<Meld>? {
        if (required.isEmpty()) return if (!useAllCards || cards.isEmpty()) emptyList() else null
        val exactRequired = required.sumOf(::minimumCards)
        if (cards.size < exactRequired) return null
        if (useAllCards && cards.size != exactRequired) return null

        fun search(pool: List<Card>, requirementIndex: Int): List<Meld>? {
            if (requirementIndex >= required.size) {
                return if (!useAllCards || pool.isEmpty()) emptyList() else null
            }

            val type = required[requirementIndex]
            val size = minimumCards(type)
            if (pool.size < size) return null
            for (candidate in combinations(pool, size)) {
                if (!validInitial(type, candidate)) continue
                val remainingPool = pool.toMutableList()
                candidate.forEach { remainingPool.remove(it) }
                val rest = search(remainingPool, requirementIndex + 1) ?: continue
                return listOf(Meld(type, candidate)) + rest
            }
            return null
        }

        return search(cards, 0)
    }

    private fun sequenceFits(values: List<Int>, jokers: Int, totalSize: Int): Boolean {
        if (values.isEmpty() || values.distinct().size != values.size) return false

        fun canFit(sorted: List<Int>): Boolean {
            if (sorted.isEmpty()) return false
            val internalGaps = sorted.zipWithNext().sumOf { (a, b) -> b - a - 1 }
            if (internalGaps > jokers) return false
            val unusedJokers = jokers - internalGaps
            val span = sorted.last() - sorted.first() + 1 + unusedJokers
            return span <= totalSize && totalSize <= 13
        }

        val aceLow = values.sorted()
        val aceHigh = values.map { if (it == 1) 14 else it }.sorted()
        return canFit(aceLow) || canFit(aceHigh)
    }

    private fun <T> combinations(items: List<T>, size: Int): List<List<T>> {
        if (size <= 0 || size > items.size) return emptyList()
        val result = mutableListOf<List<T>>()
        val picked = mutableListOf<T>()
        fun walk(start: Int) {
            if (picked.size == size) {
                result += picked.toList()
                return
            }
            val needed = size - picked.size
            val lastStart = items.size - needed
            for (index in start..lastStart) {
                picked += items[index]
                walk(index + 1)
                picked.removeAt(picked.lastIndex)
            }
        }
        walk(0)
        return result
    }

    fun score(hand: List<Card>, steals: Int = 0, perfectCut: Boolean = false): Int =
        hand.sumOf { it.rank.points } + steals * 2 - if (perfectCut) 10 else 0
}
