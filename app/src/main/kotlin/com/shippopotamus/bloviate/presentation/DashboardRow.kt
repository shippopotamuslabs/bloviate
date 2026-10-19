package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * One dashboard row (ENC-75): an optional icon tile, a label, at most
 * one sub-line, and a trailing slot for the control that matches the
 * job (chip, switch, button). A row with an [onClick] is the control —
 * the whole row is the settings deep-link, marked by the chevron —
 * instead of a sentence plus a button that repeats it.
 */
@Composable
fun DashboardRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    dividerAbove: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (dividerAbove) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .padding(end = 11.dp)
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Row - Dark", showBackground = true)
@Composable
private fun DashboardRowPreview() {
    BloviateTheme {
        DashboardCard(modifier = Modifier.padding(16.dp)) {
            DashboardRow(
                label = "Text-field detection",
                icon = Icons.Filled.Visibility,
                sub = "Spots fields, never reads them",
                onClick = {},
                trailing = { StatusChip(text = "On", tone = StatusTone.POSITIVE) },
            )
            DashboardRow(
                label = "Bubble look",
                sub = "Color, size, shape",
                dividerAbove = true,
                trailing = { StatusChip(text = "Soon", tone = StatusTone.NEUTRAL) },
            )
        }
    }
}
