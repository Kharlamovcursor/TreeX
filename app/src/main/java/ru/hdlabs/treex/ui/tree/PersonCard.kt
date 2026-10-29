package ru.hdlabs.treex.ui.tree

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hdlabs.treex.domain.graph.Person
import ru.hdlabs.treex.domain.layout.LayoutConfig
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdPanelElevated
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary

@Composable
fun PersonCard(
    person: Person,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.95f)),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) HdBlue else HdBlue.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = HdTextSecondary)
            Text(
                person.fullName,
                color = HdTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val dates = listOfNotNull(person.birthDate, person.deathDate).joinToString(" – ")
            if (dates.isNotBlank()) {
                Text(dates, color = HdTextSecondary, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

fun layoutNodeSizeModifier(): Modifier = Modifier

val layoutNodeWidthDp get() = LayoutConfig.nodeWidth
val layoutNodeHeightDp get() = LayoutConfig.nodeHeight
