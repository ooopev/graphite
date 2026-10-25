package com.oquq.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A true square spiral: one line that coils inward — 15, 13, 11 … 3 cells
 * per turn — not concentric rings. [SPIRAL_PATH] is the exact drawing walk
 * from the outer end to the center, so flowing bubbles travel the spiral
 * the way ink would.
 */
private val SPIRAL_ROWS =
    listOf(
        "111111111111111",
        "000000000000001",
        "001111111111101",
        "001000000000101",
        "001011111110101",
        "001010000010101",
        "001010111010101",
        "001010101010101",
        "001010101010101",
        "001010100010101",
        "001010111110101",
        "001010000000101",
        "001011111111101",
        "001000000000001",
        "001111111111111",
    )

private const val N = 15

/** The drawing walk: outer end → center. Bubbles ride this. */
private val SPIRAL_PATH =
    intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 29, 44, 59, 74, 89, 104, 119, 134,
        149, 164, 179, 194, 209, 224, 223, 222, 221, 220, 219, 218, 217, 216, 215, 214, 213,
        212, 197, 182, 167, 152, 137, 122, 107, 92, 77, 62, 47, 32, 33, 34, 35, 36, 37, 38,
        39, 40, 41, 42, 57, 72, 87, 102, 117, 132, 147, 162, 177, 192, 191, 190, 189, 188,
        187, 186, 185, 184, 169, 154, 139, 124, 109, 94, 79, 64, 65, 66, 67, 68, 69, 70, 85,
        100, 115, 130, 145, 160, 159, 158, 157, 156, 141, 126, 111, 96, 97, 98, 113, 128,
    )

@Composable
fun OmarchySpiral(
    color: Color,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    bold: Boolean = true,
    flow: Boolean = false,
    flowPalette: List<Color> =
        listOf(
            Color(0xFFF5F5F5),
            Color(0xFFA3A3A3),
            Color(0xFF5A5A5A),
        ),
    flowDurationMs: Int = 1600,
) {
    val phase =
        if (flow) {
            val transition = rememberInfiniteTransition(label = "spiralFlow")
            val anim by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(flowDurationMs, easing = LinearEasing)),
                label = "phase",
            )
            anim
        } else {
            0f
        }

    Canvas(modifier.size(sizeDp)) {
        // 15 pixel rows separated by 1-unit gutters: 29 units across.
        val unit = size.minDimension / (2f * N - 1f)
        val inset = if (bold) 0f else unit * 0.06f
        val grid = (2f * N - 1f) * unit
        val origin = (size.minDimension - grid) / 2f

        // Hand-placed feel: deterministic per-cell jitter — offset and size
        // wobble a little, like a pixel artist laid the spiral by eye.
        fun hash(i: Int, salt: Int): Float {
            var h = i * 2654435761L + salt * 40503L
            h = (h xor (h ushr 16)) * 0x45d9f3bL
            h = (h xor (h ushr 16)) and 0xFFFF
            return (h % 1000L).toFloat() / 1000f
        }

        fun jitter(i: Int): Triple<Float, Float, Float> {
            val jx = (hash(i, 1) - 0.5f) * unit * 0.38f
            val jy = (hash(i, 2) - 0.5f) * unit * 0.38f
            val scale = 0.9f + hash(i, 3) * 0.25f
            return Triple(jx, jy, scale)
        }

        fun topLeft(i: Int): Offset {
            val x = i % N
            val y = i / N
            val (jx, jy, _) = jitter(i)
            return Offset(
                origin + (2f * x) * unit + inset + jx,
                origin + (2f * y) * unit + inset + jy,
            )
        }

        fun cellSize(i: Int): Float = unit * (if (bold) 1f else 0.88f) * jitter(i).third

        for (y in 0 until N) {
            val row = SPIRAL_ROWS[y]
            for (x in 0 until N) {
                if (row[x] != '1') continue
                val i = y * N + x
                drawRect(color = color, topLeft = topLeft(i), size = Size(cellSize(i), cellSize(i)))
            }
        }

        if (flow) {
            // Bright segments chasing each other along the spiral walk.
            val len = SPIRAL_PATH.size
            val bubbles = flowPalette.size.coerceAtLeast(1)
            val segLen = (len / 10f).toInt().coerceAtLeast(3)
            for (b in 0 until bubbles) {
                val head = ((phase + b.toFloat() / bubbles) * len).toInt() % len
                for (s in 0 until segLen) {
                    val idx = ((head - s) % len + len) % len
                    val alpha = (1f - s.toFloat() / segLen) * 0.95f
                    drawRect(
                        color = flowPalette[b % flowPalette.size].copy(alpha = alpha),
                        topLeft = topLeft(SPIRAL_PATH[idx]),
                        size = Size(cellSize(SPIRAL_PATH[idx]), cellSize(SPIRAL_PATH[idx])),
                    )
                }
            }
        }
    }
}

/**
 * Boiling water: small square bubbles rising fast through the area this
 * draws over. Deterministic per-slot jitter — no state, pure phase math.
 */
@Composable
fun BoilingPixels(
    modifier: Modifier = Modifier,
    palette: List<Color> =
        listOf(
            Color(0xFFF5F5F5),
            Color(0xFFA3A3A3),
            Color(0xFF5A5A5A),
        ),
    slotCount: Int = 40,
    durationMs: Int = 850,
) {
    val transition = rememberInfiniteTransition(label = "boil")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val minPx = 2.dp.toPx()
        val maxPx = 5.dp.toPx()
        for (i in 0 until slotCount) {
            // Cheap deterministic hash per slot.
            val hx = (i * 137 + 61) % 1000 / 1000f
            val hy = (i * 719 + 401) % 1000 / 1000f
            val hs = (i * 331 + 199) % 1000 / 1000f
            val speed = 0.75f + hy * 0.7f // some bubbles faster
            val cycle = (phase * speed + hx) % 1f
            val y = h * (1f - cycle) - maxPx / 2f
            // Near-vertical rise: only a whisper of sideways drift.
            val x = w * ((hx + 0.04f * kotlin.math.sin(cycle * 6.28f + i)) % 1f)
            val side = minPx + (maxPx - minPx) * hs
            val alpha = kotlin.math.sin(cycle * Math.PI.toFloat()) * 0.9f
            drawRect(
                color = palette[i % palette.size].copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(side, side),
            )
        }
    }
}
