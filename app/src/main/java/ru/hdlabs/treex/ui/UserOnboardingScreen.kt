package ru.hdlabs.treex.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.hdlabs.treex.TreeXApplication
import ru.hdlabs.treex.domain.capitalizePersonName
import ru.hdlabs.treex.ui.components.HdCard
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdScaffold
import ru.hdlabs.treex.ui.components.HdTextField
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary

@Composable
fun UserOnboardingScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val scope = rememberCoroutineScope()
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val canContinue = lastName.trim().isNotBlank() && firstName.trim().isNotBlank() && !saving

    HdScaffold(title = "TreeX") {
        Text(
            "Кто вы?",
            color = HdTextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Укажите фамилию, имя и отчество. Они будут видны в истории обновления древа.",
            color = HdTextSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        HdCard {
            HdTextField(lastName, { lastName = it }, "Фамилия")
            Spacer(Modifier.height(10.dp))
            HdTextField(firstName, { firstName = it }, "Имя")
            Spacer(Modifier.height(10.dp))
            HdTextField(middleName, { middleName = it }, "Отчество")
        }
        Spacer(Modifier.height(16.dp))
        HdPrimaryButton(
            text = if (saving) "Сохранение…" else "Продолжить",
            enabled = canContinue,
        ) {
            saving = true
            scope.launch {
                app.container.settingsRepository.update {
                    it.copy(
                        userLastName = capitalizePersonName(lastName),
                        userFirstName = capitalizePersonName(firstName),
                        userMiddleName = capitalizePersonName(middleName),
                    )
                }
                saving = false
            }
        }
    }
}
