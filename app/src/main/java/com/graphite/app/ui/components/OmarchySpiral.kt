package com.graphite.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The omarchy mark, redrawn as a VORTEX.
 *
 * The old square labyrinth (15×15 cells, straight runs, right angles) is
 * gone. What replaces it is a hand-laid pixel arm per side — a true
 * Archimedean spiral that only ever coils, never runs straight:
 *
 *   · squashed into an ellipse (SQUASH) and leaned over (TILT_DEG), so the
 *     spiral reads as a funnel pulling toward its core, not a flat glyph;
 *   · dots are laid rim → core with arc-length spacing, tapering as they
 *     fall inward — the coil tightens the way a real vortex does;
 *   · every dot carries deterministic hand-wobble (offset + size jitter),
 *     like a pixel artist laid the arm by eye.
 *
 * Geometry adapts to the mark size: big marks get a fine, dense thread
 * with generous air between the rings (the coil reads at a glance); tiny
 * marks (the 26dp header logo) get fewer turns and bolder dots so the
 * swirl still legible instead of dissolving into static.
 *
 * Motion:
 *   · on first frame the vortex winds itself up, core → rim (reveal);
 *   · flow — comet streaks chase rim → core along the arm (index space,
 *     so they ride the coil like ink in water);
 *   · spinDurationMs — the whole vortex slowly rotates (connecting: fast,
 *     connected: barely-perceptible drift), pivoting on its own center;
 *   · corePulse — the nucleus breathes;
 *   · ignitionNo — each increment fires a one-shot shockwave of pixels
 *     bursting core → rim (the moment the tunnel comes up).
 */

private const val TILT_DEG = -26f // the lean — funnel axis, not a flat glyph
private const val SQUASH = 0.80f // circle → ellipse (the visible lean)
private const val TILT_RAD = (TILT_DEG * PI / 180.0).toFloat()
private const val TAU = (2.0 * PI).toFloat()

/** Per-size geometry: fine dense thread when big, bold single coil when tiny. */
private class VortexParams(
    val dotFraction: Float, // rim dot / mark size
    val ringGapK: Float, // mean ring separation, in rim-dot widths
    val tighten: Float, // >1: rings compress toward the core
    val taper: Float, // dot shrink toward the core
    val rInFraction: Float, // innermost radius / mark size
    val coreK: Float, // nucleus size, in rim-dot widths
)

private fun paramsFor(markPx: Float): VortexParams =
    when {
        markPx >= 100f -> VortexParams(0.062f, 2.2f, 1.18f, 0.45f, 0.16f, 1.4f)
        markPx >= 40f -> VortexParams(0.105f, 1.9f, 1.05f, 0.30f, 0.13f, 1.3f)
        else -> VortexParams(0.108f, 1.75f, 1.0f, 0.15f, 0.13f, 1.25f)
    }

/** Deterministic 0..1 hash — the pixel-artist wobble seed. */
private fun hash(i: Int, salt: Int): Float {
    var h = i * 2654435761L + salt * 40503L
    h = (h xor (h ushr 16)) * 0x45d9f3bL
    h = (h xor (h ushr 16)) and 0xFFFF
    return (h % 1000L).toFloat() / 1000f
}

/**
 * The vortex laid out in pixels, center-origin, tilt baked in.
 * Layout: [x, y, dotSize] × armLen × arms, each arm ordered rim → core
 * so comets can ride the array like a path.
 */
private class VortexGeometry(
    val pts: FloatArray,
    val armLen: Int,
    val dOut: Float,
    val rIn: Float,
    val rOut: Float,
    val coreD: Float,
) {
    val cosT = cos(TILT_RAD)
    val sinT = sin(TILT_RAD)
}

