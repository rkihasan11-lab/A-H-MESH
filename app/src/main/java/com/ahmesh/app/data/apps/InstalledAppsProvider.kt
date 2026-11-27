package com.ahmesh.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ahmesh.app.data.storage.AhMeshStorageManager
import com.ahmesh.app.domain.model.InstalledAppInfo
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.PendingOutgoingFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun loadLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val seenPackages = HashSet<String>()
        val results = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val packageName = activityInfo.packageName ?: continue
            if (!seenPackages.add(packageName)) continue

            val appInfo = activityInfo.applicationInfo ?: continue
            val sourceDir = appInfo.sourceDir ?: continue
            val apkFile = File(sourceDir)
            if (!apkFile.exists() || !apkFile.canRead()) continue

            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                }
            }.getOrNull()

            val label = runCatching {
                resolveInfo.loadLabel(pm).toString().trim()
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName

            val versionName = pkgInfo?.versionName?.takeIf { it.isNotBlank() } ?: "1.0"

            results += InstalledAppInfo(
                packageName = packageName,
                appName = label,
                versionName = versionName,
                sourceApkPath = sourceDir,
                apkSizeBytes = apkFile.length()
            )
        }

        results.sortedWith(
            compareByDescending<InstalledAppInfo> { it.packageName == context.packageName }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun toPendingOutgoingApk(app: InstalledAppInfo): PendingOutgoingFile {
        val sanitizedAppName = AhMeshStorageManager.sanitizeFileName(app.appName)
            .replace(' ', '_')
        val sanitizedVersion = app.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "${sanitizedAppName}_v${sanitizedVersion}.apk"
        return PendingOutgoingFile(
            fileName = fileName,
            mimeType = "application/vnd.android.package-archive",
            messageType = MessageType.APK,
            sizeBytes = app.apkSizeBytes,
            localFilePath = app.sourceApkPath,
            packageName = app.packageName
        )
    }
}
