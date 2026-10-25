package ru.hdlabs.treex.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import ru.hdlabs.treex.BuildConfig
import java.net.URLEncoder

object MaxFeedback {
    private const val PACKAGE_NAME = "ru.oneme.app"
    private const val SHARE_TEXT = "Здравствуйте! Пишу по приложению TreeX."

    fun open(context: Context): Result<Unit> {
        val configured = BuildConfig.MAX_FEEDBACK_URL.trim()
        val candidates = buildList {
            if (configured.isNotBlank()) add(configured)
            val encoded = URLEncoder.encode(SHARE_TEXT, Charsets.UTF_8.name())
            add("https://max.ru/:share?text=$encoded")
            add("https://max.ru/share?text=$encoded")
        }.distinct()
        for (uri in candidates.map { it.toUri() }) {
            val packaged = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(PACKAGE_NAME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val plain = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val result = if (isInstalled(context)) {
                launch(context, packaged).recoverCatching { launch(context, plain).getOrThrow() }
            } else {
                launch(context, plain)
            }
            if (result.isSuccess) return result
        }
        return Result.failure(
            IllegalStateException(
                "Не удалось открыть MAX. Установите мессенджер MAX или укажите ссылку на профиль в max.feedback.url при сборке.",
            ),
        )
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
        Result.failure(e)
    }
}
