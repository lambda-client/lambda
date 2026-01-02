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

import com.lambda.util.Describable

interface ReplaceConfig {
	val action: ActionStrategy
	val replace: ReplaceStrategy

	val enabled: Boolean get() = action != ActionStrategy.None

	enum class ActionStrategy(override val description: String) : Describable {
		Hide("Hides the message. Will override other strategies."),
		Delete("Deletes the matching part off the message."),
		Replace("Replace the matching string in the message with one of the following replace strategy."),
		None("Don't do anything."),
	}

	enum class ReplaceStrategy(val block: (String) -> String) {
		CensorAll({ it.replaceRange(0..<it.length, "*".repeat(it.length))}),
		CensorHalf({ it.foldIndexed("") { i, acc, char -> if (i % 2 == 0) acc + char else "$acc*" } }),
		KeepFirst({ if (it.length <= 1) it else it.replaceRange(1, it.length, "*".repeat(it.length - 1)) }),
	}
}