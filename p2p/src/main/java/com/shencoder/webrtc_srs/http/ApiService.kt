package com.shencoder.webrtc_srs.http

import com.shencoder.webrtc_srs.http.bean.SrsRequestBean
import com.shencoder.webrtc_srs.http.bean.SrsResponseBean
import com.shencoder.webrtc_srs.http.bean.WhipWhepRequestBean
import com.shencoder.webrtc_srs.http.bean.WhipWhepResponseBean
import okhttp3.ResponseBody
import retrofit2.http.*

/**
 * API服务接口
 * 支持SRS RTC API和WHIP/WHEP协议
 * @author  ShenBen
 * @date    2021/8/19 12:38
 * @email   714081644@qq.com
 */
interface ApiService {

    // 原有的SRS RTC API
    @POST("/rtc/v1/play/")
    suspend fun play(@Body body: SrsRequestBean): SrsResponseBean

    @POST("/rtc/v1/publish/")
    suspend fun publish(@Body body: SrsRequestBean): SrsResponseBean

    // WHIP协议 - WebRTC-HTTP Ingestion Protocol
    @POST
    @Headers("Content-Type: application/sdp")
    suspend fun whipPublish(
        @Url url: String,
        @Body sdp: String
    ): retrofit2.Response<WhipWhepResponseBean>

    // WHEP协议 - WebRTC-HTTP Egress Protocol  
    @POST
    @Headers("Content-Type: application/sdp")
    suspend fun whepPlay(
        @Url url: String,
        @Body sdp: String
    ): retrofit2.Response<WhipWhepResponseBean>

    // WHIP/WHEP 资源删除
    @DELETE
    suspend fun deleteResource(@Url url: String): retrofit2.Response<ResponseBody>

    // WHIP/WHEP 资源状态查询
    @GET
    suspend fun getResourceStatus(@Url url: String): retrofit2.Response<WhipWhepResponseBean>
}