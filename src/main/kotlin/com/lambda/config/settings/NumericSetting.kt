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

package com.lambda.config.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import com.lambda.config.Config
import com.lambda.config.ConfigEditorD5l
import com.lambda.config.SettingEditBuilder
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.component.KeyboardFocus
import com.lambda.gui.component.NumberInput
import com.lambda.gui.component.RowHeight
import com.lambda.gui.component.isActivation
import com.lambda.gui.component.keyboardFocusable
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.scrubbable
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * @see [com.lambda.config.Config]
 */
abstract class NumericSetting<T>(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<NumericSetting<T>, T>,
	defaultValue: T,
	visibility: () -> Boolean,
	open var range: ClosedRange<T>,
	open var step: T,
	var unit: String
) : Setting<T>(name, description, defaultValue, layer, config, visibility) where T : Number, T : Comparable<T> {
	override var value: T
		get() = super.value
		set(newVal) {
			super.value = newVal.coerceIn(range)
		}

	private val formatter = NumberFormat.getNumberInstance(Locale.getDefault())

	/**
	* Whether dragging the bar only sets the value once the button is let go, see [appliedOnRelease].
	* Until then the bar and its number show what the value is about to be.
	*/
	var appliedOnRelease = false

	override fun toString() = "${formatter.format(value)}$unit"

	/**
	* A bar filled up to the value. Dragging anywhere on it sets the value; clicking the number
	* swaps the bar for a text field to type an exact one, for ranges with more steps than pixels.
	*
	* With the keyboard focus on it, the left and right arrows take the value a step down and up,
	* and Enter or Space opens the field to type in. Done typing, the focus is back on the bar.
	*/
	@Composable
	override fun compose() {
		val colors = LambdaTheme.colors
		val value by observe { value }
		val label by observe { toString() }
		var typing by remember { mutableStateOf(false) }
		// The bar is not there while its field is: it has to ask for the focus the field gave up
		val bar = remember { FocusRequester() }
		var refocus by remember { mutableStateOf(false) }

		if (typing) {
			NumberInput(value, { this.value = convert(it) }, Modifier.fillMaxWidth(), focused = true) {
				typing = false
				refocus = KeyboardFocus.visible
			}
			return
		}
		if (refocus) LaunchedEffect(Unit) {
			bar.requestFocus()
			refocus = false
		}

		val min = range.start.toDouble()
		val max = range.endInclusive.toDouble()
		/** The value [steps] of the setting's step up from the bottom of its range */
		fun at(steps: Long) = convert(range.start.decimal + step.decimal * BigDecimal.valueOf(steps))
		fun stepsOf(value: Double) = ((value - min) / step.toDouble()).roundToLong()
		// The value moves in steps; the bar glides from one to the next
		// What the bar has been dragged to but not let go at, for a setting that waits for that
		var held by remember { mutableStateOf<T?>(null) }
		val shown = held ?: value
		val fraction by animateFloatAsState(((shown.toDouble() - min) / (max - min)).toFloat().coerceIn(0f, 1f))
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = RowHeight)
				.clip(RoundedCornerShape(Radius.Control))
				.background(colors.track)
				.drawBehind { drawRect(colors.trackFilled, size = Size(size.width * fraction, size.height)) }
				.scrubbable(onEnd = {
					held?.let { this.value = it }
					held = null
				}) { position, size ->
					val dragged = at(stepsOf(min + (position.x / size.width).coerceIn(0f, 1f) * (max - min)))
					if (appliedOnRelease) held = dragged.coerceIn(range) else this.value = dragged
				}
				.focusRequester(bar)
				.keyboardFocusable { event ->
					when {
						event.key == Key.DirectionLeft -> this.value = at(stepsOf(value.toDouble()) - 1)
						event.key == Key.DirectionRight -> this.value = at(stepsOf(value.toDouble()) + 1)
						event.isActivation -> typing = true
						else -> return@keyboardFocusable false
					}
					true
				}
				.padding(horizontal = 3.dp)
		) {
			Text(name, Modifier.weight(1f), colors.onControl, maxLines = 1)
			Text(
				text = held?.let { "${formatter.format(it)}$unit" } ?: label,
				modifier = Modifier.mouseClickable(keyboard = false) { typing = true },
				color = colors.onControl,
				maxLines = 1,
			)
		}
	}

	/**
	* A value is only saved once it is other than the default. One that is a rounding error
	* away from it is not: files written while sliders still counted in binary hold such values,
	* 0.14999999 for a default of 0.15, and they are the default.
	*/
	override val isModified
		get() = abs(originalCore.value.toDouble() - originalCore.defaultValue.toDouble()) > step.toDouble() * SAME

	/**
	* The number as the decimal it is written as. A slider counts its steps in these, because a
	* step of 0.01 is not 0.01 in binary and thirty of them do not add up to 0.3: counted in
	* binary, a bar dragged back to its default arrives a hair beside it, which shows the same
	* and is saved as a change. Counted in decimal, every stop of the bar is exactly the number
	* the code would write for it, however fine the step and however long the range.
	*/
	private val Number.decimal get() = BigDecimal(toString())

	/** A decimal as the setting's own number type: the float or double nearest to it, as a literal of it would be */
	@Suppress("UNCHECKED_CAST")
	private fun convert(number: BigDecimal): T =
		when (value) {
			is Int -> number.setScale(0, RoundingMode.HALF_UP).toInt()
			is Long -> number.setScale(0, RoundingMode.HALF_UP).toLong()
			is Float -> number.toPlainString().toFloat()
			else -> number.toPlainString().toDouble()
		} as T

	/** A double as the setting's own number type; setting it clamps it to the range */
	@Suppress("UNCHECKED_CAST")
	private fun convert(number: Double): T =
		when (value) {
			is Int -> number.roundToInt()
			is Long -> number.roundToLong()
			is Float -> number.toFloat()
			else -> number
		} as T
}

/** How far from its default, as a share of its step, a number may be and still be the default: a rounding error, never a step */
private const val SAME = 1e-4

/**
 * Has dragging the bar set the value only when the button is let go, where it otherwise follows
 * the pointer. For a value that moves the bar it is set on, the scale of the interface: applied
 * as it is dragged, the bar would shift and resize under the pointer that is setting it.
 */
fun <S : NumericSetting<*>> S.appliedOnRelease() = apply { appliedOnRelease = true }

@Suppress("unchecked_cast")
@ConfigEditorD5l
fun <T> SettingEditBuilder<T>.range(range: ClosedRange<T>) where T : Number, T : Comparable<T> {
	(entries as Collection<NumericSetting<T>>).forEach { it.range = range }
}

@Suppress("unchecked_cast")
@ConfigEditorD5l
fun <T> SettingEditBuilder<T>.step(step: T) where T : Number, T : Comparable<T> {
	(entries as Collection<NumericSetting<T>>).forEach { it.step = step }
}

@Suppress("unchecked_cast")
@ConfigEditorD5l
fun <T> SettingEditBuilder<T>.unit(unit: String) where T : Number, T : Comparable<T> {
	(entries as Collection<NumericSetting<T>>).forEach { it.unit = unit }
}
