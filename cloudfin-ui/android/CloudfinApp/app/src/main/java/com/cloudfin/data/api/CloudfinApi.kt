package com.cloudfin.data.api

import com.cloudfin.model.CoreStatus
import com.cloudfin.model.ModuleInfo
import com.cloudfin.model.ModuleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudfinApi(
    private val baseUrl: String = "http://127.0.0.1:19001"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun getCoreStatus(): Result<CoreStatus> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/core/status")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val json = JSONObject(body)
            if (json.optBoolean("ok")) {
                Result.success(parseCoreStatus(json.getJSONObject("data")))
            } else {
                Result.failure(Exception(json.optString("error")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModules(): Result<List<ModuleInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/modules")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val json = JSONObject(body)
            if (json.optBoolean("ok")) {
                val modulesArray = json.getJSONArray("data")
                val modules = mutableListOf<ModuleInfo>()
                for (i in 0 until modulesArray.length()) {
                    modules.add(parseModule(modulesArray.getJSONObject(i)))
                }
                Result.success(modules)
            } else {
                Result.failure(Exception(json.optString("error")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun controlModule(moduleId: String, action: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/modules/$moduleId/$action")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val json = JSONObject(body)
            if (json.optBoolean("ok")) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(json.optString("error")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCoreStatus(data: JSONObject): CoreStatus {
        return CoreStatus(
            version = data.getString("version"),
            uptimeSecs = data.getLong("uptime_secs"),
            deviceId = data.getString("device_id"),
            modules = emptyList() // 从 /api/modules 获取
        )
    }

    private fun parseModule(data: JSONObject): ModuleInfo {
        val statusStr = data.optString("status", "STOPPED")
        val status = try {
            ModuleStatus.valueOf(statusStr.uppercase())
        } catch (e: Exception) {
            ModuleStatus.STOPPED
        }
        return ModuleInfo(
            id = data.getString("id"),
            name = data.optString("name", data.getString("id")),
            version = data.optString("version", "unknown"),
            status = status,
            connectedPeers = data.optInt("connected_peers", 0)
        )
    }
}
