package ru.hdlabs.treex.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.hdlabs.treex.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val apkUrl: String,
    val apkFileName: String,
)

class GitHubUpdateSource(
    private val owner: String,
    private val repo: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun checkForUpdate(currentVersionCode: Int): UpdateInfo? {
        if (owner.isBlank() || repo.isBlank()) return null
        var lastError: Exception? = null
        repeat(3) { attempt ->
            if (attempt > 0) Thread.sleep(1_500L * attempt)
            runCatching {
                fetchReleaseJson("https://api.github.com/repos/$owner/$repo/releases/latest")
            }.onSuccess { text ->
                return parseRelease(text, currentVersionCode)
            }.onFailure { error ->
                lastError = error as? Exception ?: Exception(error.message, error)
            }
        }
        throw lastError ?: IllegalStateException("Не удалось проверить обновления")
    }

    private fun parseRelease(text: String, currentVersionCode: Int): UpdateInfo? {
        if (text.isBlank()) {
            throw IllegalStateException("GitHub вернул пустой ответ. Попробуйте позже или скачайте APK вручную.")
        }
        val release = try {
            json.decodeFromString<GitHubRelease>(text)
        } catch (error: SerializationException) {
            throw IllegalStateException(
                "Не удалось прочитать данные релиза (обрыв связи). " +
                    "Повторите по Wi‑Fi или откройте github.com/$owner/$repo/releases в браузере.",
                error,
            )
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException(
                "Ответ GitHub не распознан. Скачайте APK вручную с github.com/$owner/$repo/releases",
                error,
            )
        }
        val apk = release.assets.firstOrNull { it.name.equals("app-release.apk", ignoreCase = true) }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: throw IllegalStateException("В последнем релизе нет файла APK")
        val remoteCode = ReleaseVersionParser.parseVersionCode(release.tagName, currentVersionCode)
        if (remoteCode <= currentVersionCode) return null
        return UpdateInfo(
            versionName = ReleaseVersionParser.displayVersionName(release.tagName, release.name),
            changelog = release.body.orEmpty(),
            apkUrl = apk.downloadUrl,
            apkFileName = apk.name,
        )
    }

    private fun fetchReleaseJson(apiUrl: String): String {
        val url = URL(apiUrl)
        require(url.protocol.equals("https", true) && url.host.equals("api.github.com", true))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HDLabs-TreeX-Android")
            val token = BuildConfig.GITHUB_FAMILY_TOKEN.trim()
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException(
                when (code) {
                    404 -> "Релиз не найден. Новая версия появится в GitHub Releases."
                    403 -> "GitHub временно ограничил проверку обновлений. Попробуйте позже или скачайте APK вручную."
                    else -> "GitHub ответил HTTP $code"
                },
            )
        }
        return text
    }
}

object ApkDownloadHelper {
    fun download(url: String, target: File) {
        val parsed = URL(url)
        val host = parsed.host.lowercase()
        require(parsed.protocol.equals("https", true)) { "Обновление доступно только по HTTPS" }
        require(host == "github.com" || host.endsWith(".githubusercontent.com")) {
            "Недоверенный источник обновления"
        }
        var lastError: Exception? = null
        repeat(3) { attempt ->
            if (attempt > 0) Thread.sleep(1_500L * attempt)
            runCatching {
                downloadOnce(parsed, target)
                return
            }.onFailure { error ->
                lastError = error as? Exception ?: Exception(error.message, error)
                if (target.exists()) target.delete()
            }
        }
        throw lastError ?: IOException("Не удалось скачать обновление")
    }

    private fun downloadOnce(parsed: URL, target: File) {
        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HDLabs-TreeX-Android")
            val token = BuildConfig.GITHUB_FAMILY_TOKEN.trim()
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IOException("Сервер вернул HTTP $code при загрузке APK")
        }
        val expected = connection.contentLengthLong
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (expected > 0L && target.length() != expected) {
            throw IOException("Загрузка прервалась (${target.length()} из $expected байт). Попробуйте по Wi‑Fi.")
        }
        require(target.exists() && target.length() > 10_000) { "Загруженный файл повреждён или пуст" }
        target.inputStream().use { input ->
            val header = ByteArray(4)
            val read = input.read(header)
            require(read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
                "Файл не является корректным APK"
            }
        }
    }
}
