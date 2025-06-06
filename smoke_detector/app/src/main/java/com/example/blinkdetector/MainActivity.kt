package com.example.blinkdetector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusTextView: TextView
    private lateinit var historyRecyclerView: RecyclerView

    private lateinit var faceDetector: FaceDetector
    private lateinit var smokingDetector: SmokingDetector

    private val historyList: MutableList<String> = mutableListOf()
    private lateinit var historyAdapter: HistoryAdapter

    private lateinit var cameraExecutor: ExecutorService
    private var wasSmoking: Boolean = false  // для отслеживания предыдущего состояния

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // макет с PreviewView, TextView и RecyclerView

        // Инициализация элементов UI
        previewView = findViewById(R.id.previewView)
        statusTextView = findViewById(R.id.statusTextView)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)

        // Настройка списка (RecyclerView) для истории
        historyAdapter = HistoryAdapter(historyList)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter

        // Инициализация детектора лиц ML Kit с необходимыми опциями
        val detectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)  // включаем классификацию (улыбка, глаза)
            .enableTracking()  // разрешаем трекинг лица между кадрами (необязательно)
            .build()
        faceDetector = FaceDetection.getClient(detectorOptions)

        smokingDetector = SmokingDetector()  // наш детектор "курения" на основе эвристики

        cameraExecutor = Executors.newSingleThreadExecutor()  // отдельный поток для обработки кадров

        // Проверяем разрешения камеры и запускаем камеру
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    /** Запуск CameraX для отображения превью и анализа изображений */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Получаем CameraProvider
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Подготавливаем Preview (превью с камеры)
            val preview = androidx.camera.core.Preview.Builder().build().also { preview ->
                preview.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Подготавливаем анализатор изображений
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)  // чтобы не накапливать кадры
                .build()

            // Устанавливаем анализатор кадров
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            // Выбираем камеру: фронтальная камера (для отслеживания лица пользователя)
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Отвязываем все предыдущие привязки (если были)
                cameraProvider.unbindAll()
                // Привязываем превью и анализ к жизненному циклу Activity
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Ошибка запуска камеры: ${exc.message}", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Обработка каждого кадра камеры с помощью ML Kit Face Detection */
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // Преобразуем кадр в формат, пригодный для ML Kit
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            // Запускаем асинхронную обработку - поиск лиц на изображении
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        // Берём первое обнаруженное лицо (предполагаем одно лицо в кадре)
                        val face = faces[0]
                        val isSmoking = smokingDetector.isSmoking(face)
                        // Обновляем UI: статус курения
                        runOnUiThread {
                            if (isSmoking) {
                                statusTextView.text = "Курение обнаружено"
                                if (!wasSmoking) {
                                    // Если ранее не было состояния "курение", фиксируем новое событие
                                    addSmokingEvent()
                                    wasSmoking = true
                                }
                            } else {
                                statusTextView.text = "Курение не обнаружено"
                                wasSmoking = false
                            }
                        }
                    } else {
                        // Лицо не обнаружено – считаем, что курение не обнаружено
                        runOnUiThread {
                            statusTextView.text = "Курение не обнаружено"
                            wasSmoking = false
                        }
                    }
                }
                .addOnFailureListener { e ->
                    // При ошибке распознавания можно вывести лог
                    Log.e(TAG, "Ошибка детекции лица: ${e.message}", e)
                }
                .addOnCompleteListener {
                    // Освобождаем ресурс кадра для следующего
                    imageProxy.close()
                }
        } else {
            // Если нет изображения, освобождаем ресурс сразу
            imageProxy.close()
        }
    }

    /** Добавляет запись о случае курения в список истории с текущим временем */
    private fun addSmokingEvent() {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val eventText = "Курение обнаружено в $timestamp"
        historyList.add(eventText)
        // Уведомляем адаптер об обновлении данных (новый элемент в конце)
        historyAdapter.notifyItemInserted(historyList.size - 1)
        // Прокручиваем список к последнему событию, чтобы оно было видно
        historyRecyclerView.smoothScrollToPosition(historyList.size - 1)
    }

    /** Проверка разрешения на использование камеры */
    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    /** Обработка результата запроса разрешения */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                // В реальном приложении стоило бы уведомить пользователя, что без камеры функционал недоступен
                finish()  // Закрываем приложение, так как нет разрешения
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Освобождаем ресурсы: останавливаем поток и детектор лиц
        cameraExecutor.shutdown()
        faceDetector.close()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CAMERA_PERMISSION = 1001
    }
}
