package com.magikvlc.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.magikvlc.app.pip.PipController
import com.magikvlc.app.ui.MagikVLCAppNav
import com.magikvlc.app.ui.Routes
import com.magikvlc.app.ui.theme.MagikVLCTheme
import com.magikvlc.app.ui.wall.MosaicViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MosaicViewModel by viewModels()
    private lateinit var pip: PipController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pip = PipController(this, viewModel)
        setContent {
            MagikVLCTheme {
                MagikVLCAppNav(
                    viewModel = viewModel,
                    onEnterPip = { pip.enterPip() },
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        if (!isInPictureInPictureMode &&
            viewModel.autoPip.value &&
            viewModel.currentRoute.value == Routes.WALL &&
            viewModel.canEnterPip()
        ) {
            pip.enterPip()
        }
        super.onUserLeaveHint()
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            pip.onPipEntered()
            pip.refresh()
        } else {
            pip.onPipExited()
        }
        viewModel.setPipMode(isInPictureInPictureMode)
    }
}