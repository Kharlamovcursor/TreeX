package ru.hdlabs.treex.sync

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object TelegramBackup {
    private val packages = listOf(
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

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Бэкап генеалогического древа HDLabs")
            clipData = ClipData.newRawUri("backup", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val telegram = packages.firstOrNull { isInstalled(context.packageManager, it) }
        if (telegram != null) {
            intent.setPackage(telegram)
            context.grantUriPermission(telegram, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        } else {
            context.startActivity(Intent.createChooser(intent, "Отправить бэкап").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun sendToBot(token: String, chatId: String, file: File, caption: String) {
        val cleanToken = token.trim()
        require(cleanToken.matches(Regex("[0-9]{6,}:[A-Za-z0-9_-]{20,}"))) { "Некорректный токен бота" }
        require(chatId.trim().matches(Regex("-?[0-9]{1,20}"))) { "Некорректный chat id" }
        val url = URL("https://api.telegram.org/bot$cleanToken/sendDocument")
        require(url.protocol.equals("https", true) && url.host.equals("api.telegram.org", true)) {
            "Отправка разрешена только на api.telegram.org"
        }
        val boundary = "----TreeX${System.currentTimeMillis()}"
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        connection.outputStream.use { output ->
            fun field(name: String, value: String) {
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("chat_id", chatId.trim())
            field("caption", caption)
            output.write(
                "--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\nContent-Type: application/zip\r\n\r\n"
                    .toByteArray(),
            )
            file.inputStream().use { it.copyTo(output) }
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299 || !body.contains("\"ok\":true")) {
            throw IllegalStateException("Telegram не принял файл. Проверьте токен бота и chat id.")
        }
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
}
