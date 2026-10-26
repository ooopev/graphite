package com.graphite.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.graphite.app.ui.theme.Black
import com.graphite.app.ui.theme.Bone
import com.graphite.app.ui.theme.BorderStrong
import com.graphite.app.ui.theme.OutlineDark
import com.graphite.app.ui.theme.Smoke
import com.graphite.app.ui.theme.Surface02
import com.graphite.app.ui.theme.TextSecondary

/** omarchy-style section card: square corners, 1px hairline, no elevation. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, // 0dp — squared
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, OutlineDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

/** Square toggle — the omarchy answer to round Material switches. */
@Composable
fun SquareSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.small,
        color = if (checked) Bone else Surface02,
        border =
            BorderStroke(
                1.dp,
                if (checked) Bone else BorderStrong,
            ),
        modifier = modifier.size(width = 52.dp, height = 28.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(3.dp),
            contentAlignment =
                if (checked) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                },
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(if (checked) Black else Smoke),
            )
        }
    }
}

/** Settings row with trailing switch. */
@Composable
fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Bone)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SquareSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Clickable settings row with an optional leading monochrome icon. */
@Composable
fun ClickRow(
    title: String,
    description: String? = null,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.padding(end = 16.dp).size(22.dp),
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Bone)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

/** Flat monochrome square text field. */
@Composable
fun GraphiteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        singleLine = singleLine,
        minLines = minLines,
        trailingIcon = trailing,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small, // 0dp — squared
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Bone,
                unfocusedTextColor = Bone,
                focusedContainerColor = Surface02,
                unfocusedContainerColor = Surface02,
                focusedBorderColor = Bone,
                unfocusedBorderColor = OutlineDark,
                focusedLabelColor = Bone,
                unfocusedLabelColor = Smoke,
                cursorColor = Bone,
            ),
    )
}

/** Section label — JetBrains Mono, uppercase, muted: omarchy's eyebrow. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Smoke,
        letterSpacing = 1.5.sp,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}
