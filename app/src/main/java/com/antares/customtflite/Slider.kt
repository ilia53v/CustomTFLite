package com.antares.customtflite

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VolumeSlider(volume: MutableFloatState, textureViewRef: MutableState<VideoGLTextureView?>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Громкость", modifier = Modifier.width(80.dp))
        Slider(
            value = volume.floatValue,
            onValueChange = {
                volume.floatValue = it
                textureViewRef.value?.setVolume(it)
            },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SpeedSlider(speed: MutableFloatState, textureViewRef: MutableState<VideoGLTextureView?>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Скорость", modifier = Modifier.width(80.dp))
        Slider(
            value = speed.floatValue,
            onValueChange = {
                speed.floatValue = it
                textureViewRef.value?.setPlaybackSpeed(it)
            },
            valueRange = 0.25f..2f,
            modifier = Modifier.weight(1f)
        )
    }
}