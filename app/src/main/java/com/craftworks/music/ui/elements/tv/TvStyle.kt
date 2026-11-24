package com.craftworks.music.ui.elements.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.craftworks.music.R

object TvDimens {
    val ScreenPadding = 48.dp
    val RowCardWidth = 164.dp
    val CardCorner = 16.dp
}

private val CardShape = RoundedCornerShape(TvDimens.CardCorner)

@Composable
fun tvCardShape() = CardDefaults.shape(shape = CardShape)

@Composable
fun tvCardBorder() = CardDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
        shape = CardShape
    )
)

@Composable
fun tvCardScale() = CardDefaults.scale(focusedScale = 1.06f)

@Composable
fun tvCardGlow() = CardDefaults.glow(
    focusedGlow = Glow(
        elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        elevation = 14.dp
    )
)

/** The night-sky backdrop every TV screen sits on. */
@Composable
fun Modifier.auroraBackground(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.background
    val first = scheme.primary
    val second = scheme.tertiary
    val third = scheme.secondary

    return drawBehind {
        drawRect(base)
        drawRect(
            Brush.radialGradient(
                colors = listOf(first.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.10f, 0f),
                radius = size.width * 0.55f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(second.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.08f),
                radius = size.width * 0.50f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(third.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.60f, size.height * 1.05f),
                radius = size.width * 0.60f
            )
        )
    }
}

@Composable
fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .auroraBackground(),
        content = content
    )
}

@Composable
fun TvScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun TvEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = { }
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TvDimens.ScreenPadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp)
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** Asks before something is removed. Cancel comes first so it holds the initial focus. */
@Composable
fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.Action_Cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(confirmLabel)
            }
        }
    )
}
