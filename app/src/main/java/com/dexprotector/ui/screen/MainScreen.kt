package com.dexprotector.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dexprotector.model.ProtectConfig
import com.dexprotector.model.ProtectProgress
import com.dexprotector.model.ProtectState
import com.dexprotector.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val config by viewModel.config.collectAsState()
    val selectedApk by viewModel.selectedApkPath.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.selectApk(it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DexProtector",
                        fontWeight = FontWeight.Bold,
                        color = OnPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "APK DEX 加固工具",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnBackground
                    )
                    Text(
                        text = "将 DEX 文件加密存储到 SO 库中",
                        fontSize = 14.sp,
                        color = OnBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择 APK 文件",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择 APK")
                    }

                    if (selectedApk != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "已选择: ${selectedApk!!.substringAfterLast("/")}",
                            fontSize = 12.sp,
                            color = Primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "加固选项",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SwitchRow(
                        icon = Icons.Default.Lock,
                        title = "加密 DEX",
                        subtitle = "将 DEX 文件 AES-256-GCM 加密后存入 SO",
                        checked = config.encryptDex,
                        onCheckedChange = {
                            viewModel.updateConfig(config.copy(encryptDex = it))
                        }
                    )

                    SwitchRow(
                        icon = Icons.Default.BugReport,
                        title = "反调试",
                        subtitle = "检测并阻止动态调试器附加",
                        checked = config.antiDebug,
                        onCheckedChange = {
                            viewModel.updateConfig(config.copy(antiDebug = it))
                        }
                    )

                    SwitchRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "完整性校验",
                        subtitle = "运行时校验 APK 签名和文件完整性",
                        checked = config.integrityCheck,
                        onCheckedChange = {
                            viewModel.updateConfig(config.copy(integrityCheck = it))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.startProtect() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = selectedApk != null && state !is ProtectState.Processing,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    disabledContainerColor = Primary.copy(alpha = 0.4f)
                )
            ) {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state is ProtectState.Processing) "加固中..." else "开始加固",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val currentState = state) {
                is ProtectState.Idle -> {}
                is ProtectState.Processing -> {
                    ProcessingCard(currentState.progress)
                }
                is ProtectState.Success -> {
                    SuccessCard(currentState.outputPath) {
                        viewModel.reset()
                    }
                }
                is ProtectState.Error -> {
                    ErrorCard(currentState.message) {
                        viewModel.reset()
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(text = subtitle, fontSize = 12.sp, color = OnBackground.copy(alpha = 0.5f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Primary)
        )
    }
}

@Composable
private fun ProcessingCard(progress: ProtectProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = progress.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Primary,
                trackColor = Primary.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = progress.step,
                fontSize = 14.sp,
                color = Primary
            )
            Text(
                text = "${(progress.progress * 100).toInt()}%",
                fontSize = 12.sp,
                color = OnBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SuccessCard(outputPath: String, onReset: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "加固完成",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "输出路径: $outputPath",
                fontSize = 11.sp,
                color = OnBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("再来一次")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onReset: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = Error,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "加固失败",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = OnBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("重试")
            }
        }
    }
}
