package com.playbox.games.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.model.GameId

@Composable
fun GameCardArtwork(gameId: GameId, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        when (gameId) {
            GameId.TICTACTOE -> TicTacToeArt()
            GameId.CONNECT4 -> Connect4Art()
            GameId.BATTLESHIP -> BattleshipArt()
            GameId.PING_PONG -> PingPongArt()
            GameId.CHECKERS -> CheckersArt()
            GameId.REVERSI -> ReversiArt()
            GameId.MANCALA -> MancalaArt()
            GameId.DOTS_BOXES -> DotsBoxesArt()
            GameId.GAME_2048 -> Art2048()
            GameId.MINESWEEPER -> MinesweeperArt()
            GameId.WORDLE -> WordleArt()
            GameId.SNAKE -> SnakeArt()
        }
    }
}

@Composable
private fun TicTacToeArt() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-14f, pivot = Offset(size.width * 0.55f, size.height * 0.58f)) {
            val left = size.width * 0.16f
            val top = size.height * 0.16f
            val side = size.minDimension * 0.72f
            val cell = side / 3f
            val gridColor = Color(0xFF14181F)
            val stroke = 6.dp.toPx()

            for (i in 1..2) {
                drawLine(gridColor, Offset(left + cell * i, top), Offset(left + cell * i, top + side), stroke, StrokeCap.Round)
                drawLine(gridColor, Offset(left, top + cell * i), Offset(left + side, top + cell * i), stroke, StrokeCap.Round)
            }
            fun drawX(cx: Float, cy: Float, r: Float) {
                drawLine(Color(0xFF14181F), Offset(cx - r, cy - r), Offset(cx + r, cy + r), stroke * 1.9f, StrokeCap.Round)
                drawLine(Color(0xFF14181F), Offset(cx + r, cy - r), Offset(cx - r, cy + r), stroke * 1.9f, StrokeCap.Round)
                drawLine(Color(0xFFFF595E), Offset(cx - r, cy - r), Offset(cx + r, cy + r), stroke * 1.0f, StrokeCap.Round)
                drawLine(Color(0xFFFF595E), Offset(cx + r, cy - r), Offset(cx - r, cy + r), stroke * 1.0f, StrokeCap.Round)
            }
            drawX(left + cell * 1.5f, top + cell * 1.5f, cell * 0.28f)
            drawX(left + cell * 2.5f, top + cell * 2.5f, cell * 0.28f)
            val oc = Offset(left + cell * 2.5f, top + cell * 1.5f)
            drawCircle(Color(0xFF14181F), cell * 0.30f, oc, style = Stroke(stroke * 1.9f))
            drawCircle(Color(0xFF0077B6), cell * 0.30f, oc, style = Stroke(stroke * 1.0f))
        }
    }
}

@Composable
private fun Connect4Art() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-14f, pivot = Offset(size.width * 0.5f, size.height * 0.6f)) {
            val w = size.width * 0.86f
            val h = size.height * 0.74f
            val left = size.width * 0.06f
            val top = size.height * 0.16f
            drawRoundRect(
                color = Color(0xFF1E2D3B),
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(14.dp.toPx())
            )
            val cols = 5
            val rows = 4
            val cw = w / (cols + 0.5f)
            val ch = h / (rows + 0.5f)
            val r = minOf(cw, ch) * 0.36f
            val coral = Color(0xFFFF6B6B)
            val cyan = Color(0xFF38BDF8)
            val hole = Color(0xFF34495E)
            val map = listOf(
                0, 0, 1, 0, 0,
                0, 1, 0, 2, 0,
                0, 2, 1, 0, 1,
                1, 2, 2, 1, 2
            )
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val v = map[row * cols + col]
                    val c = when (v) {
                        1 -> coral
                        2 -> cyan
                        else -> hole
                    }
                    drawCircle(
                        color = c,
                        radius = r,
                        center = Offset(left + cw * (col + 0.75f), top + ch * (row + 0.75f))
                    )
                }
            }
        }
    }
}

