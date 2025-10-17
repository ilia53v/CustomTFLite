package com.antares.customtflite

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.antares.customtflite.ver2.LandscapeVideoInferenceWithOverlayScreen
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import com.antares.customtflite.ver2.segmentor.YoloV8SegmentorV2

/*
import com.antares.customtflite.ver3.VideoInferenceWithOverlayScreen
*/


@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val yolo = YoloV8SegmentorV2(context)
    //PortretVideoInferenceWithOverlayScreen(yolo)
    LandscapeVideoInferenceWithOverlayScreen(yolo)
    //VideoInferenceWithOverlayScreen(yolo)
}


