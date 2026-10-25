package ru.hdlabs.treex.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object InstallHelper {
    @Volatile
    private var pendingApk: File? = null

    fun promptInstall(context: Context, apkFile: File) {
        if (!apkFile.isFile) return
        pendingApk = apkFile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }
        launchInstaller(context, apkFile)
        pendingApk = null
    }

    fun resumePendingInstall(activity: Activity) {
        val file = pendingApk ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return
        }
        launchInstaller(activity, file)
        pendingApk = null
    }

    private fun launchInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }
}
