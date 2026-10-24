package ru.hdlabs.treex.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.hdlabs.treex.data.PhotoStore
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.initials
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdPanel
import ru.hdlabs.treex.ui.theme.HdTextPrimary

@Composable
fun PersonAvatar(person: PersonEntity, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, person.photoFile) {
        value = withContext(Dispatchers.IO) {
            val relative = person.photoFile
            if (relative.isBlank()) return@withContext null
            val file = PhotoStore.file(context, relative)
            if (!file.isFile) null else BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }
    if (image != null) {
        Image(
            bitmap = image!!,
            contentDescription = person.initials(),
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val color = when (person.gender) {
            "m" -> HdBlue.copy(alpha = 0.85f)
            "f" -> HdOrange.copy(alpha = 0.9f)
            else -> HdPanel
        }
        Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(person.initials(), color = HdTextPrimary, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.6f).sp)
        }
    }
}
