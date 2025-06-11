package com.meetingtranscriber.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.*

class SpeakerDiarization(private val modelManager: ModelManager, private val context: android.content.Context) {
    
    data class SpeakerSegment(
        val speakerId: Int,
        val startTime: Float,
        val endTime: Float,
        val confidence: Float
    )

    suspend fun diarize(audioData: FloatArray, numSpeakers: Int = 2): List<SpeakerSegment> = withContext(Dispatchers.Default) {
        val session = modelManager.getWavLMSession()
        
        // 오디오를 겹치는 세그먼트로 분할 (3초씩, 1.5초 겹침)
        val segmentLength = 16000 * 3 // 3초
        val hopLength = 16000 * 1.5.toInt() // 1.5초
        val segments = mutableListOf<FloatArray>()
        
        for (i in 0 until audioData.size step hopLength) {
            val end = minOf(i + segmentLength, audioData.size)
            val segment = audioData.sliceArray(i until end)
            if (segment.size >= segmentLength / 2) { // 최소 1.5초 이상만 처리
                segments.add(padOrTruncate(segment, segmentLength))
            }
        }
        
        // 각 세그먼트에서 화자 임베딩 추출
        val embeddings = segments.map { segment ->
            extractSpeakerEmbedding(session, segment)
        }
        
        // Spectral Clustering으로 화자 구분
        val speakerLabels = spectralClustering(embeddings, numSpeakers)
        
        // 결과를 SpeakerSegment 리스트로 변환
        val speakerSegments = mutableListOf<SpeakerSegment>()
        for (i in speakerLabels.indices) {
            val startTime = i * 1.5f // hop length in seconds
            val endTime = startTime + 3.0f // segment length in seconds
            speakerSegments.add(
                SpeakerSegment(
                    speakerId = speakerLabels[i],
                    startTime = startTime,
                    endTime = endTime,
                    confidence = 0.85f
                )
            )
        }
        
        // 연속된 같은 화자 세그먼트 병합
        mergeConsecutiveSegments(speakerSegments)
    }
    
    private suspend fun extractSpeakerEmbedding(session: OrtSession, audioSegment: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        val inputTensor = OnnxTensor.createTensor(
            session.environment,
            FloatBuffer.wrap(audioSegment),
            longArrayOf(1, audioSegment.size.toLong())
        )
        
        val results = session.run(mapOf("audio" to inputTensor))
        val outputTensor = results[0].value as Array<FloatArray>
        val embedding = outputTensor[0]
        
        inputTensor.close()
        results.close()
        
        // L2 정규화
        val norm = sqrt(embedding.map { it * it }.sum())
        embedding.map { it / norm }.toFloatArray()
    }
    
    private fun spectralClustering(embeddings: List<FloatArray>, numClusters: Int): IntArray {
        val n = embeddings.size
        val affinityMatrix = Array(n) { FloatArray(n) }
        
        // 코사인 유사도 기반 친화도 행렬 구성
        for (i in 0 until n) {
            for (j in i until n) {
                val similarity = cosineSimilarity(embeddings[i], embeddings[j])
                affinityMatrix[i][j] = similarity
                affinityMatrix[j][i] = similarity
            }
        }
        
        // 간단한 K-means 기반 클러스터링 (실제로는 spectral clustering 구현 필요)
        return kMeansClustering(embeddings, numClusters)
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
    
    private fun kMeansClustering(embeddings: List<FloatArray>, k: Int): IntArray {
        val n = embeddings.size
        val dim = embeddings[0].size
        val labels = IntArray(n)
        val centroids = Array(k) { FloatArray(dim) }
        
        // 랜덤 초기화
        for (i in 0 until k) {
            val randomIdx = (0 until n).random()
            centroids[i] = embeddings[randomIdx].copyOf()
        }
        
        // K-means 반복
        repeat(20) { // 최대 20회 반복
            // 각 점을 가장 가까운 중심에 할당
            for (i in 0 until n) {
                var minDistance = Float.MAX_VALUE
                var bestCluster = 0
                
                for (j in 0 until k) {
                    val distance = euclideanDistance(embeddings[i], centroids[j])
                    if (distance < minDistance) {
                        minDistance = distance
                        bestCluster = j
                    }
                }
                
                labels[i] = bestCluster
            }
            
            // 중심 업데이트
            for (j in 0 until k) {
                val clusterPoints = embeddings.indices.filter { labels[it] == j }
                if (clusterPoints.isNotEmpty()) {
                    for (d in 0 until dim) {
                        centroids[j][d] = clusterPoints.map { embeddings[it][d] }.average().toFloat()
                    }
                }
            }
        }
        
        return labels
    }
    
    private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        return sqrt(a.indices.map { (a[it] - b[it]).pow(2) }.sum())
    }
    
    private fun padOrTruncate(array: FloatArray, targetLength: Int): FloatArray {
        return when {
            array.size == targetLength -> array
            array.size < targetLength -> array + FloatArray(targetLength - array.size)
            else -> array.sliceArray(0 until targetLength)
        }
    }
    
    private fun mergeConsecutiveSegments(segments: List<SpeakerSegment>): List<SpeakerSegment> {
        if (segments.isEmpty()) return segments
        
        val merged = mutableListOf<SpeakerSegment>()
        var current = segments[0]
        
        for (i in 1 until segments.size) {
            val next = segments[i]
            if (current.speakerId == next.speakerId && abs(current.endTime - next.startTime) < 1.0f) {
                // 같은 화자이고 시간이 연속적이면 병합
                current = current.copy(endTime = next.endTime)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        
        return merged
    }
}