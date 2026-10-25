package ru.hdlabs.treex.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri

object MaxFeedback {
    private const val NAME = "Yakoff"
    private const val PACKAGE_NAME = "ru.oneme.app"

    fun open(context: Context): Result<Unit> {
        val uri = "https://max.ru/$NAME".toUri()
        val packaged = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val plain = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return if (isInstalled(context)) {
            launch(context, packaged).recoverCatching { launch(context, plain).getOrThrow() }
        } else {
            launch(context, plain)
        }
    }

    private fun isInstalled(context: Context): Boolean = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(PACKAGE_NAME, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun launch(context: Context, intent: Intent): Result<Unit> = try {
        context.startActivity(intent)
        Result.success(Unit)
    } catch (e: ActivityNotFoundException) {
        Result.failure(IllegalStateException("Не удалось открыть MAX. Найдите Yakoff в мессенджере MAX."))
    }
}
