package com.meetingtranscriber

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.meetingtranscriber.audio.AudioRecorder
import com.meetingtranscriber.ml.ModelDownloader
import com.meetingtranscriber.ml.ModelManager
import com.meetingtranscriber.pipeline.ProcessingPipeline
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var modelManager: ModelManager
    private lateinit var processingPipeline: ProcessingPipeline
    private var recordingFile: String? = null
    private var isRecording = false

    private lateinit var btnRecord: Button
    private lateinit var btnStop: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var txtStatus: TextView
    private lateinit var txtResult: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            initializeApp()
        } else {
            Toast.makeText(this, "권한이 필요합니다", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        checkPermissions()
    }
    
    private fun initializeViews() {
        btnRecord = findViewById(R.id.btnRecord)
        btnStop = findViewById(R.id.btnStop)
        progressBar = findViewById(R.id.progressBar)
        txtStatus = findViewById(R.id.txtStatus)
        txtResult = findViewById(R.id.txtResult)
        
        btnRecord.setOnClickListener { startRecording() }
        btnStop.setOnClickListener { stopRecordingAndProcess() }
        
        btnRecord.isEnabled = false
        btnStop.isEnabled = false
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
        )
        if (permissions.any {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            permissionLauncher.launch(permissions)
        } else {
            initializeApp()
        }
    }

    private fun initializeApp() {
        lifecycleScope.launch {
            try {
                updateStatus("시스템 상태 확인 중...")
                
                // 시스템 헬스 체크
                val healthStatus = HealthChecker.checkSystemHealth(this@MainActivity)
                val healthReport = HealthChecker.generateHealthReport(healthStatus)
                
                if (!healthStatus.isHealthy) {
                    txtResult.text = healthReport
                    updateStatus("시스템 문제 발견. 세부사항을 확인하세요.")
                    return@launch
                }
                
                if (healthStatus.warnings.isNotEmpty()) {
                    Toast.makeText(this@MainActivity, "${healthStatus.warnings.size}개 경고 발견", Toast.LENGTH_SHORT).show()
                }
                
                updateStatus("모델 다운로드 및 초기화 중...")
                
                // 모델 다운로드
                ModelDownloader.ensureModels(this@MainActivity) { file, percent ->
                    runOnUiThread {
                        progressBar.progress = percent
                        updateStatus("다운로드 중: $file ($percent%)")
                    }
                }
                
                // 모델 매니저 및 파이프라인 초기화
                audioRecorder = AudioRecorder(this@MainActivity)
                modelManager = ModelManager(this@MainActivity)
                modelManager.initializeModels()
                processingPipeline = ProcessingPipeline(this@MainActivity, modelManager)
                
                updateStatus("준비 완료! 녹음을 시작하세요.")
                btnRecord.isEnabled = true
                progressBar.progress = 0
                
            } catch (e: Exception) {
                updateStatus("초기화 실패: ${e.message}")
                txtResult.text = "오류 세부사항:\n${e.stackTraceToString()}"
                e.printStackTrace()
            }
        }
    }

    private fun startRecording() {
        try {
            recordingFile = audioRecorder.startRecording()
            isRecording = true
            btnRecord.isEnabled = false
            btnStop.isEnabled = true
            updateStatus("녹음 중... (최대 2시간)")
            
            // 녹음 시간 표시 (선택사항)
            lifecycleScope.launch {
                var seconds = 0
                while (isRecording && seconds < 7200) { // 2시간 = 7200초
                    kotlinx.coroutines.delay(1000)
                    seconds++
                    if (isRecording) {
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        updateStatus("녹음 중... (${minutes}:${String.format("%02d", secs)})")
                    }
                }
            }
            
        } catch (e: Exception) {
            updateStatus("녹음 시작 실패: ${e.message}")
            Toast.makeText(this, "녹음 오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecordingAndProcess() {
        isRecording = false
        val filePath = audioRecorder.stopRecording()
        btnRecord.isEnabled = false
        btnStop.isEnabled = false
        
        if (filePath != null) {
            updateStatus("녹음 완료. 분석을 시작합니다...")
            
            lifecycleScope.launch {
                try {
                    val result = processingPipeline.processAudio(
                        audioFile = File(filePath),
                        enableSpeakerDiarization = true
                    ) { progress, status ->
                        runOnUiThread {
                            progressBar.progress = progress
                            updateStatus(status)
                        }
                    }
                    
                    // 결과 표시
                    val resultText = buildString {
                        appendLine("=== 분석 결과 ===")
                        appendLine()
                        appendLine("📝 원본 전사:")
                        appendLine(result.transcription)
                        appendLine()
                        appendLine("🈲 한국어 번역:")
                        appendLine(result.translation)
                        appendLine()
                        appendLine("📋 요약:")
                        appendLine(result.summary)
                        appendLine()
                        if (result.speakerSegments.isNotEmpty()) {
                            appendLine("👥 화자 분리:")
                            result.speakerSegments.forEach { segment ->
                                appendLine("화자 ${segment.speakerId}: ${segment.startTime}s - ${segment.endTime}s")
                            }
                        }
                    }
                    
                    txtResult.text = resultText
                    updateStatus("분석 완료!")
                    
                } catch (e: Exception) {
                    updateStatus("분석 실패: ${e.message}")
                    txtResult.text = "오류 세부사항:\n${e.stackTraceToString()}"
                    e.printStackTrace()
                } finally {
                    btnRecord.isEnabled = true
                    progressBar.progress = 0
                }
            }
        } else {
            updateStatus("녹음 파일을 찾을 수 없습니다.")
            btnRecord.isEnabled = true
        }
    }
    
    private fun updateStatus(status: String) {
        txtStatus.text = "상태: $status"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::modelManager.isInitialized) {
            modelManager.releaseAll()
        }
    }
}