package com.huanhuan.ffmpeggui

import android.content.Intent
import android.os.Build
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin

@Composable
fun rememberMarkwon(): Markwon {
    val context = LocalContext.current
    return remember {
        Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .build()
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    linkColor: androidx.compose.ui.graphics.Color? = null
) {
    val markwon = rememberMarkwon()
    val context = LocalContext.current

    // ✅ 在Composable函数内获取颜色值
    val textColor = color ?: MaterialTheme.colorScheme.onSurfaceVariant

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                // 使用颜色值
                setTextColor(textColor.toArgb())
                textSize = 14f
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        }
    )
}

/**
 * 获取当前设备的CPU架构
 */
fun getDeviceArchitecture(): String {
    val abis = Build.SUPPORTED_ABIS
    return when {
        abis.any { it.startsWith("arm64") || it == "aarch64" } -> "arm64-v8a"
        abis.any { it.startsWith("armeabi-v7a") } -> "armeabi-v7a"
        abis.any { it.startsWith("x86_64") } -> "x86_64"
        abis.any { it.startsWith("x86") } -> "x86"
        else -> abis.firstOrNull() ?: "unknown"
    }
}

/**
 * 根据架构选择最匹配的APK下载链接
 */
fun selectApkByArchitecture(updateInfo: UpdateInfo): String? {
    val assets = updateInfo.assets ?: return null
    if (assets.isEmpty()) return null

    val deviceArch = getDeviceArchitecture()

    // 优先级1：精确匹配设备架构
    assets.firstOrNull { asset ->
        asset.name.contains(deviceArch, ignoreCase = true) &&
                asset.name.endsWith(".apk", ignoreCase = true)
    }?.browser_download_url?.let { return it }

    // 优先级2：匹配通用arm64 (如果设备是arm64但没找到精确匹配)
    if (deviceArch == "arm64-v8a") {
        assets.firstOrNull { asset ->
            asset.name.contains("arm64", ignoreCase = true) &&
                    asset.name.endsWith(".apk", ignoreCase = true)
        }?.browser_download_url?.let { return it }
    }

    // 优先级3：匹配通用armv7 (如果设备是armv7但没找到精确匹配)
    if (deviceArch == "armeabi-v7a") {
        assets.firstOrNull { asset ->
            (asset.name.contains("armv7", ignoreCase = true) ||
                    asset.name.contains("armeabi", ignoreCase = true)) &&
                    asset.name.endsWith(".apk", ignoreCase = true)
        }?.browser_download_url?.let { return it }
    }

    // 优先级4：匹配通用x86_64
    if (deviceArch == "x86_64") {
        assets.firstOrNull { asset ->
            asset.name.contains("x86_64", ignoreCase = true) &&
                    asset.name.endsWith(".apk", ignoreCase = true)
        }?.browser_download_url?.let { return it }
    }

    // 优先级5：匹配通用x86
    if (deviceArch == "x86") {
        assets.firstOrNull { asset ->
            asset.name.contains("x86", ignoreCase = true) &&
                    !asset.name.contains("x86_64", ignoreCase = true) &&
                    asset.name.endsWith(".apk", ignoreCase = true)
        }?.browser_download_url?.let { return it }
    }

    // 优先级6：返回第一个APK文件
    return assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.browser_download_url
}

/**
 * 生成加速下载链接
 */
