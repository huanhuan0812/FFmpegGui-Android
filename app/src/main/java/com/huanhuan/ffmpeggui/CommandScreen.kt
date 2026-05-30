package com.huanhuan.ffmpeggui

// 主题
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.huanhuan.ffmpeggui.ui.theme.Pink40
import com.huanhuan.ffmpeggui.ui.theme.Pink80
import com.huanhuan.ffmpeggui.ui.theme.Purple40
import com.huanhuan.ffmpeggui.ui.theme.Purple80
import com.huanhuan.ffmpeggui.ui.theme.PurpleGrey40
import com.huanhuan.ffmpeggui.ui.theme.PurpleGrey80
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun FFmpegDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}


// 主屏幕
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegCommandScreen(
    viewModel: FFmpegViewModel = viewModel()
) {
    var commandText by remember { mutableStateOf("-version") }
    val executionResult by viewModel.executionResult.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()

    // 为输入框和结果区域分别创建滚动状态
    val inputScrollState = rememberScrollState()
    val resultScrollState = rememberScrollState()

    // 当结果更新时自动滚动到底部
    LaunchedEffect(executionResult) {
        if (isExecuting) {
            // 延迟一小段时间让UI更新完成
            kotlinx.coroutines.delay(100)
            resultScrollState.animateScrollTo(resultScrollState.maxValue)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FFmpeg 命令执行器") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 上半部分：命令输入和结果
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CommandInputCard(
                    commandText = commandText,
                    onCommandChange = { commandText = it },
                    onExampleClick = { example -> commandText = example },
                    scrollState = inputScrollState
                )

                ExecutionResultCard(
                    executionResult = executionResult,
                    onClear = {
                        viewModel.clearResult()
                        // 清空时滚动到顶部
                        CoroutineScope(Dispatchers.Main).launch{
                            resultScrollState.scrollTo(0)
                        }
                    },
                    scrollState = resultScrollState
                )
            }

            // 下半部分：执行按钮和取消按钮固定在底部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExecuteButton(
                    onClick = {
                        if (commandText.isNotBlank()) {
                            viewModel.executeCommand(commandText)
                        }
                    },
                    isExecuting = isExecuting,
                    enabled = commandText.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )

                if (isExecuting) {
                    OutlinedButton(
                        onClick = { viewModel.cancelExecution() },
                        modifier = Modifier.weight(0.5f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

// 命令输入卡片
@Composable
fun CommandInputCard(
    commandText: String,
    onCommandChange: (String) -> Unit,
    onExampleClick: (String) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 多行文本输入框，带有滚动
            OutlinedTextField(
                value = commandText,
                onValueChange = onCommandChange,
                label = { Text("输入ffmpeg命令") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp)
                    .verticalScroll(scrollState),
                singleLine = false,
                maxLines = Int.MAX_VALUE,  // 允许无限行
                minLines = 4,
                isError = commandText.isBlank(),
                supportingText = {
                    if (commandText.isBlank()) {
                        Text("命令不能为空")
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Text, imeAction = ImeAction.Unspecified,platformImeOptions = null, showKeyboardOnFocus = null,hintLocales = null),
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}

// 执行结果卡片
@Composable
fun ExecutionResultCard(
    executionResult: String,
    onClear: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "执行结果",
                    style = MaterialTheme.typography.titleMedium
                )

                if (executionResult.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清空结果"
                        )
                    }
                }
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            // 可滚动的结果区域 - 使用传入的scrollState实现自动滚动
            SelectionContainer {
                Text(
                    text = executionResult.ifEmpty { "暂无执行结果，请输入命令后点击执行按钮" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                        .verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = when {
                            executionResult.contains("❌") -> MaterialTheme.colorScheme.error
                            executionResult.contains("✅") -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                )
            }
        }
    }
}

// 执行按钮
@Composable
fun ExecuteButton(
    onClick: () -> Unit,
    isExecuting: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isExecuting
    ) {
        if (isExecuting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("执行中...")
        } else {
            Text("执行命令")
        }
    }
}

@Composable
fun CommandScreen(
    onBack : () -> Unit,
    navController: NavController,
    viewModel: FFmpegViewModel = viewModel()
){
    FFmpegDemoTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "命令行模式",
                        style = MaterialTheme.typography.titleMedium
                    )

                    CommandInputCard(
                        commandText = "",
                        onCommandChange = {},
                        onExampleClick = {},
                        scrollState = rememberScrollState()
                    )

                    ExecutionResultCard(
                        executionResult = "",
                        onClear = {},
                        scrollState = rememberScrollState()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExecuteButton(
                        onClick = {},
                        isExecuting = false,
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// 预览 - 完整屏幕
@Preview(showBackground = true, name = "完整屏幕预览", heightDp = 800)
@Composable
fun PreviewFFmpegCommandScreen() {
    FFmpegDemoTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "命令行模式",
                        style = MaterialTheme.typography.titleMedium
                    )

                    CommandInputCard(
                        commandText = "ffmpeg -version\n-i input.mp4\n-c:v libx264\noutput.mp4",
                        onCommandChange = {},
                        onExampleClick = {},
                        scrollState = rememberScrollState()
                    )

                    ExecutionResultCard(
                        executionResult = "正在执行命令: ffmpeg -version\n\nffmpeg version 6.0 Copyright (c) 2000-2023 the FFmpeg developers\nbuilt with Android clang version 14.0.6\nconfiguration: --target-os=android --enable-cross-compile\nlibavutil      58.  2.100 / 58.  2.100\nlibavcodec     60.  3.100 / 60.  3.100\nlibavformat    60.  3.100 / 60.  3.100\nlibavdevice    60.  1.100 / 60.  1.100\nlibavfilter     9.  3.100 /  9.  3.100\nlibswscale      7.  1.100 /  7.  1.100\nlibswresample   4. 10.100 /  4. 10.100\nlibpostproc    57.  1.100 / 57.  1.100\n\n✅ 命令执行成功",
                        onClear = {},
                        scrollState = rememberScrollState()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExecuteButton(
                        onClick = {},
                        isExecuting = false,
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}