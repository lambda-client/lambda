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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.lambda.config.Config
import com.lambda.config.ConfigEditorD5l
import com.lambda.config.SettingEditBuilder
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.component.NumberInput
import com.lambda.gui.component.RowHeight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.scrubbable
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import java.text.NumberFormat
import java.util.*
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

	override fun toString() = "${formatter.format(value)}$unit"

	/**
	* A bar filled up to the value. Dragging anywhere on it sets the value; clicking the number
	* swaps the bar for a text field to type an exact one, for ranges with more steps than pixels.
	*/
	@Composable
	override fun compose() {
		val colors = LambdaTheme.colors
		val value by observe { value }
		val label by observe { toString() }
		var typing by remember { mutableStateOf(false) }

		if (typing) {
			NumberInput(value, { this.value = convert(it) }, Modifier.fillMaxWidth(), focused = true) {
				typing = false
			}
			return
		}

		val min = range.start.toDouble()
		val max = range.endInclusive.toDouble()
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
					val steps = ((position.x / size.width).coerceIn(0f, 1f) * (max - min) / step.toDouble()).roundToLong()
					this.value = convert(min + steps * step.toDouble())
				}
				.padding(horizontal = 3.dp)
		) {
			Text(name, Modifier.weight(1f), colors.onControl, maxLines = 1)
			Text(label, Modifier.mouseClickable { typing = true }, colors.onControl, maxLines = 1)
		}
	}

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
