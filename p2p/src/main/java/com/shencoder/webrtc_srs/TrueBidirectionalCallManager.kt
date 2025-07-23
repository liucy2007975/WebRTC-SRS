package com.shencoder.webrtc_srs

import android.content.Context
import com.elvishew.xlog.XLog
import com.shencoder.mvvmkit.util.toastError
import com.shencoder.mvvmkit.util.toastSuccess
import com.shencoder.mvvmkit.util.toastWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.*
import org.webrtc.EglBase
import com.shencoder.webrtc_srs.constant.Constant

/**
 * 真正的双向通话管理器 - SFU模式
 * 基于ZLMediaKit SFU（Selective Forwarding Unit）架构
 * 每个用户需要两个独立的流ID：
 * - 推流ID：发送自己的音视频到ZLMediaKit服务器
 * - 拉流ID：从ZLMediaKit服务器接收对方的音视频
 * 所有媒体流通过服务器转发，不建立P2P连接
 */
class TrueBidirectionalCallManager(
    private val context: Context,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val eglBaseContext: EglBase.Context
) {
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // 每个用户的流ID
    private var myPushStreamId: String? = null
    private var peerPushStreamId: String? = null
    
    // 两个独立的WhipWhepManager实例
    private val pushManager = WhipWhepManager(context, peerConnectionFactory, eglBaseContext)
    private val pullManager = WhipWhepManager(context, peerConnectionFactory, eglBaseContext)
    
    // 通话状态
    private var isInCall = false
    
    // 回调
    var onCallStarted: (() -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null
    var onCallError: ((String) -> Unit)? = null
    var onLocalVideoReady: (() -> Unit)? = null
    var onRemoteVideoReady: (() -> Unit)? = null
    
    init {
        setupCallbacks()
    }
    
    /**
     * 设置回调
     */
    private fun setupCallbacks() {
        // 推流回调（本地视频）
        pushManager.onWhipConnected = { resourceUrl ->
            XLog.i("本地推流成功: $resourceUrl")
            context.toastSuccess("本地视频已发送")
            onLocalVideoReady?.invoke()
            checkCallEstablished()
        }
        
        // 拉流回调（远程视频）
        pullManager.onWhepConnected = { resourceUrl ->
            XLog.i("远程拉流成功: $resourceUrl")
            context.toastSuccess("远程视频已接收")
            onRemoteVideoReady?.invoke()
            checkCallEstablished()
        }
        
        // 错误处理
        pushManager.onError = { error ->
            XLog.e("推流错误: $error")
            context.toastError("发送失败: $error")
            onCallError?.invoke("推流失败: $error")
        }
        
        pullManager.onError = { error ->
            XLog.e("拉流错误: $error")
            context.toastError("接收失败: $error")
            onCallError?.invoke("拉流失败: $error")
        }
    }
    
    /**
     * 开始双向通话
     * @param myPushStreamId 我的推流ID（发送自己的视频）
     * @param peerPushStreamId 对方的推流ID（接收对方的视频）
     * @param localVideoSink 本地视频预览
     * @param remoteVideoSink 远程视频显示
     */
    fun startBidirectionalCall(
        myPushStreamId: String,
        peerPushStreamId: String,
        localVideoSink: VideoSink,
        remoteVideoSink: VideoSink
    ) {
        if (isInCall) {
            context.toastWarning("通话已在进行中")
            return
        }
        
        this.myPushStreamId = myPushStreamId
        this.peerPushStreamId = peerPushStreamId
        isInCall = true
        
        XLog.i("开始双向通话")
        XLog.i("我的推流ID: $myPushStreamId")
        XLog.i("对方推流ID: $peerPushStreamId")
        
        context.toastSuccess("正在建立通话连接...")
        
        scope.launch {
            try {
                // 同时启动推流（发送自己的视频）和拉流（接收对方的视频）
                val pushJob = launch {
                    withContext(Dispatchers.IO) {
                        pushManager.initWhipPublish(myPushStreamId, localVideoSink)
                    }
                }
                
                val pullJob = launch {
                    withContext(Dispatchers.IO) {
                        pullManager.initWhepPlay(peerPushStreamId, remoteVideoSink)
                    }
                }
                
                // 等待两个连接都建立
                pushJob.join()
                pullJob.join()
                
                context.toastSuccess("双向通话已建立")
                
            } catch (e: Exception) {
                XLog.e("建立双向通话失败: ${e.message}")
                context.toastError("建立通话失败: ${e.message}")
                onCallError?.invoke("建立通话失败: ${e.message}")
                endCall()
            }
        }
    }
    
    /**
     * 检查通话是否完全建立
     */
    private fun checkCallEstablished() {
        if (pushManager.whipConnection != null && pullManager.whepConnection != null) {
            onCallStarted?.invoke()
        }
    }
    
    /**
     * 结束通话
     */
    fun endCall() {
        if (!isInCall) {
            return
        }
        
        XLog.i("结束双向通话")
        
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pushManager.release()
                    pullManager.release()
                }
                
                isInCall = false
                myPushStreamId = null
                peerPushStreamId = null
                
                onCallEnded?.invoke()
                context.toastSuccess("通话已结束")
                
            } catch (e: Exception) {
                XLog.e("结束通话失败: ${e.message}")
                context.toastError("结束通话失败: ${e.message}")
            }
        }
    }
    
    /**
     * 切换摄像头（推流端）
     */
    fun switchCamera() {
        if (!isInCall) {
            context.toastWarning("当前没有进行通话")
            return
        }
        
        try {
            pushManager.switchCamera()
            context.toastSuccess("摄像头已切换")
        } catch (e: Exception) {
            XLog.e("切换摄像头失败: ${e.message}")
            context.toastError("切换摄像头失败: ${e.message}")
        }
    }
    
    /**
     * 静音/取消静音（推流端）
     */
    fun toggleMute(): Boolean {
        if (!isInCall) {
            context.toastWarning("当前没有进行通话")
            return false
        }
        
        return try {
            val isMuted = pushManager.toggleMute()
            if (isMuted) {
                context.toastSuccess("已静音")
            } else {
                context.toastSuccess("已取消静音")
            }
            isMuted
        } catch (e: Exception) {
            XLog.e("切换静音失败: ${e.message}")
            context.toastError("切换静音失败: ${e.message}")
            false
        }
    }
    
    /**
     * 开启/关闭摄像头（推流端）
     */
    fun toggleCamera(): Boolean {
        if (!isInCall) {
            context.toastWarning("当前没有进行通话")
            return false
        }
        
        return try {
            val isCameraOff = pushManager.toggleCamera()
            if (isCameraOff) {
                context.toastSuccess("摄像头已关闭")
            } else {
                context.toastSuccess("摄像头已开启")
            }
            isCameraOff
        } catch (e: Exception) {
            XLog.e("切换摄像头失败: ${e.message}")
            context.toastError("切换摄像头失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取通话状态
     */
    fun isInCall(): Boolean = isInCall
    
    /**
     * 获取当前流信息
     */
    fun getStreamInfo(): Pair<String?, String?> = Pair(myPushStreamId, peerPushStreamId)
    
    /**
     * 释放资源
     */
    fun release() {
        if (isInCall) {
            endCall()
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                pushManager.release()
                pullManager.release()
            }
        }
    }
}