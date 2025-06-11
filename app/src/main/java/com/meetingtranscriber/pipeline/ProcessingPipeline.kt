package com.meetingtranscriber.pipeline

import android.content.Context
import com.meetingtranscriber.ml.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProcessingPipeline(context: Context, private val modelManager: ModelManager) {
    private val whisperASR = WhisperASR(modelManager, context)
    private val nllbTranslator = NLLBTranslator(modelManager, context)
    private val qwenSummarizer = QwenSummarizer(modelManager, context)
    private val speakerDiarization = SpeakerDiarization(modelManager, context)

    data class ProcessingResult(
        val transcription: String,
        val translation: String,
        val summary: String,
        val speakerSegments: List<SpeakerDiarization.SpeakerSegment>
    )

    suspend fun processAudio(audioFile: File, enableSpeakerDiarization: Boolean = true, progressCallback: (Int, String) -> Unit): ProcessingResult = withContext(Dispatchers.Default) {
        progressCallback(5, "오디오 파일 로딩 중...")
        val audioData = loadAudioFile(audioFile)
        
        progressCallback(15, "화자 분리 중...")
        val speakerSegments = if (enableSpeakerDiarization) {
            speakerDiarization.diarize(audioData, numSpeakers = 2)
        } else {
            emptyList()
        }
        
        progressCallback(35, "음성 인식 중...")
        val asrResult = whisperASR.transcribe(audioData)
        
        progressCallback(55, "번역 중...")
        val translated = nllbTranslator.translate(asrResult.text)
        
        progressCallback(75, "요약 중...")
        val summary = qwenSummarizer.summarize(translated)
        
        progressCallback(100, "완료")
        
        ProcessingResult(
            transcription = asrResult.text,
            translation = translated,
            summary = summary,
            speakerSegments = speakerSegments
        )
    }

    private fun loadAudioFile(audioFile: File): FloatArray {
        // 실제 구현에서는 WAV/PCM 파일을 FloatArray로 변환
        // 라이브러리: AudioSystem, javax.sound.sampled, FFmpeg JNI 등 활용
        return FloatArray(16000 * 60 * 30) { 0.0f } // 30분 샘플
    }
}