package ru.hdlabs.treex.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ru.hdlabs.treex.ui.background.SpaceBackground
import ru.hdlabs.treex.ui.motion.LocalHdMotion
import ru.hdlabs.treex.ui.theme.HdBlack
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdPanel
import ru.hdlabs.treex.ui.theme.HdPanelElevated
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary

@Composable
fun HdCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, HdBlue.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun HdPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
    onClick: () -> Unit,
) {
    val motion = LocalHdMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) motion.buttonPressScale else 1f,
        animationSpec = tween(motion.buttonPressDurationMs),
        label = "primaryScale",
    )
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed && enabled) HdOrange.copy(alpha = 0.22f) else Color.Transparent,
            disabledContainerColor = HdPanel,
        ),
        border = if (pressed && enabled) {
            BorderStroke(2.5.dp, HdOrange)
        } else {
            BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(HdBlue, HdOrange)))
        },
    ) {
        HdButtonContent(loading, loadingText ?: text, text)
    }
}

@Composable
fun HdSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
    onClick: () -> Unit,
) {
    val motion = LocalHdMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) motion.buttonPressScale else 1f,
        animationSpec = tween(motion.buttonPressDurationMs),
        label = "secondaryScale",
    )
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed && enabled) HdOrange.copy(alpha = 0.35f) else HdPanel,
        ),
        border = BorderStroke(
            if (pressed && enabled) 2.5.dp else 1.dp,
            if (pressed && enabled) HdOrange else HdBlue.copy(alpha = 0.4f),
        ),
    ) {
        HdButtonContent(loading, loadingText ?: text, text)
    }
}

@Composable
private fun HdButtonContent(loading: Boolean, label: String, idleLabel: String) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = HdOrange,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(if (loading) label else idleLabel, color = HdTextPrimary)
    }
}

@Composable
fun HdActionProgress(message: String?, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = HdOrange,
            trackColor = HdBlue.copy(alpha = 0.25f),
        )
        Text(message, color = HdOrange, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(4.dp))
    }
}

enum class HdSavePhase { Idle, Saving, Saved }

@Composable
fun HdSaveButton(
    idleText: String,
    phase: HdSavePhase,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when (phase) {
            HdSavePhase.Saved -> 1.05f
            HdSavePhase.Saving -> 0.98f
            HdSavePhase.Idle -> if (pressed) 0.93f else 1f
        },
        animationSpec = if (phase == HdSavePhase.Saved) spring(dampingRatio = 0.5f, stiffness = 420f) else tween(90),
        label = "saveScale",
    )
    val label = when (phase) {
        HdSavePhase.Saving -> "Сохранение…"
        HdSavePhase.Saved -> "Сохранено"
        HdSavePhase.Idle -> idleText
    }
    Button(
        onClick = onClick,
        enabled = phase != HdSavePhase.Saving,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when (phase) {
                HdSavePhase.Saved -> HdBlue.copy(alpha = 0.45f)
                HdSavePhase.Saving -> HdPanel
                HdSavePhase.Idle -> if (pressed) HdOrange.copy(alpha = 0.55f) else HdPanel
            },
            disabledContainerColor = HdPanel,
        ),
        border = BorderStroke(if (phase == HdSavePhase.Saved) 3.dp else 1.dp, HdBlue),
    ) {
        Text(label, color = HdTextPrimary)
    }
}

@Composable
fun HdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HdBlue,
            unfocusedBorderColor = HdTextSecondary.copy(alpha = 0.4f),
            focusedTextColor = HdTextPrimary,
            unfocusedTextColor = HdTextPrimary,
            focusedLabelColor = HdBlue,
            unfocusedLabelColor = HdTextSecondary,
            cursorColor = HdOrange,
            focusedContainerColor = HdBlack.copy(alpha = 0.5f),
            unfocusedContainerColor = HdBlack.copy(alpha = 0.35f),
        ),
    )
}

@Composable
fun HdLabsBrandTitle(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hdBrandTitle")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(4200, easing = LinearEasing)),
        label = "travel",
    )
    val width = 520f
    val start = travel * width - width * 0.35f
    val brush = Brush.linearGradient(
        colors = listOf(
            HdBlue,
            Color(0xFF7DD3FC),
            HdOrange,
            Color(0xFFFFE0B2),
            HdBlue,
        ),
        start = Offset(start, 0f),
        end = Offset(start + width * 0.55f, 0f),
    )
    Text(
        text = "Древо by HDLabs",
        modifier = modifier.semantics { contentDescription = "Древо by HDLabs" },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            brush = brush,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HdScaffold(
    title: String,
    titleContent: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    scroll: Boolean = true,
    progressMessage: String? = null,
    minimalTopBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        SpaceBackground()
        Scaffold(
            containerColor = HdBlack.copy(alpha = 0.42f),
            floatingActionButton = floating,
            topBar = {
                TopAppBar(
                    title = {
                        if (minimalTopBar) {
                            Text("")
                        } else if (titleContent != null) {
                            titleContent()
                        } else {
                            Text(title, color = HdTextPrimary)
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = HdTextPrimary)
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HdBlack.copy(alpha = 0.7f)),
                )
            },
        ) { padding ->
            val modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .imePadding()
            Column(if (scroll) modifier.verticalScroll(rememberScrollState()) else modifier.fillMaxSize()) {
                HdActionProgress(progressMessage)
                content()
            }
        }
    }
}
