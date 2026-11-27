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

package com.lambda.gui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import com.lambda.config.Config
import com.lambda.config.settings.complex.Bind
import com.lambda.gui.GuiConfig
import com.lambda.gui.LambdaGui
import com.lambda.gui.Reveal
import com.lambda.gui.Snap
import com.lambda.gui.Snap.snapLines
import com.lambda.gui.clickgui.MIN_PANEL_WIDTH
import com.lambda.gui.clickgui.widthOfContent
import com.lambda.gui.component.ContextMenu
import com.lambda.gui.component.MenuDivider
import com.lambda.gui.component.MenuHelp
import com.lambda.gui.component.MenuItem
import com.lambda.gui.component.MenuLabel
import com.lambda.gui.component.Text
import com.lambda.gui.component.onMousePress
import com.lambda.gui.component.rememberLast
import com.lambda.gui.component.window
import com.lambda.gui.core.observe
import com.lambda.gui.settings.ConfigSettings
import com.lambda.gui.settings.ModuleSettings
import com.lambda.gui.settings.PanelPage
import com.lambda.gui.settings.ROW_INSET
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Type
import com.lambda.module.HudModule
import com.lambda.module.Module

private val PANEL_SCALE = spring<Float>(stiffness = Spring.StiffnessMedium)
private val CONFIG_WIDTH = 150.dp
private val ANCHOR_MARK = 1.5.dp
private const val ANCHOR_MARK_ALPHA = 0.9f

internal class HudEditorState(snapping: State<Boolean>) {
	val snapping by snapping

	val bounds = mutableStateMapOf<HudModule, Rect>()

	var dragged by mutableStateOf<HudModule?>(null)
	var draggedTo by mutableStateOf(Offset.Zero)

	var menu by mutableStateOf<Pair<Offset, HudModule?>?>(null)
		private set

	var menusOpened by mutableStateOf(0)
		private set

	fun openMenu(position: Offset, hud: HudModule?) {
		menu = position to hud
		menusOpened++
	}

	fun closeMenu() {
		menu = null
	}

	var configuring by mutableStateOf<Config?>(null)
		private set

	var panelPosition by mutableStateOf(Offset.Zero)

	var panelBounds = Rect.Zero

	fun configure(config: Config?, position: Offset? = null) {
		configuring = config
		if (config != null) panelPosition = position
			?: Offset(
				(screen.width / 2) - (120 * density.density),
				screen.height * 0.15f
			)
	}

	var screen = Size.Zero
	var density = Density(1f)

	val drop: Snap.Result? by
		derivedStateOf {
			val hud = dragged ?: return@derivedStateOf null
			val element = Rect(draggedTo, bounds[hud]?.size ?: return@derivedStateOf null)
			if (this.snapping) {
				Snap.snap(
					element,
					bounds.filterKeys { it != hud }.values,
					Rect(Offset.Zero, screen),
					density
				)
			}
			else Snap.free(element, Rect(Offset.Zero, screen))
		}

	fun shownPosition(hud: HudModule) = drop?.position.takeIf { dragged == hud }
}

@Composable
internal fun HudEditor() {
	val snapping = observe { Snap.isActive }
	val editor = remember { HudEditorState(snapping) }
	editor.screen = LocalWindowInfo.current.containerSize.toSize()
	editor.density = LocalDensity.current

	val revealedElement = Reveal.module as? HudModule
	val revealedSetting = Reveal.setting
	LaunchedEffect(revealedElement, revealedSetting) {
		if (revealedElement != null && revealedSetting != null) editor.configure(revealedElement)
	}

	Box(
		Modifier
			.fillMaxSize()
			.onMousePress(PointerButton.Secondary) { editor.openMenu(it, null) }
			.pointerInput(Unit) {
				awaitPointerEventScope {
					while (true) {
						val event = awaitPointerEvent(PointerEventPass.Initial)
						if (event.type != PointerEventType.Press || editor.configuring == null) continue
						if (event.changes.first().position !in editor.panelBounds) editor.configure(null)
					}
				}
			}
			.snapLines { editor.drop }
	) {
		HudLayer(editor)
		rememberLast(editor.configuring)?.let { config ->
			key(config) { ElementSettings(config, editor.configuring === config, editor) }
		}
	}

	rememberLast(editor.menu)?.let { (position, hud) ->
		key(editor.menusOpened) {
			ContextMenu(
				editor.menu != null,
				position.round(),
				onDismiss = editor::closeMenu
			) {
				if (hud != null) ElementMenu(hud, editor)
				else ScreenMenu(position, editor)
			}
		}
	}
}

@Composable
private fun ElementMenu(hud: HudModule, editor: HudEditorState) {
	MenuLabel(hud.name)
	MenuItem("Settings...") { editor.configure(hud, editor.menu?.first) }
	MenuItem("Remove") { hud.disable() }
	MenuDivider()
	EditorHelp()
}

@Composable
private fun EditorHelp() {
	val snapping by observe {
		when {
			!GuiConfig.snapEnabled -> ""
			GuiConfig.snapKey == Bind.EMPTY -> " Elements snap into line."
			else -> " Hold ${GuiConfig.snapKey.name} to snap it into line."
		}
	}
	MenuHelp("Drag an element to move it.$snapping Right click an element to remove or configure it, or the empty screen to add one.")
}

