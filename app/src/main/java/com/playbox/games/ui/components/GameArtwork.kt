package com.playbox.games.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
            GameId.NUTS_BOLTS -> NutsBoltsArt()
            GameId.PETDOKU -> PetdokuArt()
            GameId.MULTI_SNAKES -> MultiSnakesArt()
            GameId.ARROWS_PUZZLE -> ArrowsArt()
            GameId.PAINT_MAZE -> PaintMazeArt()
            GameId.SHIKAKU -> ShikakuArt()
            GameId.NUMBER_MATCH -> NumberMatchArt()
            GameId.COLOR_BLOCKS -> ColorBlocksArt()
            GameId.GUESS_WHO -> GuessWhoArt()
            GameId.FRUIT_MERGE -> FruitMergeArt()
            GameId.ESCAPE_PARKING -> EscapeArt()
            GameId.BALL_RUN -> BallRunArt()
            GameId.MULTI_COLOR_FILL -> MultiFillArt()
            GameId.TOWER_STACK -> TowerArt()
            GameId.WATER_SORT -> WaterSortArt()
            GameId.ANIMAL_STACK -> AnimalStackArt()
            GameId.DOMINO -> DominoArt()
            GameId.FALLING_SAND -> SandArt()
            GameId.WORD_SEARCH -> WordWheelArt()
            GameId.ARCHERY -> ArcheryArt()
            GameId.DARTS -> DartsArt()
            GameId.DROP_MERGE -> DropMergeArt()
            GameId.CROSS_SUMS -> CrossSumsArt()
            GameId.CONNECT_2248 -> Connect2248Art()
            GameId.COLOR_CARDS -> ColorCardsArt()
            GameId.FLOW_COLORS -> FlowArt()
            GameId.FREECELL -> CardDeckArt("A♥", "2♦", Color(0xFFE63946))
            GameId.LUDO -> LudoArt()
            GameId.SOLITAIRE -> CardDeckArt("A♥", "4♠", Color(0xFF1B1F23))
            GameId.SUDOKU -> SudokuArt()
            GameId.HEARTS -> CardDeckArt("Q♠", "A♥", Color(0xFFE63946))
            GameId.REVERSI -> ReversiArt()
            GameId.TAP_MATCH -> TapMatchArt()
            GameId.BATTLESHIP -> BattleshipArt()
            GameId.SOCCER_POOL -> SoccerPoolArt()
            GameId.GAME_2048 -> Art2048()
            GameId.CONNECT4 -> Connect4Art()
            GameId.TICTACTOE -> TicTacToeArt()
            GameId.BACKGAMMON -> BackgammonArt()
            GameId.WORDLE -> WordleArt()
            GameId.MINESWEEPER -> MinesweeperArt()
            GameId.HANGMAN -> HangmanArt()
            GameId.SOUND_MEMORY -> SoundMemoryArt()
            GameId.SNAKE -> SnakeArt()
            GameId.CHESS -> ChessArt(isPuzzle = false)
            GameId.CHESS_PUZZLES -> ChessArt(isPuzzle = true)
            GameId.CHECKERS -> CheckersArt()
            GameId.BILLIARDS -> BilliardsArt()
            GameId.MEMORY_CARDS -> MemoryCardsArt()
            GameId.SLIDING_PUZZLE -> SliderArt()
            GameId.PING_PONG -> PingPongArt()
            GameId.WOOD_BLOCKS -> WoodBlocksArt()
            GameId.DOTS_BOXES -> DotsBoxesArt()
            GameId.MANCALA -> MancalaArt()
        }
    }
}

@Composable
private fun NutsBoltsArt() {
    Canvas(Modifier.fillMaxSize()) {
        val cols = listOf(0.22f, 0.50f, 0.78f)
        val colors = listOf(
            listOf(Color(0xFFEF476F), Color(0xFF38B000), Color(0xFF38B000)),
            listOf(Color(0xFFFFCA3A), Color(0xFF38B000)),
            listOf(Color(0xFFEF476F), Color(0xFF0096C7), Color(0xFFFFCA3A))
        )
        cols.forEachIndexed { i, fx ->
            val cx = size.width * fx
            drawRoundRect(Color(0xFFCBD5E1), Offset(cx - 5.dp.toPx(), size.height * 0.25f), Size(10.dp.toPx(), size.height * 0.65f), CornerRadius(4.dp.toPx()))
            colors[i].forEachIndexed { j, c ->
                val y = size.height * 0.80f - j * 13.dp.toPx()
                drawRoundRect(c, Offset(cx - 16.dp.toPx(), y), Size(32.dp.toPx(), 11.dp.toPx()), CornerRadius(4.dp.toPx()))
            }
        }
    }
}

