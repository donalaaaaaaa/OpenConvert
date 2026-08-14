package com.openconvert.app.domain.model

import org.json.JSONArray
import org.json.JSONObject

object ConversionPayloadCodec {
    fun encode(payload: ConversionPayload): String {
        val json = JSONObject()
        json.put("sourceUris", JSONArray(payload.sourceUris))
        json.put("sourceNames", JSONArray(payload.sourceNames))
        json.put("pageRanges", payload.pageRanges)
        json.put("pages", JSONArray(payload.pages))
        json.put("outputTreeUri", payload.outputTreeUri ?: JSONObject.NULL)
        json.put("outputUris", JSONArray(payload.outputUris))
        json.put("rotateDegrees", payload.rotateDegrees)
        json.put("batchId", payload.batchId ?: JSONObject.NULL)
        json.put("cropAspect", payload.cropAspect)
        json.put("flip", payload.flip)
        json.put("stripMetadata", payload.stripMetadata)
        return json.toString()
    }

    fun decode(raw: String?): ConversionPayload {
        if (raw.isNullOrBlank()) return ConversionPayload()
        val json = JSONObject(raw)
        return ConversionPayload(
            sourceUris = json.optJSONArray("sourceUris").toStringList(),
            sourceNames = json.optJSONArray("sourceNames").toStringList(),
            pageRanges = json.optString("pageRanges"),
            pages = json.optJSONArray("pages").toIntList(),
            outputTreeUri = json.optionalString("outputTreeUri"),
            outputUris = json.optJSONArray("outputUris").toStringList(),
            rotateDegrees = json.optInt("rotateDegrees"),
            batchId = json.optionalString("batchId"),
            cropAspect = json.optString("cropAspect", "free"),
            flip = json.optInt("flip"),
            stripMetadata = json.optBoolean("stripMetadata"),
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

    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) add(optInt(index))
        }
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() }
    }
}
