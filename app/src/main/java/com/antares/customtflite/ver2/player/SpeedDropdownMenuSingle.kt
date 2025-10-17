package com.antares.customtflite.ver2.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDropdownMenuSingle(
    availableSpeeds: List<Float> = listOf(0.1f, 0.15f, 0.2f, 0.25f, 0.5f, 0.75f, 1f, 1.25f),
    selectedSpeed: Float? = null,
    onSpeedSelected: (Float) -> Unit,
    videoViewRef: MutableState<VideoGLTextureView?>
) {
    var expanded by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(selectedSpeed ?: 1f) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded } // <- управляющий флаг
    ) {
        OutlinedTextField(
            value = "${currentSpeed}x",
            onValueChange = {},
            label = { Text("Скорость") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor() // <- очень важно, чтобы меню привязывалось
                .width(125.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableSpeeds.forEach { speed ->
                DropdownMenuItem(
                    text = { Text("${speed}x") },
                    onClick = {
                        currentSpeed = speed
                        onSpeedSelected(speed)
                        videoViewRef.value?.setPlaybackSpeed(speed)
                        expanded = false
                    }
                )
            }
        }
    }
}