private fun buildVortex(sizePx: Float, arms: Int): VortexGeometry {
    val prm = paramsFor(sizePx)
    val half = sizePx / 2f
    val dOut = sizePx * prm.dotFraction
    val rOut = half * 0.97f
    val coreD = dOut * prm.coreK
    val rIn = max(sizePx * prm.rInFraction, coreD * 1.3f)
    val ringGap = dOut * prm.ringGapK
    val turns = (rOut - rIn) / ringGap
    val cosT = cos(TILT_RAD)
    val sinT = sin(TILT_RAD)

    val pts = ArrayList<Float>(512 * 3)
    for (a in 0 until arms) {
        val phase = a * TAU / arms
        var t = 0f
        var guard = 0
        while (t < 1f && guard++ < 1600) {
            // Tightening coil: radius dives faster the deeper it goes —
            // rings compress toward the core the way a real vortex does.
            val f = 1f - (1f - t).pow(prm.tighten)
            val r = rOut - (rOut - rIn) * f
            val d = dOut * (1f - prm.taper * t)
            val ds = d * 1.15f // nearly touching — one continuous dotted thread
            // Arc-length step: evenly laid pixels however tight the coil.
            val drdt = (rOut - rIn) * prm.tighten * (1f - t).pow(prm.tighten - 1f)
            val arc = r * turns * TAU
            val denom = sqrt(drdt * drdt + arc * arc)
            val th = phase + turns * TAU * t
            // Ellipse squash, then the lean.
            val x = cos(th) * r
            val y = sin(th) * r * SQUASH
            val xr = x * cosT - y * sinT
            val yr = x * sinT + y * cosT
            // Hand-wobble: a nudge and a size breath, unique per dot.
            val i = pts.size / 3
            val jx = (hash(i, 1) - 0.5f) * d * 0.20f
            val jy = (hash(i, 2) - 0.5f) * d * 0.20f
            val dd = d * (0.92f + hash(i, 3) * 0.16f)
            pts.add(xr + jx)
            pts.add(yr + jy)
            pts.add(dd)
            t += ds / denom
        }
    }
    val arr = FloatArray(pts.size)
    for (i in pts.indices) arr[i] = pts[i]
    return VortexGeometry(arr, arr.size / 3 / arms.coerceAtLeast(1), dOut, rIn, rOut, coreD)
}

@Composable
fun OmarchySpiral(
    color: Color,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    arms: Int = 2,
    flow: Boolean = false,
    flowDurationMs: Int = 1400,
    spinDurationMs: Int = 0,
    corePulse: Boolean = false,
    ignitionNo: Int = 0,
    flowPalette: List<Color> =
        listOf(
            Color(0xFFF5F5F5),
            Color(0xFFA3A3A3),
            Color(0xFF5A5A5A),
        ),
) {
    val animated = flow || spinDurationMs > 0 || corePulse

    // Animated values are kept as State and read *inside* the draw block:
    // per-frame changes invalidate drawing only, never recomposition.
    val flowAnim: State<Float>?
    val spinAnim: State<Float>?
    val pulseAnim: State<Float>?
    if (animated) {
        val t = rememberInfiniteTransition(label = "vortex")
        flowAnim =
            t.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(flowDurationMs, easing = LinearEasing)),
                label = "flow",
            )
        spinAnim =
            if (spinDurationMs > 0) {
                t.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(spinDurationMs, easing = LinearEasing)),
                    label = "spin",
                )
            } else {
                null
            }
        pulseAnim =
            t.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
                label = "pulse",
            )
    } else {
        flowAnim = null
        spinAnim = null
        pulseAnim = null
    }

    // First frame: the vortex winds itself up, core → rim, once.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    // Ignition: a one-shot 0 → 1 shockwave per increment of ignitionNo.
    val burst = remember { Animatable(1f) }
    LaunchedEffect(ignitionNo) {
        if (ignitionNo > 0) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        }
    }

    Spacer(
        modifier
            .size(sizeDp)
            .drawWithCache {
                val g = buildVortex(size.minDimension, arms)
                onDrawBehind {
                    val spinDeg = (spinAnim?.value ?: 0f) * 360f
                    val phase = flowAnim?.value ?: 0f
                    val pulse = pulseAnim?.value ?: 0f
                    val burstT = burst.value
                    val p = reveal.value
                    val armLen = g.armLen

                    // Spin pivots on the vortex's own center: translate to
                    // the middle first, then rotate around the local origin.
                    withTransform({
                        translate(size.width / 2f, size.height / 2f)
                        rotate(spinDeg, pivot = Offset.Zero)
                    }) {
                        // ---- the coiling arm(s), revealed core → rim ----
                        for (a in 0 until arms) {
                            val base = a * armLen
                            for (idx in 0 until armLen) {
                                // birth: 0 at the core, 1 at the rim.
                                val birth = 1f - idx.toFloat() / (armLen - 1).coerceAtLeast(1)
                                val a1 = ((p - birth) * armLen / 5f).coerceIn(0f, 1f)
                                if (a1 <= 0f) continue
                                val pi = (base + idx) * 3
                                val d = g.pts[pi + 2]
                                drawRect(
                                    color = color.copy(alpha = color.alpha * a1),
                                    topLeft = Offset(g.pts[pi] - d / 2f, g.pts[pi + 1] - d / 2f),
                                    size = Size(d, d),
                                )
                            }
                        }

                        // ---- comets: rim → core along the arm ----
                        if (flow && p > 0.9f) {
                            val bubbles = flowPalette.size.coerceAtLeast(1)
                            val segLen = (armLen / 12f).toInt().coerceIn(4, 10)
                            for (a in 0 until arms) {
                                val base = a * armLen
                                for (b in 0 until bubbles) {
                                    val head = ((phase + b.toFloat() / bubbles) * armLen).toInt() % armLen
                                    for (s in 0 until segLen) {
                                        val idx = ((head - s) % armLen + armLen) % armLen
                                        val pi = (base + idx) * 3
                                        val d = g.pts[pi + 2] * 1.35f
                                        val alpha = (1f - s.toFloat() / segLen) * 0.95f
                                        drawRect(
                                            color = flowPalette[b % flowPalette.size].copy(alpha = alpha),
                                            topLeft = Offset(g.pts[pi] - d / 2f, g.pts[pi + 1] - d / 2f),
                                            size = Size(d, d),
                                        )
                                    }
                                }
                            }
                        }

                        // ---- the nucleus (first to appear; breathes when alive) ----
                        var coreScale = (0.4f + 0.6f * p).coerceAtLeast(0f)
                        if (corePulse) coreScale *= 1f + 0.16f * sin(pulse * TAU)
                        if (burstT < 1f) coreScale *= 1f + 0.9f * (1f - burstT)
                        val coreD = g.coreD * coreScale
                        drawRect(
                            color = color.copy(alpha = color.alpha * (p * 3f).coerceIn(0f, 1f)),
                            topLeft = Offset(-coreD / 2f, -coreD / 2f),
                            size = Size(coreD, coreD),
                        )

                        // ---- ignition shockwave: pixel ring bursting core → rim ----
                        if (burstT < 1f) {
                            val rWave = g.rIn * 0.6f + (g.rOut * 1.08f - g.rIn * 0.6f) * burstT
                            val wd = g.dOut * (1f - 0.35f * burstT)
                            val waveAlpha = (1f - burstT) * (1f - burstT) * 0.9f
                            val steps = 26
                            for (k in 0 until steps) {
                                val th = k * TAU / steps
                                val x = cos(th) * rWave
                                val y = sin(th) * rWave * SQUASH
                                val xr = x * g.cosT - y * g.sinT
                                val yr = x * g.sinT + y * g.cosT
                                drawRect(
                                    color = color.copy(alpha = color.alpha * waveAlpha),
                                    topLeft = Offset(xr - wd / 2f, yr - wd / 2f),
                                    size = Size(wd, wd),
                                )
                            }
                        }
                    }
                }
            },
    )
}

