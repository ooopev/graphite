package com.oquq.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.oquq.app.R
import com.oquq.app.ui.theme.Black
import com.oquq.app.ui.theme.Bone
import com.oquq.app.ui.theme.OutlineDark
import com.oquq.app.ui.theme.Surface03
import com.oquq.app.ui.theme.Surface04
import com.oquq.app.vpn.VpnStatus

/**
 * The power button — the emotional core of the home screen.
 * Idle: dark disc, thin track ring. Connecting: sweeping arc. Connected:
 * bone-white disc + breathing halo. Pure monochrome.
 */
@Composable
fun PowerButton(
    status: VpnStatus,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 230,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f),
        label = "powerScale",
    )

    val infinite = rememberInfiniteTransition(label = "power")

    // indeterminate sweep while connecting
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "sweep",
    )
    // breathing halo while connected
    val halo by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo",
    )
    val dialProgress by animateFloatAsState(
        targetValue = if (status == VpnStatus.CONNECTED) 1f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "dial",
    )

    val stroke = with(LocalDensity.current) { 3.dp.toPx() }
    val haloStroke = with(LocalDensity.current) { 1.5.dp.toPx() }
    val trackColor = OutlineDark
    val activeColor = Bone

    val iconTint = if (status == VpnStatus.CONNECTED) Black else Bone.copy(alpha = 0.86f)

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .scale(scale)
            .pointerInput(status) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onToggle() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val c = center
            val r = size.minDimension / 2f - stroke
            val innerR = r * 0.82f

            // outer track
            drawCircle(
                color = trackColor,
                radius = r,
                center = c,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            when (status) {
                VpnStatus.CONNECTING -> {
                    drawArc(
                        color = activeColor,
                        startAngle = sweep - 90f,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    drawCircle(color = Surface03, radius = innerR, center = c)
                }
                VpnStatus.CONNECTED -> {
                    // breathing halo
                    drawCircle(
                        color = activeColor.copy(alpha = 0.10f + 0.10f * halo),
                        radius = innerR + (r - innerR) * (0.25f + 0.75f * halo),
                        center = c,
                        style = Stroke(haloStroke, cap = StrokeCap.Round),
                    )
                    // full dial
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = 360f * dialProgress,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    drawCircle(color = activeColor, radius = innerR, center = c)
                }
                else -> {
                    drawCircle(
                        color = if (status == VpnStatus.DISCONNECTING) Surface04 else Surface03,
                        radius = innerR,
                        center = c,
                    )
                    drawCircle(
                        color = trackColor,
                        radius = innerR,
                        center = c,
                        style = Stroke(1f),
                    )
                }
            }
        }

        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_stat_vpn),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size((sizeDp * 0.24).dp)
                .graphicsLayer { alpha = if (status == VpnStatus.DISCONNECTING) 0.4f else 1f },
        )
    }
}
