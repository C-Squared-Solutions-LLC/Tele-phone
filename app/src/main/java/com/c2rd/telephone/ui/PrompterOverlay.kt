package com.c2rd.telephone.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.c2rd.telephone.PrompterSettings

/** Semi-transparent scrolling script that sits over the camera preview. */
@Composable
fun PrompterOverlay(
    script: String,
    settings: PrompterSettings,
    scrollState: ScrollState,
    scrolling: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val speedPx by rememberUpdatedState(with(density) { settings.speed.dp.toPx() })
    val rendered = remember(script) { Markdown.render(script) }

    LaunchedEffect(scrolling) {
        if (!scrolling) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            // ScrollState keeps the fractional remainder, so slow speeds still move smoothly.
            scrollState.dispatchRawDelta(speedPx * (now - last) / 1_000_000_000f)
            last = now
        }
    }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(Color.Black.copy(alpha = settings.overlayOpacity))
            .pointerInput(Unit) { detectTapGestures { onTap() } }
    ) {
        // Reading line sits a quarter of the way down, close to the lens.
        val markerY = maxHeight * 0.25f
        Text(
            text = rendered,
            color = Color.White,
            textAlign = if (settings.centerText) TextAlign.Center else TextAlign.Start,
            style = TextStyle(
                fontSize = settings.fontSize.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(Color.Black, Offset(0f, 2f), 6f),
            ),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { if (settings.mirrorText) scaleX = -1f }
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                // Top pad puts the first line on the marker; bottom pad lets the last line reach it.
                .padding(top = markerY, bottom = maxHeight - markerY),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(y = markerY - 2.dp)
                .padding(start = 4.dp)
                .width(10.dp)
                .height(4.dp)
                .background(Color(0xCCFF5252), RoundedCornerShape(2.dp))
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = markerY - 2.dp)
                .padding(end = 4.dp)
                .width(10.dp)
                .height(4.dp)
                .background(Color(0xCCFF5252), RoundedCornerShape(2.dp))
        )
    }
}
