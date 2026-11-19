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
import com.lambda.gui.component.Text
import com.lambda.gui.component.isActivation
import com.lambda.gui.component.keyboardFocusable
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.scrubbable
import com.lambda.gui.core.observe
import com.lambda.gui.settings.ROW_INSET
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs

private const val SAME = 1e-4

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
	private val lowest get() = range.start.decimal
	private val highest get() = range.endInclusive.decimal
	private val Number.decimal get() = BigDecimal(toString())

	var appliedOnRelease = false

	override val isModified
		get() = abs(originalCore.value.toDouble() - originalCore.defaultValue.toDouble()) > step.toDouble() * SAME

	protected abstract fun convert(number: BigDecimal): T

	override fun toString() = format(value)

	private fun format(value: T) = "${formatter.format(value)}$unit"

	private fun parse(text: String): T? =
		text.replace(',', '.').toBigDecimalOrNull()?.let { convert(it.coerceIn(lowest, highest)) }

	private fun fractionOf(value: T): Float {
		val span = range.endInclusive.toDouble() - range.start.toDouble()
		val fraction = ((value.toDouble() - range.start.toDouble()) / span).toFloat()
		return if (span > 0 && !fraction.isNaN()) fraction.coerceIn(0f, 1f) else 0f
	}

	private fun at(fraction: Float): T = when {
		fraction <= 0f -> range.start
		fraction >= 1f -> range.endInclusive
		else -> onGrid(lowest + (highest - lowest) * fraction.toBigDecimal(), RoundingMode.HALF_UP)
	}

	private fun stepped(by: Int): T =
		onGrid(value.decimal, if (by < 0) RoundingMode.CEILING else RoundingMode.FLOOR, by)

	private fun onGrid(target: BigDecimal, rounding: RoundingMode, offset: Int = 0): T {
		val step = step.decimal
		if (step.signum() <= 0) return convert(target.coerceIn(lowest, highest))
		val steps = (target - lowest).divide(step, 0, rounding) + offset.toBigDecimal()
		return convert((lowest + step * steps).coerceIn(lowest, highest))
	}

	@Composable
	override fun Editor() {
		val colors = LambdaTheme.colors
		val current by observe { value }
		var typing by remember { mutableStateOf(false) }
		val bar = remember { FocusRequester() }
		var refocus by remember { mutableStateOf(false) }

		if (typing) {
			NumberInput(current, ::parse, { value = it }, Modifier.fillMaxWidth(), focused = true) {
				typing = false
				refocus = KeyboardFocus.visible
			}
			return
		}
		if (refocus) LaunchedEffect(Unit) {
			bar.requestFocus()
			refocus = false
		}

		var held by remember { mutableStateOf<T?>(null) }
		val shown = held ?: current
		val label = remember(shown, unit) { format(shown) }
		val fraction by animateFloatAsState(fractionOf(shown))
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = Look.rowHeight)
				.clip(RoundedCornerShape(Look.controlRounding))
				.background(colors.track)
				.drawBehind { drawRect(colors.trackFilled, size = Size(size.width * fraction, size.height)) }
				.scrubbable(onEnd = {
					held?.let { value = it }
					held = null
				}) { position, size ->
					val dragged = at(position.x / size.width)
					if (appliedOnRelease) held = dragged else value = dragged
				}
				.focusRequester(bar)
				.keyboardFocusable { event ->
					when {
						event.key == Key.DirectionLeft -> value = stepped(-1)
						event.key == Key.DirectionRight -> value = stepped(1)
						event.isActivation -> typing = true
						else -> return@keyboardFocusable false
					}
					true
				}
				.padding(horizontal = ROW_INSET)
		) {
			Text(name, Modifier.weight(1f), colors.onControl, maxLines = 1)
			Text(
				text = label,
				modifier = Modifier.mouseClickable(keyboard = false) { typing = true },
				color = colors.onControl,
				maxLines = 1,
			)
		}
	}
}

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