fun getAcceleratedDownloadUrl(originalUrl: String): String {
    return when {
        originalUrl.contains("github.com") -> {
            // 将 https://github.com/... 转换为 https://gh.llkk.cc/github.com/...
            originalUrl.replace("https://github.com/", "https://gh.llkk.cc/https://github.com/")
        }
        originalUrl.contains("objects.githubusercontent.com") ||
                originalUrl.contains("github-releases") -> {
            // 对于GitHub的Release资产链接，可能需要特殊处理
            "https://gh.llkk.cc/" + originalUrl.replace("https://", "")
        }
        else -> originalUrl // 非GitHub链接不加速
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val deviceArch = remember { getDeviceArchitecture() }
    val scrollState = rememberScrollState()

    // 根据架构选择的下载链接
    val selectedApkUrl by remember(updateInfo) {
        mutableStateOf(selectApkByArchitecture(updateInfo))
    }

    // 原始发布页面链接
    val releasePageUrl = updateInfo.html_url

    // 所有可用的APK列表（用于显示）
    val availableApks = remember(updateInfo) {
        updateInfo.assets?.filter { it.name.endsWith(".apk", ignoreCase = true) } ?: emptyList()
    }

    // 原始的getApkDownloadUrl结果（用于兼容）
    val originalApkUrl = remember(updateInfo) {
        updateInfo.getApkDownloadUrl()
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "发现新版本",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                }

                // 版本信息
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "最新版本：${updateInfo.tag_name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (updateInfo.name.isNotBlank() && updateInfo.name != updateInfo.tag_name) {
                    Text(
                        text = updateInfo.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 设备架构信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "设备架构: $deviceArch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        if (selectedApkUrl != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✓ 已匹配",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (originalApkUrl != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚡ 通用版本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 更新说明标题
                Text(
                    text = "更新说明：",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // 说明内容 - 使用Markdown渲染
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    if (updateInfo.body.isNotBlank()) {
                        MarkdownText(
                            markdown = updateInfo.body,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            linkColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "暂无详细更新说明",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 可用APK列表（如果有多个）
                if (availableApks.size > 1) {
                    Text(
                        text = "可用版本：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableApks.forEach { apk ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = apk.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 下载按钮区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 第一行：架构匹配下载 和 以后再说
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("以后再说")
                        }

                        // 主下载按钮（优先使用架构匹配的APK）
                        val mainDownloadUrl = selectedApkUrl ?: originalApkUrl ?: releasePageUrl
                        val isMainDownloadEnabled = mainDownloadUrl.isNotBlank()

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
                            tooltip = {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.inverseSurface
                                    )
                                ) {
                                    Text(
                                        text = when {
                                            selectedApkUrl != null -> "下载适配 ${deviceArch} 的版本"
                                            originalApkUrl != null -> "下载通用APK版本"
                                            else -> "使用发布页面下载"
                                        },
                                        modifier = Modifier.padding(8.dp),
                                        color = MaterialTheme.colorScheme.inverseOnSurface
                                    )
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, mainDownloadUrl.toUri())
                                        context.startActivity(intent)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "无法打开下载链接: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = isMainDownloadEnabled
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    when {
                                        selectedApkUrl != null -> "下载($deviceArch)"
                                        originalApkUrl != null -> "下载(通用)"
                                        else -> "下载(发布页)"
                                    }
                                )
                            }
                        }
                    }

                    // 第二行：加速下载和发布页按钮（仅当有APK链接时显示加速选项）
                    if (selectedApkUrl != null || originalApkUrl != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 发布页按钮（备选）
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, releasePageUrl.toUri())
                                        context.startActivity(intent)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "无法打开发布页", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = releasePageUrl.isNotBlank()
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("发布页")
                            }

                            // 加速下载按钮（使用选中的APK链接加速）
                            val apkUrlForAccelerate = selectedApkUrl ?: originalApkUrl
                            if (apkUrlForAccelerate != null) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
                                    tooltip = {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.inverseSurface
                                            )
                                        ) {
                                            Text(
                                                text = "使用 gh.llkk.cc 加速下载\n适合下载缓慢的情况",
                                                modifier = Modifier.padding(8.dp),
                                                color = MaterialTheme.colorScheme.inverseOnSurface
                                            )
                                        }
                                    },
                                    state = rememberTooltipState(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Button(
                                        onClick = {
                                            val acceleratedUrl = getAcceleratedDownloadUrl(apkUrlForAccelerate)
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, acceleratedUrl.toUri())
                                                context.startActivity(intent)
                                                onDismiss()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "无法打开加速链接", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Speed,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("加速下载")
                                    }
                                }
                            }
                        }
                    } else if (releasePageUrl.isNotBlank()) {
                        // 如果没有APK只有发布页，显示一个单独的发布页按钮
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, releasePageUrl.toUri())
                                    context.startActivity(intent)
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开发布页", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("前往发布页下载")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "检查更新失败",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重试")
                    }
                }
            }
        }
    }
}