@Composable
private fun PetdokuArt() {
    Box(Modifier.fillMaxSize().rotate(-12f).padding(top = 8.dp, start = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("✕", Color(0xFF38B000))
                MiniEmojiTile("🐮", Color(0xFFE0526D))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("🐱", Color(0xFFF4A261))
                MiniEmojiTile("🐶", Color(0xFF0096C7))
            }
        }
    }
}

@Composable
private fun MiniEmojiTile(txt: String, bg: Color) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(bg),
        contentAlignment = Alignment.Center
    ) { Text(txt, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
}

@Composable
private fun MultiSnakesArt() {
    Canvas(Modifier.fillMaxSize()) {
        val colors = listOf(Color(0xFFFF595E), Color(0xFFFFCA3A), Color(0xFF8AC926), Color(0xFF1982C4), Color(0xFF6A4C93))
        for (i in 0..5) {
            drawCircle(colors[i % colors.size], 11.dp.toPx(), Offset(size.width * (0.2f + i * 0.12f), size.height * (0.75f - i * 0.08f)))
            drawCircle(Color(0xFF00B4D8), 10.dp.toPx(), Offset(size.width * (0.8f - i * 0.06f), size.height * (0.25f + i * 0.08f)))
        }
        drawCircle(Color.White, 3.5.dp.toPx(), Offset(size.width * 0.80f, size.height * 0.35f))
        drawCircle(Color.Black, 1.8.dp.toPx(), Offset(size.width * 0.80f, size.height * 0.35f))
    }
}

@Composable
private fun ArrowsArt() {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 4.dp.toPx()
        drawLine(Color(0xFF2B2D42), Offset(size.width * 0.15f, size.height * 0.3f), Offset(size.width * 0.45f, size.height * 0.3f), stroke, StrokeCap.Round)
        drawLine(Color(0xFF2B2D42), Offset(size.width * 0.45f, size.height * 0.3f), Offset(size.width * 0.45f, size.height * 0.6f), stroke, StrokeCap.Round)
        drawLine(Color(0xFFFFBE0B), Offset(size.width * 0.65f, size.height * 0.8f), Offset(size.width * 0.65f, size.height * 0.2f), stroke * 1.3f, StrokeCap.Round)
        drawLine(Color(0xFF0096C7), Offset(size.width * 0.25f, size.height * 0.82f), Offset(size.width * 0.85f, size.height * 0.82f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun PaintMazeArt() {
    Canvas(Modifier.fillMaxSize()) {
        val s = size.minDimension * 0.78f
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        drawRect(Color(0xFF4B5563), Offset(ox, oy), Size(s, s))
        drawRect(Color(0xFFE0526D), Offset(ox, oy), Size(s * 0.66f, s * 0.34f))
        drawRect(Color(0xFFE0526D), Offset(ox, oy), Size(s * 0.34f, s))
        drawRect(Color(0xFF111827), Offset(ox + s * 0.34f, oy + s * 0.34f), Size(s * 0.32f, s * 0.32f))
        drawCircle(Color.White, 10.dp.toPx(), Offset(ox + s * 0.78f, oy + s * 0.82f))
    }
}

@Composable
private fun ShikakuArt() {
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1.5f).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF4A261)), Alignment.Center) {
                    Text("9", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2B2D42), fontSize = 18.sp)
                }
                Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1E8D6)), Alignment.Center) {
                    Text("3", fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFC8556B)), Alignment.Center) {
                    Text("4", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 18.sp)
                }
                Box(Modifier.weight(1.5f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE9C46A)), Alignment.Center) {
                    Text("5", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2B2D42), fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun NumberMatchArt() {
    Box(Modifier.fillMaxSize().padding(top = 6.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(listOf("2", "8", "4", "9"), listOf("4", "1", "2", "7"), listOf("3", "6", "8", "4")).forEachIndexed { r, row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEachIndexed { c, ch ->
                        val hi = (r == 1 && c == 3) || (r == 2 && c == 2)
                        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(5.dp)).background(if (hi) Color(0xFF38BDF8) else Color(0xFFF1F5F9)), Alignment.Center) {
                            Text(ch, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorBlocksArt() {
    Canvas(Modifier.fillMaxSize()) {
        val c = 20.dp.toPx()
        val ox = size.width * 0.16f
        val oy = size.height * 0.18f
        val cells = listOf(
            Triple(0, 1, Color(0xFF9B5DE5)), Triple(0, 2, Color(0xFF9B5DE5)), Triple(0, 3, Color(0xFF9B5DE5)),
            Triple(1, 0, Color(0xFFFEE440)), Triple(2, 0, Color(0xFFFEE440)), Triple(2, 1, Color(0xFFFEE440)),
            Triple(1, 2, Color(0xFF70E000)), Triple(1, 3, Color(0xFF70E000)), Triple(2, 3, Color(0xFF70E000)),
            Triple(3, 2, Color(0xFFF15BB5)), Triple(3, 3, Color(0xFFF15BB5))
        )
        cells.forEach { (col, row, color) ->
            drawRoundRect(color, Offset(ox + col * c, oy + row * c), Size(c - 2.dp.toPx(), c - 2.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
private fun GuessWhoArt() {
    Box(Modifier.fillMaxSize().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("👩", fontSize = 24.sp); Text("🧔", fontSize = 24.sp); Text("👧", fontSize = 24.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("👴", fontSize = 24.sp); Text("👵", fontSize = 24.sp); Text("🧑", fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun FruitMergeArt() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color(0xFF2D9D4E), 28.dp.toPx(), Offset(size.width * 0.65f, size.height * 0.62f))
        drawCircle(Color(0xFFFFD166), 16.dp.toPx(), Offset(size.width * 0.30f, size.height * 0.28f))
        drawCircle(Color(0xFFEF476F), 13.dp.toPx(), Offset(size.width * 0.22f, size.height * 0.65f))
        drawCircle(Color(0xFF3A86FF), 10.dp.toPx(), Offset(size.width * 0.82f, size.height * 0.24f))
    }
}

@Composable
private fun EscapeArt() {
    Canvas(Modifier.fillMaxSize()) {
        drawRoundRect(Color(0xFFE63946), Offset(size.width * 0.1f, size.height * 0.28f), Size(size.width * 0.42f, 20.dp.toPx()), CornerRadius(6.dp.toPx()))
        drawRoundRect(Color(0xFFEDE0C8), Offset(size.width * 0.58f, size.height * 0.16f), Size(20.dp.toPx(), size.height * 0.45f), CornerRadius(6.dp.toPx()))
        drawRoundRect(Color(0xFFEDE0C8), Offset(size.width * 0.15f, size.height * 0.68f), Size(size.width * 0.56f, 20.dp.toPx()), CornerRadius(6.dp.toPx()))
    }
}

@Composable
private fun BallRunArt() {
    Canvas(Modifier.fillMaxSize()) {
        drawLine(Color(0xFF3A86FF), Offset(0f, size.height), Offset(size.width * 0.42f, size.height * 0.15f), 2.dp.toPx())
        drawLine(Color(0xFF3A86FF), Offset(size.width, size.height), Offset(size.width * 0.58f, size.height * 0.15f), 2.dp.toPx())
        drawCircle(Color(0xFFC77DFF), 24.dp.toPx(), Offset(size.width * 0.58f, size.height * 0.60f))
        drawRect(Color(0xFFFF006E), Offset(size.width * 0.26f, size.height * 0.48f), Size(14.dp.toPx(), 14.dp.toPx()))
    }
}

@Composable
private fun MultiFillArt() {
    Canvas(Modifier.fillMaxSize()) {
        val c = 22.dp.toPx()
        val ox = size.width * 0.14f
        val oy = size.height * 0.18f
        val colors = listOf(Color(0xFFF6B756), Color(0xFF2EC4B6), Color(0xFFEF476F), Color(0xFF4B5563))
        for (r in 0..2) for (col in 0..3) {
            val cl = colors[(r + col) % 4]
            drawRoundRect(cl, Offset(ox + col * c, oy + r * c), Size(c - 3.dp.toPx(), c - 3.dp.toPx()), CornerRadius(5.dp.toPx()))
        }
    }
}

@Composable
private fun TowerArt() {
    Canvas(Modifier.fillMaxSize()) {
        val colors = listOf(Color(0xFF3A0CA3), Color(0xFF4361EE), Color(0xFF4CC9F0), Color(0xFF7209B7))
        for (i in 0..3) {
            val w = size.width * (0.68f - i * 0.08f)
            drawRoundRect(colors[i], Offset((size.width - w) / 2f, size.height * (0.78f - i * 0.16f)), Size(w, 14.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
private fun WaterSortArt() {
    Canvas(Modifier.fillMaxSize()) {
        val xs = listOf(0.28f, 0.54f, 0.80f)
        val fills = listOf(
            listOf(Color(0xFF3A86FF), Color(0xFFEF476F), Color(0xFFFFCA3A)),
            listOf(Color(0xFF38B000), Color(0xFFFFCA3A), Color(0xFFEF476F), Color(0xFF38B000)),
            listOf(Color(0xFF3A86FF), Color(0xFF38B000))
        )
        xs.forEachIndexed { idx, fx ->
            val left = size.width * fx - 12.dp.toPx()
            val top = size.height * 0.20f
            val h = size.height * 0.72f
            fills[idx].forEachIndexed { j, c ->
                val segH = h / 4.5f
                drawRect(c, Offset(left + 2.dp.toPx(), top + h - (j + 1) * segH), Size(20.dp.toPx(), segH))
            }
            drawRoundRect(Color.White, Offset(left, top), Size(24.dp.toPx(), h), CornerRadius(10.dp.toPx()), style = Stroke(2.5.dp.toPx()))
        }
    }
}

@Composable
private fun AnimalStackArt() {
    Box(Modifier.fillMaxSize().padding(bottom = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🦌", fontSize = 24.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Text("🐬", fontSize = 24.sp); Text("🐨", fontSize = 22.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("🐮", fontSize = 24.sp); Text("🐯", fontSize = 24.sp) }
            Box(Modifier.size(width = 96.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF52B788)))
        }
    }
}

@Composable
private fun DominoArt() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(width = 48.dp, height = 84.dp).rotate(-14f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFAF5E8)).border(2.dp, Color(0xFF2B2D42), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("6", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1E293B))
                Box(Modifier.size(width = 32.dp, height = 2.dp).background(Color(0xFF1E293B)))
                Text("6", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1E293B))
            }
        }
    }
}

@Composable
private fun SandArt() {
    Canvas(Modifier.fillMaxSize()) {
        val p1 = Path().apply { moveTo(0f, size.height); lineTo(size.width * 0.35f, size.height * 0.55f); lineTo(size.width * 0.7f, size.height); close() }
        val p2 = Path().apply { moveTo(size.width * 0.35f, size.height); lineTo(size.width * 0.75f, size.height * 0.62f); lineTo(size.width, size.height); close() }
        drawPath(p1, Color(0xFFFF006E))
        drawPath(p2, Color(0xFFFFBE0B))
        drawRect(Color(0xFFFF006E), Offset(size.width * 0.42f, size.height * 0.16f), Size(28.dp.toPx(), 12.dp.toPx()))
    }
}

@Composable
private fun WordWheelArt() {
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension * 0.38f
        drawCircle(Color.White.copy(alpha = 0.25f), r, center)
        val pts = listOf(Offset(center.x - r * 0.6f, center.y), Offset(center.x, center.y - r * 0.6f), Offset(center.x + r * 0.4f, center.y + r * 0.5f))
        drawLine(Color(0xFFFFCA3A), pts[0], pts[1], 4.dp.toPx())
        drawLine(Color(0xFFFFCA3A), pts[1], pts[2], 4.dp.toPx())
        pts.forEach { drawCircle(Color(0xFFFFCA3A), 11.dp.toPx(), it) }
    }
}

@Composable
private fun ArcheryArt() {
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension * 0.34f
        drawCircle(Color.White, r, center)
        drawCircle(Color(0xFF0096C7), r * 0.74f, center)
        drawCircle(Color(0xFFEF476F), r * 0.48f, center)
        drawCircle(Color(0xFFFFCA3A), r * 0.22f, center)
    }
}

@Composable
private fun DartsArt() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width * 0.52f, size.height * 0.62f)
        val r = size.minDimension * 0.44f
        drawCircle(Color(0xFF14181F), r, c)
        drawCircle(Color(0xFFEF476F), r * 0.85f, c, style = Stroke(5.dp.toPx()))
        drawCircle(Color(0xFF06D6A0), r * 0.55f, c, style = Stroke(5.dp.toPx()))
        drawCircle(Color(0xFFEF476F), r * 0.16f, c)
    }
}

@Composable
private fun DropMergeArt() {
    Box(Modifier.fillMaxSize().padding(8.dp), Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniEmojiTile("4", Color(0xFF9B5DE5))
            MiniEmojiTile("64", Color(0xFFF4A261))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("32", Color(0xFF38BDF8))
                MiniEmojiTile("2", Color(0xFFEF476F))
                MiniEmojiTile("8", Color(0xFFFFCA3A))
            }
        }
    }
}

@Composable
private fun CrossSumsArt() {
    Box(Modifier.fillMaxSize().rotate(-8f).padding(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("8", Color(0xFFE0F7FA).let { Color(0xFF0284C7) })
                MiniEmojiTile("6", Color(0xFF0284C7))
                MiniEmojiTile("4", Color(0xFF0284C7))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("1", Color(0xFF0369A1))
                MiniEmojiTile("5", Color(0xFF0284C7))
                MiniEmojiTile("2", Color(0xFF0284C7))
            }
        }
    }
}

@Composable
private fun Connect2248Art() {
    Box(Modifier.fillMaxSize().rotate(-10f).padding(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniEmojiTile("2", Color(0xFFEF476F))
                MiniEmojiTile("2", Color(0xFFEF476F))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniEmojiTile("8", Color(0xFF06D6A0))
                MiniEmojiTile("4", Color(0xFF38BDF8))
            }
        }
    }
}

@Composable
private fun ColorCardsArt() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 42.dp, height = 62.dp).rotate(-16f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF38BDF8)).border(2.dp, Color.White, RoundedCornerShape(8.dp)), Alignment.Center) {
            Text("+4", color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.size(width = 42.dp, height = 62.dp).rotate(0f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF06D6A0)).border(2.dp, Color.White, RoundedCornerShape(8.dp)), Alignment.Center) {
            Text("7", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Box(Modifier.size(width = 42.dp, height = 62.dp).rotate(14f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFCA3A)).border(2.dp, Color.White, RoundedCornerShape(8.dp)), Alignment.Center) {
            Text("8", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun FlowArt() {
    Canvas(Modifier.fillMaxSize()) {
        drawLine(Color(0xFF70E000), Offset(size.width * 0.25f, size.height * 0.25f), Offset(size.width * 0.25f, size.height * 0.8f), 8.dp.toPx(), StrokeCap.Round)
        drawCircle(Color(0xFF70E000), 10.dp.toPx(), Offset(size.width * 0.25f, size.height * 0.25f))
        drawCircle(Color(0xFF70E000), 10.dp.toPx(), Offset(size.width * 0.25f, size.height * 0.8f))
        drawLine(Color(0xFFFFBE0B), Offset(size.width * 0.55f, size.height * 0.65f), Offset(size.width * 0.8f, size.height * 0.3f), 8.dp.toPx(), StrokeCap.Round)
        drawCircle(Color(0xFFFFBE0B), 10.dp.toPx(), Offset(size.width * 0.55f, size.height * 0.65f))
        drawCircle(Color(0xFFFFBE0B), 10.dp.toPx(), Offset(size.width * 0.8f, size.height * 0.3f))
    }
}

@Composable
private fun CardDeckArt(c1: String, c2: String, col: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 44.dp, height = 64.dp).rotate(-8f).clip(RoundedCornerShape(8.dp)).background(Color.White).border(2.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)), Alignment.Center) {
                Text(c1, color = col, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Box(Modifier.size(width = 44.dp, height = 64.dp).rotate(8f).clip(RoundedCornerShape(8.dp)).background(Color.White).border(2.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)), Alignment.Center) {
                Text(c2, color = Color(0xFF1E293B), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun LudoArt() {
    Canvas(Modifier.fillMaxSize()) {
        val s = size.minDimension * 0.42f
        drawRect(Color(0xFF38BDF8), Offset(size.width * 0.08f, size.height * 0.12f), Size(s, s))
        drawRect(Color(0xFF70E000), Offset(size.width * 0.52f, size.height * 0.12f), Size(s, s))
        drawRect(Color(0xFFFFCA3A), Offset(size.width * 0.08f, size.height * 0.54f), Size(s, s))
        drawRect(Color(0xFFEF476F), Offset(size.width * 0.52f, size.height * 0.54f), Size(s, s))
        drawRoundRect(Color.White, Offset(center.x - 14.dp.toPx(), center.y - 14.dp.toPx()), Size(28.dp.toPx(), 28.dp.toPx()), CornerRadius(6.dp.toPx()))
    }
}

@Composable
private fun SudokuArt() {
    Box(Modifier.fillMaxSize().rotate(-8f).padding(8.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(listOf("", "2", "5"), listOf("9", "7", "6"), listOf("", "3", "1")).forEach { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    r.forEach { v ->
                        Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFE0F2FE)), Alignment.Center) {
                            Text(v, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B), fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TapMatchArt() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniEmojiTile("🫐", Color.White)
            MiniEmojiTile("🍉", Color.White)
            MiniEmojiTile("🫐", Color.White)
        }
    }
}

@Composable
private fun SoccerPoolArt() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color(0xFF38BDF8), 16.dp.toPx(), Offset(size.width * 0.25f, size.height * 0.72f))
        drawCircle(Color(0xFFEF476F), 16.dp.toPx(), Offset(size.width * 0.62f, size.height * 0.66f))
        drawCircle(Color.White, 13.dp.toPx(), Offset(size.width * 0.38f, size.height * 0.30f))
        drawCircle(Color.Black, 13.dp.toPx(), Offset(size.width * 0.38f, size.height * 0.30f), style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun BackgammonArt() {
    Canvas(Modifier.fillMaxSize()) {
        drawRoundRect(Color(0xFFE9B98A), Offset(size.width * 0.08f, size.height * 0.14f), Size(size.width * 0.84f, size.height * 0.74f), CornerRadius(10.dp.toPx()))
        drawRoundRect(Color.White, Offset(center.x - 16.dp.toPx(), center.y - 16.dp.toPx()), Size(16.dp.toPx(), 16.dp.toPx()), CornerRadius(4.dp.toPx()))
        drawRoundRect(Color.White, Offset(center.x + 4.dp.toPx(), center.y + 4.dp.toPx()), Size(16.dp.toPx(), 16.dp.toPx()), CornerRadius(4.dp.toPx()))
        drawCircle(Color(0xFF334155), 8.dp.toPx(), Offset(size.width * 0.22f, size.height * 0.26f))
        drawCircle(Color.White, 8.dp.toPx(), Offset(size.width * 0.78f, size.height * 0.76f))
    }
}

@Composable
private fun HangmanArt() {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 5.dp.toPx()
        drawLine(Color(0xFFD62828), Offset(size.width * 0.18f, size.height * 0.85f), Offset(size.width * 0.18f, size.height * 0.18f), stroke)
        drawLine(Color(0xFFD62828), Offset(size.width * 0.18f, size.height * 0.18f), Offset(size.width * 0.60f, size.height * 0.18f), stroke)
        drawCircle(Color(0xFF3A1F0D), 12.dp.toPx(), Offset(size.width * 0.60f, size.height * 0.42f))
        drawLine(Color(0xFF3A1F0D), Offset(size.width * 0.60f, size.height * 0.54f), Offset(size.width * 0.60f, size.height * 0.78f), stroke)
    }
}

@Composable
private fun SoundMemoryArt() {
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension * 0.38f
        drawArc(Color(0xFF38BDF8), 180f, 90f, true, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
        drawArc(Color(0xFF70E000), 270f, 90f, true, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
        drawArc(Color(0xFFEF476F), 0f, 90f, true, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
        drawArc(Color(0xFFFFCA3A), 90f, 90f, true, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
        drawCircle(Color(0xFF2B2D42), r * 0.35f, center)
    }
}

@Composable
private fun ChessArt(isPuzzle: Boolean) {
    Box(Modifier.fillMaxSize().rotate(-8f), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("♛", fontSize = 26.sp, color = Color(0xFF1F242D))
                Text("♔", fontSize = 26.sp, color = Color.White)
                Text("♖", fontSize = 26.sp, color = Color.White)
            }
            Text(if (isPuzzle) "♘⚡" else "♙", fontSize = 24.sp, color = if (isPuzzle) Color(0xFF70E000) else Color.White)
        }
    }
}

@Composable
private fun BilliardsArt() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(center.x, center.y + 8.dp.toPx())
        drawCircle(Color(0xFFEF476F), 11.dp.toPx(), Offset(c.x - 16.dp.toPx(), c.y - 12.dp.toPx()))
        drawCircle(Color(0xFF38BDF8), 11.dp.toPx(), Offset(c.x + 16.dp.toPx(), c.y - 12.dp.toPx()))
        drawCircle(Color(0xFF111827), 12.dp.toPx(), c)
        drawCircle(Color.White, 5.dp.toPx(), c)
        drawLine(Color(0xFFE9C46A), Offset(size.width * 0.1f, size.height * 0.9f), Offset(size.width * 0.32f, size.height * 0.62f), 4.dp.toPx())
    }
}

@Composable
private fun MemoryCardsArt() {
    Box(Modifier.fillMaxSize().padding(10.dp), Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniEmojiTile("🌸", Color.White)
                MiniEmojiTile("", Color(0xFFFFCA3A))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniEmojiTile("", Color(0xFFFFCA3A))
                MiniEmojiTile("🏞️", Color.White)
            }
        }
    }
}

