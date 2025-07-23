# WebRTC WHIP/WHEP 双向音视频通话

本项目已成功改造为支持WHIP/WHEP协议的双向音视频通话系统，实现了设备间的实时双向语音视频通信。

## 功能特性

### 🎯 核心功能
- **WHIP协议支持**: WebRTC-HTTP Ingestion Protocol，用于音视频推流
- **WHEP协议支持**: WebRTC-HTTP Egress Protocol，用于音视频拉流
- **双向通话**: 同时支持推流和拉流，实现真正的双向通信
- **3A音频算法**: 启用回声消除、噪声抑制、自动增益控制

### 🎨 音频质量优化
- **回声消除 (AEC)**: 消除本地音频回音
- **噪声抑制 (ANS)**: 过滤环境噪音
- **自动增益控制 (AGC)**: 自动调节音量
- **高通滤波器**: 过滤低频噪音
- **双讲检测**: 智能检测通话状态

### 🎥 视频功能
- **720P高清视频**: 1280x720分辨率，30fps帧率
- **摄像头切换**: 支持前后摄像头切换
- **视频开关**: 可随时开启/关闭摄像头
- **实时预览**: 本地和远程视频实时显示

### 🎛️ 控制功能
- **静音控制**: 一键静音/取消静音
- **摄像头控制**: 开启/关闭摄像头
- **通话管理**: 开始/结束通话
- **状态反馈**: 实时显示连接状态

## 技术架构

### 协议栈
```
应用层: WHIP/WHEP HTTP API
传输层: WebRTC DTLS/SRTP
媒体层: H.264/VP8 + Opus
网络层: UDP/TCP
```

### 核心组件
- `WhipWhepManager`: WHIP/WHEP协议管理器
- `BidirectionalCallManager`: 双向通话管理器
- `ApiService`: HTTP API接口
- `PeerConnectionFactory`: WebRTC连接工厂

## 使用方法

### 1. 服务器配置
确保您的服务器支持WHIP/WHEP协议，配置如下：

```nginx
# Nginx配置示例
location /whip/ {
    proxy_pass http://your_whip_server;
    proxy_set_header Content-Type application/sdp;
}

location /whep/ {
    proxy_pass http://your_whep_server;
    proxy_set_header Content-Type application/sdp;
}
```

### 2. 客户端使用

#### 开始双向通话
1. 输入房间号
2. 点击"开始双向通话"按钮
3. 等待连接建立
4. 开始音视频通话

#### 通话控制
- **切换摄像头**: 点击"切换摄像头"按钮
- **静音/取消静音**: 点击"静音"按钮
- **开启/关闭摄像头**: 点击"关闭摄像头"按钮
- **结束通话**: 点击"结束通话"按钮

### 3. 配置参数

在 `Constant.kt` 中配置服务器地址：

```kotlin
object Constant {
    // WHIP/WHEP服务器配置
    const val WHIP_WHEP_SERVER_IP = "your_server_ip"
    const val WHIP_WHEP_SERVER_PORT = "8080"
    const val WHIP_WHEP_BASE_URL = "http://$WHIP_WHEP_SERVER_IP:$WHIP_WHEP_SERVER_PORT"
}
```

## API接口

### WHIP推流接口
```http
POST /whip/{roomId}
Content-Type: application/sdp

v=0
o=- 1234567890 2 IN IP4 127.0.0.1
s=-
t=0 0
...
```

### WHEP拉流接口
```http
POST /whep/{roomId}
Content-Type: application/sdp

v=0
o=- 1234567890 2 IN IP4 127.0.0.1
s=-
t=0 0
...
```

### 资源删除接口
```http
DELETE /resource/{resourceId}
```

## 音频3A算法配置

### 回声消除 (AEC)
```kotlin
mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
```

### 自动增益控制 (AGC)
```kotlin
mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
```

### 噪声抑制 (ANS)
```kotlin
mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
```

### 高通滤波器
```kotlin
mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
```

### 双讲检测
```kotlin
mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
```

## 性能优化

### 视频编码优化
- 使用H.264 High Profile编码
- 启用Intel VP8硬件编码
- 720P分辨率，30fps帧率

### 音频处理优化
- 启用WebRTC原生3A算法
- 48kHz采样率
- Opus编码，自适应比特率

### 网络优化
- ICE连接优化
- DTLS加密传输
- 自适应码率控制

## 错误处理

### 常见错误及解决方案

1. **连接失败**
   - 检查服务器地址配置
   - 确认网络连接正常
   - 验证WHIP/WHEP服务是否启动

2. **音频质量问题**
   - 检查3A算法是否启用
   - 确认麦克风权限
   - 调整音频约束参数

3. **视频显示问题**
   - 检查摄像头权限
   - 确认SurfaceView初始化
   - 验证视频轨道状态

## 兼容性

### 支持的Android版本
- Android 5.0 (API 21) 及以上

### 支持的WebRTC版本
- WebRTC 1.0 及以上

### 支持的编码格式
- 视频: H.264, VP8, VP9
- 音频: Opus, PCMA, PCMU

## 开发说明

### 项目结构
```
p2p/
├── src/main/java/com/shencoder/webrtc_srs/
│   ├── WhipWhepManager.kt          # WHIP/WHEP协议管理器
│   ├── BidirectionalCallManager.kt # 双向通话管理器
│   ├── MainActivity.kt             # 主界面
│   ├── http/
│   │   ├── ApiService.kt           # API接口
│   │   └── bean/
│   │       └── WhipWhepBean.kt     # 数据模型
│   └── constant/
│       └── Constant.kt             # 配置常量
```

### 扩展开发
如需添加新功能，可以：

1. 在 `WhipWhepManager` 中添加新的协议支持
2. 在 `BidirectionalCallManager` 中添加新的控制功能
3. 在 `ApiService` 中添加新的API接口
4. 在UI中添加新的控制按钮

## 许可证

本项目基于MIT许可证开源。

## 联系方式

如有问题或建议，请联系开发团队。 