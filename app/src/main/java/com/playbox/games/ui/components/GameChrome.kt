package com.playbox.games.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.model.BotDifficulty
import com.playbox.games.model.GameId
import com.playbox.games.model.GameRules
import com.playbox.games.model.PlayMode
import com.playbox.games.network.OnlineRoomState
import com.playbox.games.ui.theme.PlayBoxColors
import com.playbox.games.ui.theme.bounceClick

@Composable
fun PlayBoxButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = PlayBoxColors.OnlinePurple,
    contentColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        content = content
    )
}

@Composable
fun PlayBoxOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = PlayBoxColors.DarkInk,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = contentPadding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        content = content
    )
}

@Composable
fun GameTopBar(
    gameId: GameId,
    title: String,
    mode: PlayMode,
    difficulty: BotDifficulty,
    roomState: OnlineRoomState?,
    opponentEmoji: String?,
    themeBg: Color = PlayBoxColors.HeaderSurface,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onSendEmoji: (String) -> Unit
) {
    var showRules by remember { mutableStateOf(false) }
    val isDark = themeBg != PlayBoxColors.HeaderSurface && themeBg.luminance() < 0.36f
    val barBg = if (isDark) themeBg else PlayBoxColors.HeaderSurface
    val btnBg = if (isDark) Color.White.copy(alpha = 0.14f) else PlayBoxColors.CreamBg
    val backBg = if (isDark) Color.White else PlayBoxColors.CreamBg
    val backTint = if (isDark) Color(0xFFE0526D) else PlayBoxColors.DarkInk
    val iconTint = if (isDark) Color.White else PlayBoxColors.DarkInk
    val titleColor = if (isDark) Color.White else PlayBoxColors.DarkInk
    val subColor = if (isDark) Color.White.copy(alpha = 0.72f) else PlayBoxColors.MutedInk

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBg)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(backBg)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = backTint)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.replace("\n", " "),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when (mode) {
                    PlayMode.SOLO -> "Одиночная · ${difficulty.label}"
                    PlayMode.LOCAL_2P -> "2 игрока · На одном экране"
                    PlayMode.ONLINE -> {
                        val code = roomState?.code ?: ""
                        val opp = roomState?.opponent?.name ?: "Ждём соперника..."
                        "Комната $code · $opp"
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (mode == PlayMode.ONLINE && roomState != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (roomState.isConnected) Color(0xFFE6F9F0) else Color(0xFFFFEAEA)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (roomState.isConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = if (roomState.isConnected) Color(0xFF0A8754) else Color(0xFFD90429),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (roomState.latencyMs > 0) "${roomState.latencyMs}мс" else roomState.code,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PlayBoxColors.DarkInk,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButton(
                onClick = { showRules = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(btnBg)
            ) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "Правила", tint = iconTint)
            }
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onRestart,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(btnBg)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Заново", tint = iconTint)
            }
        }

        if (mode == PlayMode.ONLINE) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val emojis = listOf("🔥", "👏", "😂", "🤔", "😱", "🏆")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    emojis.forEach { e ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(btnBg)
                                .bounceClick { onSendEmoji(e) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(e, fontSize = 15.sp)
                        }
                    }
                }
                if (roomState != null) {
                    Text(
                        text = "Счёт ${roomState.scoreP1} : ${roomState.scoreP2}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = if (isDark) PlayBoxColors.BoltGold else PlayBoxColors.OnlinePurple,
                        maxLines = 1
                    )
                }
            }
        }

        AnimatedVisibility(visible = opponentEmoji != null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            if (opponentEmoji != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PlayBoxColors.OnlinePurple,
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = "Соперник: $opponentEmoji",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            containerColor = PlayBoxColors.HeaderSurface,
            title = { Text("Правила: ${title.replace("\n", " ")}", fontWeight = FontWeight.ExtraBold) },
            text = { Text(GameRules.forGame(gameId), fontSize = 14.sp, lineHeight = 20.sp, color = PlayBoxColors.DarkInk) },
            confirmButton = {
                PlayBoxButton(onClick = { showRules = false }) {
                    Text("Понятно", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        )
    }
}

@Composable
fun GameResultBanner(
    title: String,
    subtitle: String,
    mode: PlayMode,
    roomState: OnlineRoomState?,
    onRematch: () -> Unit,
    onExit: () -> Unit,
    rematchLabelOverride: String? = null
) {
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(initialScale = 0.88f) + fadeIn()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .border(2.5.dp, Color.White, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = PlayBoxColors.HeaderSurface,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PlayBoxColors.BoltGold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = PlayBoxColors.DarkInk, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PlayBoxColors.DarkInk, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = PlayBoxColors.MutedInk, maxLines = 2)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PlayBoxOutlinedButton(
                        onClick = onExit,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text("В меню", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    }
                    val rematchLabel = if (mode == PlayMode.ONLINE) {
                        val votes = roomState?.rematchVotes?.size ?: 0
                        val myVoted = roomState?.rematchVotes?.contains(roomState.playerIndex) == true
                        if (myVoted) "Ждём ($votes/2)" else "Реванш ($votes/2)"
                    } else {
                        rematchLabelOverride ?: "Ещё раз"
                    }
                    PlayBoxButton(
                        onClick = onRematch,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text(rematchLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
