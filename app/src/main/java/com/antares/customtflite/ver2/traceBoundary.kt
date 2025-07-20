package com.antares.customtflite.ver2

import android.graphics.Bitmap
import com.antares.customtflite.data.Pixel

public fun traceBoundary(
    binary: Array<BooleanArray>,
    visited: Array<BooleanArray>,
    startX: Int,
    startY: Int
): List<Pair<Int, Int>> {
    val w = binary[0].size
    val h = binary.size

    val directions = listOf(
        Pair(1, 0), Pair(1, 1), Pair(0, 1), Pair(-1, 1),
        Pair(-1, 0), Pair(-1, -1), Pair(0, -1), Pair(1, -1)
    )

    val contour = mutableListOf<Pair<Int, Int>>()
    var cx = startX
    var cy = startY
    var dir = 0

    do {
        contour.add(Pair(cx, cy))
        visited[cy][cx] = true

        var found = false
        for (i in 0..7) {
            val ndir = (dir + i) % 8
            val nx = cx + directions[ndir].first
            val ny = cy + directions[ndir].second

            if (nx in 0 until w && ny in 0 until h && binary[ny][nx] && !visited[ny][nx]) {
                cx = nx
                cy = ny
                dir = (ndir + 5) % 8 // направление "вперёд" по контуру
                found = true
                break
            }
        }

        if (!found) break

    } while ((cx != startX || cy != startY) && contour.size < 1000)

    return contour
}