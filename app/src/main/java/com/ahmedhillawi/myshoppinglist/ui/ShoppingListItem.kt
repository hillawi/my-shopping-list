package com.ahmedhillawi.myshoppinglist.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val StrikethroughDurationMillis = 550

@Composable
fun ShoppingListItem(
    item: ShoppingItem,
    onCheckedChange: (Boolean) -> Unit,
    onImportantToggle: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Active items are filtered out of the list the instant isPurchased flips
    // to true, which would cut the strikethrough animation off before it's
    // visible. This keeps the row showing as checked locally for the
    // animation's duration before the real toggle removes it from view.
    var pendingChecked by remember { mutableStateOf(false) }
    val displayAsPurchased = item.isPurchased || pendingChecked

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Display quantity prominently if it's not the default "1"
                if (item.quantity != "1") {
                    val quantityColor by animateColorAsState(
                        targetValue = if (displayAsPurchased) Color.Gray else MaterialTheme.colorScheme.primary,
                        animationSpec = tween(StrikethroughDurationMillis),
                        label = "quantityColor"
                    )
                    Text(
                        text = "${item.quantity} ",
                        fontWeight = FontWeight.Bold,
                        color = quantityColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                StrikethroughText(
                    text = item.name,
                    struckThrough = displayAsPurchased,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        leadingContent = {
            Checkbox(
                checked = displayAsPurchased,
                onCheckedChange = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isChecked && !item.isPurchased) {
                        pendingChecked = true
                        scope.launch {
                            delay(StrikethroughDurationMillis.toLong())
                            onCheckedChange(true)
                        }
                    } else {
                        pendingChecked = false
                        onCheckedChange(isChecked)
                    }
                }
            )
        },
        trailingContent = {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onImportantToggle()
                }
            ) {
                Icon(
                    imageVector = if(item.isImportant) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Important",
                    tint = if(item.isImportant) Color(0xFFFF9800) else Color.Gray.copy(alpha = 0.5f)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// Draws the strikethrough as a line that grows across the text and fades its
// color to gray, instead of an instant TextDecoration.LineThrough toggle.
@Composable
private fun StrikethroughText(
    text: String,
    struckThrough: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val lineProgress by animateFloatAsState(
        targetValue = if (struckThrough) 1f else 0f,
        animationSpec = tween(StrikethroughDurationMillis, easing = FastOutSlowInEasing),
        label = "strikethroughProgress"
    )
    val normalColor = LocalContentColor.current
    val textColor by animateColorAsState(
        targetValue = if (struckThrough) Color.Gray else normalColor,
        animationSpec = tween(StrikethroughDurationMillis),
        label = "strikethroughColor"
    )

    Text(
        text = text,
        style = style,
        color = textColor,
        modifier = modifier.drawWithContent {
            drawContent()
            if (lineProgress > 0f) {
                val y = size.height / 2f
                drawLine(
                    color = textColor,
                    start = Offset(0f, y),
                    end = Offset(size.width * lineProgress, y),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    )
}
