package com.shencoder.webrtc_srs.constant

/**
 * 常量配置
 * @author  ShenBen
 * @date    2021/8/26 16:59
 * @email   714081644@qq.com
 */
object Constant {
    /**
     * SRS服务器IP
     */
    const val SRS_SERVER_IP = "192.168.2.91"

    /**
     * SRS服务http请求端口，默认1985
     */
    const val SRS_SERVER_HTTP_PORT = "1985"

    /**
     * SRS服务https请求端口，默认1990
     */
    const val SRS_SERVER_HTTPS_PORT = "1990"

    /**
     * WHIP/WHEP服务器IP (可以是SRS服务器或其他支持WHIP/WHEP的服务器)
     */
    const val WHIP_WHEP_SERVER_IP = "192.168.2.91"

    /**
     * WHIP/WHEP服务端口，默认8080
     */
    const val WHIP_WHEP_SERVER_PORT = "8080"

    const val SRS_SERVER_HTTP = "$SRS_SERVER_IP:$SRS_SERVER_HTTP_PORT"

    const val SRS_SERVER_HTTPS = "$SRS_SERVER_IP:$SRS_SERVER_HTTPS_PORT"

    /**
     * WHIP/WHEP基础URL - ZLMediaKit格式
     */
    const val WHIP_WHEP_BASE_URL = "http://$WHIP_WHEP_SERVER_IP:$WHIP_WHEP_SERVER_PORT"
}