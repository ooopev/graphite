package com.graphite.app.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.graphite.app.ui.components.OmarchySpiral
import com.graphite.app.ui.theme.Bone
import com.graphite.app.ui.theme.BorderStrong
import com.graphite.app.ui.theme.Smoke
import com.graphite.app.ui.theme.Surface03
import com.graphite.app.vpn.VpnStatus

/**
 * The power square: a pixel vortex leaning into its core. Tap it and the
 * coil starts to breathe — comets chase rim → core while the whole spiral
 * slowly turns; connected locks it steady with a data stream running
 * through, a breathing nucleus, and a one-shot shockwave the moment the
 * tunnel comes up.
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

    val connected = status == VpnStatus.CONNECTED
    val connecting = status == VpnStatus.CONNECTING
    // Ignition counter: ticks up once every time the tunnel comes up —
    // fires the one-shot shockwave + core flash inside the vortex.
    var ignitionNo by remember { mutableIntStateOf(0) }
    LaunchedEffect(connected) {
        if (connected) ignitionNo++
    }
    // Always the same black square; only the spiral changes.
    val squareColor = Surface03
    val frameColor = when {
        connected -> Bone
        connecting -> Bone
        else -> BorderStrong
    }
    // Idle: gray spiral. Live: white spiral + fast squares chasing along it.
    val spiralBase =
        when {
            connected -> Bone
            connecting -> Bone
            else -> Smoke
        }
    val bubblePalette =
        listOf(
            Color(0xFFF5F5F5),
            Color(0xFFA3A3A3),
            Color(0xFF5A5A5A),
        )

    Box(
        modifier =
            modifier
                .size(sizeDp.dp)
                .scale(scale)
                .background(squareColor)
                .border(BorderStroke(1.dp, frameColor))
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
        OmarchySpiral(
            color = spiralBase,
            sizeDp = (sizeDp * 0.70).dp,
            flow = connecting || connected,
            flowDurationMs = if (connecting) 900 else 1600,
            spinDurationMs = when {
                connecting -> 9000
                connected -> 60000
                else -> 0
            },
            corePulse = connected,
            ignitionNo = ignitionNo,
            flowPalette = bubblePalette,
            modifier =
                Modifier.graphicsLayer {
                    alpha = if (status == VpnStatus.DISCONNECTING) 0.4f else 1f
                },
        )
    }
}
