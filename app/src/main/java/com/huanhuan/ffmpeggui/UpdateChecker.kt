// UpdateChecker.kt
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

    // 当前版本号提供器
    private var currentVersionProvider: (() -> String)? = null

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Available(
            val updateInfo: UpdateInfo,
            val compareResult: VersionCompareResult  // 使用工具类的比较结果
        ) : UpdateState()
        object NotAvailable : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    // 初始化设置
    fun initialize(versionProvider: () -> String) {
        currentVersionProvider = versionProvider
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

                    val json = Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    }

                    val updateInfo = json.decodeFromString<UpdateInfo>(response)

                    // 使用 VersionComparator 进行版本比较
                    val currentVersion = currentVersionProvider?.invoke() ?: "1.0.0"
                    val compareResult = VersionComparator.compare(currentVersion, updateInfo.tag_name)

                    withContext(Dispatchers.Main) {
                        _updateState.value = UpdateState.Available(
                            updateInfo = updateInfo,
                            compareResult = compareResult
                        )
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