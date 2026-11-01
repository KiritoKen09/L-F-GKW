package com.example.data.local

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(data)
            val result = ArrayList<String>()
            for (i in 0 until arr.length()) {
                result.add(arr.getString(i))
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Failed to deserialize string list: $data", e)
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringIntMap(map: Map<String, Int>?): String {
        if (map.isNullOrEmpty()) return "{}"
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    @TypeConverter
    fun toStringIntMap(data: String?): Map<String, Int> {
        if (data.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(data)
            val result = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optInt(key, 0)
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Failed to deserialize string-int map: $data", e)
            emptyMap()
        }
    }
}
