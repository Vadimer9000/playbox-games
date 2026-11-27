package com.playbox.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.playbox.games.data.PlayerPrefs
import com.playbox.games.games.BattleshipScreen
import com.playbox.games.games.CheckersScreen
import com.playbox.games.games.Connect4Screen
import com.playbox.games.games.DotsBoxesScreen
import com.playbox.games.games.Game2048Screen
import com.playbox.games.games.MancalaScreen
import com.playbox.games.games.MinesweeperScreen
import com.playbox.games.games.PingPongScreen
import com.playbox.games.games.ReversiScreen
import com.playbox.games.games.SnakeScreen
import com.playbox.games.games.TicTacToeScreen
import com.playbox.games.games.WordleScreen
import com.playbox.games.model.GameCatalog
import com.playbox.games.model.GameId
import com.playbox.games.model.LaunchConfig
import com.playbox.games.model.PlayMode
import com.playbox.games.network.OnlineClient
import com.playbox.games.network.OnlineEvent
import com.playbox.games.ui.components.GameTopBar
import com.playbox.games.ui.screens.HomeScreen
import com.playbox.games.ui.theme.PlayBoxColors
import com.playbox.games.ui.theme.PlayBoxTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = PlayerPrefs(applicationContext)
        val onlineClient = OnlineClient()

        setContent {
            PlayBoxTheme {
                PlayBoxApp(prefs = prefs, onlineClient = onlineClient)
            }
        }
    }
}

@Composable
private fun PlayBoxApp(prefs: PlayerPrefs, onlineClient: OnlineClient) {
    var activeLaunch by remember { mutableStateOf<LaunchConfig?>(null) }
    var resetSignal by remember { mutableIntStateOf(0) }
    var opponentEmoji by remember { mutableStateOf<String?>(null) }
    val roomState by onlineClient.roomState.collectAsState()

    LaunchedEffect(Unit) {
        onlineClient.events.collect { event ->
            when (event) {
                is OnlineEvent.OpponentEmoji -> {
                    opponentEmoji = event.emoji
                }
                is OnlineEvent.GameChanged -> {
                    val gId = GameId.fromId(event.gameId)
                    if (gId != null && activeLaunch != null) {
                        activeLaunch = activeLaunch!!.copy(
                            gameId = gId,
                            seed = event.seed,
                            startingPlayer = event.startingPlayer
                        )
                        resetSignal++
                    }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(opponentEmoji) {
        if (opponentEmoji != null) {
            delay(2200)
            opponentEmoji = null
        }
    }

    val current = activeLaunch
    if (current == null) {
        HomeScreen(
            prefs = prefs,
            onlineClient = onlineClient,
            roomState = roomState,
            onLaunchGame = { cfg ->
                resetSignal = 0
                activeLaunch = cfg
            }
        )
    } else {
        BackHandler {
            if (current.mode == PlayMode.ONLINE) onlineClient.disconnect()
            activeLaunch = null
        }

        val def = GameCatalog.get(current.gameId)
        val exitGame: () -> Unit = {
            if (current.mode == PlayMode.ONLINE) onlineClient.disconnect()
            activeLaunch = null
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PlayBoxColors.CreamBg)
                .navigationBarsPadding()
        ) {
            GameTopBar(
                title = def.title,
                mode = current.mode,
                difficulty = current.difficulty,
                roomState = roomState,
                opponentEmoji = opponentEmoji,
                onBack = exitGame,
                onRestart = {
                    if (current.mode == PlayMode.ONLINE) onlineClient.requestRematch()
                    else resetSignal++
                },
                onSendEmoji = { emoji -> onlineClient.sendEmoji(emoji) }
            )

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when (current.gameId) {
                    GameId.TICTACTOE -> TicTacToeScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.CONNECT4 -> Connect4Screen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.BATTLESHIP -> BattleshipScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.PING_PONG -> PingPongScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.CHECKERS -> CheckersScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.REVERSI -> ReversiScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.MANCALA -> MancalaScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.DOTS_BOXES -> DotsBoxesScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.GAME_2048 -> Game2048Screen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.MINESWEEPER -> MinesweeperScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.SNAKE -> SnakeScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                    GameId.WORDLE -> WordleScreen(current, prefs, onlineClient, roomState, resetSignal, exitGame)
                }
            }
        }
    }
}
