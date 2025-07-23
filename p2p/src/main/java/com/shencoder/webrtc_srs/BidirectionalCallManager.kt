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
 * 双向通话管理器
 * 协调WHIP推流和WHEP拉流，实现完整的双向音视频通话功能
 */
class BidirectionalCallManager(
    private val context: Context,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val eglBaseContext: EglBase.Context
) {
    
    private val whipWhepManager = WhipWhepManager(context, peerConnectionFactory, eglBaseContext)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // 通话状态
    private var isInCall = false
    private var currentRoomId: String? = null
    
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
        whipWhepManager.onWhipConnected = { resourceUrl ->
            XLog.i("WHIP推流连接成功: $resourceUrl")
            context.toastSuccess("本地视频已连接")
            onLocalVideoReady?.invoke()
        }
        
        whipWhepManager.onWhepConnected = { resourceUrl ->
            XLog.i("WHEP拉流连接成功: $resourceUrl")
            context.toastSuccess("远程视频已连接")
            onRemoteVideoReady?.invoke()
        }
        
        whipWhepManager.onError = { error ->
            XLog.e("通话错误: $error")
            context.toastError(error)
            onCallError?.invoke(error)
        }
    }
    
    /**
     * 开始双向通话
     * @param roomId 房间ID
     * @param localVideoSink 本地视频显示
     * @param remoteVideoSink 远程视频显示
     */
    fun startBidirectionalCall(
        roomId: String,
        localVideoSink: VideoSink,
        remoteVideoSink: VideoSink
    ) {
        if (isInCall) {
            context.toastWarning("通话已在进行中")
            return
        }
        
        currentRoomId = roomId
        isInCall = true
        
        XLog.i("开始双向通话，房间ID: $roomId")
        context.toastSuccess("正在建立通话连接...")
        
        scope.launch {
            try {
                // 同时启动WHIP推流和WHEP拉流
                val whipJob = launch {
                    withContext(Dispatchers.IO) {
                        whipWhepManager.initWhipPublish(roomId, localVideoSink)
                    }
                }
                
                val whepJob = launch {
                    withContext(Dispatchers.IO) {
                        whipWhepManager.initWhepPlay(roomId, remoteVideoSink)
                    }
                }
                
                // 等待两个连接都建立
                whipJob.join()
                whepJob.join()
                
                onCallStarted?.invoke()
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
                    whipWhepManager.release()
                }
                
                isInCall = false
                currentRoomId = null
                
                onCallEnded?.invoke()
                context.toastSuccess("通话已结束")
                
            } catch (e: Exception) {
                XLog.e("结束通话失败: ${e.message}")
                context.toastError("结束通话失败: ${e.message}")
            }
        }
    }
    
    /**
     * 切换摄像头
     */
    fun switchCamera() {
        if (!isInCall) {
            context.toastWarning("当前没有进行通话")
            return
        }
        
        try {
            whipWhepManager.switchCamera()
            context.toastSuccess("摄像头已切换")
        } catch (e: Exception) {
            XLog.e("切换摄像头失败: ${e.message}")
            context.toastError("切换摄像头失败: ${e.message}")
        }
    }
    
    /**
     * 静音/取消静音
     */
    fun toggleMute(): Boolean {
        if (!isInCall) {
            context.toastWarning("当前没有进行通话")
            return false
        }
        
        return try {
            val isMuted = whipWhepManager.toggleMute()
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
     * 开启/关闭摄像头
     */
    fun toggleCamera(): Boolean {
        if (!isInCall) {
            context.toastWarning("当前没有进行通话")
            return false
        }
        
        return try {
            val isCameraOff = whipWhepManager.toggleCamera()
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
     * 获取当前房间ID
     */
    fun getCurrentRoomId(): String? = currentRoomId
    
    /**
     * 释放资源
     */
    fun release() {
        if (isInCall) {
            endCall()
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                whipWhepManager.release()
            }
        }
    }
} 