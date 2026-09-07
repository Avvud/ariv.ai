package com.example.arivai.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class HardwareReport(
    val totalRamMb: Long,
    val availRamMb: Long,
    val isLowMemory: Boolean,
    val cpuCores: Int,
    val supportedAbis: List<String>,
    val isArm64: Boolean,
    val recommendedModel: String,
    val canRun4BModel: Boolean
)

object HardwareChecker {

    fun checkDeviceHardware(context: Context): HardwareReport {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val availRamMb = memoryInfo.availMem / (1024 * 1024)
        val cpuCores = Runtime.getRuntime().availableProcessors()

        val abis = Build.SUPPORTED_ABIS.toList()
        val isArm64 = abis.any { it.contains("arm64") }

        // 2GB+ free RAM is sufficient to run on-device GGUF inference
        val canRun4B = (availRamMb >= 2000) && isArm64
        val recommended = if (availRamMb >= 3500) {
            "Qwen3-4B-Instruct-Q4_K_M.gguf (~2.5GB file)"
        } else {
            "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf (~950MB file) [Recommended for 3x faster speed]"
        }

        return HardwareReport(
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            isLowMemory = memoryInfo.lowMemory,
            cpuCores = cpuCores,
            supportedAbis = abis,
            isArm64 = isArm64,
            recommendedModel = recommended,
            canRun4BModel = canRun4B
        )
    }
}
