package com.autoflow.app.ui.builder

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Column whose items can be reordered by long-pressing and dragging the handle modifier passed
 * to [itemContent]. Items are positional: the list is updated live through [onMove] while dragging.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    itemContent: @Composable (index: Int, item: T, dragHandle: Modifier, dragging: Boolean) -> Unit,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.forEachIndexed { index, item ->
            val currentIndex by rememberUpdatedState(index)
            val isDragging = draggingIndex == index
            val handle = Modifier.pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggingIndex = currentIndex
                        offset = 0f
                    },
                    onDragEnd = {
                        draggingIndex = null
                        offset = 0f
                    },
                    onDragCancel = {
                        draggingIndex = null
                        offset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        offset += amount.y
                        if (offset > 0 && from < currentItems.lastIndex) {
                            val next = (heights[from + 1] ?: 0) + spacingPx
                            if (offset > next / 2) {
                                currentOnMove(from, from + 1)
                                draggingIndex = from + 1
                                offset -= next
                            }
                        } else if (offset < 0 && from > 0) {
                            val previous = (heights[from - 1] ?: 0) + spacingPx
                            if (-offset > previous / 2) {
                                currentOnMove(from, from - 1)
                                draggingIndex = from - 1
                                offset += previous
                            }
                        }
                    },
                )
            }
            Box(
                Modifier
                    .onSizeChanged { heights[index] = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) offset else 0f }
                    .then(if (isDragging) Modifier.shadow(8.dp) else Modifier),
            ) {
                itemContent(index, item, handle, isDragging)
            }
        }
    }
}
