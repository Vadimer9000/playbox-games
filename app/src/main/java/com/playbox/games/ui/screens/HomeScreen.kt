package com.playbox.games.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.playbox.games.model.GameRules
import com.playbox.games.model.LaunchConfig
import com.playbox.games.model.PlayMode
import com.playbox.games.network.OnlineClient
import com.playbox.games.network.OnlineRoomState
import com.playbox.games.ui.components.GameCardArtwork
import com.playbox.games.ui.components.PlayBoxButton
import com.playbox.games.ui.components.PlayBoxOutlinedButton
import com.playbox.games.ui.theme.PlayBoxColors
import com.playbox.games.ui.theme.bounceClick
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
        // Branded Top Header with PlayBox Logo, Wins Counter & Online Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PlayBoxColors.HeaderSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .bounceClick { showSettingsDialog = true }
                    .padding(end = 6.dp)
            ) {
                PlayBoxLogoBadge(modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "PlayBox",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = PlayBoxColors.DarkInk,
                        maxLines = 1
                    )
                    Text(
                        text = prefs.playerName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PlayBoxColors.MutedInk,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Wins / Bolt counter pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFECC2),
                modifier = Modifier.bounceClick { showSettingsDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(PlayBoxColors.BoltGold)
                            .border(1.5.dp, PlayBoxColors.DarkInk, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = PlayBoxColors.DarkInk, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "$totalWins",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = PlayBoxColors.DarkInk,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Online Hub pill button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PlayBoxColors.OnlinePurple,
                shadowElevation = 3.dp,
                modifier = Modifier.bounceClick {
                    onlineError = null
                    showOnlineDialog = true
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (roomState?.isConnected == true) "КОД ${roomState.code}" else "ОНЛАЙН",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Search + Category filter chips
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Найти игру из 54...", fontSize = 14.sp) },
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
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = GameRules.forGame(sheetGame.id),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = PlayBoxColors.MutedInk
                )

                Spacer(Modifier.height(14.dp))

                if (sheetGame.supportsBot) {
                    Text("Сложность бота", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PlayBoxColors.DarkInk)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotDifficulty.entries.forEach { diff ->
                            FilterChip(
                                selected = selectedDifficulty == diff,
                                onClick = { selectedDifficulty = diff },
                                label = { Text(diff.label, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // 1. Solo / Bot
                PlayBoxButton(
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
                    containerColor = Color(0xFF118AB2)
                ) {
                    Icon(if (sheetGame.supportsBot) Icons.Rounded.SmartToy else Icons.Rounded.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (sheetGame.supportsBot) "Против бота (${selectedDifficulty.label})" else "Одиночная игра",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (sheetGame.supportsLocal2P) {
                    Spacer(Modifier.height(10.dp))
                    PlayBoxButton(
                        onClick = {
                            activeGameSheet = null
                            onLaunchGame(LaunchConfig(gameId = sheetGame.id, mode = PlayMode.LOCAL_2P))
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        containerColor = Color(0xFFEF476F)
                    ) {
                        Icon(Icons.Rounded.People, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Вдвоём на одном экране", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false)
                    }
                }

                if (sheetGame.supportsOnline) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PlayBoxButton(
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
                            containerColor = PlayBoxColors.OnlinePurple,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Text("Создать комнату", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }

                        PlayBoxOutlinedButton(
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
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Text("Быстрый матч", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, softWrap = false)
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

    // Online Room Lobby Dialog
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
                    PlayBoxButton(
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
                        }
                    ) {
                        Text("Войти", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    PlayBoxButton(
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
                        }
                    ) {
                        Text("Начать игру", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (activeRoom != null) onlineClient.disconnect()
                    showOnlineDialog = false
                }) {
                    Text(if (activeRoom != null) "Выйти" else "Закрыть")
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
                        PlayBoxOutlinedButton(onClick = { serverInput = PlayerPrefs.DEFAULT_SERVER_URL }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                            Text("Внешний HTTPS", fontSize = 11.sp, maxLines = 1)
                        }
                        PlayBoxOutlinedButton(onClick = { serverInput = PlayerPrefs.LAN_SERVER_URL }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                            Text("Локальный LAN", fontSize = 11.sp, maxLines = 1)
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
                PlayBoxButton(
                    onClick = {
                        prefs.playerName = nameInput
                        prefs.serverUrl = serverInput
                        prefs.hapticsEnabled = haptics
                        showSettingsDialog = false
                    }
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold, maxLines = 1)
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
fun PlayBoxLogoBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF7B2CBF), Color(0xFFF72585), Color(0xFFFF7A2F))
            )
        )
        val pad = size.minDimension * 0.16f
        drawRoundRect(
            color = Color(0xFFFFF8EE),
            topLeft = Offset(pad, pad * 1.15f),
            size = Size(size.width - pad * 2, size.height - pad * 2.3f),
            cornerRadius = CornerRadius(6.dp.toPx())
        )
        // Mini D-pad
        val dCol = Color(0xFF232733)
        drawRect(dCol, Offset(size.width * 0.25f, size.height * 0.45f), Size(size.width * 0.20f, size.height * 0.10f))
        drawRect(dCol, Offset(size.width * 0.31f, size.height * 0.37f), Size(size.width * 0.08f, size.height * 0.26f))
        // Mini colorful buttons
        drawCircle(Color(0xFF38BDF8), size.minDimension * 0.055f, Offset(size.width * 0.67f, size.height * 0.40f))
        drawCircle(Color(0xFFEF476F), size.minDimension * 0.055f, Offset(size.width * 0.59f, size.height * 0.50f))
        drawCircle(Color(0xFF06D6A0), size.minDimension * 0.055f, Offset(size.width * 0.75f, size.height * 0.50f))
        drawCircle(Color(0xFFFFCA3A), size.minDimension * 0.055f, Offset(size.width * 0.67f, size.height * 0.60f))
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
            .bounceClick(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(4.dp, Color.White, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = game.cardBg,
            shadowElevation = 5.dp
        ) {
            Box(Modifier.fillMaxSize()) {
                // Subtle top glossy highlight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Text(
                        text = game.title,
                        color = game.titleColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
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
        }

        if (game.badge != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = game.badgeColor,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = game.badge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
