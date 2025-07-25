package com.shencoder.webrtc_srs

import android.content.Context
import com.elvishew.xlog.XLog
import com.shencoder.mvvmkit.util.toastError
import com.shencoder.mvvmkit.util.toastWarning
import com.shencoder.webrtc_srs.http.RetrofitClient
import com.shencoder.webrtc_srs.http.bean.WhipWhepResponseBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.*
import org.webrtc.EglBase
import com.shencoder.webrtc_srs.constant.Constant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * WHIP/WHEP协议管理器
 * 处理双向音视频通话的推流和拉流
 */
class WhipWhepManager(
    private val context: Context,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val eglBaseContext: EglBase.Context
) {
    
    private val retrofitClient = RetrofitClient()
    
    // 推流相关
    public var whipConnection: PeerConnection? = null
    private var whipVideoTrack: VideoTrack? = null
    private var whipAudioTrack: AudioTrack? = null
    private var whipCameraCapturer: CameraVideoCapturer? = null
    private var whipSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var whipResourceUrl: String? = null
    
    // 拉流相关
    public var whepConnection: PeerConnection? = null
    private var whepResourceUrl: String? = null
    
    // 回调
    var onWhipConnected: ((String) -> Unit)? = null
    var onWhepConnected: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    /**
     * 初始化WHIP推流
     * @param roomId 房间ID
     * @param localVideoSink 本地视频显示
     */
    suspend fun initWhipPublish(roomId: String, localVideoSink: VideoSink) {
        try {
            // 创建音频源和轨道 - PCMA编码，8000Hz单声道
            val audioSource = createAudioSourceWith3A()
            val audioTrack = peerConnectionFactory.createAudioTrack("whip_audio_track", audioSource)
            
            // 创建H264视频源和轨道
            val videoTrack = createVideoTrackWithH264(localVideoSink)
            
            // 创建PeerConnection - 配置WebRTC over TCP模式
            val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                keyType = PeerConnection.KeyType.ECDSA
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                
                // 禁用UDP传输，强制使用TCP
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                
                // 禁用外部ICE服务器，直接使用WebRTC服务器
                
                // 配置TCP连接参数
                enableDscp = true
                enableCpuOveruseDetection = true
            }
            
            val peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnectionObserver() {
                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                        super.onConnectionChange(newState)
                        XLog.i("WHIP连接状态变化: $newState")
                        when (newState) {
                            PeerConnection.PeerConnectionState.CONNECTED -> {
                                onWhipConnected?.invoke(whipResourceUrl ?: "")
                            }
                            PeerConnection.PeerConnectionState.FAILED -> {
                                onError?.invoke("WHIP连接失败")
                            }
                            else -> {}
                        }
                    }
                }
            )?.apply {
                // 添加音频轨道
                addTransceiver(
                    audioTrack,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                )
                
                // 添加视频轨道
                addTransceiver(
                    videoTrack,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                )
            }
            
            whipConnection = peerConnection
            whipAudioTrack = audioTrack
            whipVideoTrack = videoTrack
            
            // 创建Offer - 使用H264和PCMA编码约束
            val mediaConstraints = createMediaConstraints()
            peerConnection?.createOffer(object : SdpAdapter("whip_create_offer") {
                override fun onCreateSuccess(description: SessionDescription?) {
                    super.onCreateSuccess(description)
                    description?.let { offer ->
                        if (offer.type == SessionDescription.Type.OFFER) {
                            // 修改SDP以强制使用H264和PCMA
                            val modifiedSdp = modifySdpForH264AndPCMA(offer.description)
                            val modifiedOffer = SessionDescription(offer.type, modifiedSdp)
                            
                            peerConnection.setLocalDescription(SdpAdapter("whip_set_local"), modifiedOffer)
                            // 发送WHIP请求
                            GlobalScope.launch {
                                sendWhipRequest(modifiedOffer.description, roomId)
                            }
                        }
                    }
                }
                
                override fun onCreateFailure(error: String?) {
                    super.onCreateFailure(error)
                    onError?.invoke("WHIP创建Offer失败: $error")
                }
            }, mediaConstraints)
            
        } catch (e: Exception) {
            XLog.e("WHIP初始化失败: ${e.message}")
            onError?.invoke("WHIP初始化失败: ${e.message}")
        }
    }
    
    /**
     * 初始化WHEP拉流
     * @param roomId 房间ID
     * @param remoteVideoSink 远程视频显示
     */
    suspend fun initWhepPlay(roomId: String, remoteVideoSink: VideoSink) {
        try {
            // 创建PeerConnection - 配置WebRTC over TCP模式
            val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                keyType = PeerConnection.KeyType.ECDSA
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                
                // 禁用UDP传输，强制使用TCP
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                
                // 禁用外部ICE服务器，直接使用WebRTC服务器
                
                // 配置TCP连接参数
                enableDscp = true
                enableCpuOveruseDetection = true
            }
            
            val peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnectionObserver() {
                    override fun onAddStream(mediaStream: MediaStream?) {
                        super.onAddStream(mediaStream)
                        mediaStream?.let { stream ->
                            // 添加视频轨道到显示
                            if (stream.videoTracks.isNotEmpty()) {
                                stream.videoTracks[0].addSink(remoteVideoSink)
                            }
                        }
                    }
                    
                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                        super.onConnectionChange(newState)
                        XLog.i("WHEP连接状态变化: $newState")
                        when (newState) {
                            PeerConnection.PeerConnectionState.CONNECTED -> {
                                onWhepConnected?.invoke(whepResourceUrl ?: "")
                            }
                            PeerConnection.PeerConnectionState.FAILED -> {
                                onError?.invoke("WHEP连接失败")
                            }
                            else -> {}
                        }
                    }
                }
            )?.apply {
                // 添加接收音频轨道
                addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
                
                // 添加接收视频轨道
                addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
            }
            
            whepConnection = peerConnection
            
            // 创建Offer - 使用H264和PCMA编码约束
            val mediaConstraints = createMediaConstraints()
            peerConnection?.createOffer(object : SdpAdapter("whep_create_offer") {
                override fun onCreateSuccess(description: SessionDescription?) {
                    super.onCreateSuccess(description)
                    description?.let { offer ->
                        if (offer.type == SessionDescription.Type.OFFER) {
                            // 修改SDP以支持H264和PCMA接收
                            val modifiedSdp = modifySdpForH264AndPCMA(offer.description)
                            val modifiedOffer = SessionDescription(offer.type, modifiedSdp)
                            
                            peerConnection.setLocalDescription(SdpAdapter("whep_set_local"), modifiedOffer)
                            // 发送WHEP请求
                            GlobalScope.launch {
                                sendWhepRequest(modifiedOffer.description, roomId)
                            }
                        }
                    }
                }
                
                override fun onCreateFailure(error: String?) {
                    super.onCreateFailure(error)
                    onError?.invoke("WHEP创建Offer失败: $error")
                }
            }, mediaConstraints)
            
        } catch (e: Exception) {
            XLog.e("WHEP初始化失败: ${e.message}")
            onError?.invoke("WHEP初始化失败: ${e.message}")
        }
    }
    
    /**
     * 发送WHIP推流请求 - ZLMediaKit格式
     */
    private suspend fun sendWhipRequest(sdp: String, roomId: String) {
        try {
            // ZLMediaKit格式: /index/api/webrtc?app=live&stream={roomId}&type=push&codec=H264/PCMA
            val whipUrl = "${Constant.WHIP_WHEP_BASE_URL}/index/api/webrtc?app=live&stream=$roomId&type=push&codec=H264/PCMA"
            
            val response = withContext(Dispatchers.IO) {
                retrofitClient.apiService.whipPublish(whipUrl, sdp)
            }
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                responseBody?.let { body ->
                    whipResourceUrl = body.location ?: body.link
                    XLog.i("ZLMediaKit推流成功，资源URL: $whipResourceUrl")
                    
                    // 设置远程SDP
                    val answerSdp = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        body.sdp ?: ""
                    )
                    whipConnection?.setRemoteDescription(
                        SdpAdapter("whip_set_remote"),
                        answerSdp
                    )
                }
            } else {
                val errorMsg = "ZLMediaKit推流失败: ${response.code()} ${response.message()}"
                XLog.e(errorMsg)
                onError?.invoke(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "ZLMediaKit推流请求异常: ${e.message}"
            XLog.e(errorMsg)
            onError?.invoke(errorMsg)
        }
    }
    
    /**
     * 发送WHEP拉流请求 - ZLMediaKit格式
     */
    private suspend fun sendWhepRequest(sdp: String, roomId: String) {
        try {
            // ZLMediaKit格式: /index/api/webrtc?app=live&stream={roomId}&type=play&codec=H264/PCMA
            val whepUrl = "${Constant.WHIP_WHEP_BASE_URL}/index/api/webrtc?app=live&stream=$roomId&type=play&codec=H264/PCMA"
            
            val response = withContext(Dispatchers.IO) {
                retrofitClient.apiService.whepPlay(whepUrl, sdp)
            }
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                responseBody?.let { body ->
                    whepResourceUrl = body.location ?: body.link
                    XLog.i("ZLMediaKit拉流成功，资源URL: $whepResourceUrl")
                    
                    // 设置远程SDP
                    val answerSdp = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        body.sdp ?: ""
                    )
                    whepConnection?.setRemoteDescription(
                        SdpAdapter("whep_set_remote"),
                        answerSdp
                    )
                }
            } else {
                val errorMsg = "ZLMediaKit拉流失败: ${response.code()} ${response.message()}"
                XLog.e(errorMsg)
                onError?.invoke(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "ZLMediaKit拉流请求异常: ${e.message}"
            XLog.e(errorMsg)
            onError?.invoke(errorMsg)
        }
    }
    
    /**
     * 创建带3A算法的音频源 - 配置PCMA编码
     */
    private fun createAudioSourceWith3A(): AudioSource {
        val audioConstraints = MediaConstraints().apply {
            // 启用回声消除
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
            // 自动增益控制
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            // 噪声抑制
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            // 高通滤波器
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            // 语音活动检测
            mandatory.add(MediaConstraints.KeyValuePair("voiceActivityDetection", "true"))
            // 舒适噪声
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseReduction", "true"))
            // 音频采样率设置
            mandatory.add(MediaConstraints.KeyValuePair("sampleRate", "8000"))
            mandatory.add(MediaConstraints.KeyValuePair("channelCount", "1"))
            mandatory.add(MediaConstraints.KeyValuePair("maxSampleRate", "8000"))
        }
        
        return peerConnectionFactory.createAudioSource(audioConstraints)
    }
    
    
    /**
     * 切换摄像头
     */
    fun switchCamera() {
        whipCameraCapturer?.switchCamera(null)
    }
    
    /**
     * 静音/取消静音
     */
    fun toggleMute(): Boolean {
        val audioTrack = whipAudioTrack
        if (audioTrack != null) {
            val isEnabled = audioTrack.enabled()
            audioTrack.setEnabled(!isEnabled)
            return !isEnabled
        }
        return false
    }
    
    /**
     * 开启/关闭摄像头
     */
    fun toggleCamera(): Boolean {
        val videoTrack = whipVideoTrack
        if (videoTrack != null) {
            val isEnabled = videoTrack.enabled()
            videoTrack.setEnabled(!isEnabled)
            return !isEnabled
        }
        return false
    }
    
    /**
     * 创建H264视频编码器配置
     */
    private fun createVideoTrackWithH264(localVideoSink: VideoSink): VideoTrack {
        // 创建摄像头捕获器
        val cameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator()
        }
        
        // 优先使用前置摄像头
        var cameraName: String? = null
        for (name in cameraEnumerator.deviceNames) {
            if (cameraEnumerator.isFrontFacing(name)) {
                cameraName = name
                break
            }
        }
        
        // 如果没有前置摄像头，使用后置摄像头
        if (cameraName == null) {
            for (name in cameraEnumerator.deviceNames) {
                if (cameraEnumerator.isBackFacing(name)) {
                    cameraName = name
                    break
                }
            }
        }
        
        // 如果都没有，使用第一个可用摄像头
        if (cameraName == null && cameraEnumerator.deviceNames.isNotEmpty()) {
            cameraName = cameraEnumerator.deviceNames[0]
        }
        
        val capturer = cameraName?.let { cameraEnumerator.createCapturer(it, null) }
        
        if (capturer != null) {
            whipCameraCapturer = capturer
            whipSurfaceTextureHelper = SurfaceTextureHelper.create("whip_camera_thread", eglBaseContext)
            
            val videoSource = peerConnectionFactory.createVideoSource(capturer.isScreencast)
            capturer.initialize(whipSurfaceTextureHelper, context, videoSource.capturerObserver)
            
            // 配置H264编码参数（通过SDP修改实现）
            
            // 启动摄像头捕获 (1280x720, 30fps)
            capturer.startCapture(1280, 720, 30)
            
            val videoTrack = peerConnectionFactory.createVideoTrack("whip_video_track", videoSource)
            videoTrack.addSink(localVideoSink)
            
            return videoTrack
        } else {
            throw IllegalStateException("没有可用的摄像头")
        }
    }

    /**
     * 配置媒体约束以支持H264和PCMA
     */
    private fun createMediaConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            // 配置视频编码器优先级
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            
            // 音频配置
            mandatory.add(MediaConstraints.KeyValuePair("VoiceActivityDetection", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            
            // 视频编码器配置
            val video = MediaConstraints.KeyValuePair("video", "H264")
            mandatory.add(video)
            
            // 音频编码器配置
            val audio = MediaConstraints.KeyValuePair("audio", "PCMA")
            mandatory.add(audio)
        }
    }

    /**
     * 释放资源
     */
    suspend fun release() {
        try {
            // 删除WHIP资源
            whipResourceUrl?.let { url ->
                retrofitClient.apiService.deleteResource(url)
            }
            // 删除WHEP资源
            whepResourceUrl?.let { url ->
                retrofitClient.apiService.deleteResource(url)
            }
            // 释放摄像头资源
            whipCameraCapturer?.stopCapture()
            whipCameraCapturer?.dispose()
            whipSurfaceTextureHelper?.dispose()
            // 释放PeerConnection
            whipConnection?.dispose()
            whepConnection?.dispose()
            // 释放轨道
            whipVideoTrack?.dispose()
            whipAudioTrack?.dispose()
        } catch (e: Exception) {
            XLog.e("释放资源失败: ${e.message}")
        }
    }

    /**
     * 修改SDP以强制使用H264和PCMA编码，并优化TCP传输
     */
    private fun modifySdpForH264AndPCMA(sdp: String): String {
        val lines = sdp.split("\r\n").toMutableList()
        val modifiedLines = mutableListOf<String>()
        
        // H264编码配置 - 优化TCP传输
        val h264Profile = "42e01f"  // H264 baseline profile level 3.1
        val h264RtpMap = "a=rtpmap:96 H264/90000"
        val h264Fmtp = "a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=$h264Profile"
        
        // PCMA编码配置 - 优化TCP传输
        val pcmaRtpMap = "a=rtpmap:8 PCMA/8000/1"
        val pcmaFmtp = "a=fmtp:8 maxplaybackrate=8000;stereo=0;useinbandfec=1"
        
        var inVideoSection = false
        var inAudioSection = false
        
        for (line in lines) {
            when {
                line.startsWith("v=") -> {
                    modifiedLines.add(line)
                    // 添加TCP优化配置
                    modifiedLines.add("a=ice-options:tcp-so")
                    modifiedLines.add("a=rtcp-fb:* transport-cc")
                }
                line.startsWith("m=video") -> {
                    inVideoSection = true
                    inAudioSection = false
                    modifiedLines.add(line)
                    // 添加H264编码配置
                    modifiedLines.add(h264RtpMap)
                    modifiedLines.add(h264Fmtp)
                    // 添加TCP传输优化
                    modifiedLines.add("a=rtcp-mux")
                    modifiedLines.add("a=rtcp-rsize")
                }
                line.startsWith("m=audio") -> {
                    inVideoSection = false
                    inAudioSection = true
                    modifiedLines.add(line)
                    // 添加PCMA编码配置
                    modifiedLines.add(pcmaRtpMap)
                    modifiedLines.add(pcmaFmtp)
                    // 添加TCP传输优化
                    modifiedLines.add("a=rtcp-mux")
                }
                line.startsWith("a=rtpmap") && inVideoSection && line.contains("H264") -> {
                    // 跳过原有的H264配置，使用我们定义的
                    continue
                }
                line.startsWith("a=rtpmap") && inAudioSection && line.contains("PCMA") -> {
                    // 跳过原有的PCMA配置，使用我们定义的
                    continue
                }
                line.startsWith("a=fmtp") && (inVideoSection || inAudioSection) -> {
                    // 跳过原有的fmtp配置
                    continue
                }
                line.startsWith("a=candidate") && line.contains("udp") -> {
                    // 跳过UDP候选，只保留TCP
                    continue
                }
                else -> {
                    modifiedLines.add(line)
                }
            }
        }
        
        return modifiedLines.joinToString("\r\n")
    }
} 