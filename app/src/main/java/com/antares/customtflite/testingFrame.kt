package com.antares.customtflite

//Детекция запасдывает на пару кадров
//требуется ускорение отрисовки

/*videoView.onFrameCaptured = label@{ frame ->
                           val now = System.currentTimeMillis()
                           if (now - lastInferenceTime.value < inferenceIntervalMs) return@label
                           if (isRunning.value) return@label

                           lastInferenceTime.value = now
                           isRunning.value = true

                           val videoW = frame.width
                           val videoH = frame.height
                           val inferenceSize = 640

                           if (lastSize.value != videoW to videoH) {
                               overlayBitmapRef.value = Bitmap.createBitmap(inferenceSize, inferenceSize, Bitmap.Config.ARGB_8888)
                               drawerRef.value = YoloContourDrawer(
                                   displaySize = Size(videoW, videoH)
                               )
                               lastSize.value = videoW to videoH
                           }

                           val frozenFrame = frame.copy(Bitmap.Config.ARGB_8888, false)
                           val displaySize = drawerRef.value?.displaySize
                           Log.i("Video", "Frame=${frozenFrame.width}x${frozenFrame.height}, Display=${displaySize?.width}x${displaySize?.height}")
                           scope.launch {
                               try {
                                   val (contours, rawMask, objects) = withContext(Dispatchers.Default) {
                                       yolo.runInference(frozenFrame)
                                   }

                                   val baseOverlay = overlayBitmapRef.value
                                   val drawer = drawerRef.value

                                   if (baseOverlay != null && drawer != null) {
                                       baseOverlay.eraseColor(Color.TRANSPARENT)

                                       val filteredObjects = objects.filter { it.confidence >= 0.3f }

                                       val bboxList = filteredObjects.map { obj ->
                                           Triple(obj.topLeft, obj.bottomRight, obj.confidence)
                                       }

                                       val drawnOverlay = drawer.drawDetections(bboxList, contours)
                                       overlayBitmapRef.value = drawnOverlay
                                   }
                               } catch (e: Exception) {
                                   e.printStackTrace()
                               } finally {
                                   isRunning.value = false
                               }
                           }
                       }*/