# 📱 Android Studio 실행 가이드

## 1️⃣ Android Studio에서 프로젝트 열기

### 1.1 프로젝트 Import
```bash
# Android Studio 실행 후
File -> Open -> /home/hseung/side_project/MeetingTranscriber 선택
```

### 1.2 필수 SDK 설정
- **Minimum SDK**: API 26 (Android 8.0)
- **Target SDK**: API 34 (Android 14)
- **Build Tools**: 34.0.0
- **NDK**: 25.1.8937393 (JNI 사용 시)

---

## 2️⃣ Gradle Sync & Build

### 2.1 첫 번째 Sync
```bash
# Android Studio에서 자동으로 Gradle Sync 실행됨
# 또는 수동으로: Build -> Clean Project -> Rebuild Project
```

### 2.2 Dependency 다운로드 확인
```bash
# 다음 라이브러리들이 자동 다운로드 됨:
✅ ONNX Runtime Mobile: 1.16.3
✅ TensorFlow Lite: 2.13.0  
✅ Kotlin Coroutines: 1.7.3
✅ OkHttp: 4.11.0
✅ Material Design: 1.9.0
```

---

## 3️⃣ 디바이스/에뮬레이터 설정

### 3.1 실제 Android 디바이스 (권장)
```bash
# 1. 디바이스 설정
Settings -> About Phone -> Build Number (7번 탭)
Settings -> Developer Options -> USB Debugging (ON)

# 2. 최소 스펙 요구사항:
- RAM: 6GB+ (AI 모델 로딩)
- Storage: 5GB+ (모델 파일들)
- Android 8.0+ (API 26+)
```

### 3.2 AVD 에뮬레이터 설정
```bash
# Tools -> AVD Manager -> Create Virtual Device
- Phone: Pixel 7 Pro
- System Image: API 34 (Android 14)
- RAM: 8GB
- Internal Storage: 16GB
```

---

## 4️⃣ 앱 실행 및 테스트

### 4.1 첫 실행
```bash
# Android Studio에서
Run -> Run 'app' (Shift+F10)
```

### 4.2 예상 동작
```bash
📱 앱 시작
├── 🔄 모델 다운로드 시작 (최초 실행시)
│   ├── Whisper Tiny (39MB)
│   ├── NLLB-600M (1.2GB)  
│   ├── Qwen2.5-1.8B (3.6GB)
│   └── WavLM-Base+ (377MB)
├── 🎤 녹음 버튼 활성화
├── 📊 실시간 진행률 표시
└── 📝 결과 화면 (한국어)
```

---

## 5️⃣ 문제 해결

### 5.1 Gradle Sync 실패시
```bash
# 해결책 1: Gradle Wrapper 재다운로드
./gradlew wrapper --gradle-version 8.2

# 해결책 2: Gradle Cache 삭제
~/.gradle/caches/ 폴더 삭제 후 재시작
```

### 5.2 ONNX Runtime 오류시
```bash
# build.gradle(app)에서 ABI 필터 확인:
ndk {
    abiFilters "arm64-v8a", "x86_64"
}
```

### 5.3 JNI 라이브러리 없음 오류
```bash
# 현재는 Fallback 모드로 동작합니다.
# 로그에서 "Using fallback implementation" 메시지 확인
```

### 5.4 모델 다운로드 실패시
```bash
# 인터넷 연결 확인
# 또는 수동으로 models/ 폴더에 파일 배치:
app/src/main/assets/models/
├── whisper-tiny.onnx
├── nllb-200-600M.onnx
├── qwen2.5-1.8b-instruct.onnx
└── wavlm-base-plus.onnx
```

---

## 6️⃣ 성능 최적화

### 6.1 빌드 성능
```bash
# gradle.properties 추가:
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
org.gradle.parallel=true
org.gradle.caching=true
```

### 6.2 런타임 성능
```bash
# 발열 방지:
- 에어컨이 있는 환경에서 테스트
- 배터리 95% 이상에서 시작
- 백그라운드 앱 종료
```

---

## 7️⃣ 로그 확인

### 7.1 핵심 로그 태그들
```bash
# Android Studio Logcat에서 필터링:
MeetingTranscriber   # 메인 앱 로그
ModelManager        # 모델 로딩 상태  
AudioRecorder       # 녹음 상태
ProcessingPipeline  # 전체 파이프라인
TokenizerUtils      # 토크나이저 상태
```

### 7.2 성공적인 실행 로그 예시
```bash
I/ModelManager: Models loaded successfully
I/AudioRecorder: Recording started
I/ProcessingPipeline: Processing audio chunk 1/10
I/WhisperASR: Transcription: "안녕하세요"
I/NLLBTranslator: Translation: "Привет"
I/QwenSummarizer: Summary generated
```

---

## 🎯 **성공 기준**

✅ **앱이 크래시 없이 시작**  
✅ **모델 다운로드 진행률 표시**  
✅ **녹음 버튼 클릭 가능**  
✅ **Fallback 토크나이저 동작**  
✅ **더미 결과라도 화면에 표시**  

---

*💡 실제 AI 모델을 사용하려면 JNI 라이브러리 컴파일이 필요하지만, 현재 Fallback 모드로 전체 플로우 테스트가 가능합니다.*