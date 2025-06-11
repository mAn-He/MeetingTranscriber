package com.meetingtranscriber.pipeline

import android.content.Context
import com.meetingtranscriber.audio.AudioFileProcessor
import com.meetingtranscriber.ml.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 메모리 최적화된 처리 파이프라인 - 동적 모델 로딩/해제
 */
class OptimizedProcessingPipeline(
    private val context: Context, 
    private val modelManager: OptimizedModelManager
) {
    private val audioProcessor = AudioFileProcessor(context)

    data class ProcessingResult(
        val transcription: String,
        val translation: String,
        val summary: String,
        val speakerSegments: List<SpeakerDiarization.SpeakerSegment>,
        val processingStats: ProcessingStats
    )

    data class ProcessingStats(
        val totalTimeMs: Long,
        val audioLengthMs: Long,
        val peakMemoryUsageMB: Long,
        val modelLoadTimes: Map<String, Long>
    )

    suspend fun processAudio(
        audioFile: File, 
        enableSpeakerDiarization: Boolean = true, 
        progressCallback: (Int, String) -> Unit
    ): ProcessingResult = withContext(Dispatchers.Default) {
        
        val startTime = System.currentTimeMillis()
        val modelLoadTimes = mutableMapOf<String, Long>()
        var peakMemoryUsage = 0L

        try {
            progressCallback(5, "오디오 파일 분석 중...")
            val audioInfo = audioProcessor.getAudioInfo(audioFile)
            
            progressCallback(10, "오디오 데이터 로딩 중...")
            val audioData = audioProcessor.loadAudioFileStreaming(audioFile) { fileProgress ->
                val progress = 10 + (fileProgress * 10).toInt()
                progressCallback(progress, "오디오 로딩 중... ${(fileProgress * 100).toInt()}%")
            }

            // 화자 분리 (선택적)
            val speakerSegments = if (enableSpeakerDiarization) {
                progressCallback(25, "화자 분리 중...")
                val loadStart = System.currentTimeMillis()
                val session = modelManager.loadModel(OptimizedModelManager.ModelType.WAVLM)
                modelLoadTimes["WavLM"] = System.currentTimeMillis() - loadStart
                
                val diarization = SpeakerDiarization(modelManager, context)
                val segments = diarization.diarizeWithSession(session, audioData, numSpeakers = 2)
                
                // WavLM 모델 즉시 해제 (메모리 절약)
                modelManager.releaseCurrentModel()
                peakMemoryUsage = maxOf(peakMemoryUsage, modelManager.getMemoryUsage().usedMemoryMB)
                
                segments
            } else {
                emptyList()
            }

            // ASR (음성 인식)
            progressCallback(40, "음성 인식 중...")
            val loadStart1 = System.currentTimeMillis()
            val whisperSession = modelManager.loadModel(OptimizedModelManager.ModelType.WHISPER)
            modelLoadTimes["Whisper"] = System.currentTimeMillis() - loadStart1
            
            val whisperASR = WhisperASR(modelManager, context)
            val transcriptionResult = whisperASR.transcribeWithSession(whisperSession, audioData)
            
            // Whisper 모델 해제
            modelManager.releaseCurrentModel()
            peakMemoryUsage = maxOf(peakMemoryUsage, modelManager.getMemoryUsage().usedMemoryMB)

            // 번역
            progressCallback(60, "번역 중...")
            val loadStart2 = System.currentTimeMillis()
            val nllbSession = modelManager.loadModel(OptimizedModelManager.ModelType.NLLB)
            modelLoadTimes["NLLB"] = System.currentTimeMillis() - loadStart2
            
            val translator = NLLBTranslator(modelManager, context)
            val translation = translator.translateWithSession(nllbSession, transcriptionResult.text)
            
            // NLLB 모델 해제
            modelManager.releaseCurrentModel()
            peakMemoryUsage = maxOf(peakMemoryUsage, modelManager.getMemoryUsage().usedMemoryMB)

            // 요약
            progressCallback(80, "요약 중...")
            val loadStart3 = System.currentTimeMillis()
            val qwenSession = modelManager.loadModel(OptimizedModelManager.ModelType.QWEN)
            modelLoadTimes["Qwen"] = System.currentTimeMillis() - loadStart3
            
            val summarizer = QwenSummarizer(modelManager, context)
            val summary = summarizer.summarizeWithSession(qwenSession, translation)
            
            // Qwen 모델 해제
            modelManager.releaseCurrentModel()
            peakMemoryUsage = maxOf(peakMemoryUsage, modelManager.getMemoryUsage().usedMemoryMB)

            progressCallback(100, "완료")

            val totalTime = System.currentTimeMillis() - startTime
            val processingStats = ProcessingStats(
                totalTimeMs = totalTime,
                audioLengthMs = audioInfo.durationMs,
                peakMemoryUsageMB = peakMemoryUsage,
                modelLoadTimes = modelLoadTimes
            )

            ProcessingResult(
                transcription = transcriptionResult.text,
                translation = translation,
                summary = summary,
                speakerSegments = speakerSegments,
                processingStats = processingStats
            )

        } finally {
            // 모든 리소스 해제
            modelManager.releaseWakeLock()
            System.gc()
        }
    }
}