package com.huanhuan.ffmpeggui

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val tag_name: String,      // 版本标签，如 "v1.0.1"
    val name: String,          // 版本名称
    val body: String,          // 更新说明
    val html_url: String,      // 下载页面
    val assets: List<Asset>?   // 资源文件
) {
    @Serializable
    data class Asset(
        val name: String,      // 文件名
        val browser_download_url: String  // 下载链接
    )

    // 获取APK下载链接
    fun getApkDownloadUrl(): String? {
        return assets?.find { it.name.endsWith(".apk") }?.browser_download_url
    }
}