@Composable
private fun BattleshipArt() {
    Canvas(Modifier.fillMaxSize()) {
        val cols = 5
        val rows = 4
        val cell = size.width / cols
        val grid = Color(0xFF1E607A)
        val sea = Color(0xFF287C9C)
        drawRect(sea, topLeft = Offset(0f, size.height * 0.08f), size = Size(size.width, size.height * 0.92f))
        for (i in 0..cols) {
            drawLine(grid, Offset(i * cell, size.height * 0.08f), Offset(i * cell, size.height), 1.5.dp.toPx())
        }
        for (j in 0..rows) {
            val y = size.height * 0.08f + j * (size.height * 0.23f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
        }
        // Ship silhouette
        drawRoundRect(
            color = Color(0xFFE63946),
            topLeft = Offset(size.width * 0.14f, size.height * 0.36f),
            size = Size(size.width * 0.72f, size.height * 0.16f),
            cornerRadius = CornerRadius(12.dp.toPx())
        )
        // Explosion star
        val cx = size.width * 0.58f
        val cy = size.height * 0.44f
        drawCircle(Color.White, 16.dp.toPx(), Offset(cx, cy))
        drawCircle(Color(0xFF1D2432), 8.dp.toPx(), Offset(cx, cy))
        // Cross miss
        val mx = size.width * 0.80f
        val my = size.height * 0.78f
        val d = 8.dp.toPx()
        drawLine(Color(0xFF00B4D8), Offset(mx - d, my - d), Offset(mx + d, my + d), 4.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFF00B4D8), Offset(mx + d, my - d), Offset(mx - d, my + d), 4.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun PingPongArt() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-22f, pivot = center) {
            val tableW = size.width * 0.88f
            val tableH = size.height * 0.56f
            val left = (size.width - tableW) / 2f
            val top = (size.height - tableH) / 2f + 10.dp.toPx()
            drawRect(Color(0xFF2B2D42), Offset(left - 4.dp.toPx(), top - 4.dp.toPx()), Size(tableW + 8.dp.toPx(), tableH + 8.dp.toPx()))
            drawRect(Color(0xFF52B788), Offset(left, top), Size(tableW, tableH))
            drawLine(Color.White, Offset(left + tableW / 2f, top), Offset(left + tableW / 2f, top + tableH), 2.5.dp.toPx())
            // Ball & trail
            drawCircle(Color.White.copy(alpha = 0.45f), 11.dp.toPx(), Offset(left + tableW * 0.48f, top + tableH * 0.55f))
            drawCircle(Color(0xFFFFD166), 8.dp.toPx(), Offset(left + tableW * 0.62f, top + tableH * 0.30f))
            drawCircle(Color(0xFF2B2D42), 8.dp.toPx(), Offset(left + tableW * 0.62f, top + tableH * 0.30f), style = Stroke(2.5.dp.toPx()))
            // Paddles
            drawCircle(Color(0xFF00BBF9), 15.dp.toPx(), Offset(left + tableW * 0.14f, top + tableH * 0.72f))
            drawCircle(Color(0xFFEF476F), 15.dp.toPx(), Offset(left + tableW * 0.88f, top + tableH * 0.28f))
        }
    }
}

