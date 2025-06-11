package com.meetingtranscriber.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ModelDownloader {
    private val client = OkHttpClient()

    // 모델/토크나이저 파일명과 다운로드 URL 매핑
    private val modelUrls = mapOf(
        // Whisper
        "whisper_tiny_int8.onnx" to "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/models/ggml-tiny.en-q8_0.bin",
        "vocab.json" to "https://huggingface.co/openai/whisper-tiny/resolve/main/vocab.json",
        "merges.txt" to "https://huggingface.co/openai/whisper-tiny/resolve/main/merges.txt",
        // NLLB
        "nllb_600m_int8.onnx" to "https://huggingface.co/facebook/nllb-200-distilled-600M/resolve/main/model.onnx",
        "sentencepiece.bpe.model" to "https://huggingface.co/facebook/nllb-200-distilled-600M/resolve/main/sentencepiece.bpe.model",
        // Qwen
        "qwen2.5_1.8b_int8.onnx" to "https://huggingface.co/Qwen/Qwen2.5-1.8B-Chat/resolve/main/model.onnx",
        "qwen_tokenizer.json" to "https://huggingface.co/Qwen/Qwen2.5-1.8B-Chat/resolve/main/tokenizer.json",
        // WavLM (Speaker Diarization)
        "wavlm_base_plus.onnx" to "https://huggingface.co/microsoft/wavlm-base-plus/resolve/main/model.onnx"
    )

    suspend fun ensureModels(context: Context, progressCallback: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        for ((fileName, url) in modelUrls) {
            val outFile = File(modelsDir, fileName)
            if (!outFile.exists()) {
                progressCallback(fileName, 0)
                downloadFile(url, outFile, progressCallback)
            } else {
                progressCallback(fileName, 100)
            }
        }
    }

    private fun downloadFile(url: String, outFile: File, progressCallback: (String, Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download $url: ${response.code}")
            val body = response.body ?: throw IOException("No response body for $url")
            val total = body.contentLength()
            val input = body.byteStream()
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    val percent = if (total > 0) (downloaded * 100 / total).toInt() else -1
                    progressCallback(outFile.name, percent)
                }
            }
        }
    }
}