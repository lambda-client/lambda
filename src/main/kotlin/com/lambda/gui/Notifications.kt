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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lambda.gui.component.ContentColor
import com.lambda.gui.component.Overlay
import com.lambda.gui.component.Text
import com.lambda.gui.component.window
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Motion
import com.lambda.gui.theme.NotificationFlow
import com.lambda.gui.theme.NotificationLimit
import com.lambda.gui.theme.NotificationSide
import com.lambda.gui.theme.Type
import com.lambda.interaction.handler.handlers.NotificationHandler
import com.lambda.interaction.handler.handlers.NotificationHandler.Notification
import com.lambda.util.math.toCompose
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val MARGIN = 6.dp
private val GAP = 4.dp
private val STRIPE = 2.dp
private const val PREVIEW_COUNT = 3
private const val PREVIEW_ALPHA = 0.8f

@Composable
internal fun Notifications() {
	val notifications = NotificationHandler.notifications
	if (notifications.isEmpty()) return
	Scaled { Stack(notifications) }
}

@Composable
internal fun NotificationPreview() {
	val option = GuiConfig.notificationOption
	val limit by observe { option as? NotificationLimit ?: GuiConfig.notificationLimit }
	val max by observe { GuiConfig.maxNotifications }
	val leftSide = (option as? NotificationSide ?: Look.notificationSide) == NotificationSide.Left
	val rising = (option as? NotificationFlow ?: Look.notificationFlow) == NotificationFlow.Up
	val height = Look.notificationHeight
	val screen = LocalWindowInfo.current.containerSize
	var older by remember { mutableStateOf(0) }
	var newest by remember { mutableStateOf(0) }
	Scaled {
		val margin = with(LocalDensity.current) { MARGIN.roundToPx() }
		val gap = with(LocalDensity.current) { GAP.roundToPx() }
		val line = margin + ((1f - height) * (screen.height - margin * 2)).roundToInt()
		val room =
			if (rising) line.coerceAtLeast(margin + older) - margin
			else screen.height - margin - line.coerceAtMost(screen.height - margin - older)
		val count =
			when {
				limit == NotificationLimit.Count -> max
				older == 0 || newest == 0 -> PREVIEW_COUNT
				else -> (1 + (room - newest) / (older + gap)).coerceAtLeast(1)
			}
		val newestAt =
			if (rising) 0
			else count - 1
		val ghosts =
			remember(count, newestAt) {
				List(count) {
					if (it == newestAt) Notification("Newest notification", "New ones appear here", null)
					else Notification("Older notification", "", null)
				}
			}
		val oldest =
			if (count == 1) newest
			else older
		Overlay(
			position = { window, size ->
				IntOffset(
					if (leftSide) margin
					else window.width - margin - size.width,
					(
						if (rising) line.coerceAtLeast(margin + oldest) - size.height
						else line.coerceAtMost(window.height - margin - oldest)
					).coerceAtMost(window.height - margin - size.height).coerceAtLeast(margin)
				)
			}
		) {
			Column(
				Modifier.graphicsLayer { alpha = PREVIEW_ALPHA },
				verticalArrangement = Arrangement.spacedBy(GAP)
			) {
				ghosts.forEachIndexed { index, ghost ->
					Card(
						ghost,
						leftSide,
						Modifier.onSizeChanged {
							if (index == newestAt) newest = it.height
							else older = it.height
						}
					)
				}
			}
		}
	}
}