@Composable
private fun SliderArt() {
    Box(Modifier.fillMaxSize().rotate(-10f).padding(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("1", Color.White.let { Color(0xFFF4A261) })
                MiniEmojiTile("2", Color(0xFFF4A261))
                MiniEmojiTile("3", Color(0xFFF4A261))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("4", Color(0xFFE76F51))
                MiniEmojiTile("5", Color(0xFFE76F51))
                MiniEmojiTile("6", Color(0xFFE76F51))
            }
        }
    }
}

@Composable
private fun WoodBlocksArt() {
    Canvas(Modifier.fillMaxSize()) {
        val c = 15.dp.toPx()
        val ox = size.width * 0.16f
        val oy = size.height * 0.16f
        for (r in 0..3) for (col in 0..3) {
            val fill = (r == 2 || col == 2)
            drawRoundRect(if (fill) Color(0xFFF3C68F) else Color(0xFF543940), Offset(ox + col * c, oy + r * c), Size(c - 2.dp.toPx(), c - 2.dp.toPx()), CornerRadius(3.dp.toPx()))
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
            val cx = left + cell * 1.5f
            val cy = top + cell * 1.5f
            val r = cell * 0.28f
            drawLine(Color(0xFFFF595E), Offset(cx - r, cy - r), Offset(cx + r, cy + r), stroke, StrokeCap.Round)
            drawLine(Color(0xFFFF595E), Offset(cx + r, cy - r), Offset(cx - r, cy + r), stroke, StrokeCap.Round)
            drawCircle(Color(0xFF0077B6), r, Offset(left + cell * 2.5f, cy), style = Stroke(stroke))
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
            drawRoundRect(Color(0xFF1E2D3B), Offset(left, top), Size(w, h), CornerRadius(14.dp.toPx()))
            val cols = 5; val rows = 4
            val cw = w / (cols + 0.5f); val ch = h / (rows + 0.5f)
            val r = minOf(cw, ch) * 0.36f
            for (row in 0 until rows) for (col in 0 until cols) {
                val c = when {
                    row == 3 && col in listOf(0, 3) -> Color(0xFFFF6B6B)
                    row == 2 && col in listOf(1, 2) -> Color(0xFF38BDF8)
                    else -> Color(0xFF34495E)
                }
                drawCircle(c, r, Offset(left + cw * (col + 0.75f), top + ch * (row + 0.75f)))
            }
        }
    }
}

