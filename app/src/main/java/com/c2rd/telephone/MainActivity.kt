package com.c2rd.telephone

import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.c2rd.telephone.ui.PrompterScreen
import com.c2rd.telephone.ui.ScriptScreen
import com.c2rd.telephone.ui.readText

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private val remote = RemoteInput()
    private val sharedText = mutableStateOf<String?>(null)
    private var mediaSession: MediaSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)
        handleIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var prompting by rememberSaveable { mutableStateOf(false) }
                var script by remember { mutableStateOf(prefs.script) }
                val incoming by sharedText

                LaunchedEffect(incoming) {
                    incoming?.let {
                        script = it
                        prefs.script = it
                        sharedText.value = null
                        prompting = false
                    }
                }

                if (prompting) {
                    PrompterScreen(script, prefs, remote, onBack = { prompting = false })
                } else {
                    ScriptScreen(
                        script = script,
                        onScriptChange = {
                            script = it
                            prefs.script = it
                        },
                        onStart = { prompting = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: streamExtra(intent)?.let { readText(this, it) }
        if (!text.isNullOrBlank()) sharedText.value = text
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        remote.handle(event) || super.dispatchKeyEvent(event)

    // Headset / media remotes route through the active media session rather than the
    // focused window on some devices, so claim media buttons while we're in front.
    override fun onResume() {
        super.onResume()
        mediaSession = MediaSession(this, "Tele-phone").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = keyEventExtra(mediaButtonIntent) ?: return false
                    return remote.handle(event)
                }
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }
    }

    override fun onPause() {
        mediaSession?.release()
        mediaSession = null
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun keyEventExtra(intent: Intent): KeyEvent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
}