/**
 * Boiling water: small square bubbles rising STRICTLY upward through the
 * area this draws over — each bubble keeps its own column for its whole
 * life, no sideways drift, no edge wrap. Deterministic per-slot jitter,
 * no state, pure phase math.
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
        val margin = maxPx * 0.6f
        // Column lattice: bubbles rise in visible vertical columns —
        // a boil read as coherent up-flow, never sideways scatter.
        val columns = (w / (maxPx * 3.2f)).toInt().coerceIn(6, 20)
        val colW = (w - 2f * margin) / columns
        for (i in 0 until slotCount) {
            val col = i % columns
            val row = i / columns
            // Cheap deterministic hash per slot.
            val hx = (i * 137 + 61) % 1000 / 1000f
            val hy = (i * 719 + 401) % 1000 / 1000f
            val hs = (i * 331 + 199) % 1000 / 1000f
            val speed = 0.8f + hy * 0.4f // gentle spread — columns stay readable
            // Golden-ratio stagger: column + row offsets spread cycles
            // evenly, so every column boils without clumping.
            val stagger = (col * 0.618f + row * 0.382f) % 1f
            val cycle = (phase * speed + stagger) % 1f
            val y = h * (1f - cycle) - maxPx / 2f
            val side = minPx + (maxPx - minPx) * hs
            // Fixed x for life: column center + a whisper of scatter.
            val x = margin + col * colW + (colW - side) * (0.2f + 0.6f * hx)
            // Quick fade-in at the floor, gentle fade-out at the rim.
            val fadeIn = (cycle * 12f).coerceAtMost(1f)
            val fadeOut = ((1f - cycle) * 8f).coerceAtMost(1f)
            val alpha = fadeIn * fadeOut * 0.95f
            drawRect(
                color = palette[i % palette.size].copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(side, side),
            )
        }
    }
}
