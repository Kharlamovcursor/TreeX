package ru.hdlabs.treex.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import ru.hdlabs.treex.BuildConfig

object TelegramFeedback {
    private const val USERNAME = "Yakofffff"

    private val knownPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "nekox.messenger",
        "tw.nekomimi.nekogram",
        "com.exteragram.messenger",
        "uz.unnarsx.cherrygram",
        "com.radolyn.ayugram",
        "xyz.nextalone.nagram",
        "app.nicegram",
    )

    private val browserPackages = setOf(
        "com.android.browser",
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.sec.android.app.sbrowser",
        "com.mi.globalbrowser",
        "com.yandex.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.UCMobile.intl",
        "mark.via.gp",
    )

    fun open(context: Context): Result<Unit> {
        val name = username()
        val appUri = "tg://resolve?domain=$name".toUri()
        val webUri = "https://t.me/$name".toUri()
        val packageName = findTelegramPackage(context, appUri, webUri)
            ?: return Result.failure(IllegalStateException("Не найден клиент Telegram. Откройте его и найдите @$name."))
        return launch(context, packageName, appUri).recoverCatching {
            launch(context, packageName, webUri).getOrThrow()
        }
    }

    private fun username(): String {
        val direct = BuildConfig.TELEGRAM_FEEDBACK_URL.trim()
        if (direct.isNotEmpty()) parseUsername(direct.toUri())?.let { return it }
        val bot = BuildConfig.TELEGRAM_FEEDBACK_BOT.trim().removePrefix("@")
        if (bot.isNotEmpty()) return bot
        return USERNAME
    }

    private fun parseUsername(uri: Uri): String? {
        if (uri.scheme == "tg") return uri.getQueryParameter("domain")?.trim()?.takeIf { it.isNotEmpty() }
        val segment = uri.pathSegments.firstOrNull()?.trim().orEmpty()
        if (segment.isEmpty() || segment.startsWith("+")) return null
        return segment.removePrefix("@")
    }

    private fun findTelegramPackage(context: Context, appUri: Uri, webUri: Uri): String? {
        val pm = context.packageManager
        queryPackages(pm, Intent(Intent.ACTION_VIEW, appUri)).firstOrNull()?.let { return it }
        queryPackages(pm, Intent(Intent.ACTION_VIEW, webUri))
            .firstOrNull { it !in browserPackages }
            ?.let { return it }
        return knownPackages.firstOrNull { isInstalled(pm, it) }
    }

    private fun queryPackages(pm: PackageManager, intent: Intent): List<String> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0
        return pm.queryIntentActivities(intent, flags)
            .map { it.activityInfo.packageName }
            .filter { it !in browserPackages }
            .distinct()
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun launch(context: Context, packageName: String, uri: Uri): Result<Unit> = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        Result.success(Unit)
    } catch (e: ActivityNotFoundException) {
        Result.failure(e)
    }
}
