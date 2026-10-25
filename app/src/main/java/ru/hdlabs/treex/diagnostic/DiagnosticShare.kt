package ru.hdlabs.treex.diagnostic

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object DiagnosticShare {
    fun share(context: Context, reportFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TreeX — отчёт")
            putExtra(
                Intent.EXTRA_TEXT,
                "Отчёт об ошибке TreeX. Приложите файл или отправьте его разработчику в Telegram или MAX.",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(
            Intent.createChooser(intent, "Отправить отчёт").apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
