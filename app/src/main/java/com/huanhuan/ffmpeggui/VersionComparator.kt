// VersionComparator.kt
package com.huanhuan.ffmpeggui

import com.vdurmont.semver4j.Semver

/**
 * 版本比较工具类
 * 使用 semver4j 库进行语义化版本比较
 */
object VersionComparator {

    // 版本比较模式，LOOSE 模式能处理更多格式（如 v1.2.3）
    private val COMPARISON_MODE = Semver.SemverType.LOOSE

    // 版本号前缀，自动移除
    private val VERSION_PREFIX_REGEX = Regex("^[vV]")

    /**
     * 比较两个版本号
     * @param currentVersion 当前版本号
     * @param latestVersion 最新版本号（从GitHub获取）
     * @return 版本比较结果
     */
    fun compare(currentVersion: String, latestVersion: String): VersionCompareResult {
        return try {
            // 清理版本号（移除 v 前缀）
            val cleanCurrent = currentVersion.replace(VERSION_PREFIX_REGEX, "")
            val cleanLatest = latestVersion.replace(VERSION_PREFIX_REGEX, "")

            // 创建 Semver 对象
            val currentSemver = Semver(cleanCurrent, COMPARISON_MODE)
            val latestSemver = Semver(cleanLatest, COMPARISON_MODE)

            // 执行比较
            val isNewer = latestSemver.isGreaterThan(currentSemver)
            val isSame = latestSemver.isEqualTo(currentSemver)

            // 如果是新版本，计算差异类型
            val diffType = if (isNewer) {
                determineDiffType(currentSemver, latestSemver)
            } else {
                null
            }

            VersionCompareResult.Success(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                isNewer = isNewer,
                isSame = isSame,
                isOlder = latestSemver.isLowerThan(currentSemver),
                diffType = diffType
            )
        } catch (e: Exception) {
            // 解析失败时降级为字符串比较
            e.printStackTrace()
            VersionCompareResult.Fallback(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                isNewer = latestVersion != currentVersion,
                error = e.message ?: "版本解析失败"
            )
        }
    }

    /**
     * 确定版本差异类型
     */
    private fun determineDiffType(current: Semver, latest: Semver): VersionDiff {
        return when {
            latest.major > current.major -> VersionDiff.MAJOR
            latest.minor > current.minor -> VersionDiff.MINOR
            latest.patch > current.patch -> VersionDiff.PATCH

            else -> VersionDiff.UNKNOWN
        }
    }

    /**
     * 检查是否真的有更新（比字符串比较更准确）
     */
    fun hasUpdate(currentVersion: String, latestVersion: String): Boolean {
        return when (val result = compare(currentVersion, latestVersion)) {
            is VersionCompareResult.Success -> result.isNewer
            is VersionCompareResult.Fallback -> result.isNewer
        }
    }

    /**
     * 获取版本差异的描述文本
     */
    fun getDiffDescription(diffType: VersionDiff?): String {
        return when (diffType) {
            VersionDiff.MAJOR -> "主要版本更新（包含不兼容的API变更）"
            VersionDiff.MINOR -> "次要版本更新（新增功能，向下兼容）"
            VersionDiff.PATCH -> "补丁版本更新（问题修复，向下兼容）"
            VersionDiff.SUFFIX -> "预发布版本变更"
            VersionDiff.UNKNOWN -> "有新版本可用"
            null -> "版本未知"
        }
    }

    /**
     * 格式化版本显示
     */
    fun formatVersionForDisplay(version: String): String {
        return if (version.startsWith("v", ignoreCase = true)) {
            version // 保留原有格式
        } else {
            "v$version" // 添加 v 前缀使显示更友好
        }
    }
}

/**
 * 版本差异类型枚举
 */
enum class VersionDiff {
    MAJOR,      // 主版本号变化 (x.0.0)
    MINOR,      // 次版本号变化 (0.x.0)
    PATCH,      // 修订号变化 (0.0.x)
    SUFFIX,     // 后缀变化 (alpha, beta, rc)
    UNKNOWN     // 未知变化
}

/**
 * 版本比较结果密封类
 */
sealed class VersionCompareResult {

    /**
     * 当前版本号
     */
    abstract val currentVersion: String

    /**
     * 最新版本号
     */
    abstract val latestVersion: String

    /**
     * 成功比较的结果（使用语义化版本）
     */
    data class Success(
        override val currentVersion: String,
        override val latestVersion: String,
        val isNewer: Boolean,      // 最新版本 > 当前版本
        val isSame: Boolean,       // 最新版本 == 当前版本
        val isOlder: Boolean,      // 最新版本 < 当前版本
        val diffType: VersionDiff? // 差异类型（当 isNewer 为 true 时有效）
    ) : VersionCompareResult()

    /**
     * 降级比较的结果（使用字符串比较）
     */
    data class Fallback(
        override val currentVersion: String,
        override val latestVersion: String,
        val isNewer: Boolean,      // 字符串不相同即为新版本
        val error: String          // 解析失败的原因
    ) : VersionCompareResult()

    /**
     * 便捷方法：是否有新版本
     */
    fun isNewVersionAvailable(): Boolean {
        return when (this) {
            is Success -> isNewer
            is Fallback -> isNewer
        }
    }

    /**
     * 获取版本差异描述
     */
    fun getDescription(): String {
        return when (this) {
            is Success -> {
                when {
                    isNewer -> "发现新版本 ${VersionComparator.formatVersionForDisplay(latestVersion)}"
                    isSame -> "当前已是最新版本"
                    isOlder -> "当前版本高于最新版本（可能处于测试阶段）"
                    else -> "版本状态未知"
                }
            }
            is Fallback -> {
                if (isNewer) {
                    "发现新版本 ${VersionComparator.formatVersionForDisplay(latestVersion)}（版本格式不标准）"
                } else {
                    "当前已是最新版本"
                }
            }
        }
    }

    /**
     * 获取详细的版本信息
     */
    fun getDetailedInfo(): String {
        return buildString {
            appendLine("当前版本：${VersionComparator.formatVersionForDisplay(currentVersion)}")
            appendLine("最新版本：${VersionComparator.formatVersionForDisplay(latestVersion)}")

            when (this@VersionCompareResult) {
                is Success -> {
                    when {
                        isNewer -> {
                            appendLine("状态：可更新")
                            diffType?.let {
                                appendLine("更新类型：${VersionComparator.getDiffDescription(it)}")
                            }
                        }
                        isSame -> appendLine("状态：已是最新")
                        isOlder -> appendLine("状态：当前版本高于最新版本")
                    }
                }
                is Fallback -> {
                    appendLine("状态：${if (isNewer) "可更新（粗略比较）" else "已是最新"}")
                    appendLine("备注：版本格式非标准，使用字符串比较")
                }
            }
        }
    }
}