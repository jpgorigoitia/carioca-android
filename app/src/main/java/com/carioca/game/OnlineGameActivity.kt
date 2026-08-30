package com.carioca.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class OnlineGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val roomId = intent.getStringExtra("room_id").orEmpty()
        val token = intent.getStringExtra("token").orEmpty()
        if (roomId.isBlank() || token.isBlank()) {
            finish()
            return
        }

        setContent {
            OnlinePlayableSession(
                roomId = roomId,
                token = token,
                onExit = { finish() }
            )
        }
    }
}
