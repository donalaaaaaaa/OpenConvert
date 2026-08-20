package com.openconvert.app.domain.preset

import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ResolutionPreset

/**
 * 把预设的尺寸约束（§8.1 的「最长边 1920」「1024×1024」）换算成
 * 引擎能直接执行的目标像素。
 *
 * 优先级：固定尺寸 > 最长边 > [ResolutionPreset] 百分比。
 * **只缩小不放大**——预设意图是省体积，把小图放大只会增大文件且损失观感。
 */
object PresetSizing {

    data class TargetSize(val width: Int, val height: Int)

    /**
     * @return 目标尺寸；null 表示保持原尺寸。
     */
    fun resolve(preset: Preset, sourceWidth: Int, sourceHeight: Int): TargetSize? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        preset.fixedWidthPx?.let { w ->
            preset.fixedHeightPx?.let { h ->
                if (w > 0 && h > 0) return TargetSize(w, h)
            }
        }

        preset.longestEdgePx?.let { limit ->
            if (limit <= 0) return null
            val longest = maxOf(sourceWidth, sourceHeight)
            if (longest <= limit) return null // 已经够小，不放大
            val scale = limit.toDouble() / longest
            return TargetSize(
                width = (sourceWidth * scale).toInt().coerceAtLeast(1),
                height = (sourceHeight * scale).toInt().coerceAtLeast(1),
            )
        }

        if (preset.resolution != ResolutionPreset.ORIGINAL) {
            val percent = preset.resolution.scalePercent
            return TargetSize(
                width = (sourceWidth * percent / 100).coerceAtLeast(1),
                height = (sourceHeight * percent / 100).coerceAtLeast(1),
            )
        }

        return null
    }

    /** 把预设里与 payload 相关的字段（裁剪、去元数据）写进任务负载。 */
    fun applyTo(payload: ConversionPayload, preset: Preset): ConversionPayload = payload.copy(
        cropAspect = preset.cropAspect,
        stripMetadata = preset.stripMetadata,
    )
}
