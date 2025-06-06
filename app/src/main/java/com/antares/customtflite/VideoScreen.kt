package com.antares.customtflite

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/*
@Composable
fun VideoScreen(
    modifier: Modifier = Modifier,
    videoUri: Uri,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ExoPlayer instance
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    val glView = remember { GLView(context) }
    val segmentor = remember { YoloV8Segmentor(context) }

    val frameExtractor = remember {
        VideoFrameExtractor(
            context = context,
            videoUri = videoUri,
            segmentor = segmentor,
        ) { contours ->
            glView.updateContours(contours)
        }
    }

    // Lifecycle observer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _: LifecycleOwner, event: Lifecycle.Event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    player.playWhenReady = true
                    frameExtractor.start()
                }

                Lifecycle.Event.ON_STOP -> {
                    player.playWhenReady = false
                    frameExtractor.stop()
                }

                Lifecycle.Event.ON_DESTROY -> {
                    player.release()
                    frameExtractor.stop()
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
            frameExtractor.stop()
        }
    }

    // Compose UI
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                FrameLayout(it).apply {
                    val playerView = PlayerView(it).apply {
                        useController = false
                        this.player = player
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                    addView(playerView)
                    addView(glView)
                }
            }
        )
    }
}*/
