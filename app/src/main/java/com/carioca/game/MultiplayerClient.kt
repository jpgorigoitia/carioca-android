package com.carioca.game

import com.carioca.game.domain.GameState
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

private const val SUPABASE_URL = "https://hzvfvwyahppfpaatomjz.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_7WMC2hu5Y--KozNeMlXg5A_p1ipnOrE"

data class OnlinePlayer(
    val seat: Int,
    val name: String,
    val isHost: Boolean
)

data class OnlineRoom(
    val roomId: String,
    val code: String,
    val mode: String,
    val visibility: String,
    val maxPlayers: Int,
    val status: String,
    val mySeat: Int,
    val isHost: Boolean,
    val version: Long = 0,
    val players: List<OnlinePlayer> = emptyList(),
    val gameState: GameState? = null
)

data class PublicRoom(
    val roomId: String,
    val code: String,
    val mode: String,
    val maxPlayers: Int,
    val playerCount: Int
)

object MultiplayerClient {
    fun newPlayerToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun createRoom(
        playerName: String,
        token: String,
        mode: String,
        visibility: String,
        maxPlayers: Int
    ): OnlineRoom {
        val payload = JSONObject()
            .put("p_name", playerName.trim())
            .put("p_token", token)
            .put("p_mode", mode)
            .put("p_visibility", visibility)
            .put("p_max_players", maxPlayers)
        return parseRoom(rpc("carioca_create_room", payload))
    }

    fun joinRoom(code: String, playerName: String, token: String): OnlineRoom {
        val payload = JSONObject()
            .put("p_code", code.trim().uppercase())
            .put("p_name", playerName.trim())
            .put("p_token", token)
        return parseRoom(rpc("carioca_join_room", payload))
    }

    fun snapshot(roomId: String, token: String): OnlineRoom {
        val payload = JSONObject()
            .put("p_room_id", roomId)
            .put("p_token", token)
        return parseRoom(rpc("carioca_room_snapshot", payload))
    }

    fun startGame(roomId: String, token: String, state: GameState): OnlineRoom {
        val payload = JSONObject()
            .put("p_room_id", roomId)
            .put("p_token", token)
            .put("p_state", GameStateJson.encode(state))
        return parseRoom(rpc("carioca_start_game", payload))
    }

    fun updateGame(roomId: String, token: String, expectedVersion: Long, state: GameState): OnlineRoom {
        val payload = JSONObject()
            .put("p_room_id", roomId)
            .put("p_token", token)
            .put("p_expected_version", expectedVersion)
            .put("p_state", GameStateJson.encode(state))
        return parseRoom(rpc("carioca_update_game", payload))
    }

    fun publicRooms(): List<PublicRoom> {
        val value = rpc("carioca_public_rooms", JSONObject())
        val array = when (value) {
            is JSONArray -> value
            is String -> JSONArray(value)
            else -> JSONArray(value.toString())
        }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            PublicRoom(
                roomId = item.getString("room_id"),
                code = item.getString("code"),
                mode = item.getString("mode"),
                maxPlayers = item.getInt("max_players"),
                playerCount = item.getInt("player_count")
            )
        }
    }

    private fun parseRoom(value: Any): OnlineRoom {
        val obj = when (value) {
            is JSONObject -> value
            is String -> JSONObject(value)
            else -> JSONObject(value.toString())
        }
        val players = mutableListOf<OnlinePlayer>()
        val array = obj.optJSONArray("players") ?: JSONArray()
        for (index in 0 until array.length()) {
            val player = array.getJSONObject(index)
            players += OnlinePlayer(
                seat = player.getInt("seat"),
                name = player.getString("name"),
                isHost = player.optBoolean("is_host", false)
            )
        }
        val gameState = when (val raw = obj.opt("game_state")) {
            null, JSONObject.NULL -> null
            is JSONObject -> runCatching { GameStateJson.decode(raw) }.getOrNull()
            is String -> raw.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { GameStateJson.decode(it) }.getOrNull() }
            else -> runCatching { GameStateJson.decode(raw) }.getOrNull()
        }
        return OnlineRoom(
            roomId = obj.getString("room_id"),
            code = obj.getString("code"),
            mode = obj.getString("mode"),
            visibility = obj.optString("visibility", "PRIVATE"),
            maxPlayers = obj.getInt("max_players"),
            status = obj.getString("status"),
            mySeat = obj.optInt("my_seat", obj.optInt("seat", 0)),
            isHost = obj.optBoolean("is_host", obj.optInt("seat", -1) == 0),
            version = obj.optLong("version", 0L),
            players = players,
            gameState = gameState
        )
    }

    private fun rpc(name: String, body: JSONObject): Any {
        val connection = URL("$SUPABASE_URL/rest/v1/rpc/$name").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", SUPABASE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Online service error ($code)")
            }
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) return JSONArray(trimmed)
            if (trimmed.startsWith("{")) return JSONObject(trimmed)
            return trimmed.trim('"')
        } finally {
            connection.disconnect()
        }
    }
}