@Composable
private fun BattleshipArt() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF287C9C), Offset(0f, size.height * 0.08f), Size(size.width, size.height * 0.92f))
        drawRoundRect(Color(0xFFE63946), Offset(size.width * 0.14f, size.height * 0.36f), Size(size.width * 0.72f, size.height * 0.16f), CornerRadius(12.dp.toPx()))
        drawCircle(Color.White, 15.dp.toPx(), Offset(size.width * 0.58f, size.height * 0.44f))
        drawCircle(Color(0xFF1D2432), 7.dp.toPx(), Offset(size.width * 0.58f, size.height * 0.44f))
    }
}

@Composable
private fun PingPongArt() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-22f, pivot = center) {
            val tableW = size.width * 0.88f; val tableH = size.height * 0.56f
            val left = (size.width - tableW) / 2f; val top = (size.height - tableH) / 2f + 10.dp.toPx()
            drawRect(Color(0xFF52B788), Offset(left, top), Size(tableW, tableH))
            drawLine(Color.White, Offset(left + tableW / 2f, top), Offset(left + tableW / 2f, top + tableH), 2.5.dp.toPx())
            drawCircle(Color(0xFFFFD166), 8.dp.toPx(), Offset(left + tableW * 0.62f, top + tableH * 0.30f))
            drawCircle(Color(0xFF00BBF9), 15.dp.toPx(), Offset(left + tableW * 0.14f, top + tableH * 0.72f))
            drawCircle(Color(0xFFEF476F), 15.dp.toPx(), Offset(left + tableW * 0.88f, top + tableH * 0.28f))
        }
    }
}

