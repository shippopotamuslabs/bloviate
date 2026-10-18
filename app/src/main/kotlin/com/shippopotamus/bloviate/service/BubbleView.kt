package com.shippopotamus.bloviate.service

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shippopotamus.bloviate.R

private val BUBBLE_SIZE: Dp = 56.dp
private val BUBBLE_ICON_SIZE: Dp = 28.dp
private val BUBBLE_ELEVATION: Dp = 6.dp
private const val DISMISS_ZONE_SCALE: Float = 0.8f
private const val FULL_SCALE: Float = 1f

/**
 * The floating dictation bubble, Idle state (see `contracts/UI_DESIGN_SPEC.md`
 * §3). Purely visual: touch is intercepted by the overlay service's drag
 * controller before it reaches the composition, and window show/hide is
 * driven by field focus. While the bubble is dragged inside the dismiss zone
 * it shrinks toward the zone as the dismissal affordance.
 *
 * Recording/Transcribing/Done/Error visuals land with the pipeline in
 * Phase 2 (ENC-48).
 */
@Composable
fun BubbleContent(
    inDismissZone: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.bubble_content_description)
    val containerColor = if (inDismissZone) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (inDismissZone) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val scale = if (inDismissZone) DISMISS_ZONE_SCALE else FULL_SCALE
    Surface(
        modifier = modifier
            .size(BUBBLE_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = BUBBLE_ELEVATION,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                modifier = Modifier.size(BUBBLE_ICON_SIZE),
            )
        }
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Idle - Light", showBackground = true)
@Preview(
    name = "Idle - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BubbleContentIdlePreview() {
    PreviewTheme {
        BubbleContent(inDismissZone = false)
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Dismiss zone - Light", showBackground = true)
@Preview(
    name = "Dismiss zone - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BubbleContentDismissZonePreview() {
    PreviewTheme {
        BubbleContent(inDismissZone = true)
    }
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
