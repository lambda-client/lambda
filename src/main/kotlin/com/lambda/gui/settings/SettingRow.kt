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

package com.lambda.gui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lambda.Lambda.mc
import com.lambda.config.entries.Setting
import com.lambda.config.settings.CharSetting
import com.lambda.config.settings.FunctionSetting
import com.lambda.config.settings.NumericSetting
import com.lambda.config.settings.StringSetting
import com.lambda.config.settings.collections.CollectionSetting
import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.complex.BlockPosSetting
import com.lambda.config.settings.complex.BlockSetting
import com.lambda.config.settings.complex.ColorSetting
import com.lambda.config.settings.complex.KeybindSetting
import com.lambda.config.settings.complex.Vec3dSetting
import com.lambda.gui.GuiScreen
import com.lambda.gui.clickgui.Reveal
import com.lambda.gui.component.Button
import com.lambda.gui.component.Checkmark
import com.lambda.gui.component.Dropdown
import com.lambda.gui.component.LocalClickOnRelease
import com.lambda.gui.component.NumberInput
import com.lambda.gui.component.RowHeight
import com.lambda.gui.component.TextInput
import com.lambda.gui.component.Tooltip
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.scrubbable
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.displayValue
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The editor for one setting, whatever its type. The description is its tooltip. A right click
 * does nothing to a setting: in this interface it only ever opens and closes things.
 *
 * Every editor is one row tall, so a list of settings reads as a list, and each kind looks
 * unlike the others, so it is plain at a glance what a row is: a tick box, a bar that fills,
 * a chip with an arrow, a key cap, a swatch, a field to type in. Editors that need more room
 * than a row open over the interface instead of pushing the rows below apart.
 *
 * Nothing is drawn around or between them. The row under the pointer is lit, which shows
 * how far it reaches and which value belongs to which name.
 *
 * Settings without an editor here (a block, a map) can only be changed with the config command.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingRow(setting: Setting<*>) {
    val disabled by observe { setting.disabled() }

    // Lit and scrolled to while it is what the search points at, once the tab has turned to it
    // and the groups around it have unfolded
    val revealed = Reveal.setting === setting
    val lit by animateColorAsState(if (revealed) LambdaTheme.colors.onSurface.copy(alpha = 0.35f) else Color.Transparent, tween(250))
    val intoView = remember { BringIntoViewRequester() }
    LaunchedEffect(revealed) {
        if (revealed) {
            awaitFrames(SETTLE_MILLIS)
            intoView.bringIntoView()
        }
    }

    Tooltip(setting.description, Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .settingCell()
                .bringIntoViewRequester(intoView)
                // Over what the row holds: a slider fills its row and would hide a light behind it
                .drawWithContent {
                    drawContent()
                    drawRect(lit)
                }
                .enabled(!disabled)
        ) {
            // A setting is changed when the button is let go, so a slip of the hand can be taken back
            CompositionLocalProvider(LocalClickOnRelease provides true) {
                when (setting) {
                    is BooleanSetting -> Toggle(setting)
                    is NumericSetting<*> -> Slider(setting)
                    is EnumSetting<*> -> EnumDropdown(setting)
                    is StringSetting -> TextField(setting)
                    is CharSetting -> CharField(setting)
                    is ColorSetting -> ColorPicker(setting)
                    is KeybindSetting -> KeybindButton(setting)
                    is FunctionSetting<*, *> -> Button(setting.name, Modifier.fillMaxWidth().height(RowHeight)) { setting.value() }
                    is CollectionSetting<*> -> CollectionButton(setting)
                    is BlockSetting -> BlockPicker(setting)
                    is BlockPosSetting -> BlockPosFields(setting)
                    is Vec3dSetting -> Vec3dFields(setting)
                }
            }
        }
    }
}

/**
 * The room a setting takes in a list: the width of the panel, lit while the pointer is on it.
 * For what is listed among settings without being one, too.
 */
internal fun Modifier.settingCell(): Modifier = composed {
    fillMaxWidth()
        .padding(vertical = 0.5.dp)
        .clip(RoundedCornerShape(Radius.Small))
        .hoverHighlight(LambdaTheme.colors.hover)
}

/** How long a tab takes to turn and a group to unfold, before a setting in them is where it will stay */
private const val SETTLE_MILLIS = 250L

/** Dims the element and keeps the pointer from reaching it while not [enabled] */
private fun Modifier.enabled(enabled: Boolean): Modifier =
    if (enabled) this
    else alpha(0.5f).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/** The shape most settings share: the [name], and at the end of the row whatever edits the value */
@Composable
internal fun Labeled(name: String, modifier: Modifier = Modifier, editor: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = RowHeight).padding(start = 3.dp)
    ) {
        Text(name, Modifier.weight(1f), maxLines = 1)
        editor()
    }
}

@Composable
private fun Toggle(setting: BooleanSetting) {
    val checked by observe { setting.value }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .mouseClickable { setting.value = !setting.value }
            .padding(horizontal = 3.dp)
    ) {
        Checkmark(checked)
        Text(setting.name, maxLines = 1)
    }
}

/**
 * A bar filled up to the value. Dragging anywhere on it sets the value; clicking the number
 * swaps the bar for a text field to type an exact one, for ranges with more steps than pixels.
 */
