package com.playbox.games.data

import android.content.Context
import com.playbox.games.model.GameId

class PlayerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("playbox_prefs", Context.MODE_PRIVATE)

    var playerName: String
        get() = prefs.getString("player_name", "Игрок") ?: "Игрок"
        set(value) {
            val clean = value.trim().take(20).ifEmpty { "Игрок" }
            prefs.edit().putString("player_name", clean).apply()
        }

    var serverUrl: String
        get() = prefs.getString("server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) {
            val clean = value.trim().trimEnd('/').ifEmpty { DEFAULT_SERVER_URL }
            prefs.edit().putString("server_url", clean).apply()
        }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()

    var totalWins: Int
        get() = prefs.getInt("total_wins", 0)
        private set(value) = prefs.edit().putInt("total_wins", value).apply()

    fun recordWin(gameId: GameId) {
        totalWins += 1
        val key = "wins_${gameId.id}"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun getWins(gameId: GameId): Int = prefs.getInt("wins_${gameId.id}", 0)

    fun updateBestScore(gameId: GameId, score: Int): Boolean {
        val key = "best_${gameId.id}"
        val current = prefs.getInt(key, 0)
        if (score > current) {
            prefs.edit().putInt(key, score).apply()
            return true
        }
        return false
    }

    fun getBestScore(gameId: GameId): Int = prefs.getInt("best_${gameId.id}", 0)

    fun updateBestTime(gameId: GameId, seconds: Int): Boolean {
        val key = "best_time_${gameId.id}"
        val current = prefs.getInt(key, Int.MAX_VALUE)
        if (seconds < current) {
            prefs.edit().putInt(key, seconds).apply()
            return true
        }
        return false
    }

    fun getBestTime(gameId: GameId): Int? {
        val v = prefs.getInt("best_time_${gameId.id}", -1)
        return if (v >= 0) v else null
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://rvha-home.duckdns.org:8443/playbox"
        const val LAN_SERVER_URL = "http://192.168.1.160/playbox"
    }
}
