package com.example.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Robust Permission and OEM Compatibility Manager for Android 10+ (API 29-36).
 * Handles standard permissions, MANAGE_EXTERNAL_STORAGE, Media Permissions (Android 13+),
 * and special vendor quirks (Samsung One UI, Xiaomi MIUI/HyperOS, Motorola, etc.).
 */
object PermissionHelper {

    enum class DeviceManufacturer {
        SAMSUNG,
        XIAOMI,
        MOTOROLA,
        HUAWEI,
        OPPO_REALME,
        VIVO,
        GOOGLE_PIXEL,
        OTHER
    }

    val currentManufacturer: DeviceManufacturer by lazy {
        val brand = (Build.BRAND ?: "").lowercase()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        when {
            brand.contains("samsung") || manufacturer.contains("samsung") -> DeviceManufacturer.SAMSUNG
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") || manufacturer.contains("xiaomi") -> DeviceManufacturer.XIAOMI
            brand.contains("motorola") || brand.contains("moto") || manufacturer.contains("motorola") -> DeviceManufacturer.MOTOROLA
            brand.contains("huawei") || brand.contains("honor") || manufacturer.contains("huawei") -> DeviceManufacturer.HUAWEI
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") || manufacturer.contains("oppo") -> DeviceManufacturer.OPPO_REALME
            brand.contains("vivo") || brand.contains("iqoo") || manufacturer.contains("vivo") -> DeviceManufacturer.VIVO
            brand.contains("google") || manufacturer.contains("google") -> DeviceManufacturer.GOOGLE_PIXEL
            else -> DeviceManufacturer.OTHER
        }
    }

    val manufacturerDisplayName: String
        get() = when (currentManufacturer) {
            DeviceManufacturer.SAMSUNG -> "Samsung (One UI)"
            DeviceManufacturer.XIAOMI -> "Xiaomi / Redmi / POCO (MIUI / HyperOS)"
            DeviceManufacturer.MOTOROLA -> "Motorola (Moto)"
            DeviceManufacturer.HUAWEI -> "Huawei / Honor (EMUI / MagicOS)"
            DeviceManufacturer.OPPO_REALME -> "Oppo / Realme / OnePlus (ColorOS / Realme UI)"
            DeviceManufacturer.VIVO -> "Vivo / iQOO (Funtouch OS / OriginOS)"
            DeviceManufacturer.GOOGLE_PIXEL -> "Google Pixel (Stock Android)"
            DeviceManufacturer.OTHER -> "${Build.MANUFACTURER.capitalize()} (${Build.MODEL})"
        }

    /**
     * Checks if the app has full file system access (MANAGE_EXTERNAL_STORAGE on Android 11+
     * or READ/WRITE_EXTERNAL_STORAGE on Android 10 and below).
     */
    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (e: Exception) {
                false
            }
        } else {
            hasLegacyStoragePermission(context)
        }
    }

    /**
     * Checks legacy storage permissions (Android 10 and below).
     */
    fun hasLegacyStoragePermission(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val writeGranted = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return readGranted && writeGranted
    }

    /**
     * Checks modern granular media permissions (Android 13+).
     */
    fun hasMediaPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val video = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            images && video && audio
        } else {
            hasLegacyStoragePermission(context)
        }
    }

    /**
     * Overall file access check: returns true if either full storage manager or adequate media/legacy is granted.
     */
    fun hasAnyStorageAccess(context: Context): Boolean {
        return hasAllFilesAccess(context) || hasMediaPermissions(context) || hasLegacyStoragePermission(context)
    }

    /**
     * Checks permission to install APK packages directly.
     */
    fun hasInstallPackagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }
    }

    /**
     * Checks notification permission (Android 13+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if battery optimization is disabled for this app.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * Array of runtime permissions needed based on Android API level.
     */
    fun getRuntimeStoragePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    // =========================================================================
    // INTENT OPENERS WITH MULTI-VENDOR FALLBACK
    // =========================================================================

    /**
     * Opens the All Files Access permission screen (Android 11+), with resilient fallback.
     */
    fun requestAllFilesAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Attempt 1: Direct app manage external storage intent
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            // Attempt 2: General all files access list
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            // Attempt 3: App details settings
            return openAppDetailsSettings(context)
        }
        return false
    }

    /**
     * Opens the Unknown Sources / Install Packages permission screen.
     */
    fun requestInstallPackagesPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            try {
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            return openAppDetailsSettings(context)
        }
        return true
    }

    /**
     * Opens App Info / Details settings.
     */
    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens battery optimization request/settings for long operations.
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Opens OEM-specific autostart/background manager (Samsung, Xiaomi, Motorola, Huawei, Oppo).
     */
    fun openOemBackgroundSettings(context: Context): Boolean {
        val oemIntents = when (currentManufacturer) {
            DeviceManufacturer.XIAOMI -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.securityscan.MainActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT)
            )
            DeviceManufacturer.SAMSUNG -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.dashboard.SMMainActivity"))
            )
            DeviceManufacturer.HUAWEI -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            )
            DeviceManufacturer.OPPO_REALME -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.clean.ListProtectedAppActivity"))
            )
            DeviceManufacturer.VIVO -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            )
            DeviceManufacturer.MOTOROLA -> listOf(
                Intent().setComponent(ComponentName("com.motorola.ccc.notification", "com.motorola.ccc.notification.PermissionActivity")),
                Intent().setComponent(ComponentName("com.motorola.genie", "com.motorola.genie.MainActivity"))
            )
            else -> emptyList()
        }

        for (intent in oemIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        // Fallback to app details
        return openAppDetailsSettings(context)
    }

    /**
     * Returns vendor-specific tips and guidance strings for users.
     */
    fun getOemGuidanceTips(): String {
        return when (currentManufacturer) {
            DeviceManufacturer.XIAOMI ->
                "Em aparelhos Xiaomi/Redmi/POCO (MIUI/HyperOS), ative 'Acesso a todos os arquivos' e configure 'Sem restrições' na economia de bateria para transferências contínuas em segundo plano."
            DeviceManufacturer.SAMSUNG ->
                "Em aparelhos Samsung (One UI), certifique-se de conceder 'Gerenciar todos os arquivos' em 'Acesso especial' e verifique se o Arcbox não está na lista de 'Aplicativos em suspensão profunda'."
            DeviceManufacturer.MOTOROLA ->
                "Em aparelhos Motorola (Moto), habilite 'Acesso a todos os arquivos' e garanta que as permissões de armazenamento e mídia estejam ativadas em 'Configurações > Apps'."
            DeviceManufacturer.HUAWEI ->
                "Em dispositivos Huawei/Honor, configure 'Inicialização de aplicativos' para 'Gerenciar manualmente' e ative 'Executar em segundo plano'."
            DeviceManufacturer.OPPO_REALME ->
                "Em aparelhos Oppo/Realme/OnePlus (ColorOS), ative 'Inicialização automática' e conceda 'Acesso a todos os arquivos' nas permissões do app."
            else ->
                "Para total compatibilidade no Android 10 ou superior, conceda 'Acesso a todos os arquivos' nas Configurações do Sistema."
        }
    }
}
