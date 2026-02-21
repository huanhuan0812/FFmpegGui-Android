package com.huanhuan.ffmpeggui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val GITHUB_API = "https://api.github.com/repos/huanhuan0812/FFmpegGui-Android/releases/latest"

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Available(val updateInfo: UpdateInfo) : UpdateState()
        object NotAvailable : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    fun checkForUpdates() {
        _updateState.value = UpdateState.Checking

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(GITHUB_API).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }

                    // 配置Json解析器，忽略未知字段并处理空值
                    val json = Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true // 将null值转换为默认值
                    }

                    val updateInfo = json.decodeFromString<UpdateInfo>(response)

                    withContext(Dispatchers.Main) {
                        _updateState.value = UpdateState.Available(updateInfo)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _updateState.value = UpdateState.Error("检查更新失败：HTTP ${connection.responseCode}")
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _updateState.value = UpdateState.Error("网络错误：${e.message}")
                }
            }
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}