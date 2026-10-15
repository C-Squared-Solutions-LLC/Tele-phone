package com.c2rd.telephone

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.video.Quality
import androidx.core.content.edit

enum class VideoQuality(val label: String, val quality: Quality) {
    UHD("4K", Quality.UHD),
    FHD("1080p", Quality.FHD),
    HD("720p", Quality.HD),
}

enum class BitrateLevel(val label: String, val multiplier: Double) {
    STANDARD("Standard", 1.0),
    HIGH("High", 1.7),
    MAX("Max", 2.4),
}

data class PrompterSettings(
    /** Scroll speed in dp per second. */
    val speed: Float = 40f,
    val fontSize: Float = 34f,
    val overlayOpacity: Float = 0.45f,
    val overlayHeight: Float = 0.42f,
    val mirrorText: Boolean = false,
    val centerText: Boolean = true,
    val countdownSeconds: Int = 3,
    val restartEachTake: Boolean = true,
    val scrollWithRecording: Boolean = true,
)

data class CameraSettings(
    val lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    val quality: VideoQuality = VideoQuality.UHD,
    val fps: Int = 30,
    val bitrate: BitrateLevel = BitrateLevel.HIGH,
    val stabilization: Boolean = true,
    val mirrorFrontVideo: Boolean = false,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("teleprompter", Context.MODE_PRIVATE)

    var script: String
        get() = sp.getString("script", null) ?: DEFAULT_SCRIPT
        set(value) = sp.edit { putString("script", value) }

    var prompter: PrompterSettings
        get() {
            val d = PrompterSettings()
            return PrompterSettings(
                speed = sp.getFloat("speed", d.speed),
                fontSize = sp.getFloat("fontSize", d.fontSize),
                overlayOpacity = sp.getFloat("overlayOpacity", d.overlayOpacity),
                overlayHeight = sp.getFloat("overlayHeight", d.overlayHeight),
                mirrorText = sp.getBoolean("mirrorText", d.mirrorText),
                centerText = sp.getBoolean("centerText", d.centerText),
                countdownSeconds = sp.getInt("countdown", d.countdownSeconds),
                restartEachTake = sp.getBoolean("restartEachTake", d.restartEachTake),
                scrollWithRecording = sp.getBoolean("scrollWithRecording", d.scrollWithRecording),
            )
        }
        set(v) = sp.edit {
            putFloat("speed", v.speed)
            putFloat("fontSize", v.fontSize)
            putFloat("overlayOpacity", v.overlayOpacity)
            putFloat("overlayHeight", v.overlayHeight)
            putBoolean("mirrorText", v.mirrorText)
            putBoolean("centerText", v.centerText)
            putInt("countdown", v.countdownSeconds)
            putBoolean("restartEachTake", v.restartEachTake)
            putBoolean("scrollWithRecording", v.scrollWithRecording)
        }

    var camera: CameraSettings
        get() {
            val d = CameraSettings()
            return CameraSettings(
                lensFacing = sp.getInt("lensFacing", d.lensFacing),
                quality = enumOr(sp.getString("quality", null), d.quality),
                fps = sp.getInt("fps", d.fps),
                bitrate = enumOr(sp.getString("bitrate", null), d.bitrate),
                stabilization = sp.getBoolean("stabilization", d.stabilization),
                mirrorFrontVideo = sp.getBoolean("mirrorFrontVideo", d.mirrorFrontVideo),
            )
        }
        set(v) = sp.edit {
            putInt("lensFacing", v.lensFacing)
            putString("quality", v.quality.name)
            putInt("fps", v.fps)
            putString("bitrate", v.bitrate.name)
            putBoolean("stabilization", v.stabilization)
            putBoolean("mirrorFrontVideo", v.mirrorFrontVideo)
        }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        val DEFAULT_SCRIPT = """
            # Welcome to Tele-phone

            Paste your script here, or **share** text into the app from Docs, Notes, or anywhere else.

            Markdown works: **bold**, *italic*, ==highlight==, ~~strike~~, headings, lists and > quotes.

            [pause] Anything in square brackets shows up as a stage cue.

            - Tap the text to pause or resume scrolling
            - Pinch the picture to zoom, tap it to focus
            - Your Bluetooth remote starts and stops the take

            Takes are saved to **Movies/Teleprompter** and show up in your gallery.
        """.trimIndent()
    }
}
