package com.tonyscratch.tsdj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.ui.components.TsButton
import com.tonyscratch.tsdj.ui.components.TsIcon
import com.tonyscratch.tsdj.ui.components.TsIcons
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type
import java.util.Locale
import kotlin.math.max

fun hms(sec: Long): String = String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)

fun ms(sec: Double): String {
    val s = max(0.0, sec).toLong()
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

@Composable
fun BackButton(label: String = "Mixer", onClick: () -> Unit) {
    TsButton(Modifier.height(36.dp), neutral = true, description = "Back to $label", onClick = onClick) { fg ->
        TsIcon(TsIcons.Back, fg, 17.dp)
        Spacer(Modifier.width(4.dp))
        Text(label, style = Type.ui(12.sp, 800, fg))
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search tracks and artists") {
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(50)).background(TS.Graphite)
            .border(1.dp, TS.Line, RoundedCornerShape(50)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TsIcon(TsIcons.Search, TS.Muted, 16.dp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Type.ui(12.sp, 600, TS.Muted), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Type.ui(12.sp, 600),
                cursorBrush = SolidColor(TS.Orange),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(28.dp).pointerInput(Unit) { detectTapGestures { onChange("") } }, contentAlignment = Alignment.Center) {
                TsIcon(TsIcons.Close, TS.Muted, 14.dp, "Clear search")
            }
        }
    }
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(TS.Black)
            .border(1.dp, TS.Line2, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = Type.ui(13.sp, 600, TS.Muted))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = Type.ui(13.sp, 600), cursorBrush = SolidColor(TS.Orange),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        )
    }
}

@Composable
fun Chip(text: String, on: Boolean, color: Color, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(if (on) color else TS.Graphite)
            .border(1.dp, if (on) color else TS.Line2, RoundedCornerShape(50))
            .semantics { role = Role.Button }
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TsIcon(icon, if (on) TS.Black else color, 13.dp)
            Spacer(Modifier.width(5.dp))
        } else if (on) {
            TsIcon(TsIcons.Check, TS.Black, 12.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = Type.ui(11.sp, 800, if (on) TS.Black else TS.Text), maxLines = 1)
    }
}

@Composable
fun EmptyState(text: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = Type.ui(13.sp, 600, TS.Text2))
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            TsButton(Modifier.height(40.dp), color = TS.Orange, filled = true, onClick = onAction) { fg ->
                Text(action, style = Type.ui(13.sp, 800, fg))
            }
        }
    }
}
