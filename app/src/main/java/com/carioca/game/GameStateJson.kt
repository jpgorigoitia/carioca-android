package com.carioca.game

import com.carioca.game.domain.Card
import com.carioca.game.domain.Difficulty
import com.carioca.game.domain.GameMode
import com.carioca.game.domain.GameState
import com.carioca.game.domain.Meld
import com.carioca.game.domain.MeldType
import com.carioca.game.domain.PlayerState
import com.carioca.game.domain.Rank
import com.carioca.game.domain.Suit
import com.carioca.game.domain.TurnPhase
import org.json.JSONArray
import org.json.JSONObject

object GameStateJson {
    fun encode(state: GameState): JSONObject = JSONObject()
        .put("mode", state.mode.name)
        .put("difficulty", state.difficulty.name)
        .put("roundIndex", state.roundIndex)
        .put("players", JSONArray().apply { state.players.forEach { put(encodePlayer(it)) } })
        .put("drawPile", JSONArray().apply { state.drawPile.forEach { put(encodeCard(it)) } })
        .put("discardPile", JSONArray().apply { state.discardPile.forEach { put(encodeCard(it)) } })
        .put("currentPlayer", state.currentPlayer)
        .put("phase", state.phase.name)
        .put("message", state.message)
        .put("roundWinner", state.roundWinner ?: JSONObject.NULL)
        .put("gameWinner", state.gameWinner ?: JSONObject.NULL)

    fun decode(value: Any): GameState {
        val obj = when (value) {
            is JSONObject -> value
            is String -> JSONObject(value)
            else -> JSONObject(value.toString())
        }
        return GameState(
            mode = GameMode.valueOf(obj.getString("mode")),
            difficulty = Difficulty.valueOf(obj.optString("difficulty", Difficulty.MEDIUM.name)),
            roundIndex = obj.getInt("roundIndex"),
            players = decodeArray(obj.getJSONArray("players"), ::decodePlayer),
            drawPile = decodeArray(obj.getJSONArray("drawPile"), ::decodeCard),
            discardPile = decodeArray(obj.getJSONArray("discardPile"), ::decodeCard),
            currentPlayer = obj.getInt("currentPlayer"),
            phase = TurnPhase.valueOf(obj.getString("phase")),
            selected = emptySet(),
            message = obj.optString("message", ""),
            roundWinner = nullableInt(obj, "roundWinner"),
            gameWinner = nullableInt(obj, "gameWinner")
        )
    }

    private fun encodePlayer(player: PlayerState): JSONObject = JSONObject()
        .put("name", player.name)
        .put("isHuman", player.isHuman)
        .put("hand", JSONArray().apply { player.hand.forEach { put(encodeCard(it)) } })
        .put("melds", JSONArray().apply { player.melds.forEach { put(encodeMeld(it)) } })
        .put("score", player.score)
        .put("roundPoints", player.roundPoints)
        .put("steals", player.steals)

    private fun decodePlayer(obj: JSONObject): PlayerState = PlayerState(
        name = obj.getString("name"),
        isHuman = obj.optBoolean("isHuman", true),
        hand = decodeArray(obj.getJSONArray("hand"), ::decodeCard),
        melds = decodeArray(obj.getJSONArray("melds"), ::decodeMeld),
        score = obj.optInt("score", 0),
        roundPoints = obj.optInt("roundPoints", 0),
        steals = obj.optInt("steals", 0)
    )

    private fun encodeMeld(meld: Meld): JSONObject = JSONObject()
        .put("type", meld.type.name)
        .put("cards", JSONArray().apply { meld.cards.forEach { put(encodeCard(it)) } })

    private fun decodeMeld(obj: JSONObject): Meld = Meld(
        type = MeldType.valueOf(obj.getString("type")),
        cards = decodeArray(obj.getJSONArray("cards"), ::decodeCard)
    )

    private fun encodeCard(card: Card): JSONObject = JSONObject()
        .put("rank", card.rank.name)
        .put("suit", card.suit?.name ?: JSONObject.NULL)
        .put("deck", card.deck)
        .put("copy", card.copy)

    private fun decodeCard(obj: JSONObject): Card = Card(
        rank = Rank.valueOf(obj.getString("rank")),
        suit = if (obj.isNull("suit")) null else Suit.valueOf(obj.getString("suit")),
        deck = obj.optInt("deck", 0),
        copy = obj.optInt("copy", 0)
    )

    private fun nullableInt(obj: JSONObject, key: String): Int? = if (!obj.has(key) || obj.isNull(key)) null else obj.getInt(key)

    private fun <T> decodeArray(array: JSONArray, decoder: (JSONObject) -> T): List<T> =
        (0 until array.length()).map { decoder(array.getJSONObject(it)) }
}
