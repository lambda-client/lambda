/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lambda.gui.component.ContentColor
import com.lambda.gui.component.Text
import com.lambda.gui.component.window
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Motion
import com.lambda.gui.theme.Type
import com.lambda.interaction.handler.handlers.NotificationHandler
import com.lambda.interaction.handler.handlers.NotificationHandler.Notification
import com.lambda.util.math.toCompose
import kotlin.math.roundToInt

private val MARGIN = 6.dp
private val GAP = 4.dp
private val STRIPE = 2.dp

@Composable
internal fun Notifications() {
    val notifications = NotificationHandler.notifications
    if (notifications.isEmpty()) return
    val heights = remember { mutableStateMapOf<Notification, Int>() }
    val density = LocalDensity.current
    val scaled = Density(density.density * Look.notificationScale, density.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        Stack(notifications, heights)
    }
}

@Composable
private fun Stack(notifications: List<Notification>, heights: MutableMap<Notification, Int>) {
    Box(Modifier.fillMaxSize()) {
        notifications.forEach { notification ->
            key(notification) {
                val stacked =
                    notifications
                        .takeWhile { it !== notification }
                        .filterNot { it.dismissed }
                        .sumOf { heights[it] ?: 0 }
                val beneath = notifications.takeWhile { it !== notification }.count { !it.dismissed }
                Toast(
                    notification = notification,
                    stacked = stacked,
                    beneath = beneath,
                    height = heights[notification] ?: 0,
                    onMeasured = { heights[notification] = it },
                    onGone = {
                        heights -= notification
                        NotificationHandler.remove(notification)
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Composable
private fun Toast(
    notification: Notification,
    stacked: Int,
    beneath: Int,
    height: Int,
    onMeasured: (height: Int) -> Unit,
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LambdaTheme.colors
    val density = LocalDensity.current
    val reach = with(density) { (Look.notificationWidth + MARGIN * 4).toPx() }
    val rise =
        if (notification.dismissed) -height - with(density) { (MARGIN * 4).toPx() }
        else with(density) { (MARGIN + GAP * beneath).toPx() } + stacked
    val entered = remember { Animatable(0f) }
    val risen = remember { Animatable(rise) }
    LaunchedEffect(Unit) { entered.animateTo(1f, Motion.NOTIFICATIONS) }
    LaunchedEffect(rise) {
        risen.animateTo(rise, Motion.NOTIFICATIONS)
        if (notification.dismissed) onGone()
    }

    val shape = RoundedCornerShape(Look.panelRounding)
    Row(
        modifier
            .offset {
                IntOffset(
                    ((1f - entered.value) * reach).roundToInt() - MARGIN.roundToPx(),
                    -risen.value.roundToInt()
                )
            }
            .onSizeChanged { onMeasured(it.height) }
            .width(Look.notificationWidth)
            .height(IntrinsicSize.Min)
            .window(colors.panel, colors.border, shape, floating = true)
    ) {
        Box(
            Modifier
                .width(STRIPE)
                .fillMaxHeight()
                .background(notification.accent?.toCompose() ?: colors.primary)
        )
        ContentColor(colors.onSurface) {
            Column(Modifier.padding(Look.panelPadding, Look.panelPadding / 2)) {
                Text(notification.title, style = Type.label, maxLines = 1)
                if (notification.message.isNotEmpty()) {
                    Text(notification.message, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
