package com.magikvlc.app.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import com.magikvlc.app.ui.wall.MosaicViewModel

private const val PIP_INTENTS_FILTER = "pip_action"
private const val PIP_INTENT_ACTION = "pip_action_code"

/**
 * Picture-in-Picture al estilo mpvEx adaptado a una activity única + Compose.
 * Las acciones (play/pause, ±10 s) las resuelve [MosaicViewModel] sobre el tile enfocado.
 */
class PipController(
    private val activity: Activity,
    private val viewModel: MosaicViewModel,
) {
    private var pipReceiver: BroadcastReceiver? = null

    fun canSupport(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun enterPip() {
        if (!canSupport()) return
        runCatching {
            activity.enterPictureInPictureMode(buildPipParams())
        }.onFailure {
            Log.e("PipController", "No se pudo entrar en PiP", it)
        }
    }

    fun refresh() {
        if (!canSupport()) return
        runCatching { activity.setPictureInPictureParams(buildPipParams()) }
    }

    fun onPipEntered() = registerReceiver()

    fun onPipExited() = unregisterReceiver()

    private fun buildPipParams(): PictureInPictureParams =
        PictureInPictureParams.Builder().apply {
            setAspectRatio(viewModel.pipAspectRatio() ?: Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(false)
            }
            setActions(createRemoteActions())
        }.build()

    private fun createRemoteActions(): List<RemoteAction> {
        val playing = viewModel.focusedTilePlaying()
        return listOf(
            createAction("Retroceder", android.R.drawable.ic_media_rew, ACTION_REWIND),
            if (playing) {
                createAction("Pausa", android.R.drawable.ic_media_pause, ACTION_PLAY_PAUSE)
            } else {
                createAction("Reproducir", android.R.drawable.ic_media_play, ACTION_PLAY_PAUSE)
            },
            createAction("Avanzar", android.R.drawable.ic_media_ff, ACTION_FORWARD),
        )
    }

    private fun createAction(title: String, icon: Int, actionCode: Int): RemoteAction {
        val intent = Intent(PIP_INTENTS_FILTER).apply {
            putExtra(PIP_INTENT_ACTION, actionCode)
            setPackage(activity.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            actionCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(activity, icon), title, title, pendingIntent)
    }

    private fun registerReceiver() {
        if (pipReceiver != null) return
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val code = intent?.getIntExtra(PIP_INTENT_ACTION, 0) ?: 0
                viewModel.pipAction(code)
                refresh()
            }
        }
        val filter = IntentFilter(PIP_INTENTS_FILTER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(pipReceiver, filter)
        }
    }

    private fun unregisterReceiver() {
        pipReceiver?.let {
            runCatching { activity.unregisterReceiver(it) }
            pipReceiver = null
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = 1
        const val ACTION_REWIND = 2
        const val ACTION_FORWARD = 3
    }
}