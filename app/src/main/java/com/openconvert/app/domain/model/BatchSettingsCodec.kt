package com.openconvert.app.domain.model

import org.json.JSONArray
import org.json.JSONObject

object BatchSettingsCodec {
    fun encode(settings: BatchSettings): String {
        val json = JSONObject()
        json.put("sourceUris", JSONArray(settings.sourceUris))
        json.put("sourceNames", JSONArray(settings.sourceNames))
        json.put("sourceFormats", JSONArray(settings.sourceFormats))
        json.put("targetFormat", settings.targetFormat)
        json.put("quality", settings.quality)
        json.put("resolution", settings.resolution)
        json.put("outputTreeUri", settings.outputTreeUri)
        return json.toString()
    }

    fun decode(raw: String?): BatchSettings {
        if (raw.isNullOrBlank()) return BatchSettings()
        val json = JSONObject(raw)
        return BatchSettings(
            sourceUris = json.optJSONArray("sourceUris").toStringList(),
            sourceNames = json.optJSONArray("sourceNames").toStringList(),
            sourceFormats = json.optJSONArray("sourceFormats").toStringList(),
            targetFormat = json.optString("targetFormat"),
            quality = json.optString("quality", QualityPreset.BALANCED.name),
            resolution = json.optString("resolution", ResolutionPreset.ORIGINAL.name),
            outputTreeUri = json.optString("outputTreeUri"),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }
}