@Composable
private fun CheckersArt() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-14f, pivot = center) {
            val s = size.minDimension * 0.9f; val cell = s / 4f
            for (r in 0..3) for (c in 0..3) {
                drawRect(if ((r + c) % 2 == 1) Color(0xFFB59672) else Color(0xFFEDE0C8), Offset(c * cell, r * cell), Size(cell, cell))
            }
            drawCircle(Color(0xFF2E3239), cell * 0.34f, Offset(1.5f * cell, 0.5f * cell))
            drawCircle(Color.White, cell * 0.34f, Offset(1.5f * cell, 2.5f * cell))
        }
    }
}

@Composable
private fun ReversiArt() {
    Canvas(Modifier.fillMaxSize()) {
        rotate(-10f, pivot = center) {
            val s = size.minDimension * 0.86f; val cell = s / 4f
            drawRect(Color(0xFF2B9348), Offset(0f, 0f), Size(s, s))
            drawCircle(Color(0xFF1F242D), cell * 0.38f, Offset(1.5f * cell, 1.5f * cell))
            drawCircle(Color.White, cell * 0.38f, Offset(2.5f * cell, 1.5f * cell))
            drawCircle(Color.White, cell * 0.38f, Offset(1.5f * cell, 2.5f * cell))
            drawCircle(Color(0xFF1F242D), cell * 0.38f, Offset(2.5f * cell, 2.5f * cell))
        }
    }
}

