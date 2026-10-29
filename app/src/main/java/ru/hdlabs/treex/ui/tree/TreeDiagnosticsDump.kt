package ru.hdlabs.treex.ui.tree

import android.content.Context
import ru.hdlabs.treex.BuildConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Debug-only tree UI snapshot for sharing without adb (see [TreeViewModel.dumpDiagnostics]). */
object TreeDiagnosticsDump {

    fun dumpDiagnostics(context: Context, state: TreeUiState): File {
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(dir, "tree-state-$stamp.txt")
        val line = formatLine(state)
        file.writeText(
            buildString {
                appendLine("TreeX tree UI diagnostics")
                appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) build=${BuildConfig.BUILD_TYPE}")
                appendLine(line)
            },
        )
        return file
    }

    fun formatLine(state: TreeUiState): String {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return when (state) {
            TreeUiState.Loading -> "$ts | uiState=Loading | persons=- | families=- | nodes=- | edges=- | " +
                "fullNameNonBlank=-/- | transform=- | bounds=- | errors=[]"
            is TreeUiState.Error -> "$ts | uiState=Error | persons=- | families=- | nodes=- | edges=- | " +
                "fullNameNonBlank=-/- | transform=- | bounds=- | errors=[${state.message}]"
            is TreeUiState.Ready -> {
                val g = state.graph
                val l = state.layout
                val total = l.nodes.size
                val named = l.nodes.count { n ->
                    g.person(n.personId)?.fullName?.isNotBlank() == true
                }
                val t = state.transform
                val b = l.bounds
                val errs = listOfNotNull(state.error?.takeIf { it.isNotBlank() })
                "$ts | uiState=Ready | persons=${g.persons.size} | families=${g.families.size} | " +
                    "nodes=$total | edges=${l.edges.size} | fullNameNonBlank=$named/$total | " +
                    "transform=(ox=${t.offsetX}, oy=${t.offsetY}, scale=${t.scale}) | " +
                    "bounds=(${b.minX},${b.minY}..${b.maxX},${b.maxY}) | errors=$errs"
            }
        }
    }
}
