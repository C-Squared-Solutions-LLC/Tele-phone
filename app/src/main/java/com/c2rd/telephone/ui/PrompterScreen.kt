@file:OptIn(ExperimentalMaterial3Api::class)

package com.c2rd.telephone.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.c2rd.telephone.BitrateLevel
import com.c2rd.telephone.CameraEngine
import com.c2rd.telephone.CameraSettings
import com.c2rd.telephone.EngineState
import com.c2rd.telephone.Prefs
import com.c2rd.telephone.PrompterSettings
import com.c2rd.telephone.RemoteAction
import com.c2rd.telephone.RemoteInput
import com.c2rd.telephone.VideoQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PrompterScreen(script: String, prefs: Prefs, remote: RemoteInput, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // --- permissions ---
    var hasCamera by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = context.isGranted(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filterNot { context.isGranted(it) }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // --- settings ---
    var ps by remember { mutableStateOf(prefs.prompter) }
    var cs by remember { mutableStateOf(prefs.camera) }
    val updatePs: (PrompterSettings) -> Unit = { ps = it; prefs.prompter = it }
    val updateCs: (CameraSettings) -> Unit = { cs = it; prefs.camera = it }

    // --- camera ---
    val engine = remember { CameraEngine(context.applicationContext) }
    val es by engine.state
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }

    LaunchedEffect(hasCamera, cs) {
        if (hasCamera) engine.bind(lifecycleOwner, previewView, cs, activity.displayRotation())
    }
    DisposableEffect(Unit) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) = engine.setRotation(activity.displayRotation())
        }
        dm.registerDisplayListener(listener, null)
        onDispose {
            dm.unregisterDisplayListener(listener)
            engine.release()
        }
    }

    // --- full screen while prompting ---
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // --- take state ---
    val scrollState = rememberScrollState()
    var scrolling by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Video orientation is fixed when the take starts, so don't let the screen spin mid-take.
    val busy = countdown > 0 || es.starting || es.recording
    LaunchedEffect(busy) {
        activity.requestedOrientation =
            if (busy) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    LaunchedEffect(es.recording) {
        if (ps.scrollWithRecording) scrolling = es.recording
    }

    val appContext = context.applicationContext
    val toggleRecord: () -> Unit = {
        when {
            countdownJob?.isActive == true -> {
                countdownJob?.cancel()
                countdown = 0
            }
            engine.isRecording -> engine.stopRecording()
            !es.bound -> toast(appContext, "Camera not ready")
            else -> {
                if (ps.restartEachTake) scope.launch { scrollState.scrollTo(0) }
                countdownJob = scope.launch {
                    for (i in ps.countdownSeconds downTo 1) {
                        countdown = i
                        delay(1000)
                    }
                    countdown = 0
                    engine.startRecording(
                        onSaved = { toast(appContext, "Saved to Movies/${CameraEngine.FOLDER}") },
                        onError = { toast(appContext, it) },
                    )
                }
            }
        }
    }

    val overlayHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() } * ps.overlayHeight
    val step by rememberUpdatedState(overlayHeightPx * 0.4f)
    val currentToggle by rememberUpdatedState(toggleRecord)
    DisposableEffect(remote) {
        remote.listener = { action ->
            when (action) {
                RemoteAction.TOGGLE_RECORD -> currentToggle()
                RemoteAction.TOGGLE_SCROLL -> scrolling = !scrolling
                RemoteAction.SCROLL_FORWARD -> scope.launch { scrollState.animateScrollBy(step) }
                RemoteAction.SCROLL_BACK -> scope.launch { scrollState.animateScrollBy(-step) }
            }
        }
        onDispose { remote.listener = null }
    }

    BackHandler {
        engine.stopRecording()
        onBack()
    }

    // --- UI ---
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCamera) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            var focusPoint by remember { mutableStateOf<Offset?>(null) }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) engine.zoomBy(zoom) }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { p ->
                                engine.focusAt(previewView.meteringPointFactory, p.x, p.y)
                                focusPoint = p
                            },
                            onDoubleTap = { engine.resetZoom() },
                        )
                    }
            )
            focusPoint?.let { p ->
                val r = with(density) { 32.dp.toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((p.x - r).roundToInt(), (p.y - r).roundToInt()) }
                        .size(64.dp)
                        .border(2.dp, Color(0xFFFFD54F), CircleShape)
                )
                LaunchedEffect(p) {
                    delay(1200)
                    focusPoint = null
                }
            }
        } else {
            PermissionPrompt(Modifier.align(Alignment.Center))
        }

        PrompterOverlay(
            script = script,
            settings = ps,
            scrollState = scrollState,
            scrolling = scrolling,
            onTap = { scrolling = !scrolling },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .fillMaxWidth(if (landscape) 0.7f else 1f)
                .fillMaxHeight(ps.overlayHeight),
        )

        if (countdown > 0) {
            Text(
                countdown.toString(),
                color = Color.White,
                fontSize = 120.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Controls(
            es = es,
            ps = ps,
            busy = busy,
            scrolling = scrolling,
            onBack = {
                engine.stopRecording()
                onBack()
            },
            onFlip = {
                updateCs(
                    cs.copy(
                        lensFacing = if (cs.lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK
                        else CameraSelector.LENS_FACING_FRONT
                    )
                )
            },
            onRecord = toggleRecord,
            onToggleScroll = { scrolling = !scrolling },
            onSpeed = { delta -> updatePs(ps.copy(speed = (ps.speed + delta).coerceIn(5f, 300f))) },
            onSettings = { showSettings = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showSettings) {
        SettingsSheet(
            ps = ps,
            cs = cs,
            es = es,
            cameraLocked = busy,
            lastKey = remote.lastKey.value,
            onPs = updatePs,
            onCs = updateCs,
            onExposure = engine::setExposure,
            onTorch = engine::setTorch,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun Controls(
    es: EngineState,
    ps: PrompterSettings,
    busy: Boolean,
    scrolling: Boolean,
    onBack: () -> Unit,
    onFlip: () -> Unit,
    onRecord: () -> Unit,
    onToggleScroll: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        es.error?.let {
            Pill { Text(it, color = Color(0xFFFFAB91), fontSize = 12.sp) }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (es.recording) {
                Pill {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(Color.Red, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(formatDuration(es.recordedNanos), color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else if (es.description.isNotEmpty()) {
                Pill { Text(es.description, color = Color.White, fontSize = 12.sp) }
            }
            if (es.zoomRatio > 1.01f) {
                Pill { Text("%.1fx".format(es.zoomRatio), color = Color.White, fontSize = 12.sp) }
            }
            Pill {
                IconButton(onClick = { onSpeed(-5f) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, "Slower", tint = Color.White)
                }
                Text("Speed ${ps.speed.roundToInt()}", color = Color.White, fontSize = 12.sp)
                IconButton(onClick = { onSpeed(5f) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, "Faster", tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Back to script", onClick = onBack)
            RoundButton(Icons.Default.Cameraswitch, "Switch camera", enabled = !busy, onClick = onFlip)
            RecordButton(active = busy, onClick = onRecord)
            RoundButton(
                if (scrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (scrolling) "Pause scrolling" else "Start scrolling",
                onClick = onToggleScroll,
            )
            RoundButton(Icons.Default.Settings, "Settings", onClick = onSettings)
        }
    }
}

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Row(
        Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(52.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
    ) {
        Icon(icon, description, tint = if (enabled) Color.White else Color.Gray)
    }
}

@Composable
private fun RecordButton(active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .border(BorderStroke(4.dp, Color.White), CircleShape)
            .padding(8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(Color.Red, RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Red, CircleShape)
            )
        }
    }
}

@Composable
private fun PermissionPrompt(modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Camera permission is needed to record.", color = Color.White)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }) { Text("Open app settings") }
    }
}

@Composable
private fun SettingsSheet(
    ps: PrompterSettings,
    cs: CameraSettings,
    es: EngineState,
    cameraLocked: Boolean,
    lastKey: String?,
    onPs: (PrompterSettings) -> Unit,
    onCs: (CameraSettings) -> Unit,
    onExposure: (Int) -> Unit,
    onTorch: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xEE1E1E24),
        scrimColor = Color.Transparent,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Section("Prompter")
            LabeledSlider("Scroll speed", ps.speed, 5f..300f, ps.speed.roundToInt().toString()) { onPs(ps.copy(speed = it)) }
            LabeledSlider("Text size", ps.fontSize, 16f..80f, "${ps.fontSize.roundToInt()} sp") { onPs(ps.copy(fontSize = it)) }
            LabeledSlider("Background", ps.overlayOpacity, 0f..0.9f, "${(ps.overlayOpacity * 100).roundToInt()}%") {
                onPs(ps.copy(overlayOpacity = it))
            }
            LabeledSlider("Text area height", ps.overlayHeight, 0.2f..0.9f, "${(ps.overlayHeight * 100).roundToInt()}%") {
                onPs(ps.copy(overlayHeight = it))
            }
            ToggleRow("Center text", ps.centerText) { onPs(ps.copy(centerText = it)) }
            ToggleRow("Mirror text (beam-splitter rig)", ps.mirrorText) { onPs(ps.copy(mirrorText = it)) }
            ToggleRow("Scroll starts with recording", ps.scrollWithRecording) { onPs(ps.copy(scrollWithRecording = it)) }
            ToggleRow("Restart script each take", ps.restartEachTake) { onPs(ps.copy(restartEachTake = it)) }
            ChipRow("Countdown", listOf(0, 3, 5, 10), ps.countdownSeconds, { if (it == 0) "Off" else "${it}s" }) {
                onPs(ps.copy(countdownSeconds = it))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Camera")
            if (es.exposureRange.first != es.exposureRange.last) {
                LabeledSlider(
                    "Exposure",
                    es.exposureIndex.toFloat(),
                    es.exposureRange.first.toFloat()..es.exposureRange.last.toFloat(),
                    es.exposureIndex.toString(),
                    steps = (es.exposureRange.last - es.exposureRange.first - 1).coerceAtLeast(0),
                ) { onExposure(it.roundToInt()) }
            }
            if (es.hasFlash) ToggleRow("Light", es.torchOn, onChange = onTorch)

            if (cameraLocked) {
                Text("Stop recording to change video settings.", color = Color.Gray, fontSize = 12.sp)
            }
            ChipRow("Resolution", VideoQuality.entries, cs.quality, { it.label }, enabled = !cameraLocked) {
                onCs(cs.copy(quality = it))
            }
            ChipRow("Frame rate", listOf(24, 30, 60), cs.fps, { "$it fps" }, enabled = !cameraLocked) {
                onCs(cs.copy(fps = it))
            }
            ChipRow("Bitrate", BitrateLevel.entries, cs.bitrate, { it.label }, enabled = !cameraLocked) {
                onCs(cs.copy(bitrate = it))
            }
            ToggleRow("Stabilization", cs.stabilization, enabled = !cameraLocked) { onCs(cs.copy(stabilization = it)) }
            ToggleRow("Mirror front camera video", cs.mirrorFrontVideo, enabled = !cameraLocked) {
                onCs(cs.copy(mirrorFrontVideo = it))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Bluetooth remote")
            Text(
                "Start/stop: play-pause, volume, enter, camera key.\n" +
                    "Scroll: arrows, page up/down, next/previous track. Space pauses scrolling.",
                color = Color.LightGray,
                fontSize = 13.sp,
            )
            Text("Last key received: ${lastKey ?: "none yet"}", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(display, color = Color.LightGray, fontSize = 14.sp)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    text: (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text(option)) },
                    enabled = enabled,
                )
            }
        }
    }
}

private fun formatDuration(nanos: Long): String {
    val total = nanos / 1_000_000_000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

private fun Context.isGranted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun Context.findActivity(): Activity {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    error("No activity for context")
}

@Suppress("DEPRECATION")
private fun Activity.displayRotation(): Int = windowManager.defaultDisplay.rotation
