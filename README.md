# Voice App - Android Recording with Transcription & Summary

A robust Android voice recording application with real-time transcription and AI-powered summary generation.

## 🎯 Features

### 1. **Robust Audio Recording**
- ✅ Foreground service with persistent notification
- ✅ 30-second chunks with 2-second overlap for speech continuity
- ✅ Real-time timer with pause/resume functionality
- ✅ Edge case handling:
  - Phone call detection (auto-pause/resume)
  - Audio focus loss handling
  - Microphone source changes (Bluetooth/wired/USB headset)
  - Low storage detection
  - Process death recovery
  - Silent audio detection (10 seconds)

### 2. **Transcript Generation**
- ✅ Automatic transcription of audio chunks
- ✅ Room database storage (single source of truth)
- ✅ Correct ordering via chunkIndex
- ✅ Retry mechanism for failed chunks
- ✅ Mock API (ready for OpenAI Whisper or Google Gemini integration)

### 3. **AI Summary Generation**
- ✅ Structured summary (Title, Summary, Action Items, Key Points)
- ✅ Real-time streaming UI updates
- ✅ Background generation (continues if app is killed)
- ✅ Error handling with retry
- ✅ Mock API (ready for OpenAI GPT or Google Gemini integration)

## 🏗️ Architecture

- **MVVM**: ViewModel → Repository → DAO/API
- **Dependency Injection**: Hilt
- **Database**: Room
- **Networking**: Retrofit (configured, using mocks)
- **UI**: 100% Jetpack Compose
- **Async**: Coroutines & Flow

## 📋 Requirements

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: Latest
- **Kotlin**: 2.0.21+
- **Gradle**: 8.13.0+

## 🚀 Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Run the app

## 📝 Permissions

The app requires the following permissions:
- `RECORD_AUDIO` - For audio recording
- `POST_NOTIFICATIONS` - For persistent notifications (Android 13+)
- `READ_PHONE_STATE` - For phone call detection (optional)
- `FOREGROUND_SERVICE` - For foreground service
- `FOREGROUND_SERVICE_MICROPHONE` - For microphone foreground service

## 🔧 Configuration

### Replace Mock APIs

To use real transcription APIs, replace `MockTranscriptionService`:
- OpenAI Whisper API
- Google Speech-to-Text API
- Google Gemini 2.5 Flash

To use real summary APIs, replace `MockSummaryService`:
- OpenAI GPT-4/GPT-3.5
- Google Gemini

See `NetworkModule.kt` for API configuration.

## 📦 Dependencies

- **Hilt**: Dependency injection
- **Room**: Local database
- **Retrofit**: API calls
- **WorkManager**: Background tasks
- **Compose**: UI framework
- **Coroutines & Flow**: Async operations

## 📄 License

This project is created for assignment purposes.

## 📹 Demo Video

A screen recording demonstrating the complete app flow is available below:

[![Demo Video](https://img.youtube.com/vi/VIDEO_ID/0.jpg)](https://www.youtube.com/watch?v=VIDEO_ID)

**Or attach via GitHub Releases:**
1. Go to your repository: https://github.com/felixny/voice-app
2. Click **"Releases"** → **"Create a new release"**
3. Upload your demo video file
4. Add description and publish
5. Link will be: `https://github.com/felixny/voice-app/releases`

**Alternative Hosting Options:**
- **YouTube**: Upload as Unlisted and paste link
- **Google Drive**: Upload video, share link, set to "Anyone with link"
- **Loom**: Record directly or upload, get shareable link
- **Vimeo**: Upload video and embed link

### How to Attach Demo Video to GitHub:

**Option 1: GitHub Releases (Recommended)**
```
1. Go to: https://github.com/felixny/voice-app/releases/new
2. Tag: v1.0.0
3. Title: Initial Release - Voice App Demo
4. Upload: Your demo video file (.mp4)
5. Publish release
6. Link: Share the release URL
```

**Option 2: Embed in README**
If video is on YouTube/Loom/Vimeo:
```markdown
[![Demo Video](https://img.youtube.com/vi/VIDEO_ID/0.jpg)](https://www.youtube.com/watch?v=VIDEO_ID)
```

**Option 3: Direct Link**
Add link in README:
```markdown
## 📹 Demo Video
[Watch Demo Video Here](YOUR_VIDEO_LINK)
```

**Option 4: Add to Repository**
Upload video file to repository (not recommended for large files):
- Create `demo/` folder
- Upload `demo-screen-recording.mp4`
- Add to `.gitignore` if > 100MB, or use Git LFS

## 👤 Author

Built for Android Developer Take Home Assignment

