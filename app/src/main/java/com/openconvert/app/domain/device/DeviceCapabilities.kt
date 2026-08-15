package com.openconvert.app.domain.device

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

data class CodecCapability(
    val mimeType: String,
    val isEncoder: Boolean,
    val isHardwareAccelerated: Boolean,
    val maxSupportedWidth: Int = 0,
    val maxSupportedHeight: Int = 0,
    val maxSupportedFps: Int = 0,
)

data class DeviceHardwareProfile(
    val hasH264HardwareEncoder: Boolean,
    val hasH265HardwareEncoder: Boolean,
    val hasVp8HardwareEncoder: Boolean,
    val hasVp9HardwareEncoder: Boolean,
    val hasAv1HardwareDecoder: Boolean,
    val hasAv1HardwareEncoder: Boolean,
    val maxSupportedVideoResolution: String,
    val supportedEncoders: List<CodecCapability>,
)

/**
 * 本机多媒体硬件编解码能力探测器（计划书 §十八、§十九）。
 * 通过 MediaCodecList 读取真实硬件加速能力，为 EngineSelector 与 Smart Advisor 提供硬性依据。
 */
object DeviceCapabilities {

    private var cachedProfile: DeviceHardwareProfile? = null

    fun getHardwareProfile(): DeviceHardwareProfile {
        cachedProfile?.let { return it }

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecInfos = codecList.codecInfos

        val capabilities = mutableListOf<CodecCapability>()

        var h264HwEnc = false
        var h265HwEnc = false
        var vp8HwEnc = false
        var vp9HwEnc = false
        var av1HwDec = false
        var av1HwEnc = false
        var maxW = 1920
        var maxH = 1080

        for (info in codecInfos) {
            val isEncoder = info.isEncoder
            val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                    !info.name.startsWith("c2.android.", ignoreCase = true)
            }

            for (type in info.supportedTypes) {
                val mime = type.lowercase()
                val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull()
                val videoCaps = caps?.videoCapabilities

                val supportedW = videoCaps?.supportedWidths?.upper ?: 0
                val supportedH = videoCaps?.supportedHeights?.upper ?: 0

                if (supportedW > maxW) maxW = supportedW
                if (supportedH > maxH) maxH = supportedH

                val cap = CodecCapability(
                    mimeType = mime,
                    isEncoder = isEncoder,
                    isHardwareAccelerated = isHw,
                    maxSupportedWidth = supportedW,
                    maxSupportedHeight = supportedH,
                )
                capabilities += cap

                if (isEncoder && isHw) {
                    when (mime) {
                        MediaFormat.MIMETYPE_VIDEO_AVC -> h264HwEnc = true
                        MediaFormat.MIMETYPE_VIDEO_HEVC -> h265HwEnc = true
                        MediaFormat.MIMETYPE_VIDEO_VP8 -> vp8HwEnc = true
                        MediaFormat.MIMETYPE_VIDEO_VP9 -> vp9HwEnc = true
                        MediaFormat.MIMETYPE_VIDEO_AV1 -> av1HwEnc = true
                    }
                }
                if (!isEncoder && isHw && mime == MediaFormat.MIMETYPE_VIDEO_AV1) {
                    av1HwDec = true
                }
            }
        }

        val profile = DeviceHardwareProfile(
            hasH264HardwareEncoder = h264HwEnc,
            hasH265HardwareEncoder = h265HwEnc,
            hasVp8HardwareEncoder = vp8HwEnc,
            hasVp9HardwareEncoder = vp9HwEnc,
            hasAv1HardwareDecoder = av1HwDec,
            hasAv1HardwareEncoder = av1HwEnc,
            maxSupportedVideoResolution = "${maxW}x$maxH",
            supportedEncoders = capabilities.filter { it.isEncoder },
        )

        cachedProfile = profile
        return profile
    }
}
