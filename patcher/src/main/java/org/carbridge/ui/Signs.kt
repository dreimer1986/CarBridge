package org.carbridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.carbridge.R

internal val SignShape = RoundedCornerShape(20.dp)

private val SignBorderShape = RoundedCornerShape(15.dp)

internal val Gutter = 16.dp

/** A guide-sign blank: green field, inset white border. */
@Composable
internal fun GuidePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val signs = LocalSigns.current
    Surface(modifier, shape = SignShape, color = signs.guide, contentColor = signs.onGuide, shadowElevation = 2.dp) {
        Column(
            Modifier.padding(5.dp).border(2.dp, signs.onGuide, SignBorderShape)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            content = content,
        )
    }
}

@Composable
internal fun SignHeader(onSettings: () -> Unit, onAbout: () -> Unit) {
    val signs = LocalSigns.current
    Column(
        Modifier.fillMaxWidth().background(signs.guide)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(start = Gutter, end = 4.dp, top = 12.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BridgeMark, contentDescription = null, tint = signs.onGuide, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = signs.onGuide,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings), tint = signs.onGuide)
            }
            IconButton(onClick = onAbout) {
                Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.about_cd), tint = signs.onGuide)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.titleMedium,
            color = signs.onGuideMuted,
            modifier = Modifier.padding(end = Gutter),
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = Gutter, end = Gutter, top = 32.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
    )
}

@Composable
internal fun Loading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun cautionButton() = LocalSigns.current.let {
    ButtonDefaults.buttonColors(containerColor = it.onCaution, contentColor = it.caution)
}

@Composable
internal fun AppIcon(icon: ImageBitmap, sizeDp: Int) {
    Image(icon, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
}

@Composable
internal fun KeyCard(title: String, body: String, action: String, onAction: () -> Unit, other: String, onOther: () -> Unit) {
    val signs = LocalSigns.current
    Surface(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 20.dp),
        shape = SignShape,
        color = signs.caution,
        contentColor = signs.onCaution,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = signs.onCautionMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onAction, colors = cautionButton()) { Text(action) }
                TextButton(onClick = onOther, colors = ButtonDefaults.textButtonColors(contentColor = signs.onCaution)) {
                    Text(other)
                }
            }
        }
    }
}
