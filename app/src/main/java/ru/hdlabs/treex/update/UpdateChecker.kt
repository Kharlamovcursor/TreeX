package ru.hdlabs.treex.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
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
        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        require(url.protocol.equals("https", true) && url.host.equals("api.github.com", true))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HDLabs-TreeX-Android")
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException(
                when (code) {
                    404 -> "Релиз не найден. Новая версия появится в GitHub Releases."
                    403 -> "GitHub временно ограничил проверку обновлений"
                    else -> "GitHub ответил HTTP $code"
                },
            )
        }
        val release = json.decodeFromString<GitHubRelease>(text)
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
}

object ApkDownloadHelper {
    fun download(url: String, target: File) {
        val parsed = URL(url)
        val host = parsed.host.lowercase()
        require(parsed.protocol.equals("https", true)) { "Обновление доступно только по HTTPS" }
        require(host == "github.com" || host.endsWith(".githubusercontent.com")) {
            "Недоверенный источник обновления"
        }
        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
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
