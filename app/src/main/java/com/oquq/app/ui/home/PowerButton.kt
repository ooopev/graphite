package com.oquq.app.ui.home

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
import androidx.compose.runtime.getValue
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
import com.oquq.app.ui.components.OmarchySpiral
import com.oquq.app.ui.theme.Bone
import com.oquq.app.ui.theme.BorderStrong
import com.oquq.app.ui.theme.Smoke
import com.oquq.app.ui.theme.Surface03
import com.oquq.app.vpn.VpnStatus

/**
 * The power square: the omarchy spiral, finished and still. Tap it and
 * pixels start chasing through the labyrinth; connected inverts it —
 * white square, black spiral, gray bubbles running along the rings.
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
            flowPalette = bubblePalette,
            flowDurationMs = if (connecting) 900 else 700,
            modifier =
                Modifier.graphicsLayer {
                    alpha = if (status == VpnStatus.DISCONNECTING) 0.4f else 1f
                },
        )
    }
}
