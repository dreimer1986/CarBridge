package org.carbridge.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer

/** Index letters for a list sorted by label: '#' for anything that doesn't start with A–Z. */
object Letters {
    const val OTHER = '#'
    val ALL = listOf(OTHER) + ('A'..'Z')

    fun of(label: String): Char {
        val first = label.trimStart().firstOrNull() ?: return OTHER
        // "Écran" files under E
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
        return if (base in 'A'..'Z') base else OTHER
    }

    /**
     * Position in [labels] to jump to for [letter]: its first label, else the next letter that
     * has one, else the last label. Null for an empty list.
     */
    fun target(labels: List<String>, letter: Char): Int? {
        if (labels.isEmpty()) return null
        val firsts = labels.map(::of)
        for (candidate in ALL.drop(ALL.indexOf(letter))) {
            val i = firsts.indexOf(candidate)
            if (i >= 0) return i
        }
        return labels.lastIndex
    }
}

/**
 * A fast-scroll strip of letters along the right edge. Tap or drag to [onLetter]; letters with
 * no apps are dimmed, and [current] is the letter at the top of the list.
 */
@Composable
fun AlphabetIndex(present: Set<Char>, current: Char?, onLetter: (Char) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<Char?>(null) }
    var height by remember { mutableIntStateOf(1) }

    fun pick(y: Float) {
        val letter = Letters.ALL[(y / height * Letters.ALL.size).toInt().coerceIn(0, Letters.ALL.lastIndex)]
        if (letter != dragging) {
            dragging = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(letter)
        }
    }

    Box(modifier.width(IndexWidth)) {
        Column(
            Modifier.fillMaxHeight().width(IndexWidth)
                .semantics { contentDescription = "Jump to letter" }
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { pick(it.y); tryAwaitRelease(); dragging = null })
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                    ) { change, _ -> pick(change.position.y) }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (letter in Letters.ALL) {
                val active = letter == (dragging ?: current)
                Text(
                    letter.toString(),
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        active -> MaterialTheme.colorScheme.primary
                        letter in present -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                )
            }
        }
        // The finger covers the strip, so show the letter beside it
        dragging?.let { letter ->
            Surface(
                // Wider than the strip: centred on it, then moved left of it
                Modifier.align(Alignment.Center).offset(x = -(BubbleSize / 2 + IndexWidth / 2 + 8.dp)).requiredSize(BubbleSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(letter.toString(), style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

/** Room list rows leave on their right for the strip. */
val IndexWidth = 24.dp
private val BubbleSize = 56.dp
