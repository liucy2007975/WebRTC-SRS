# ZLMediaKit WebRTC一对一通话使用说明

## 服务器配置

### ZLMediaKit配置
确保在ZLMediaKit配置文件中启用WebRTC功能：

```ini
[rtc]
# 启用rtc服务
enable_rtc=1
# rtc服务监听端口
port=8080

[http]
# http服务监听端口
port=80
sslport=443
```

## 客户端配置

### 更新服务器地址
在 `Constant.kt` 中修改服务器地址：

```kotlin
const val WHIP_WHEP_SERVER_IP = "your-zlm-server-ip"
const val WHIP_WHEP_SERVER_PORT = "8080"
```

### 使用示例

#### 1. 初始化推流（发布端）
```kotlin
val whipWhepManager = WhipWhepManager(context, peerConnectionFactory, eglBaseContext)

// 监听回调
whipWhepManager.onWhipConnected = { url ->
    Log.d("WebRTC", "推流成功: $url")
}

// 启动推流
lifecycleScope.launch {
    whipWhepManager.initWhipPublish("room123", localVideoView)
}
```

#### 2. 初始化拉流（播放端）
```kotlin
// 启动拉流
lifecycleScope.launch {
    whipWhepManager.initWhepPlay("room123", remoteVideoView)
}

// 监听回调
whipWhepManager.onWhepConnected = { url ->
    Log.d("WebRTC", "拉流成功: $url")
}
```

## API端点说明

### ZLMediaKit WebRTC端点

#### 推流端点
- **URL**: `POST /index/api/webrtc?app=live&stream={streamId}&type=push&codec=H264/PCMA`
- **参数**:
  - `app`: 应用名，固定为"live"
  - `stream`: 流ID，对应房间号
  - `type`: 固定为"push"
  - `codec`: 编码格式，设置为"H264/PCMA"

#### 拉流端点
- **URL**: `POST /index/api/webrtc?app=live&stream={streamId}&type=play&codec=H264/PCMA`
- **参数**:
  - `app`: 应用名，固定为"live"
  - `stream`: 流ID，对应房间号
  - `type`: 固定为"play"
  - `codec`: 编码格式，设置为"H264/PCMA"

## 测试步骤

### 1. 启动ZLMediaKit服务器
```bash
# Linux/Mac
./MediaServer -c config.ini

# Windows
MediaServer.exe -c config.ini
```

### 2. 配置服务器地址
修改 `p2p/src/main/java/com/shencoder/webrtc_srs/constant/Constant.kt`：

```kotlin
object Constant {
    const val WHIP_WHEP_SERVER_IP = "192.168.1.100" // 你的ZLMediaKit服务器IP
    const val WHIP_WHEP_SERVER_PORT = "8080"
    // 其他配置...
}
```

### 3. 运行应用
- 安装APK到两个设备
- 设备A：作为发布端，调用 `initWhipPublish`
- 设备B：作为播放端，调用 `initWhepPlay`
- 使用相同的 `roomId` 进行匹配

## 故障排除

### 常见问题

1. **连接失败**
   - 检查ZLMediaKit是否启动
   - 确认网络连接正常
   - 验证端口是否开放

2. **无视频显示**
   - 检查摄像头权限
   - 确认ZLMediaKit配置正确
   - 查看ZLMediaKit日志

3. **音频问题**
   - 检查麦克风权限
   - 确认音频编码设置为PCMA
   - 验证3A处理配置

### 日志调试
```kotlin
// 开启详细日志
XLog.init(LogLevel.ALL)
```

## ZLMediaKit验证

### 测试推流
```bash
curl -X POST "http://your-server:8080/index/api/webrtc?app=live&stream=test&type=push&codec=H264/PCMA" \
  -H "Content-Type: application/sdp" \
  -d "your_sdp_offer"
```

### 测试拉流
```bash
curl -X POST "http://your-server:8080/index/api/webrtc?app=live&stream=test&type=play&codec=H264/PCMA" \
  -H "Content-Type: application/sdp" \
  -d "your_sdp_offer"
```