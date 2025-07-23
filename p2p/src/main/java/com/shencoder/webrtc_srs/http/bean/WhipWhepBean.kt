package com.shencoder.webrtc_srs.http.bean

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * WHIP/WHEP 请求Bean
 */
@JsonClass(generateAdapter = true)
data class WhipWhepRequestBean(
    @Json(name = "sdp")
    val sdp: String
)

/**
 * WHIP/WHEP 响应Bean
 */
@JsonClass(generateAdapter = true)
data class WhipWhepResponseBean(
    @Json(name = "sdp")
    val sdp: String?,
    @Json(name = "location")
    val location: String?,
    @Json(name = "link")
    val link: String?
)

/**
 * WHIP/WHEP 资源信息Bean
 */
@JsonClass(generateAdapter = true)
data class WhipWhepResourceBean(
    @Json(name = "resource_id")
    val resourceId: String?,
    @Json(name = "resource_url")
    val resourceUrl: String?,
    @Json(name = "status")
    val status: String?
) 