@Composable
private fun <T> Slider(setting: NumericSetting<T>) where T : Number, T : Comparable<T> {
    val colors = LambdaTheme.colors
    val value by observe { setting.value }
    val label by observe { setting.toString() }
    var typing by remember { mutableStateOf(false) }

    if (typing) {
        NumberInput(value, { setting.value = setting.convert(it) }, Modifier.fillMaxWidth(), focused = true) {
            typing = false
        }
        return
    }

    val min = setting.range.start.toDouble()
    val max = setting.range.endInclusive.toDouble()
    // The value moves in steps; the bar glides from one to the next
    val fraction by animateFloatAsState(((value.toDouble() - min) / (max - min)).toFloat().coerceIn(0f, 1f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clip(RoundedCornerShape(Radius.Small))
            .background(colors.track)
            .drawBehind { drawRect(colors.primary, size = Size(size.width * fraction, size.height)) }
            .scrubbable { position, size ->
                val steps = ((position.x / size.width).coerceIn(0f, 1f) * (max - min) / setting.step.toDouble()).roundToLong()
                setting.value = setting.convert(min + steps * setting.step.toDouble())
            }
            .padding(horizontal = 3.dp)
    ) {
        Text(setting.name, Modifier.weight(1f), colors.onControl)
        Text(label, Modifier.mouseClickable { typing = true }, colors.onControl, maxLines = 1)
    }
}

/** A double as the setting's own number type; the setting clamps it to its range */
@Suppress("UNCHECKED_CAST")
private fun <T> NumericSetting<T>.convert(number: Double): T where T : Number, T : Comparable<T> =
    when (value) {
        is Int -> number.roundToInt()
        is Long -> number.roundToLong()
        is Float -> number.toFloat()
        else -> number
    } as T

@Composable
private fun <T : Enum<T>> EnumDropdown(setting: EnumSetting<T>) {
    val selected by observe { setting.value }
    Dropdown(
        label = setting.name,
        options = selected.declaringJavaClass.enumConstants.asList(),
        selected = selected,
        optionLabel = { it.displayValue },
        modifier = Modifier.padding(start = 3.dp),
    ) { setting.value = it }
}

@Composable
private fun TextField(setting: StringSetting) {
    val value by observe { setting.value }
    // Text that runs over several lines is the one editor that cannot keep to a row
    if (setting.multiline) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(setting.name, Modifier.padding(start = 3.dp, top = 1.dp))
        TextInput(value, { setting.value = it }, Modifier.fillMaxWidth(), singleLine = false)
    }
    else Labeled(setting.name) {
        TextInput(value, { setting.value = it }, Modifier.width(TextFieldWidth))
    }
}

@Composable
private fun CharField(setting: CharSetting) {
    val value by observe { setting.value }
    Labeled(setting.name) {
        // The field holds a single character: whatever is typed last replaces it
        TextInput(value.toString(), { text -> text.lastOrNull()?.let { setting.value = it } }, Modifier.width(18.dp))
    }
}

/**
 * Shows the bind as the cap of a key and, once clicked, records the next key or mouse button as
 * the new one. The recording itself happens in [GuiScreen], which sees the raw input before the
 * interface does.
 */
@Composable
private fun KeybindButton(setting: KeybindSetting) {
    val colors = LambdaTheme.colors
    val bind by observe { setting.value }
    var recording by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (recording) GuiScreen.keybindRecorder = null }
    }

    Labeled(setting.name) {
        Tooltip("Click, then press a key or mouse button. Backspace unbinds, Escape cancels.") {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(RowHeight)
                    .clip(RoundedCornerShape(Radius.Small))
                    .background(if (recording) colors.primary else colors.track)
                    .mouseClickable {
                        recording = true
                        GuiScreen.keybindRecorder = { recorded ->
                            recorded?.let { setting.value = it }
                            recording = false
                        }
                    }
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = if (recording) "Press a key" else bind.name,
                    color = if (recording) colors.onPrimary else colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BlockPosFields(setting: BlockPosSetting) {
    val value by observe { setting.value.toImmutable() }
    Labeled(setting.name) {
        NumberInput(value.x, { setting.value = BlockPos(it.roundToInt(), value.y, value.z) }, Modifier.width(NumberFieldWidth))
        NumberInput(value.y, { setting.value = BlockPos(value.x, it.roundToInt(), value.z) }, Modifier.width(NumberFieldWidth))
        NumberInput(value.z, { setting.value = BlockPos(value.x, value.y, it.roundToInt()) }, Modifier.width(NumberFieldWidth))
        Tooltip("Use the block you are looking at") {
            Button("Target", Modifier.height(RowHeight)) {
                mc.crosshairTarget?.blockResult?.blockPos?.let { setting.value = it }
                    ?: setting.info("No block under crosshair")
            }
        }
    }
}

@Composable
private fun Vec3dFields(setting: Vec3dSetting) {
    val value by observe { setting.value }
    Labeled(setting.name) {
        NumberInput(value.x, { setting.value = Vec3d(it, value.y, value.z) }, Modifier.width(NumberFieldWidth))
        NumberInput(value.y, { setting.value = Vec3d(value.x, it, value.z) }, Modifier.width(NumberFieldWidth))
        NumberInput(value.z, { setting.value = Vec3d(value.x, value.y, it) }, Modifier.width(NumberFieldWidth))
    }
}

/** Width of the field a line of text is typed into, beside its name */
private val TextFieldWidth = 90.dp

/** Width of each field of a position; longer numbers scroll inside it */
private val NumberFieldWidth = 34.dp
