package com.meetingtranscriber.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

class QwenSummarizer(private val modelManager: ModelManager, private val context: android.content.Context) {
    suspend fun summarize(text: String): String = withContext(Dispatchers.Default) {
        // Qwen 토크나이저 JNI로 인코딩
        val inputTokens = QwenTokenizerUtils.encode(text)
        val session = modelManager.getQwenSession()
        val inputTensor = OnnxTensor.createTensor(
            session.environment,
            LongBuffer.wrap(inputTokens),
            longArrayOf(1, inputTokens.size.toLong())
        )
        val results = session.run(mapOf("input_ids" to inputTensor))
        val outputTensor = results[0].value as Array<LongArray>
        val summary = QwenTokenizerUtils.decode(outputTensor[0])
        inputTensor.close()
        results.close()
        summary
    }
}