@Composable
private fun CheckersArt() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-14f, pivot = Offset(size.width * 0.55f, size.height * 0.55f)) {
            val s = size.minDimension * 0.92f
            val cell = s / 4f
            val ox = size.width * 0.05f
            val oy = size.height * 0.14f
            for (r in 0..3) {
                for (c in 0..3) {
                    val dark = (r + c) % 2 == 1
                    drawRect(
                        color = if (dark) Color(0xFFB59672) else Color(0xFFEDE0C8),
                        topLeft = Offset(ox + c * cell, oy + r * cell),
                        size = Size(cell, cell)
                    )
                }
            }
            val pr = cell * 0.34f
            drawCircle(Color(0xFF2E3239), pr, Offset(ox + 1.5f * cell, oy + 0.5f * cell))
            drawCircle(Color(0xFF495057), pr * 0.75f, Offset(ox + 1.5f * cell, oy + 0.5f * cell))
            drawCircle(Color(0xFF2E3239), pr, Offset(ox + 0.5f * cell, oy + 1.5f * cell))
            drawCircle(Color(0xFF1F242D), pr, Offset(ox + 2.5f * cell, oy + 1.5f * cell))
            drawCircle(Color.White, pr, Offset(ox + 1.5f * cell, oy + 2.5f * cell))
            drawCircle(Color(0xFF2B2D42), pr, Offset(ox + 1.5f * cell, oy + 2.5f * cell), style = Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun ReversiArt() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-10f, pivot = Offset(size.width * 0.55f, size.height * 0.55f)) {
            val s = size.minDimension * 0.9f
            val cell = s / 5f
            val ox = size.width * 0.08f
            val oy = size.height * 0.14f
            drawRect(Color(0xFF2B9348), Offset(ox, oy), Size(s, s))
            for (i in 0..5) {
                drawLine(Color(0xFF1B4332), Offset(ox + i * cell, oy), Offset(ox + i * cell, oy + s), 1.5.dp.toPx())
                drawLine(Color(0xFF1B4332), Offset(ox, oy + i * cell), Offset(ox + s, oy + i * cell), 1.5.dp.toPx())
            }
            val r = cell * 0.38f
            val blacks = listOf(1 to 1, 2 to 1, 0 to 2, 3 to 2, 4 to 3)
            val whites = listOf(2 to 2, 1 to 3, 2 to 3, 3 to 3, 1 to 4)
            blacks.forEach { (c, row) ->
                drawCircle(Color(0xFF1F242D), r, Offset(ox + (c + 0.5f) * cell, oy + (row + 0.5f) * cell))
            }
            whites.forEach { (c, row) ->
                drawCircle(Color.White, r, Offset(ox + (c + 0.5f) * cell, oy + (row + 0.5f) * cell))
            }
        }
    }
}

@Composable
private fun MancalaArt() {
    Canvas(Modifier.fillMaxSize()) {
        val left = size.width * 0.08f
        val top = size.height * 0.22f
        val w = size.width * 0.88f
        val h = size.height * 0.64f
        drawRoundRect(Color(0xFF202531), Offset(left - 3.dp.toPx(), top - 3.dp.toPx()), Size(w + 6.dp.toPx(), h + 6.dp.toPx()), CornerRadius(18.dp.toPx()))
        drawRoundRect(Color(0xFFF6C26B), Offset(left, top), Size(w, h), CornerRadius(16.dp.toPx()))
        // Store left
        drawRoundRect(Color(0xFF28A5D8), Offset(left + 8.dp.toPx(), top + 8.dp.toPx()), Size(w * 0.18f, h - 16.dp.toPx()), CornerRadius(12.dp.toPx()))
        val pitStart = left + w * 0.30f
        val pitStep = (w * 0.64f) / 3f
        val pitR = minOf(pitStep, h / 2f) * 0.35f
        for (c in 0..2) {
            val cx = pitStart + c * pitStep + pitR
            drawCircle(Color(0xFF28A5D8), pitR, Offset(cx, top + h * 0.28f))
            drawCircle(Color(0xFFE76F51), pitR, Offset(cx, top + h * 0.72f))
            // Seeds
            drawCircle(Color(0xFFF4EFE6), 3.dp.toPx(), Offset(cx - 3.dp.toPx(), top + h * 0.28f))
            drawCircle(Color(0xFFF4EFE6), 3.dp.toPx(), Offset(cx + 4.dp.toPx(), top + h * 0.28f + 2.dp.toPx()))
            drawCircle(Color(0xFFF4EFE6), 3.dp.toPx(), Offset(cx, top + h * 0.72f))
        }
    }
}

@Composable
private fun DotsBoxesArt() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-10f, pivot = center) {
            val s = size.minDimension * 0.84f
            val step = s / 3f
            val ox = size.width * 0.14f
            val oy = size.height * 0.14f
            // Filled box
            drawRect(Color(0xFFF25C54), Offset(ox, oy + step), Size(step, step))
            // Lines
            drawLine(Color(0xFFF25C54), Offset(ox, oy), Offset(ox + step, oy), 5.dp.toPx(), StrokeCap.Round)
            drawLine(Color(0xFFF25C54), Offset(ox + step, oy), Offset(ox + step, oy + step), 5.dp.toPx(), StrokeCap.Round)
            drawLine(Color(0xFF00A8E8), Offset(ox + step, oy + step), Offset(ox + 2 * step, oy + step), 5.dp.toPx(), StrokeCap.Round)
            // Dots
            for (r in 0..3) {
                for (c in 0..3) {
                    drawCircle(Color(0xFF14181F), 5.5.dp.toPx(), Offset(ox + c * step, oy + r * step))
                }
            }
        }
    }
}

