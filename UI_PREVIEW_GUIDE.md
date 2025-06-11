# Android Studio에서 UI 미리보기 가이드

## 🎨 **Layout Editor에서 UI 확인**

### 1. **Layout Editor 열기**
1. Android Studio에서 `app/src/main/res/layout/activity_main.xml` 파일 더블클릭
2. 하단에 **Design** | **Split** | **Code** 탭 확인
3. **Design** 탭을 클릭하면 시각적 미리보기 확인 가능

### 2. **실시간 Preview**
- **Split** 모드: 코드와 미리보기를 동시에 확인
- XML 코드 수정 시 실시간으로 미리보기 업데이트
- 다양한 디바이스 크기/해상도로 테스트 가능

### 3. **디바이스 프리뷰 옵션**
- 상단 드롭다운에서 디바이스 선택:
  - Pixel 6 Pro
  - Galaxy S22
  - Nexus 5X
  - 태블릿 등
- 세로/가로 모드 전환 가능
- API 레벨별 미리보기 가능

## 🔧 **Interactive Preview 활성화**

### 1. **Preview 창에서 Interactive 모드 켜기**
```
1. Layout Editor 우측 상단의 "👁️ Interactive Preview" 버튼 클릭
2. 버튼 클릭, 스크롤 등 인터랙션 테스트 가능
3. 실제 앱 실행 없이도 UI 동작 확인
```

### 2. **Sample Data 활용**
```xml
<!-- activity_main.xml에 sample data 추가 -->
<TextView
    android:id="@+id/txtResult"
    android:text="@tools:sample/lorem[4]"
    tools:text="📝 원본 전사: 안녕하세요...
🈲 한국어 번역: 회의 내용...
📋 요약: 주요 논의사항..." />
```

## 📱 **에뮬레이터 없이 UI 테스트**

### 1. **Layout Inspector 사용**
```
1. Android Studio 메뉴: Tools → Layout Inspector
2. 실행 중인 앱의 실시간 UI 구조 확인
3. View 속성, 크기, 마진 등 실시간 디버깅
```

### 2. **Multi-Preview 설정**
```kotlin
// Compose 사용 시 (참고용)
@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MeetingTranscriberPreview() {
    // UI 미리보기
}
```

## 🎯 **현재 UI 구조 확인 방법**

### 1. **즉시 확인 가능**
- `activity_main.xml` 파일을 Android Studio에서 열면 바로 미리보기 가능
- ScrollView 내부의 LinearLayout 구조 확인
- 버튼, ProgressBar, TextView 배치 상태 확인

### 2. **UI 수정 시 실시간 반영**
- XML 속성 변경 시 즉시 미리보기 업데이트
- `android:text`, `android:textSize`, `android:padding` 등 수정 가능
- 색상, 크기, 여백 등 시각적으로 조정 가능

## 📋 **UI 구성 요소 확인**

현재 구현된 UI:
✅ **녹음 시작 버튼** (`btnRecord`)
✅ **녹음 종료 및 분석 버튼** (`btnStop`)  
✅ **진행률 바** (`progressBar`)
✅ **상태 텍스트** (`txtStatus`)
✅ **결과 표시 영역** (`txtResult`) - 스크롤 가능

## 🔍 **UI 개선 팁**

### 1. **Material Design Components 활용**
```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/btnRecord"
    style="@style/Widget.MaterialComponents.Button"
    app:icon="@drawable/ic_mic" />
```

### 2. **ConstraintLayout으로 성능 최적화**
```xml
<androidx.constraintlayout.widget.ConstraintLayout
    app:layout_constraintTop_toTopOf="parent">
```

### 3. **다크 모드 지원**
```xml
<TextView android:textColor="?android:attr/textColorPrimary" />
```

---

**결론**: Android Studio Layout Editor만으로도 실제 기기 없이 완전한 UI 미리보기와 인터랙션 테스트가 가능합니다!