@Composable
private fun MancalaArt() {
    Canvas(Modifier.fillMaxSize()) {
        drawRoundRect(Color(0xFFF6C26B), Offset(size.width * 0.08f, size.height * 0.22f), Size(size.width * 0.84f, size.height * 0.62f), CornerRadius(16.dp.toPx()))
        drawCircle(Color(0xFF28A5D8), 11.dp.toPx(), Offset(size.width * 0.36f, size.height * 0.38f))
        drawCircle(Color(0xFFE76F51), 11.dp.toPx(), Offset(size.width * 0.64f, size.height * 0.66f))
    }
}

@Composable
private fun DotsBoxesArt() {
    Canvas(Modifier.fillMaxSize()) {
        val s = size.minDimension * 0.78f; val step = s / 3f
        val ox = size.width * 0.14f; val oy = size.height * 0.14f
        drawRect(Color(0xFFF25C54), Offset(ox, oy + step), Size(step, step))
        for (r in 0..3) for (c in 0..3) drawCircle(Color(0xFF14181F), 5.dp.toPx(), Offset(ox + c * step, oy + r * step))
    }
}

@Composable
private fun Art2048() {
    Box(Modifier.fillMaxSize().rotate(-12f).padding(top = 12.dp, start = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniEmojiTile("256", Color(0xFFF4C56A))
                MiniEmojiTile("2048", Color(0xFFFAB824))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniEmojiTile("128", Color(0xFFF6D28B))
                MiniEmojiTile("64", Color(0xFFF4845F))
            }
        }
    }
}

