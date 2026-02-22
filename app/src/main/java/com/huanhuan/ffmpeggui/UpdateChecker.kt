package com.huanhuan.ffmpeggui

import org.semver4j.Semver
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
            val isNewVersion: Boolean,
            val comparisonResult: VersionComparisonResult? = null
        ) : UpdateState()
        object NotAvailable : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    // 版本比较结果详情
    data class VersionComparisonResult(
        val currentVersion: String,
        val latestVersion: String,
        val isNewer: Boolean,
        val isSame: Boolean,
        val diffType: VersionDiff? = null
    )

    // 版本差异类型
    enum class VersionDiff {
        MAJOR, MINOR, PATCH, PRE_RELEASE, UNKNOWN
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

                    // 使用 semver4j 进行版本比较
                    val comparisonResult = compareVersions(updateInfo.tag_name)

                    withContext(Dispatchers.Main) {
                        when {
                            comparisonResult == null -> {
                                // 版本解析失败，降级为字符串比较
                                val currentVersion = currentVersionProvider?.invoke() ?: "1.0.0"
                                val isNewVersion = updateInfo.tag_name != currentVersion
                                _updateState.value = UpdateState.Available(
                                    updateInfo = updateInfo,
                                    isNewVersion = isNewVersion
                                )
                            }
                            comparisonResult.isNewer -> {
                                // 有新版本
                                _updateState.value = UpdateState.Available(
                                    updateInfo = updateInfo,
                                    isNewVersion = true,
                                    comparisonResult = comparisonResult
                                )
                            }
                            else -> {
                                // 版本相同或更低
                                _updateState.value = UpdateState.Available(
                                    updateInfo = updateInfo,
                                    isNewVersion = false,
                                    comparisonResult = comparisonResult
                                )
                            }
                        }
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

    // 使用 org.semver4j 比较版本
    private fun compareVersions(latestVersionStr: String): VersionComparisonResult? {
        val currentVersionStr = currentVersionProvider?.invoke() ?: return null

        return try {
            // 清理版本号（移除可能的 'v' 前缀）
            val cleanCurrent = currentVersionStr.trimStart('v', 'V')
            val cleanLatest = latestVersionStr.trimStart('v', 'V')

            // 创建 Semver 对象
            val currentSemver = Semver(cleanCurrent)
            val latestSemver = Semver(cleanLatest)

            // 比较版本
            val isNewer = latestSemver.isGreaterThan(currentSemver)
            val isSame = latestSemver.isEqualTo(currentSemver)

            // 确定差异类型
            val diffType = when {
                latestSemver.major > currentSemver.major -> VersionDiff.MAJOR
                latestSemver.minor > currentSemver.minor -> VersionDiff.MINOR
                latestSemver.patch > currentSemver.patch -> VersionDiff.PATCH
                latestSemver.isStable != currentSemver.isStable -> VersionDiff.PRE_RELEASE
                else -> null
            }

            VersionComparisonResult(
                currentVersion = currentVersionStr,
                latestVersion = latestVersionStr,
                isNewer = isNewer,
                isSame = isSame,
                diffType = if (isNewer) diffType else null
            )
        } catch (e: Exception) {
            // 版本解析失败，返回 null，让调用方使用字符串比较
            e.printStackTrace()
            null
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    // 获取版本差异的描述文本
    fun getVersionDiffDescription(result: VersionComparisonResult): String {
        return when (result.diffType) {
            VersionDiff.MAJOR -> "主要版本更新（可能包含不兼容的API变更）"
            VersionDiff.MINOR -> "次要版本更新（新增功能，向下兼容）"
            VersionDiff.PATCH -> "补丁版本更新（问题修复，向下兼容）"
            VersionDiff.PRE_RELEASE -> "预发布版本更新"
            else -> "有新版本可用"
        }
    }
}