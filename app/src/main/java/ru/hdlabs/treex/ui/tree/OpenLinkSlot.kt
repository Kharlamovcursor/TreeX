package ru.hdlabs.treex.ui.tree

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdPanelElevated
import ru.hdlabs.treex.ui.theme.HdTextSecondary

@Composable
fun OpenLinkSlot(
    slot: OpenLinkSlotState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, HdOrange.copy(alpha = 0.8f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("+", color = HdOrange)
        }
    }
}
