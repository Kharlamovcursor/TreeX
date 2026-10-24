package ru.hdlabs.treex.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

class GitHubTreeSync(
    private val owner: String,
    private val repo: String,
    private val branch: String,
    private val path: String,
    private val token: String,
) {
    data class RemoteFile(val sha: String, val text: String)
    data class RemoteCommit(val sha: String, val message: String, val date: String)

    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(owner.matches(SLUG)) { "Некорректный владелец репозитория" }
        require(repo.matches(SLUG)) { "Некорректное имя репозитория" }
        require(branch.isNotBlank()) { "Не указана ветка Git" }
        require(path.isNotBlank()) { "Не указан путь к файлу в Git" }
        require(token.isNotBlank()) { "Нет токена GitHub" }
    }

    fun fetch(): RemoteFile? = fetchRef(branch, missingIsNull = true)

    fun fetchExact(ref: String): RemoteFile =
        fetchRef(ref, missingIsNull = false) ?: throw IllegalStateException("Версия в Git не найдена")

    fun listCommits(): List<RemoteCommit> {
        val url = "https://api.github.com/repos/$owner/$repo/commits?per_page=20" +
            "&sha=${encode(branch)}&path=${encode(path.trim().trim('/'))}"
        val (code, body) = request("GET", url, null)
        if (code !in 200..299) throw IllegalStateException(githubMessage(code, body))
        return json.parseToJsonElement(body).jsonArray.map { element ->
            val item = element.jsonObject
            val commit = item["commit"]?.jsonObject
            RemoteCommit(
                sha = item["sha"]?.jsonPrimitive?.content.orEmpty(),
                message = commit?.get("message")?.jsonPrimitive?.content.orEmpty().lineSequence().firstOrNull().orEmpty(),
                date = commit?.get("author")?.jsonObject?.get("date")?.jsonPrimitive?.content.orEmpty().take(16).replace('T', ' '),
            )
        }.filter { it.sha.isNotBlank() }
    }

    fun push(text: String, currentSha: String?, message: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 900_000) {
            "Файл древа слишком большой для GitHub. Фото в Git не отправляются — отправьте ZIP в Telegram."
        }
        val payload = buildString {
            append("{\"message\":")
            append(jsonString(message))
            append(",\"content\":")
            append(jsonString(Base64.getEncoder().encodeToString(bytes)))
            append(",\"branch\":")
            append(jsonString(branch))
            if (!currentSha.isNullOrBlank()) {
                append(",\"sha\":")
                append(jsonString(currentSha))
            }
            append('}')
        }
        val url = "https://api.github.com/repos/$owner/$repo/contents/${encodedPath()}"
        val (code, body) = request("PUT", url, payload)
        if (code !in 200..299) throw IllegalStateException(githubMessage(code, body))
        val root = json.parseToJsonElement(body).jsonObject
        return root["content"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
            ?: throw IllegalStateException("GitHub не вернул номер версии")
    }

    private fun fetchRef(ref: String, missingIsNull: Boolean): RemoteFile? {
        val url = "https://api.github.com/repos/$owner/$repo/contents/${encodedPath()}?ref=${encode(ref)}"
        val (code, body) = request("GET", url, null)
        if (code == 404 && missingIsNull) return null
        if (code !in 200..299) throw IllegalStateException(githubMessage(code, body))
        val root = json.parseToJsonElement(body).jsonObject
        val encoded = root["content"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("GitHub не отдал содержимое файла. Файл слишком большой или это не файл.")
        val sha = root["sha"]?.jsonPrimitive?.content.orEmpty()
        val text = String(Base64.getDecoder().decode(encoded.replace("\n", "").replace("\r", "")), Charsets.UTF_8)
        return RemoteFile(sha, text)
    }

    private fun encodedPath(): String =
        path.trim().trim('/').split('/').filter { it.isNotBlank() }.joinToString("/") { encode(it) }

    private fun request(method: String, url: String, body: String?): Pair<Int, String> {
        val parsed = URL(url)
        require(parsed.protocol.equals("https", ignoreCase = true) && parsed.host.equals("api.github.com", true)) {
            "Запрос к Git разрешён только на api.github.com"
        }
        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 40_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "HDLabs-TreeX-Android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to text
    }

    private fun githubMessage(code: Int, body: String): String {
        val api = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        return when (code) {
            401 -> "GitHub не принял токен. Нужен токен с правом contents."
            403 -> api ?: "GitHub запретил доступ. Репозиторий должен быть доступен этому токену."
            404 -> "Репозиторий или файл не найден. Проверьте владельца, имя и доступ токена."
            409 -> "В Git уже есть другая версия. Сначала нажмите «Обновить информацию»."
            else -> api ?: "GitHub ответил HTTP $code"
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

    companion object {
        private val SLUG = Regex("[A-Za-z0-9_.-]{1,80}")
    }
}
