package com.playbox.games.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.model.BotDifficulty
import com.playbox.games.model.PlayMode
import com.playbox.games.network.OnlineRoomState
import com.playbox.games.ui.theme.PlayBoxColors

@Composable
fun GameTopBar(
    title: String,
    mode: PlayMode,
    difficulty: BotDifficulty,
    roomState: OnlineRoomState?,
    opponentEmoji: String?,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onSendEmoji: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlayBoxColors.HeaderSurface)
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
                    .background(PlayBoxColors.CreamBg)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = PlayBoxColors.DarkInk)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.replace("\n", " "),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PlayBoxColors.DarkInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when (mode) {
                    PlayMode.SOLO -> "Одиночная · ${difficulty.label}"
                    PlayMode.LOCAL_2P -> "2 игрока · На одном экране"
                    PlayMode.ONLINE -> {
                        val code = roomState?.code ?: ""
                        val opp = roomState?.opponent?.name ?: "Ожидание соперника..."
                        "Комната $code · $opp"
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = PlayBoxColors.MutedInk,
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
                            color = PlayBoxColors.DarkInk
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButton(
                onClick = onRestart,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PlayBoxColors.CreamBg)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Заново", tint = PlayBoxColors.DarkInk)
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
                                .background(PlayBoxColors.CreamBg)
                                .clickable { onSendEmoji(e) }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
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
                        color = PlayBoxColors.OnlinePurple
                    )
                }
            }
        }

        AnimatedVisibility(visible = opponentEmoji != null, enter = fadeIn(), exit = fadeOut()) {
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
}

@Composable
fun GameResultBanner(
    title: String,
    subtitle: String,
    mode: PlayMode,
    roomState: OnlineRoomState?,
    onRematch: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(2.dp, Color.White, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = PlayBoxColors.HeaderSurface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PlayBoxColors.DarkInk)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 14.sp, color = PlayBoxColors.MutedInk)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("В меню", fontWeight = FontWeight.Bold)
                }
                val rematchLabel = if (mode == PlayMode.ONLINE) {
                    val votes = roomState?.rematchVotes?.size ?: 0
                    val myVoted = roomState?.rematchVotes?.contains(roomState.playerIndex) == true
                    if (myVoted) "Ждём ($votes/2)" else "Реванш ($votes/2)"
                } else {
                    "Ещё раз"
                }
                Button(
                    onClick = onRematch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PlayBoxColors.OnlinePurple)
                ) {
                    Text(rematchLabel, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
