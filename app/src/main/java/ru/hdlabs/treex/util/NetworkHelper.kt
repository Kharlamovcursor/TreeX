package ru.hdlabs.treex.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.UnknownHostException

object NetworkHelper {
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun looksLikeNetworkIssue(message: String?): Boolean {
        val text = message?.lowercase()?.trim() ?: return false
        if (text.isBlank()) return false
        return text.contains("unable to resolve host") ||
            text.contains("failed to connect") ||
            text.contains("network is unreachable") ||
            text.contains("no address associated with hostname") ||
            text.contains("timeout") ||
            text.contains("etimedout") ||
            text.contains("ehostunreach") ||
            text.contains("enetunreach")
    }

    fun looksLikeNetworkIssue(error: Throwable): Boolean =
        error is UnknownHostException || looksLikeNetworkIssue(error.message)

    /** null — не показывать пользователю (временная сеть). */
    fun toUserMessage(error: Throwable): String? {
        if (looksLikeNetworkIssue(error)) {
            return "Нет интернета. Синхронизация с Git отложена."
        }
        val raw = error.message.orEmpty()
        if (raw.contains("unexpected end", ignoreCase = true) ||
            raw.contains("EOF", ignoreCase = true) ||
            raw.contains("end of input", ignoreCase = true)
        ) {
            return "Связь оборвалась или ответ сервера неполный. Повторите по Wi‑Fi " +
                "или скачайте APK с github.com/Kharlamovcursor/TreeX/releases"
        }
        return raw.takeIf { it.isNotBlank() }
    }

    fun toUserMessage(message: String?): String? {
        if (looksLikeNetworkIssue(message)) {
            return "Нет интернета. Синхронизация с Git отложена."
        }
        return message?.takeIf { it.isNotBlank() }
    }
}
