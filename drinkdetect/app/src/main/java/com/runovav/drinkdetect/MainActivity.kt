package com.runovav.drinkdetect
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.runovav.drinkdetect.ui.theme.DrinkdetectTheme
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrinkdetectTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FaceDetectionApp()
                }
            }
        }
    }
}

@Composable
fun FaceDetectionApp() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraScreen()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required to use this feature.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // State for detected faces and image dimensions (from the analyzer)
    var detectedFaces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var detectedBottles by remember { mutableStateOf<List<RectF>>(emptyList()) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // Configure ML Kit Face Detector
    val faceDetectorOptions = remember {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    }
    val faceDetector = remember { FaceDetection.getClient(faceDetectorOptions) }

    val customObjectDetectorOptions = remember {
        ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(0.15f)
            .setLabelAllowList(listOf("bottle", "toothbrush"))
            .build()
    }

    val objectDetector = remember {
        ObjectDetector.createFromFileAndOptions(
            context, // the application context
            "efficientdet.tflite", // must be same as the filename in assets folder
            customObjectDetectorOptions
        )
    }

    // Face Detection Analyzer
    val faceDetectionAnalyzer = remember(faceDetector) {
        DetectionAnalyzer(
            faceDetector = faceDetector,
            objectDetector = objectDetector,
            onFacesDetected = { faces, width, height ->
                detectedFaces = faces
                imageWidth = width
                imageHeight = height
            },
            onBottlesDetected = { bottles ->
                detectedBottles = bottles
            }
        )
    }

    val facesWithBottles = filterFacesWithBottlesInFront(detectedFaces.map { f -> f.boundingBox.toRectF() }, detectedBottles)

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            faceDetector.close() // Release ML Kit detector resources
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480)) // Analyze lower resolution for performance
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                imageAnalysis.setAnalyzer(cameraExecutor, faceDetectionAnalyzer)

                try {
                    cameraProvider.unbindAll() // Unbind use cases before rebinding
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e("CameraScreen", "Use case binding failed", exc)
                }
                previewView
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView -> // Re-bind on cameraSelector change
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(cameraExecutor, faceDetectionAnalyzer)

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e("CameraScreen", "Use case re-binding failed", exc)
                }
            }
        )

        FaceOverlay(
            faces = facesWithBottles,
            originalImageWidth = imageWidth,
            originalImageHeight = imageHeight,
            isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA,
            modifier = Modifier.fillMaxSize()
        )

        BottleOverlay(
            bottles = detectedBottles,
            originalImageWidth = 1080,
            originalImageHeight = 1080,
            isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA,
            modifier = Modifier.fillMaxSize()
        )

        // Button to switch camera
        FloatingActionButton (
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            onClick = {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
            }
        ) {
            Icon(Icons.Default.Cameraswitch, "")
        }

        Button(
            onClick = {

            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Text("Пьющих из бутылок: "+ facesWithBottles.size.toString())
        }
    }
}



