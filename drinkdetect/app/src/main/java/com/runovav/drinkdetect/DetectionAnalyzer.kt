package com.runovav.drinkdetect

import android.annotation.SuppressLint
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class DetectionAnalyzer(
    private val faceDetector: FaceDetector,
    private val objectDetector: ObjectDetector,
    private val onFacesDetected: (faces: List<Face>, imageWidth: Int, imageHeight: Int) -> Unit,
    private val onBottlesDetected: (bottles: List<RectF>) -> Unit
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val imageWidth = imageProxy.width // or image.width, but imageProxy is directly from camera
            val imageHeight = imageProxy.height // or image.height

            val imageProcessor = ImageProcessor.Builder()
                .add(Rot90Op(-imageProxy.imageInfo.rotationDegrees / 90)) // Compensate for rotation to make image upright
                .build()

            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(imageProxy.toBitmap()))

            scope.launch {
                try {
                    val faces = faceDetector.process(image).await()
                    val objects = objectDetector.detect(tensorImage)

                    onBottlesDetected(objects.map { o -> o.boundingBox})
                    onFacesDetected(faces, imageWidth, imageHeight)
                } catch (e: Exception) {
                    Log.e("FaceDetectionAnalyzer", "Face detection failed", e)
                } finally {
                    imageProxy.close() // Crucial to close the ImageProxy
                }
            }
        } else {
            imageProxy.close() // Also close if mediaImage is null
        }
    }
}