@Composable
private fun MinesweeperArt() {
    Box(Modifier.fillMaxSize().rotate(-10f).padding(start = 10.dp, top = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("🚩", Color(0xFFF1F5F9))
                MiniEmojiTile("3", Color(0xFF38424D))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("💣", Color(0xFFF1F5F9))
                MiniEmojiTile("1", Color(0xFF38424D))
            }
        }
    }
}

@Composable
private fun WordleArt() {
    Box(Modifier.fillMaxSize().rotate(-10f).padding(start = 8.dp, top = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("С", Color(0xFF3D405B)); MiniEmojiTile("Л", Color(0xFFF4A261)); MiniEmojiTile("В", Color(0xFF40916C))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniEmojiTile("И", Color(0xFF40916C)); MiniEmojiTile("Г", Color(0xFF40916C)); MiniEmojiTile("Р", Color(0xFF40916C))
            }
        }
    }
}

@Composable
private fun SnakeArt() {
    Canvas(Modifier.fillMaxSize()) {
        val pts = listOf(0.35f to 0.25f, 0.55f to 0.25f, 0.72f to 0.35f, 0.72f to 0.65f, 0.50f to 0.78f, 0.28f to 0.72f)
        pts.forEach { (fx, fy) -> drawCircle(Color(0xFF38B058), 9.dp.toPx(), Offset(size.width * fx, size.height * fy)) }
        drawCircle(Color(0xFFE63946), 9.dp.toPx(), Offset(size.width * 0.22f, size.height * 0.44f))
    }
}
