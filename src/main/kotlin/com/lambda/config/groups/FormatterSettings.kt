/*
 * Copyright 2025 Lambda
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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
import com.lambda.util.NamedEnum

class FormatterSettings(
	c: Configurable,
	vararg baseGroup: NamedEnum,
) : FormatterConfig, SettingGroup(c) {
	val localeEnum by c.setting("Locale", FormatterConfig.Locales.US, "The regional formatting used for numbers").group(*baseGroup).index()
	override val locale get() = localeEnum.locale

	val sep by c.setting("Separator", FormatterConfig.TupleSeparator.Comma, "Separator for string serialization of tuple data structures").group(*baseGroup).index()
	val customSep by c.setting("Custom Separator", "") { sep == FormatterConfig.TupleSeparator.Custom }.group(*baseGroup).index()
	override val separator get() = if (sep == FormatterConfig.TupleSeparator.Custom) customSep else sep.separator

	val group by c.setting("Tuple Prefix", FormatterConfig.TupleGrouping.Parentheses).group(*baseGroup).index()
	override val prefix get() = group.prefix
	override val postfix get() = group.postfix

	val floatingPrecision by c.setting("Floating Precision", 3, 0..6, 1, "Precision for floating point numbers").group(*baseGroup).index()
	override val precision get() = floatingPrecision

	val timeFormat by c.setting("Time Format", FormatterConfig.Time.IsoDateTime).group(*baseGroup).index()
	override val format get() = timeFormat.format
}