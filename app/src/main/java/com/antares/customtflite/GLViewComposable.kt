package com.antares.customtflite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun GLViewComposable(contours: List<Contour>) {
    AndroidView(
        factory = { context -> GLView(context) },
        modifier = Modifier.fillMaxSize(),
        update = { view -> view.updateContours(contours) }
    )
}