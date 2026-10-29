package ru.hdlabs.treex.ui.tree

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.lifeLine
import ru.hdlabs.treex.ui.PersonAvatar
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdPanelElevated
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary

/**
 * Temporary main-tree UI (phase 0a): person list until [ru.hdlabs.treex.ui.tree] canvas lands in phase 3.
 */
@Composable
fun TreeListStub(
    people: List<PersonEntity>,
    selectedId: String,
    freshId: String,
    modifier: Modifier = Modifier,
    onPersonClick: (PersonEntity) -> Unit,
    onOpenDetail: (PersonEntity) -> Unit,
) {
    val living = people.filter { !it.vacant }.sortedBy { it.fullName().lowercase() }
    Column(modifier) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.9f)),
            border = BorderStroke(1.dp, HdBlue.copy(alpha = 0.35f)),
        ) {
            Text(
                "Древо на экране временно в виде списка — идёт новая раскладка. " +
                    "Выберите человека, откройте карточку или добавьте родственника через меню.",
                color = HdTextSecondary,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(living, key = { it.id }) { person ->
                val selected = person.id == selectedId
                val fresh = person.id == freshId
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPersonClick(person) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.94f)),
                    border = BorderStroke(
                        width = if (selected || fresh) 2.dp else 1.dp,
                        color = when {
                            fresh -> HdOrange
                            selected -> HdBlue
                            else -> HdBlue.copy(alpha = 0.28f)
                        },
                    ),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(person, 56.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(person.fullName(), color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                            val years = person.lifeLine()
                            if (years.isNotBlank()) {
                                Text(years, color = HdTextSecondary)
                            }
                        }
                        TextButton(onClick = { onOpenDetail(person) }) {
                            Text("Карточка", color = HdOrange)
                        }
                    }
                }
            }
        }
    }
}