@Composable
private fun ScreenMenu(position: Offset, editor: HudEditorState) {
	val screen = LocalWindowInfo.current.containerSize
	val hidden by observe { Hud.modules.filter { !it.isEnabled }.sortedBy { it.name.lowercase() } }
	if (hidden.isEmpty()) MenuLabel("Every HUD element is shown")
	else {
		MenuLabel("Add HUD Element")
		hidden.forEach { hud ->
			MenuItem(hud.name) {
				hud.anchorX = (position.x / screen.width).coerceIn(0f, 1f)
				hud.anchorY = (position.y / screen.height).coerceIn(0f, 1f)
				hud.enable()
			}
		}
	}
	MenuDivider()
	MenuItem("HUD Settings...") { editor.configure(HudConfig, position) }
	EditorHelp()
	MenuItem("Back") { LambdaGui.back() }
}

@Composable
private fun ElementSettings(config: Config, open: Boolean, editor: HudEditorState) {
	val colors = LambdaTheme.colors
	val density = LocalDensity.current
	val maxHeight = with(density) { editor.screen.height.toDp() } * 0.6f
	var size by remember { mutableStateOf(IntSize.Zero) }
	val room =
		Offset(
			(editor.screen.width - size.width).coerceAtLeast(0f),
			(editor.screen.height - size.height).coerceAtLeast(0f)
		)
	val position =
		Offset(
			editor.panelPosition.x.coerceIn(0f, room.x),
			editor.panelPosition.y.coerceIn(0f, room.y)
		)
	val shownAt by rememberUpdatedState(position)
	if (open) editor.panelBounds = Rect(position, size.toSize())
	DisposableEffect(Unit) {
		LambdaGui.onEscape = { (editor.configuring != null).also { editor.configure(null) } }
		onDispose { LambdaGui.onEscape = null }
	}

	val visibility = remember { MutableTransitionState(false) }
	visibility.targetState = open
	AnimatedVisibility(
		visibleState = visibility,
		modifier = Modifier.offset { position.round() },
		enter = scaleIn(PANEL_SCALE),
		exit = scaleOut(PANEL_SCALE),
	) {
		Column(
			Modifier
				.onSizeChanged { size = it }
				.window(colors.panel, colors.border, RoundedCornerShape(Look.panelRounding), floating = true)
				.pointerInput(Unit) {
					awaitEachGesture {
						val down = awaitFirstDown()
						down.consume()
						editor.panelPosition = shownAt
						drag(down.id) { change ->
							editor.panelPosition += change.positionChange()
							change.consume()
						}
					}
				}
				.padding(Look.panelPadding)
		) {
			if (config is Module) {
				var page by remember { mutableStateOf(PanelPage.Settings) }
				Column(
					Modifier
						.widthIn(MIN_PANEL_WIDTH, Look.settingsWidth)
						.heightIn(max = maxHeight)
						.widthOfContent()
				) {
					Text(
						config.name,
						Modifier.padding(start = ROW_INSET, bottom = Look.settingSpacing),
						style = Type.title,
						maxLines = 1
					)
					ModuleSettings(config, page, { page = it }, rememberScrollState())
				}
			}
			else ConfigSettings(config, maxHeight, onDone = { editor.configure(null) }, Modifier.width(CONFIG_WIDTH))
		}
	}
}

internal fun Modifier.hudEditable(hud: HudModule, editor: HudEditorState): Modifier =
	composed {
		val anchor by observe { hud.anchor }
		Modifier
			.onGloballyPositioned { editor.bounds[hud] = Rect(it.positionInRoot(), it.size.toSize()) }
			.onMousePress(PointerButton.Secondary) { position ->
				editor.bounds[hud]?.let { editor.openMenu(it.topLeft + position, hud) }
			}
			.pointerInput(hud) {
				awaitEachGesture {
					val down = awaitFirstDown()
					val origin = editor.bounds[hud] ?: return@awaitEachGesture
					down.consume()

					editor.draggedTo = origin.topLeft
					editor.dragged = hud
					try {
						drag(down.id) { change ->
							editor.draggedTo += change.positionChange()
							change.consume()
						}
					} finally {
						hud.place((editor.drop?.position ?: origin.topLeft).round())
						editor.dragged = null
					}
				}
			}
			.drawWithContent {
				drawContent()
				val width = Look.outlineWidth.toPx()
				val alpha =
					if (editor.dragged == hud || Reveal.module === hud) 0.9f
					else 0.35f
				drawRect(
					color = Color.White.copy(alpha = alpha),
					topLeft = Offset(width / 2, width / 2),
					size = Size(size.width - width, size.height - width),
					style = Stroke(width)
				)
				val mark = ANCHOR_MARK.toPx()
				drawRect(
					Color.White.copy(alpha = ANCHOR_MARK_ALPHA),
					Offset(
						((size.width - mark) * anchor.x).coerceAtLeast(0f),
						((size.height - mark) * anchor.y).coerceAtLeast(0f)
					),
					Size(mark, mark)
				)
			}
	}
