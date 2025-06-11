package com.meetingtranscriber.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * 실제 오디오 파일 처리 - WAV, MP3, M4A 지원
 */
class AudioFileProcessor(private val context: Context) {

    data class AudioInfo(
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long,
        val format: String
    )

    suspend fun getAudioInfo(audioFile: File): AudioInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(audioFile.absolutePath)
            
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 16000
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "unknown"
            
            AudioInfo(
                sampleRate = 16000, // Whisper는 16kHz 고정
                channels = 1, // Mono
                durationMs = duration,
                format = mimeType
            )
        } finally {
            retriever.release()
        }
    }

    suspend fun loadAudioFileStreaming(
        audioFile: File, 
        progressCallback: (Float) -> Unit
    ): FloatArray = withContext(Dispatchers.IO) {
        
        when (audioFile.extension.lowercase()) {
            "wav" -> loadWavFile(audioFile, progressCallback)
            "mp3", "m4a", "aac" -> loadCompressedAudioFile(audioFile, progressCallback)
            else -> throw UnsupportedOperationException("Unsupported audio format: ${audioFile.extension}")
        }
    }

    private suspend fun loadWavFile(
        audioFile: File, 
        progressCallback: (Float) -> Unit
    ): FloatArray = withContext(Dispatchers.IO) {
        
        FileInputStream(audioFile).use { fis ->
            // WAV 헤더 읽기
            val header = ByteArray(44)
            fis.read(header)
            
            // 헤더 정보 파싱
            val channels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            
            // 데이터 크기 계산
            val dataSize = audioFile.length() - 44
            val samplesCount = (dataSize / (bitsPerSample / 8) / channels).toInt()
            
            // 오디오 데이터를 청크 단위로 읽기 (메모리 효율성)
            val chunkSize = 8192 // 8KB 청크
            val audioData = mutableListOf<Float>()
            val buffer = ByteArray(chunkSize)
            var bytesRead = 0L
            
            while (true) {
                val read = fis.read(buffer)
                if (read == -1) break
                
                bytesRead += read
                progressCallback(bytesRead.toFloat() / dataSize)
                
                // 16-bit PCM to Float 변환
                for (i in 0 until read step 2) {
                    if (i + 1 < read) {
                        val sample = ByteBuffer.wrap(buffer, i, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short.toFloat() / 32768.0f
                        audioData.add(sample)
                    }
                }
            }
            
            // 16kHz로 리샘플링 (필요시)
            val resampledData = if (sampleRate != 16000) {
                resample(audioData.toFloatArray(), sampleRate, 16000)
            } else {
                audioData.toFloatArray()
            }
            
            // 모노로 변환 (필요시)
            if (channels > 1) {
                convertToMono(resampledData, channels)
            } else {
                resampledData
            }
        }
    }

    private suspend fun loadCompressedAudioFile(
        audioFile: File, 
        progressCallback: (Float) -> Unit
    ): FloatArray = withContext(Dispatchers.IO) {
        
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(audioFile.absolutePath)
            
            // 오디오 트랙 찾기
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                throw IllegalArgumentException("No audio track found in file")
            }
            
            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            // MediaExtractor로 원시 PCM 데이터 추출
            val audioData = mutableListOf<Float>()
            val buffer = ByteBuffer.allocate(8192)
            var totalSize = 0L
            
            // 파일 크기 추정 (진행률 계산용)
            val estimatedSize = audioFile.length()
            
            while (true) {
                val bytesRead = extractor.readSampleData(buffer, 0)
                if (bytesRead < 0) break
                
                totalSize += bytesRead
                progressCallback(totalSize.toFloat() / estimatedSize)
                
                // 압축 해제된 PCM 데이터를 Float로 변환
                buffer.rewind()
                for (i in 0 until bytesRead step 2) {
                    if (i + 1 < bytesRead) {
                        val sample = buffer.getShort(i).toFloat() / 32768.0f
                        audioData.add(sample)
                    }
                }
                
                extractor.advance()
                buffer.clear()
            }
            
            // 16kHz 모노로 변환
            var result = audioData.toFloatArray()
            if (sampleRate != 16000) {
                result = resample(result, sampleRate, 16000)
            }
            if (channels > 1) {
                result = convertToMono(result, channels)
            }
            
            result
            
        } finally {
            extractor.release()
        }
    }

    private fun resample(audioData: FloatArray, fromSampleRate: Int, toSampleRate: Int): FloatArray {
        if (fromSampleRate == toSampleRate) return audioData
        
        val ratio = fromSampleRate.toDouble() / toSampleRate
        val newLength = (audioData.size / ratio).toInt()
        val resampled = FloatArray(newLength)
        
        for (i in resampled.indices) {
            val srcIndex = (i * ratio).toInt()
            resampled[i] = if (srcIndex < audioData.size) audioData[srcIndex] else 0f
        }
        
        return resampled
    }

    private fun convertToMono(audioData: FloatArray, channels: Int): FloatArray {
        if (channels == 1) return audioData
        
        val monoData = FloatArray(audioData.size / channels)
        for (i in monoData.indices) {
            var sum = 0f
            for (ch in 0 until channels) {
                sum += audioData[i * channels + ch]
            }
            monoData[i] = sum / channels
        }
        
        return monoData
    }
}