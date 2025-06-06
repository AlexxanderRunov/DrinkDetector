package com.example.blinkdetector

import com.google.mlkit.vision.face.Face

class SmokingDetector {

    /** Определяет, указывает ли лицо на возможный факт курения на основе эвристики */
    fun isSmoking(face: Face): Boolean {
        val smileProb = face.smilingProbability ?: -1f
        val leftEyeProb = face.leftEyeOpenProbability ?: -1f
        val rightEyeProb = face.rightEyeOpenProbability ?: -1f
        val rotY = face.headEulerAngleY    // поворот головы в сторону (в градусах)
        val rotX = face.headEulerAngleX    // наклон головы вверх/вниз (в градусах)

        // Условия эвристики
        val notSmiling = smileProb >= 0f && smileProb < 0.3f          // почти не улыбается
        val eyeSquint = (leftEyeProb >= 0f && leftEyeProb < 0.5f) ||
                (rightEyeProb >= 0f && rightEyeProb < 0.5f)   // хотя бы один глаз прищурен/прикрыт
        val headTurned = kotlin.math.abs(rotY) > 15                   // голова повернута в сторону более чем на 15°
        val headTilted = rotX > 10 || rotX < -10                      // голова заметно поднята вверх (>10°) или опущена вниз (<-10°)

        // Считаем, что человек курит, если все основные признаки совпадают
        return notSmiling && eyeSquint && (headTurned || headTilted)
    }
}
