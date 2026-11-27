package com.playbox.games.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.data.PlayerPrefs
import com.playbox.games.model.BotDifficulty
import com.playbox.games.model.GameCatalog
import com.playbox.games.model.GameCategory
import com.playbox.games.model.GameDefinition
import com.playbox.games.model.GameId
import com.playbox.games.model.LaunchConfig
import com.playbox.games.model.PlayMode
import com.playbox.games.network.OnlineClient
import com.playbox.games.network.OnlineRoomState
import com.playbox.games.ui.components.GameCardArtwork
import com.playbox.games.ui.theme.PlayBoxColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    onLaunchGame: (LaunchConfig) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var selectedCategory by remember { mutableStateOf(GameCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var activeGameSheet by remember { mutableStateOf<GameDefinition?>(null) }
    var showOnlineDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var selectedDifficulty by remember { mutableStateOf(BotDifficulty.MEDIUM) }
    var joinCodeInput by remember { mutableStateOf("") }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var isOnlineBusy by remember { mutableStateOf(false) }

    val totalWins = remember(activeGameSheet, showSettingsDialog) { prefs.totalWins }

    val filteredGames = remember(selectedCategory, searchQuery) {
        GameCatalog.all.filter { game ->
            val catOk = selectedCategory == GameCategory.ALL || game.category == selectedCategory
            val q = searchQuery.trim().lowercase()
            val qOk = q.isEmpty() || game.title.lowercase().contains(q) || game.subtitle.lowercase().contains(q)
            catOk && qOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayBoxColors.CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header inspired by reference screenshots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PlayBoxColors.HeaderSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlayBoxColors.CreamBg)
            ) {
                Icon(Icons.Rounded.Menu, contentDescription = "Меню", tint = Color(0xFFB57C84))
            }

            Spacer(Modifier.width(12.dp))

            // Wins / Bolt counter pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFECC2),
                modifier = Modifier.clickable { showSettingsDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(PlayBoxColors.BoltGold)
                            .border(1.5.dp, PlayBoxColors.DarkInk, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = PlayBoxColors.DarkInk, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$totalWins",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = PlayBoxColors.DarkInk
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Online Hub button (replaces "Remove Ads" pill)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PlayBoxColors.OnlinePurple,
                shadowElevation = 2.dp,
                modifier = Modifier.clickable {
                    onlineError = null
                    showOnlineDialog = true
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (roomState?.isConnected == true) "КОМНАТА ${roomState.code}" else "ОНЛАЙН ИГРА",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Search + Category filter chips
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Найти игру...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = PlayBoxColors.MutedInk) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GameCategory.entries.forEach { cat ->
                    val selected = cat == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                cat.label,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PlayBoxColors.DarkInk,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // 2-column game cards grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredGames, key = { it.id.id }) { game ->
                GameGridCard(
                    game = game,
                    onClick = {
                        onlineError = null
                        activeGameSheet = game
                    }
                )
            }
        }
    }

    // Game Launch Mode Sheet (Solo / 2P Local / Online)
    val sheetGame = activeGameSheet
    if (sheetGame != null) {
        ModalBottomSheet(
            onDismissRequest = { if (!isOnlineBusy) activeGameSheet = null },
            containerColor = PlayBoxColors.HeaderSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = sheetGame.title.replace("\n", " "),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PlayBoxColors.DarkInk
                )
                Text(sheetGame.subtitle, fontSize = 14.sp, color = PlayBoxColors.MutedInk)

                Spacer(Modifier.height(14.dp))

                if (sheetGame.supportsBot) {
                    Text("Сложность бота", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PlayBoxColors.DarkInk)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotDifficulty.entries.forEach { diff ->
                            FilterChip(
                                selected = selectedDifficulty == diff,
                                onClick = { selectedDifficulty = diff },
                                label = { Text(diff.label, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // 1. Solo / Bot
                Button(
                    onClick = {
                        activeGameSheet = null
                        onLaunchGame(
                            LaunchConfig(
                                gameId = sheetGame.id,
                                mode = PlayMode.SOLO,
                                difficulty = selectedDifficulty
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF118AB2))
                ) {
                    Icon(if (sheetGame.supportsBot) Icons.Rounded.SmartToy else Icons.Rounded.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (sheetGame.supportsBot) "Играть против бота (${selectedDifficulty.label})" else "Одиночная игра",
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (sheetGame.supportsLocal2P) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            activeGameSheet = null
                            onLaunchGame(
                                LaunchConfig(
                                    gameId = sheetGame.id,
                                    mode = PlayMode.LOCAL_2P
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF476F))
                    ) {
                        Icon(Icons.Rounded.People, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Вдвоём на одном экране", fontWeight = FontWeight.ExtraBold)
                    }
                }

                if (sheetGame.supportsOnline) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            enabled = !isOnlineBusy,
                            onClick = {
                                isOnlineBusy = true
                                onlineError = null
                                scope.launch {
                                    val res = onlineClient.createRoom(prefs.serverUrl, sheetGame.id, prefs.playerName)
                                    isOnlineBusy = false
                                    res.onSuccess { join ->
                                        onlineClient.connect(prefs.serverUrl, join, prefs.playerName)
                                        activeGameSheet = null
                                        showOnlineDialog = true
                                    }.onFailure { err ->
                                        onlineError = err.message ?: "Не удалось создать комнату"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PlayBoxColors.OnlinePurple)
                        ) {
                            Text("Создать комнату", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            enabled = !isOnlineBusy,
                            onClick = {
                                isOnlineBusy = true
                                onlineError = null
                                scope.launch {
                                    val res = onlineClient.quickMatch(prefs.serverUrl, sheetGame.id, prefs.playerName)
                                    isOnlineBusy = false
                                    res.onSuccess { join ->
                                        onlineClient.connect(prefs.serverUrl, join, prefs.playerName)
                                        activeGameSheet = null
                                        if (join.playerIndex == 2) {
                                            onLaunchGame(
                                                LaunchConfig(
                                                    gameId = sheetGame.id,
                                                    mode = PlayMode.ONLINE,
                                                    roomCode = join.code,
                                                    playerIndex = join.playerIndex,
                                                    seed = join.seed
                                                )
                                            )
                                        } else {
                                            showOnlineDialog = true
                                        }
                                    }.onFailure { err ->
                                        onlineError = err.message ?: "Сервер недоступен"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Быстрый матч", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }

                if (isOnlineBusy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Подключение к серверу...", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
                    }
                }
                if (onlineError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(onlineError!!, color = Color(0xFFD90429), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Online Room Lobby Dialog (Join by code OR Wait for friend)
    if (showOnlineDialog) {
        val activeRoom = roomState
        AlertDialog(
            onDismissRequest = { showOnlineDialog = false },
            containerColor = PlayBoxColors.HeaderSurface,
            title = {
                Text(
                    text = if (activeRoom != null) "Комната ${activeRoom.code}" else "Онлайн-игра",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activeRoom == null) {
                        Text("Введите 5-значный код друга, чтобы войти в его комнату:", fontSize = 13.sp)
                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = { joinCodeInput = it.uppercase().take(6) },
                            label = { Text("Код комнаты (напр. ABC12)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (onlineError != null) {
                            Text(onlineError!!, color = Color(0xFFD90429), fontSize = 12.sp)
                        }
                    } else {
                        val gameDef = GameId.fromId(activeRoom.gameId)?.let { GameCatalog.get(it) }
                        Text("Игра: ${gameDef?.title?.replace("\n", " ") ?: activeRoom.gameId}", fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PlayBoxColors.CreamBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clipboard.setText(AnnotatedString(activeRoom.code)) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Код для друга (нажмите, чтобы скопировать)", fontSize = 11.sp, color = PlayBoxColors.MutedInk)
                                    Text(activeRoom.code, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                                }
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Копировать")
                            }
                        }
                        activeRoom.players.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (p.connected) PlayBoxColors.SuccessGreen else Color.Gray)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Игрок ${p.index}: ${p.name}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (!activeRoom.bothConnected) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Ждём второго игрока...", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (activeRoom == null) {
                    Button(
                        enabled = joinCodeInput.length >= 4 && !isOnlineBusy,
                        onClick = {
                            isOnlineBusy = true
                            onlineError = null
                            scope.launch {
                                val res = onlineClient.joinRoom(prefs.serverUrl, joinCodeInput, prefs.playerName)
                                isOnlineBusy = false
                                res.onSuccess { join ->
                                    onlineClient.connect(prefs.serverUrl, join, prefs.playerName)
                                    val gId = GameId.fromId(join.gameId) ?: GameId.TICTACTOE
                                    showOnlineDialog = false
                                    onLaunchGame(
                                        LaunchConfig(
                                            gameId = gId,
                                            mode = PlayMode.ONLINE,
                                            roomCode = join.code,
                                            playerIndex = join.playerIndex,
                                            seed = join.seed
                                        )
                                    )
                                }.onFailure { err ->
                                    onlineError = err.message ?: "Комната не найдена"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PlayBoxColors.OnlinePurple)
                    ) {
                        Text("Войти", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        enabled = activeRoom.bothConnected || GameId.fromId(activeRoom.gameId)?.let { !GameCatalog.get(it).isDuelOnline } == true,
                        onClick = {
                            val gId = GameId.fromId(activeRoom.gameId) ?: GameId.TICTACTOE
                            showOnlineDialog = false
                            onLaunchGame(
                                LaunchConfig(
                                    gameId = gId,
                                    mode = PlayMode.ONLINE,
                                    roomCode = activeRoom.code,
                                    playerIndex = activeRoom.playerIndex,
                                    seed = activeRoom.seed,
                                    startingPlayer = activeRoom.startingPlayer
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PlayBoxColors.OnlinePurple)
                    ) {
                        Text("Начать игру", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (activeRoom != null) onlineClient.disconnect()
                    showOnlineDialog = false
                }) {
                    Text(if (activeRoom != null) "Выйти из комнаты" else "Закрыть")
                }
            }
        )
    }

    // Settings / Profile Dialog
    if (showSettingsDialog) {
        var nameInput by remember { mutableStateOf(prefs.playerName) }
        var serverInput by remember { mutableStateOf(prefs.serverUrl) }
        var haptics by remember { mutableStateOf(prefs.hapticsEnabled) }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = PlayBoxColors.HeaderSurface,
            title = { Text("Профиль и настройки", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Никнейм в онлайне") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        label = { Text("URL сервера") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { serverInput = PlayerPrefs.DEFAULT_SERVER_URL }) {
                            Text("Внешний HTTPS", fontSize = 11.sp)
                        }
                        TextButton(onClick = { serverInput = PlayerPrefs.LAN_SERVER_URL }) {
                            Text("Локальный LAN", fontSize = 11.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Виброотклик", fontWeight = FontWeight.SemiBold)
                        Switch(checked = haptics, onCheckedChange = { haptics = it })
                    }
                    Text(
                        text = "Всего побед: ${prefs.totalWins} · Рекорд 2048: ${prefs.getBestScore(GameId.GAME_2048)} · Змейка: ${prefs.getBestScore(GameId.SNAKE)}",
                        fontSize = 12.sp,
                        color = PlayBoxColors.MutedInk
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.playerName = nameInput
                        prefs.serverUrl = serverInput
                        prefs.hapticsEnabled = haptics
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PlayBoxColors.OnlinePurple)
                ) {
                    Text("Сохранить", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun GameGridCard(
    game: GameDefinition,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.84f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(4.dp, Color.White, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            color = game.cardBg,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Text(
                    text = game.title,
                    color = game.titleColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(game.accentColor)
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    GameCardArtwork(gameId = game.id, modifier = Modifier.fillMaxSize())
                }
            }
        }

        if (game.badge != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-4).dp, y = (-10).dp),
                shape = RoundedCornerShape(10.dp),
                color = game.badgeColor,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = game.badge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
