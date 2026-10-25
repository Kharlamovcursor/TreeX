package ru.hdlabs.treex.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ru.hdlabs.treex.ui.components.HdScaffold

@Composable
fun TreeHelpScreen(nav: NavHostController) {
    HdScaffold(title = "Справка по древу", onBack = { nav.popBackStack() }) {
        HelpSection(
            "Древо на экране",
            listOf(
                "Двумя пальцами увеличивайте и уменьшайте, одним — сдвигайте. Кнопка «Всё древо» в углу возвращает обзор.",
                "Оранжевая линия — супруги, синяя — родители и дети.",
            ),
        )
        HelpSection(
            "Карточки",
            listOf(
                "Один тап — выбор и приближение. Два тапа — полная карточка человека.",
                "Контур переливается по краю: розовый у женщин, синий у мужчин.",
                "Внизу появляется короткая полоска с именем; нажмите её, чтобы развернуть действия. Жест «назад» сворачивает панель или снимает выбор.",
            ),
        )
        HelpSection(
            "Удаление и пустое место",
            listOf(
                "«Удалить» убирает данные человека, но место на древе и связи остаются — появляется «Пустое место».",
                "Там можно создать новую карточку или изменить связи. Чтобы убрать место совсем, удалите пустое место ещё раз.",
                "Свободные линии: оранжевая точка на линии или «Свободные связи» в панели действий.",
            ),
        )
        HelpSection(
            "Связи",
            listOf(
                "В «Изменить» блок «К кому относится»: отец, мать, супруг — выбрать или убрать.",
            ),
        )
        Spacer(Modifier.height(24.dp))
    }
}
