package com.dexprotector.model

data class ApkInfo(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val appName: String = "",
    val applicationClass: String? = null,
    val dexFiles: List<String> = emptyList(),
    val nativeLibs: List<String> = emptyList(),
    val abiList: List<String> = emptyList()
)

data class ProtectConfig(
    val encryptDex: Boolean = true,
    val antiDebug: Boolean = false,
    val integrityCheck: Boolean = false,
    val outputDir: String = "",
    val customKey: String? = null
)

data class ProtectProgress(
    val step: String = "",
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val isError: Boolean = false,
    val message: String = ""
)

sealed class ProtectState {
    data object Idle : ProtectState()
    data class Processing(val progress: ProtectProgress) : ProtectState()
    data class Success(val outputPath: String) : ProtectState()
    data class Error(val message: String) : ProtectState()
}
