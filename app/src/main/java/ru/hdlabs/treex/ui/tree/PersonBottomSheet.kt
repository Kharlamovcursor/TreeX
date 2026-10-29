package ru.hdlabs.treex.ui.tree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.Person
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdSecondaryButton
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonBottomSheet(
    sheet: BottomSheetState,
    graph: GenealogyGraph,
    onDismiss: () -> Unit,
    onEditPerson: (String) -> Unit,
    onAddRelative: (anchorPersonId: String, kind: RelativeKind) -> Unit,
    onNavigateAdd: (anchorPersonId: String, role: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (sheet) {
            is BottomSheetState.PersonDetails -> {
                val person = graph.person(sheet.personId) ?: return@ModalBottomSheet
                PersonSheetContent(
                    person = person,
                    graph = graph,
                    onEdit = { onEditPerson(person.id) },
                    onAddParent = {
                        onNavigateAdd(person.id, if (person.gender.name == "FEMALE") "mother" else "father")
                        onDismiss()
                    },
                    onAddSpouse = {
                        onNavigateAdd(person.id, "spouse")
                        onDismiss()
                    },
                    onAddChild = {
                        onNavigateAdd(person.id, "child")
                        onDismiss()
                    },
                )
            }
            is BottomSheetState.AddRelative -> {
                Column(Modifier.padding(16.dp)) {
                    Text("Добавить родственника", color = HdTextPrimary)
                    Spacer(Modifier.height(8.dp))
                    val role = when (sheet.kind) {
                        RelativeKind.PARENT -> "father"
                        RelativeKind.SPOUSE -> "spouse"
                        RelativeKind.CHILD -> "child"
                    }
                    HdPrimaryButton("Создать через редактор") {
                        onNavigateAdd(sheet.anchorPersonId, role)
                        onDismiss()
                    }
                    HdSecondaryButton("Отмена", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun PersonSheetContent(
    person: Person,
    graph: GenealogyGraph,
    onEdit: () -> Unit,
    onAddParent: () -> Unit,
    onAddSpouse: () -> Unit,
    onAddChild: () -> Unit,
) {
    Column(Modifier.padding(16.dp).fillMaxWidth()) {
        Text(person.fullName, color = HdTextPrimary)
        val dates = listOfNotNull(person.birthDate, person.deathDate).joinToString(" – ")
        if (dates.isNotBlank()) Text(dates, color = HdTextSecondary)
        Spacer(Modifier.height(8.dp))
        Text("Родители: ${graph.parentsOf(person.id).joinToString { it.fullName }}", color = HdTextSecondary)
        Text("Супруги: ${graph.spousesOf(person.id).joinToString { it.fullName }}", color = HdTextSecondary)
        Text("Дети: ${graph.childrenOf(person.id).joinToString { it.fullName }}", color = HdTextSecondary)
        Spacer(Modifier.height(12.dp))
        HdSecondaryButton("Добавить родителя", onClick = onAddParent)
        HdSecondaryButton("Добавить супруга", onClick = onAddSpouse)
        HdSecondaryButton("Добавить ребёнка", onClick = onAddChild)
        Spacer(Modifier.height(8.dp))
        HdPrimaryButton("Редактировать", onClick = onEdit)
    }
}
