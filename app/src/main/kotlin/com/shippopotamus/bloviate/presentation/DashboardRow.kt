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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * One dashboard row (ENC-75): a leading visual (an icon tile, or a
 * [leading] slot — the Style list's radio buttons), a label, at most
 * one sub-line, and a trailing slot for the control that matches the
 * job (chip, switch, button). A row with an [onClick] is the control —
 * the whole row is the settings deep-link, marked by the chevron —
 * instead of a sentence plus a button that repeats it. [below] renders
 * extra content inside the row's card under the row itself (the
 * selected style's sample). A disabled row dims and stops clicking.
 *
 * Spacing comes only from [BloviateSpacing] (Compose Gospel, ENC-86):
 * s16 horizontal / s12 vertical padding, s12 leading gap. A clickable
 * row is a custom interactive, so it carries
 * [minimumInteractiveComponentSize] — the 48dp touch-target law applies
 * even when the row's content is shorter than 48dp.
 */
@Composable
fun DashboardRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: @Composable (() -> Unit)? = null,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    clickRole: Role = Role.Button,
    enabled: Boolean = true,
    contentDescription: String? = null,
    showChevron: Boolean = onClick != null,
    dividerAbove: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    below: @Composable (() -> Unit)? = null,
) {
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
    }
    val subColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (dividerAbove) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.minimumInteractiveComponentSize()
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, role = clickRole, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = BloviateSpacing.s16, vertical = BloviateSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(modifier = Modifier.padding(end = BloviateSpacing.s12)) {
                    leading()
                }
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .padding(end = BloviateSpacing.s12)
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
                    color = labelColor,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = subColor,
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
                        .padding(start = BloviateSpacing.s4)
                        .size(18.dp),
                )
            }
        }
        if (below != null) {
            below()
        }
    }
}

private const val DISABLED_CONTENT_ALPHA = 0.38f

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Row - Dark", showBackground = true)
@Composable
private fun DashboardRowPreview() {
    BloviateTheme {
        DashboardCard(modifier = Modifier.padding(BloviateSpacing.s16)) {
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
