package com.c2rd.telephone

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class EngineState(
    val bound: Boolean = false,
    val starting: Boolean = false,
    val recording: Boolean = false,
    val recordedNanos: Long = 0,
    val description: String = "",
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val exposureIndex: Int = 0,
    val exposureRange: IntRange = 0..0,
    val hasFlash: Boolean = false,
    val torchOn: Boolean = false,
    val error: String? = null,
)

class CameraEngine(private val context: Context) {
    val state = mutableStateOf(EngineState())

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var owner: LifecycleOwner? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    val isRecording: Boolean get() = recording != null

    private suspend fun provider(): ProcessCameraProvider {
        provider?.let { return it }
        val p = suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, mainExecutor)
        }
        provider = p
        return p
    }

    private data class Attempt(val quality: Quality, val fps: Range<Int>?, val stabilize: Boolean)

    suspend fun bind(owner: LifecycleOwner, previewView: PreviewView, s: CameraSettings, rotation: Int) {
        if (recording != null) return
        val p = provider()
        this.owner?.let { camera?.cameraInfo?.zoomState?.removeObservers(it) }
        p.unbindAll()
        state.value = state.value.copy(bound = false, error = null)

        val wanted = CameraSelector.Builder().requireLensFacing(s.lensFacing).build()
        val selector = when {
            p.hasCamera(wanted) -> wanted
            p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
        val info = p.getCameraInfo(selector)
        val caps = Recorder.getVideoCapabilities(info)
        val supported = caps.getSupportedQualities(DynamicRange.SDR)

        // Highest quality at or below the one asked for that this camera can actually do.
        val ladder = VideoQuality.entries.map { it.quality }
        val quality = ladder.drop(ladder.indexOf(s.quality.quality)).firstOrNull { it in supported }
            ?: supported.firstOrNull()
            ?: Quality.FHD

        val ranges = info.supportedFrameRateRanges
        val fpsRange = ranges.firstOrNull { it.lower == s.fps && it.upper == s.fps }
            ?: ranges.firstOrNull { it.upper == s.fps }
        val stabilize = s.stabilization && caps.isStabilizationSupported

        // 4K60 and stabilization are the usual things a phone refuses together, so peel
        // them off one at a time until binding works.
        val attempts = buildList {
            add(Attempt(quality, fpsRange, stabilize))
            if (fpsRange != null) add(Attempt(quality, null, stabilize))
            if (stabilize) add(Attempt(quality, null, false))
            if (quality != Quality.FHD && Quality.FHD in supported) {
                add(Attempt(Quality.FHD, fpsRange, false))
                add(Attempt(Quality.FHD, null, false))
            }
        }.distinct()

        var lastError: Exception? = null
        for (a in attempts) {
            try {
                val fps = a.fps?.upper ?: 30
                val bitrate = bitrateFor(a.quality, fps, s.bitrate)
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(a.quality))
                    .setTargetVideoEncodingBitRate(bitrate)
                    .build()
                val vcBuilder = VideoCapture.Builder(recorder)
                    .setTargetRotation(rotation)
                    .setMirrorMode(if (s.mirrorFrontVideo) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF)
                a.fps?.let { vcBuilder.setTargetFrameRate(it) }
                if (a.stabilize) vcBuilder.setVideoStabilizationEnabled(true)
                val vc = vcBuilder.build()

                val pv = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .build()
                    )
                    .setTargetRotation(rotation)
                    .build()
                pv.surfaceProvider = previewView.surfaceProvider

                val cam = p.bindToLifecycle(owner, selector, pv, vc)
                camera = cam
                this.owner = owner
                preview = pv
                videoCapture = vc

                val exposure = cam.cameraInfo.exposureState
                val desc = buildString {
                    append(labelOf(a.quality)).append(" · ").append(fps).append(" fps · ")
                    append(bitrate / 1_000_000).append(" Mbps")
                    if (a.stabilize) append(" · Stabilized")
                }
                val notes = buildList {
                    if (a.quality != quality) add("${labelOf(quality)} unavailable")
                    if (s.fps != 30 && a.fps == null) add("${s.fps} fps unavailable")
                    if (s.stabilization && !a.stabilize) add("stabilization off")
                }
                state.value = state.value.copy(
                    bound = true,
                    description = desc,
                    exposureIndex = exposure.exposureCompensationIndex,
                    exposureRange = exposure.exposureCompensationRange.let { it.lower..it.upper },
                    hasFlash = cam.cameraInfo.hasFlashUnit(),
                    torchOn = false,
                    error = notes.takeIf { it.isNotEmpty() }?.joinToString(", ", postfix = " on this camera"),
                )
                cam.cameraInfo.zoomState.observe(owner) { z ->
                    state.value = state.value.copy(zoomRatio = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio)
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Bind failed for $a", e)
                lastError = e
                p.unbindAll()
            }
        }
        state.value = state.value.copy(bound = false, error = "Camera failed: ${lastError?.message}")
    }

    fun setRotation(rotation: Int) {
        if (recording != null) return
        preview?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    @SuppressLint("MissingPermission")
    fun startRecording(onSaved: (Uri) -> Unit, onError: (String) -> Unit) {
        val vc = videoCapture ?: return onError("Camera not ready")
        if (recording != null) return

        val name = "TP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            // Own folder under Movies. MediaStore indexes it, so Photos / Gallery list it
            // as a "Teleprompter" album.
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
        }
        val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()

        var pending = vc.output.prepareRecording(context, output)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        state.value = state.value.copy(starting = true)
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start ->
                    state.value = state.value.copy(starting = false, recording = true, recordedNanos = 0)

                is VideoRecordEvent.Status ->
                    state.value = state.value.copy(recordedNanos = event.recordingStats.recordedDurationNanos)

                is VideoRecordEvent.Finalize -> {
                    recording = null
                    state.value = state.value.copy(starting = false, recording = false)
                    val uri = event.outputResults.outputUri
                    when {
                        !event.hasError() -> onSaved(uri)
                        // These still leave a playable file behind (e.g. app backgrounded mid-take).
                        uri != Uri.EMPTY && event.error in RECOVERABLE -> onSaved(uri)
                        else -> onError("Recording failed (${event.error}): ${event.cause?.message}")
                    }
                }
            }
        }
    }

    fun stopRecording() {
        recording?.stop()
    }

    fun zoomBy(factor: Float) {
        val s = state.value
        camera?.cameraControl?.setZoomRatio((s.zoomRatio * factor).coerceIn(s.minZoom, s.maxZoom))
    }

    fun resetZoom() {
        camera?.cameraControl?.setZoomRatio(1f.coerceIn(state.value.minZoom, state.value.maxZoom))
    }

    fun focusAt(factory: MeteringPointFactory, x: Float, y: Float) {
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(5, TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun setExposure(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
        state.value = state.value.copy(exposureIndex = index)
    }

    fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
        state.value = state.value.copy(torchOn = on)
    }

    fun release() {
        recording?.stop()
        owner?.let { camera?.cameraInfo?.zoomState?.removeObservers(it) }
        provider?.unbindAll()
        camera = null
        preview = null
        videoCapture = null
    }

    companion object {
        private const val TAG = "CameraEngine"
        const val FOLDER = "Teleprompter"

        private val RECOVERABLE = setOf(
            VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE,
        )

        private fun labelOf(q: Quality) = VideoQuality.entries.firstOrNull { it.quality == q }?.label ?: "SD"

        /** Target bitrate in bps. The encoder clamps this to whatever it supports. */
        fun bitrateFor(q: Quality, fps: Int, level: BitrateLevel): Int {
            val baseMbps = when (q) {
                Quality.UHD -> 45.0
                Quality.FHD -> 18.0
                Quality.HD -> 10.0
                else -> 5.0
            }
            val fpsFactor = if (fps >= 50) 1.5 else 1.0
            return (baseMbps * level.multiplier * fpsFactor * 1_000_000).toInt()
        }
    }
}