@Composable
private fun Scaled(content: @Composable () -> Unit) {
	val density = LocalDensity.current
	val scaled = Density(density.density * Look.notificationScale, density.fontScale)
	CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

@Composable
private fun Stack(notifications: List<Notification>) {
	val heights = remember { mutableStateMapOf<Notification, Int>() }
	val density = LocalDensity.current
	val screen = LocalWindowInfo.current.containerSize
	val margin = with(density) { MARGIN.roundToPx() }
	val gap = with(density) { GAP.roundToPx() }
	val live = notifications.filterNot { it.dismissed }
	val oldest = live.firstOrNull()?.let { heights[it] } ?: 0
	val line = margin + ((1f - Look.notificationHeight) * (screen.height - margin * 2)).roundToInt()
	val rising = Look.notificationFlow == NotificationFlow.Up
	val anchor =
		if (rising) line.coerceAtLeast(margin + oldest)
		else line.coerceAtMost(screen.height - margin - oldest)
	val stacked = (live.sumOf { (heights[it] ?: 0) + gap } - gap).coerceAtLeast(0)
	val base =
		if (rising) anchor.coerceAtLeast(margin + stacked).coerceAtMost(screen.height - margin)
		else anchor.coerceAtMost(screen.height - margin - stacked).coerceAtLeast(margin)

	val limit by observe { GuiConfig.notificationLimit }
	if (limit == NotificationLimit.Screen && live.all { it in heights }) {
		val room =
			if (rising) anchor - margin
			else screen.height - margin - anchor
		var taken = -gap
		val fitting =
			live.takeLastWhile {
				taken += (heights[it] ?: 0) + gap
				taken <= room
			}
		val crowded = live.dropLast(fitting.size.coerceAtLeast(1))
		SideEffect { crowded.forEach { it.dismissed = true } }
	}

	Box(Modifier.fillMaxSize()) {
		notifications.forEach { notification ->
			key(notification) {
				val height = heights[notification] ?: 0
				val ahead = live.takeWhile { it !== notification }.sumOf { (heights[it] ?: 0) + gap }
				Toast(
					notification = notification,
					top =
						if (rising) base - ahead - height
						else base + ahead,
					leaving =
						if (rising) height + gap
						else -height - gap,
					measured = notification in heights,
					onMeasured = { heights[notification] = it },
					onGone = {
						heights -= notification
						NotificationHandler.remove(notification)
					},
				)
			}
		}
	}
}

@Composable
private fun Toast(
	notification: Notification,
	top: Int,
	leaving: Int,
	measured: Boolean,
	onMeasured: (height: Int) -> Unit,
	onGone: () -> Unit,
) {
	val density = LocalDensity.current
	val screen = LocalWindowInfo.current.containerSize
	val margin = with(density) { MARGIN.toPx() }
	val width = with(density) { Look.notificationWidth.toPx() }
	val reach = width + margin * 4
	val leftSide = Look.notificationSide == NotificationSide.Left
	val entered = remember { Animatable(0f) }
	val placed = remember { Animatable(top.toFloat()) }
	val opacity = remember { Animatable(1f) }
	var settled by remember { mutableStateOf(false) }
	LaunchedEffect(Unit) { entered.animateTo(1f, Motion.NOTIFICATIONS) }
	LaunchedEffect(top, measured, notification.dismissed) {
		if (notification.dismissed) return@LaunchedEffect
		if (settled) placed.animateTo(top.toFloat(), Motion.NOTIFICATIONS)
		else placed.snapTo(top.toFloat())
		settled = measured
	}
	LaunchedEffect(notification.dismissed) {
		if (!notification.dismissed) return@LaunchedEffect
		val end = placed.value + leaving
		val leavesScreen = end >= screen.height || end + abs(leaving) <= 0
		if (!leavesScreen) launch { opacity.animateTo(0f, Motion.NOTIFICATIONS) }
		placed.animateTo(end, Motion.NOTIFICATIONS)
		onGone()
	}

	Card(
		notification,
		leftSide,
		Modifier
			.offset {
				val hidden = (1f - entered.value) * reach
				IntOffset(
					(
						if (leftSide) margin - hidden
						else screen.width - margin - width + hidden
					).roundToInt(),
					placed.value.roundToInt()
				)
			}
			.graphicsLayer { alpha = opacity.value }
			.onSizeChanged { onMeasured(it.height) }
	)
}

@Composable
private fun Card(notification: Notification, leftSide: Boolean, modifier: Modifier = Modifier) {
	val colors = LambdaTheme.colors
	val accent = notification.accent?.toCompose() ?: colors.primary
	Row(
		modifier
			.width(Look.notificationWidth)
			.height(IntrinsicSize.Min)
			.window(colors.panel, colors.border, RoundedCornerShape(Look.panelRounding), floating = true)
	) {
		if (!leftSide) Stripe(accent)
		ContentColor(colors.onSurface) {
			Column(Modifier.weight(1f).padding(Look.panelPadding, Look.panelPadding / 2)) {
				Text(notification.title, style = Type.label, maxLines = 1)
				if (notification.message.isNotEmpty()) {
					Text(notification.message, color = colors.onSurfaceVariant)
				}
			}
		}
		if (leftSide) Stripe(accent)
	}
}

@Composable
private fun Stripe(color: Color) =
	Box(
		Modifier
			.width(STRIPE)
			.fillMaxHeight()
			.background(color)
	)
