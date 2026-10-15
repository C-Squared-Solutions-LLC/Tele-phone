package com.c2rd.telephone

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf

enum class RemoteAction { TOGGLE_RECORD, TOGGLE_SCROLL, SCROLL_FORWARD, SCROLL_BACK }

/**
 * Maps key events from Bluetooth remotes to prompter actions.
 *
 * Cheap selfie shutters send VOLUME_UP (the "iOS" button) or ENTER (the "Android" button).
 * Headsets and media remotes send PLAY_PAUSE / HEADSETHOOK. Page turners send arrows or PAGE_UP/DOWN.
 */
class RemoteInput {
    /** Set while the prompter screen is showing. Null means keys pass through untouched. */
    var listener: ((RemoteAction) -> Unit)? = null

    /** Last key seen, shown in settings so you can tell what your remote actually sends. */
    val lastKey = mutableStateOf<String?>(null)

    private var lastAction: RemoteAction? = null
    private var lastFireAt = 0L

    fun handle(event: KeyEvent): Boolean {
        val l = listener ?: return false
        val action = actionFor(event.keyCode) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            lastKey.value = KeyEvent.keyCodeToString(event.keyCode)
            val now = SystemClock.uptimeMillis()
            // Some remotes fire two codes per press (e.g. ENTER + VOLUME_UP), and the media
            // session can echo the same press. Swallow repeats of one action inside 400ms.
            val bounce = action == lastAction && now - lastFireAt < 400
            lastAction = action
            lastFireAt = now
            if (!bounce) l(action)
        }
        return true
    }

    private fun actionFor(code: Int): RemoteAction? = when (code) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_RECORD,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_CAMERA,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteAction.TOGGLE_RECORD

        KeyEvent.KEYCODE_SPACE -> RemoteAction.TOGGLE_SCROLL

        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> RemoteAction.SCROLL_FORWARD

        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_REWIND -> RemoteAction.SCROLL_BACK

        else -> null
    }
}
