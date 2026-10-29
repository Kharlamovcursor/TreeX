package ru.hdlabs.treex.ui.tree

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import android.widget.Toast
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
                    Text("Dump diagnostics", color = HdOrange)
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
                            onTransform = viewModel::onTransform,
                        )
                        TextButton(
                            onClick = { viewModel.fitToScreen(viewW, viewH) },
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
