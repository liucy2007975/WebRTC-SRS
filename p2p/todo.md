# 一对一语音视频通话需求文档

## 项目概述
在当前p2p模块下实现基于WebRTC的一对一语音视频通话功能，使用WHIP/WHEP协议进行SDP交换。

## 技术规格要求

### 视频编码
- **编码格式**: H264
- **分辨率**: 自适应 (建议 640x480 或 1280x720)
- **帧率**: 30fps
- **码率**: 自适应 (建议 500kbps-2Mbps)

### 音频编码
- **编码格式**: PCMA (G.711 A-law)
- **采样率**: 8000Hz
- **通道**: 单声道
- **码率**: 64kbps

### 音频3A处理
- **AEC (Acoustic Echo Cancellation)**: 声学回声消除
- **AGC (Automatic Gain Control)**: 自动增益控制
- **ANS (Automatic Noise Suppression)**: 自动噪声抑制

### 网络协议
- **传输模式**: WebRTC over TCP (禁用UDP传输)
- **信令协议**: WHIP/WHEP (WebRTC-HTTP ingestion protocol)
- **WHIP端点**: 用于发布本地音视频流到WebRTC服务器
- **WHEP端点**: 用于从WebRTC服务器拉取远端音视频流
- **ICE配置**: 禁用外部ICE服务器，直接使用WebRTC服务器地址
- **连接方式**: 所有通信通过WebRTC服务器中转，不建立P2P连接

## 功能需求

### 核心功能
1. **音视频采集**
   - 前置/后置摄像头切换
   - 麦克风音频采集
   - 摄像头权限处理

2. **编解码配置**
   - H264视频编码器配置
   - PCMA音频编码器配置
   - 编码参数动态调整

3. **WebRTC连接管理**
   - PeerConnection创建与管理
   - ICE候选收集与交换
   - NAT穿透处理

4. **WHIP/WHEP协议实现**
   - WHIP客户端: 发布本地流到服务器
   - WHEP客户端: 从服务器拉取远端流
   - SDP offer/answer交换

5. **UI界面**
   - 本地视频预览
   - 远端视频显示
   - 音频开关控制
   - 视频开关控制
   - 通话状态显示

### 高级功能
1. **网络自适应**
   - 带宽估计
   - 码率自适应调整
   - 网络质量检测

2. **错误处理**
   - 网络重连机制
   - 设备权限处理
   - 编码器故障恢复

## 技术实现路径

### 第一阶段: 基础配置
- [ ] 配置WebRTC编码参数
- [ ] 设置H264和PCMA编码器
- [ ] 启用音频3A处理

### 第二阶段: WHIP/WHEP实现
- [ ] 实现WHIP客户端发布功能
- [ ] 实现WHEP客户端订阅功能
- [ ] 完善SDP交换机制

### 第三阶段: 连接管理
- [ ] 配置WebRTC over TCP模式
- [ ] 禁用UDP传输和外部ICE
- [ ] 配置服务器直连模式
- [ ] 网络状态监控

### 第四阶段: UI与测试
- [ ] 完善用户界面
- [ ] 功能完整性测试
- [ ] 性能优化

## 接口定义

### WHIP端点
- **URL格式**: `http://{server}/whip/{stream_id}`
- **方法**: POST (SDP offer), PATCH (ICE candidates)
- **内容类型**: application/sdp

### WHEP端点
- **URL格式**: `http://{server}/whep/{stream_id}`
- **方法**: POST (SDP offer), PATCH (ICE candidates)
- **内容类型**: application/sdp

## 测试场景

### 功能测试
1. 本地音视频采集测试
2. 编码器配置验证
3. WHIP/WHEP协议交互测试
4. 端到端通话测试

### 网络测试
1. 不同网络环境测试(WiFi/4G/5G)
2. NAT穿透测试
3. 网络抖动和丢包处理

### 设备测试
1. 不同Android版本兼容性
2. 不同设备性能测试
3. 前后摄像头切换测试

## 部署配置

### 服务器配置
- **WebRTC服务器**: 支持WHIP/WHEP协议，支持TCP传输
- **服务器地址**: 直接连接到WebRTC服务器，无需外部ICE
- **信令协议**: HTTP RESTful API (WHIP/WHEP端点)

### 客户端配置
- **权限配置**: 摄像头、麦克风权限
- **网络配置**: 允许HTTP/HTTPS访问
- **编码配置**: H264和PCMA参数设置