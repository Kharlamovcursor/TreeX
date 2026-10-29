package ru.hdlabs.treex.ui.tree

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import ru.hdlabs.treex.BuildConfig
import ru.hdlabs.treex.TreeXApplication
import ru.hdlabs.treex.diagnostic.AppEventLog
import ru.hdlabs.treex.diagnostic.DiagnosticShare
import ru.hdlabs.treex.navigation.Routes
import ru.hdlabs.treex.ui.components.HdLabsBrandTitle
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdScaffold
import ru.hdlabs.treex.ui.components.HdSecondaryButton
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdTextSecondary

@Composable
fun TreeScreen(nav: NavHostController, highlight: String = "") {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val viewModel: TreeViewModel = viewModel(factory = TreeViewModelFactory(app.container))
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(highlight) {
        if (highlight.isNotBlank()) {
            viewModel.onPersonClick(highlight)
        }
    }

    HdScaffold(
        title = "Древо by HDLabs",
        titleContent = { HdLabsBrandTitle() },
        scroll = false,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = HdOrange)
            }
            if (BuildConfig.DEBUG) {
                TextButton(
                    onClick = {
                        val file = viewModel.dumpDiagnostics(context)
                        val path = file.absolutePath
                        Log.i("TreeDiagnostics", "dump written: $path")
                        Toast.makeText(context, "Диагностика: ${file.name}", Toast.LENGTH_LONG).show()
                        DiagnosticShare.share(context, file)
                    },
                ) {
                    Text("Dump diagnostics", color = HdOrange)
                }
            }
            TextButton(onClick = { viewModel.reload() }) {
                Text("Обновить", color = HdOrange)
            }
        },
    ) {
        when (val state = uiState) {
            TreeUiState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is TreeUiState.Error -> Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.message, color = HdTextSecondary)
                if (state.recoverable) {
                    HdPrimaryButton("Повторить") { viewModel.reload() }
                }
                HdSecondaryButton("Добавить человека") { nav.navigate(Routes.edit("new")) }
            }
            is TreeUiState.Ready -> {
                LaunchedEffect(state.layout) {
                    AppEventLog.info("tree-ui", "Canvas tree screen active, nodes=${state.layout.nodes.size}")
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val viewW = with(density) { maxWidth.toPx() }
                    val viewH = with(density) { maxHeight.toPx() }
                    val fitPaddingPx = with(density) { 40.dp.toPx() }
                    LaunchedEffect(viewW, viewH, fitPaddingPx) {
                        viewModel.clampTransformToViewport(viewW, viewH, fitPaddingPx)
                    }
                    Box(Modifier.fillMaxSize()) {
                        if (state.error != null) {
                            Text(
                                state.error,
                                color = HdOrange,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(8.dp),
                            )
                        }
                        TreeCanvas(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            onPersonClick = viewModel::onPersonClick,
                            onSlotClick = viewModel::onSlotClick,
                            onTransform = { scale, ox, oy, w, h ->
                                viewModel.onTransform(scale, ox, oy, w, h, fitPaddingPx)
                            },
                            paddingPx = fitPaddingPx,
                        )
                        TextButton(
                            onClick = { viewModel.fitToScreen(viewW, viewH, fitPaddingPx) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Text("Всё древо", color = HdOrange)
                        }
                        HdSecondaryButton(
                            "Добавить",
                            onClick = { nav.navigate(Routes.edit("new")) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        )
                    }
                }
                state.sheet?.let { sheet ->
                    PersonBottomSheet(
                        sheet = sheet,
                        graph = state.graph,
                        onDismiss = viewModel::dismissSheet,
                        onEditPerson = { nav.navigate(Routes.edit(it)) },
                        onAddRelative = viewModel::requestAddRelative,
                        onNavigateAdd = { anchor, role -> nav.navigate(Routes.edit("new", role, anchor)) },
                    )
                }
            }
        }
    }
}
