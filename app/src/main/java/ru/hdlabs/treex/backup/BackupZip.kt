package ru.hdlabs.treex.backup

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupZip {
    fun write(context: Context, document: TreeDocument, target: File) {
        val doc = TreeCodec.normalize(document).copy(exportedAtMillis = System.currentTimeMillis())
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("tree.json"))
            zip.write(TreeCodec.encode(doc).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            doc.people.forEach { person ->
                val relative = person.photoFile
                if (!relative.startsWith("photos/") || relative.contains("..")) return@forEach
                val source = File(context.filesDir, relative)
                if (!source.isFile) return@forEach
                zip.putNextEntry(ZipEntry(relative))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun readDocument(bytes: ByteArray): TreeDocument {
        var document: TreeDocument? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/').removePrefix("/")
                if (name == "tree.json") {
                    document = TreeCodec.decode(zip.readBytes().toString(Charsets.UTF_8))
                }
                zip.closeEntry()
            }
        }
        return document ?: throw IllegalArgumentException("В архиве нет tree.json")
    }

    fun copyPhotos(context: Context, bytes: ByteArray) {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/').removePrefix("/")
                val fileName = name.substringAfter("photos/", "")
                if (name.startsWith("photos/") && !name.contains("..") &&
                    fileName.matches(Regex("[A-Za-z0-9._-]{1,80}"))
                ) {
                    val target = File(context.filesDir, "photos/$fileName")
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }
}