@Composable
private fun Art2048() {
    Box(Modifier.fillMaxSize().rotate(-12f).padding(top = 12.dp, start = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTile("256", Color(0xFFF4C56A), Color.White)
                MiniTile("2048", Color(0xFFFAB824), Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTile("128", Color(0xFFF6D28B), Color.White)
                MiniTile("64", Color(0xFFF4845F), Color.White)
            }
        }
    }
}

@Composable
private fun MiniTile(label: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@Composable
private fun MinesweeperArt() {
    Box(Modifier.fillMaxSize().rotate(-10f).padding(start = 10.dp, top = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MineTile("🚩", Color(0xFFF1F5F9), Color.Red)
                MineTile("3", Color(0xFF38424D), Color(0xFFEF4444))
                MineTile("🚩", Color(0xFFF1F5F9), Color.Red)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MineTile("💣", Color(0xFFF1F5F9), Color.Black)
                MineTile("1", Color(0xFF38424D), Color(0xFF38BDF8))
                MineTile("2", Color(0xFF38424D), Color(0xFF4ADE80))
            }
        }
    }
}

@Composable
private fun MineTile(txt: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(txt, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@Composable
private fun WordleArt() {
    Box(Modifier.fillMaxSize().rotate(-10f).padding(start = 8.dp, top = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WordTile("С", Color(0xFF3D405B))
                WordTile("Л", Color(0xFFF4A261))
                WordTile("О", Color(0xFF3D405B))
                WordTile("В", Color(0xFF40916C))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WordTile("И", Color(0xFF40916C))
                WordTile("Г", Color(0xFF40916C))
                WordTile("Р", Color(0xFF40916C))
                WordTile("А", Color(0xFF40916C))
            }
        }
    }
}

@Composable
private fun WordTile(ch: String, bg: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(ch, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@Composable
private fun SnakeArt() {
    Canvas(Modifier.fillMaxSize()) {
        val segs = listOf(
            0.35f to 0.20f, 0.50f to 0.20f, 0.65f to 0.20f, 0.78f to 0.20f,
            0.78f to 0.36f, 0.78f to 0.52f, 0.78f to 0.68f, 0.78f to 0.84f,
            0.62f to 0.84f, 0.46f to 0.84f, 0.30f to 0.84f, 0.20f to 0.70f
        )
        val r = 10.dp.toPx()
        segs.forEachIndexed { idx, (fx, fy) ->
            val p = Offset(size.width * fx, size.height * fy)
            drawCircle(Color(0xFF2D9D4E), r, p)
            drawCircle(Color(0xFF49C66D), r * 0.75f, p)
            if (idx == 0) {
                drawCircle(Color.White, 4.dp.toPx(), Offset(p.x - 3.dp.toPx(), p.y - 3.dp.toPx()))
                drawCircle(Color.Black, 2.dp.toPx(), Offset(p.x - 3.dp.toPx(), p.y - 3.dp.toPx()))
                drawCircle(Color.White, 4.dp.toPx(), Offset(p.x - 3.dp.toPx(), p.y + 3.dp.toPx()))
                drawCircle(Color.Black, 2.dp.toPx(), Offset(p.x - 3.dp.toPx(), p.y + 3.dp.toPx()))
            }
        }
        // Apple
        val apple = Offset(size.width * 0.20f, size.height * 0.44f)
        drawCircle(Color(0xFFE63946), 10.dp.toPx(), apple)
        drawCircle(Color(0xFFFFB5A7), 3.dp.toPx(), Offset(apple.x - 3.dp.toPx(), apple.y - 3.dp.toPx()))
    }
}
