package com.antares.customtflite

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor


@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val yolo = YoloV8Segmentor(context)
    //PortretVideoInferenceWithOverlayScreen(yolo)
    LandscapeVideoInferenceWithOverlayScreen(yolo)
}


