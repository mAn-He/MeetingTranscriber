package com.meetingtranscriber.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class WhisperASR(private val modelManager: ModelManager, private val context: android.content.Context) {
    data class TranscriptionResult(val text: String, val confidence: Float)

    suspend fun transcribe(audioData: FloatArray): TranscriptionResult = withContext(Dispatchers.Default) {
        val session = modelManager.getWhisperSession()
        val inputTensor = OnnxTensor.createTensor(
            session.environment,
            FloatBuffer.wrap(audioData),
            longArrayOf(1, audioData.size.toLong())
        )
        val results = session.run(mapOf("audio" to inputTensor))
        val outputTensor = results[0].value as Array<*>
        val tokens = outputTensor.map { (it as Number).toLong() }.toLongArray()
        val transcriptionText = TokenizerUtils.whisperDecode(tokens)
        inputTensor.close()
        results.close()
        TranscriptionResult(text = transcriptionText, confidence = 0.8f)
    }
}
