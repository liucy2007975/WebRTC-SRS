# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WebRTC-SRS is an Android demo application demonstrating WebRTC streaming with the [SRS](https://github.com/ossrs/srs) server. The project contains multiple modules showcasing different WebRTC use cases including push, pull, P2P communication, multi-channel calls, and HTTP callbacks.

## Architecture Structure

### Multi-Module Organization
The project is organized into distinct modules, each serving a specific purpose:

- **p2p**: Simulates 1v1 video calls using WebRTC with WHIP/WHEP protocol support for bidirectional streaming
- **push**: Demonstrates pushing WebRTC streams to SRS server
- **pull**: Shows pulling WebRTC streams from SRS server  
- **multichannel**: Multi-party video calling functionality
- **http-callback**: SRS HTTP callback handling for server events
- **webrtc**: Contains the native WebRTC library (libwebrtc.aar + native .so files)

### Core Technologies
- **WebRTC**: Native WebRTC implementation via custom AAR library
- **Kotlin**: Primary language with MVVM architecture pattern
- **Coroutines**: Async operations and flow control
- **Retrofit**: HTTP client for SRS API communication
- **SignalR/Socket.IO**: Real-time signaling for P2P communication
- **Koin/Dagger**: Dependency injection (varies by module)

## Build Commands

### Project Setup
```bash
# Clean and rebuild entire project
./gradlew clean build

# Build specific module
./gradlew :p2p:assembleDebug
./gradlew :push:assembleDebug  
./gradlew :pull:assembleDebug
./gradlew :multichannel:assembleDebug
./gradlew :http-callback:assembleDebug

# Install APK to device
./gradlew :p2p:installDebug
./gradlew :push:installDebug
./gradlew :pull:installDebug
./gradlew :multichannel:installDebug
./gradlew :http-callback:installDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

### Development Environment
- **Gradle Version**: 7.0.2
- **Kotlin Version**: 1.7.10
- **Min SDK**: 21 (Android 5.0+)
- **Target SDK**: 30 (Android 11)
- **Compile SDK**: 30

## Key Configuration Files

### Server Configuration
Update server endpoints in `Constant.kt` files:
- **SRS Server**: `192.168.2.91:1985` (HTTP) / `192.168.2.91:1990` (HTTPS)  
- **WHIP/WHEP Server**: `192.168.2.91:8080`

### Module-Specific Constants
Each module contains its own `Constant.kt` with server configuration. Check module-specific README files for setup instructions.

## Core Components Architecture

### WHIP/WHEP Implementation (p2p module)
- **WhipWhepManager.kt**: Handles WHIP (push) and WHEP (pull) protocol operations
- **BidirectionalCallManager.kt**: Coordinates bidirectional streaming for P2P calls
- **ApiService.kt**: Retrofit service for HTTP API calls
- **PeerConnectionObserver.kt**: WebRTC peer connection event handling

### WebRTC Setup Pattern
Each module follows a consistent pattern:
1. **PeerConnectionFactory** initialization in Application class
2. **SurfaceViewRenderer** for local/remote video display
3. **CameraVideoCapturer** for video capture
4. **AudioTrack/VideoTrack** creation and management
5. **ICE candidate handling** via signaling servers

### Audio Processing
- **3A Algorithm Support**: AEC (Echo Cancellation), AGC (Auto Gain Control), ANS (Noise Suppression)
- **High-pass filtering** enabled
- **Opus codec** for audio with 48kHz sampling

## Development Workflow

### Testing Approach
- Unit tests use JUnit 4.13.2
- Instrumentation tests via AndroidX Test framework
- Each module has separate test configurations

### Debugging
- Logging via XLog library
- LeakCanary enabled in debug builds
- WebRTC native logging available

### Deployment Notes
- WebRTC native libraries included for arm64-v8a and armeabi-v7a architectures
- ProGuard rules configured for WebRTC classes
- Network security config allows cleartext traffic for local development

## Module Interdependencies
- All application modules depend on the `:webrtc` module for WebRTC functionality
- Shared utility classes via custom libraries (MVVMKit, WebRTCExtension)
- Consistent theming and UI patterns across modules