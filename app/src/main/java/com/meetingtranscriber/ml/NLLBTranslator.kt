package com.meetingtranscriber.ml

import ai.onnxruntime.OnnxTensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer

class NLLBTranslator(private val modelManager: ModelManager, private val context: android.content.Context) {
    suspend fun translate(text: String): String = withContext(Dispatchers.Default) {
        val session = modelManager.getNLLBSession()
        val tokens = TokenizerUtils.spEncode(text)
        val inputIds = OnnxTensor.createTensor(
            session.environment,
            LongBuffer.wrap(tokens),
            longArrayOf(1, tokens.size.toLong())
        )
        val results = session.run(mapOf("input_ids" to inputIds))
        val outputTensor = results[0].value as Array<LongArray>
        val translatedText = TokenizerUtils.spDecode(outputTensor[0])
        inputIds.close()
        results.close()
        translatedText
    }
}
