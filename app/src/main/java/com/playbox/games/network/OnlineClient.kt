package com.playbox.games.network

import android.util.Log
import com.playbox.games.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OnlinePlayerInfo(
    val index: Int,
    val name: String,
    val connected: Boolean
)

data class OnlineRoomState(
    val code: String = "",
    val gameId: String = "",
    val playerIndex: Int = 1,
    val seed: Int = 42,
    val startingPlayer: Int = 1,
    val players: List<OnlinePlayerInfo> = emptyList(),
    val scoreP1: Int = 0,
    val scoreP2: Int = 0,
    val rematchVotes: List<Int> = emptyList(),
    val isConnected: Boolean = false,
    val latencyMs: Long = 0L
) {
    val opponent: OnlinePlayerInfo?
        get() = players.firstOrNull { it.index != playerIndex }

    val bothConnected: Boolean
        get() = players.count { it.connected } >= 2
}

sealed class OnlineEvent {
    data class OpponentMove(val playerIndex: Int, val payload: JSONObject) : OnlineEvent()
    data class OpponentState(val playerIndex: Int, val payload: JSONObject) : OnlineEvent()
    data class MatchFinished(val winner: Int, val scoreP1: Int, val scoreP2: Int) : OnlineEvent()
    data class RematchStart(val seed: Int, val startingPlayer: Int, val scoreP1: Int, val scoreP2: Int) : OnlineEvent()
    data class GameChanged(val gameId: String, val seed: Int, val startingPlayer: Int) : OnlineEvent()
    data class OpponentEmoji(val emoji: String) : OnlineEvent()
    data class Error(val message: String) : OnlineEvent()
}

data class RoomJoinResult(
    val code: String,
    val playerIndex: Int,
    val playerToken: String,
    val gameId: String,
    val seed: Int
)

class OnlineClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var lastPingSentAt: Long = 0L

    private val _roomState = MutableStateFlow<OnlineRoomState?>(null)
    val roomState: StateFlow<OnlineRoomState?> = _roomState.asStateFlow()

    private val _events = MutableSharedFlow<OnlineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<OnlineEvent> = _events.asSharedFlow()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun createRoom(
        baseUrl: String,
        gameId: GameId,
        playerName: String
    ): Result<RoomJoinResult> = postRoomRequest(
        url = "${baseUrl.trimEnd('/')}/api/rooms",
        bodyJson = JSONObject().apply {
            put("gameId", gameId.id)
            put("playerName", playerName)
        }
    )

    suspend fun quickMatch(
        baseUrl: String,
        gameId: GameId,
        playerName: String
    ): Result<RoomJoinResult> = postRoomRequest(
        url = "${baseUrl.trimEnd('/')}/api/quickmatch",
        bodyJson = JSONObject().apply {
            put("gameId", gameId.id)
            put("playerName", playerName)
        }
    )

    suspend fun joinRoom(
        baseUrl: String,
        code: String,
        playerName: String
    ): Result<RoomJoinResult> = postRoomRequest(
        url = "${baseUrl.trimEnd('/')}/api/rooms/${code.trim().uppercase()}/join",
        bodyJson = JSONObject().apply {
            put("playerName", playerName)
        }
    )

    private suspend fun postRoomRequest(url: String, bodyJson: JSONObject): Result<RoomJoinResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toString().toRequestBody(jsonMedia))
                    .build()
                http.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull()
                        error(detail?.ifBlank { null } ?: "Ошибка сервера (${response.code})")
                    }
                    val obj = JSONObject(raw)
                    RoomJoinResult(
                        code = obj.getString("code"),
                        playerIndex = obj.getInt("playerIndex"),
                        playerToken = obj.getString("playerToken"),
                        gameId = obj.getString("gameId"),
                        seed = obj.optInt("seed", 42)
                    )
                }
            }
        }

    fun connect(
        baseUrl: String,
        joinResult: RoomJoinResult,
        playerName: String
    ) {
        disconnect()
        val wsBase = baseUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val encodedName = java.net.URLEncoder.encode(playerName, "UTF-8")
        val wsUrl = "$wsBase/ws/${joinResult.code}?token=${joinResult.playerToken}&name=$encodedName"

        _roomState.value = OnlineRoomState(
            code = joinResult.code,
            gameId = joinResult.gameId,
            playerIndex = joinResult.playerIndex,
            seed = joinResult.seed,
            isConnected = false
        )

        val request = Request.Builder().url(wsUrl).build()
        webSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _roomState.value = _roomState.value?.copy(isConnected = true)
                startPingLoop()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("OnlineClient", "WS failure: ${t.message}")
                _roomState.value = _roomState.value?.copy(isConnected = false)
                scope.launch {
                    _events.emit(OnlineEvent.Error(t.message ?: "Соединение потеряно"))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _roomState.value = _roomState.value?.copy(isConnected = false)
            }
        })
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(4000)
                lastPingSentAt = System.currentTimeMillis()
                sendRaw(JSONObject().put("type", "ping").put("ts", lastPingSentAt))
            }
        }
    }

    private fun handleMessage(text: String) {
        runCatching {
            val obj = JSONObject(text)
            when (obj.optString("type")) {
                "welcome" -> {
                    val idx = obj.optInt("playerIndex", 1)
                    _roomState.value = _roomState.value?.copy(playerIndex = idx, isConnected = true)
                }
                "pong" -> {
                    val ts = obj.optLong("ts", 0L)
                    if (ts > 0L) {
                        val rtt = (System.currentTimeMillis() - ts).coerceAtLeast(1L)
                        _roomState.value = _roomState.value?.copy(latencyMs = rtt)
                    }
                }
                "room_state" -> {
                    val playersJson = obj.optJSONArray("players")
                    val players = buildList {
                        if (playersJson != null) {
                            for (i in 0 until playersJson.length()) {
                                val p = playersJson.getJSONObject(i)
                                add(
                                    OnlinePlayerInfo(
                                        index = p.getInt("index"),
                                        name = p.optString("name", "Игрок"),
                                        connected = p.optBoolean("connected", false)
                                    )
                                )
                            }
                        }
                    }
                    val votesArr = obj.optJSONArray("rematchVotes")
                    val votes = buildList {
                        if (votesArr != null) {
                            for (i in 0 until votesArr.length()) add(votesArr.getInt(i))
                        }
                    }
                    val scoresObj = obj.optJSONObject("scores")
                    _roomState.value = (_roomState.value ?: OnlineRoomState()).copy(
                        code = obj.optString("code"),
                        gameId = obj.optString("gameId"),
                        seed = obj.optInt("seed", 42),
                        startingPlayer = obj.optInt("startingPlayer", 1),
                        players = players,
                        scoreP1 = scoresObj?.optInt("1", 0) ?: 0,
                        scoreP2 = scoresObj?.optInt("2", 0) ?: 0,
                        rematchVotes = votes,
                        isConnected = true
                    )
                }
                "opponent_move" -> {
                    val idx = obj.optInt("playerIndex", 0)
                    val payload = obj.optJSONObject("payload") ?: JSONObject()
                    scope.launch { _events.emit(OnlineEvent.OpponentMove(idx, payload)) }
                }
                "opponent_state" -> {
                    val idx = obj.optInt("playerIndex", 0)
                    val payload = obj.optJSONObject("payload") ?: JSONObject()
                    scope.launch { _events.emit(OnlineEvent.OpponentState(idx, payload)) }
                }
                "match_finished" -> {
                    val winner = obj.optInt("winner", 0)
                    val scoresObj = obj.optJSONObject("scores")
                    val s1 = scoresObj?.optInt("1", 0) ?: 0
                    val s2 = scoresObj?.optInt("2", 0) ?: 0
                    _roomState.value = _roomState.value?.copy(scoreP1 = s1, scoreP2 = s2)
                    scope.launch { _events.emit(OnlineEvent.MatchFinished(winner, s1, s2)) }
                }
                "rematch_start" -> {
                    val seed = obj.optInt("seed", 42)
                    val starter = obj.optInt("startingPlayer", 1)
                    val scoresObj = obj.optJSONObject("scores")
                    val s1 = scoresObj?.optInt("1", 0) ?: 0
                    val s2 = scoresObj?.optInt("2", 0) ?: 0
                    _roomState.value = _roomState.value?.copy(
                        seed = seed,
                        startingPlayer = starter,
                        scoreP1 = s1,
                        scoreP2 = s2,
                        rematchVotes = emptyList()
                    )
                    scope.launch { _events.emit(OnlineEvent.RematchStart(seed, starter, s1, s2)) }
                }
                "game_changed" -> {
                    val gId = obj.optString("gameId")
                    val seed = obj.optInt("seed", 42)
                    val starter = obj.optInt("startingPlayer", 1)
                    _roomState.value = _roomState.value?.copy(
                        gameId = gId,
                        seed = seed,
                        startingPlayer = starter,
                        rematchVotes = emptyList()
                    )
                    scope.launch { _events.emit(OnlineEvent.GameChanged(gId, seed, starter)) }
                }
                "opponent_emoji" -> {
                    val emoji = obj.optString("emoji")
                    if (emoji.isNotEmpty()) {
                        scope.launch { _events.emit(OnlineEvent.OpponentEmoji(emoji)) }
                    }
                }
            }
        }
    }

    fun sendMove(payload: JSONObject) {
        sendRaw(JSONObject().put("type", "move").put("payload", payload))
    }

    fun sendState(payload: JSONObject) {
        sendRaw(JSONObject().put("type", "game_state").put("payload", payload))
    }

    fun sendGameOver(winnerPlayerIndex: Int) {
        sendRaw(JSONObject().put("type", "game_over").put("winner", winnerPlayerIndex))
    }

    fun requestRematch() {
        sendRaw(JSONObject().put("type", "rematch_request"))
    }

    fun changeGame(gameId: GameId) {
        sendRaw(JSONObject().put("type", "change_game").put("gameId", gameId.id))
    }

    fun sendEmoji(emoji: String) {
        sendRaw(JSONObject().put("type", "emoji").put("emoji", emoji))
    }

    private fun sendRaw(obj: JSONObject) {
        webSocket?.send(obj.toString())
    }

    fun disconnect() {
        pingJob?.cancel()
        pingJob = null
        webSocket?.close(1000, "Leaving room")
        webSocket = null
        _roomState.value = null
    }
}
