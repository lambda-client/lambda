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

package com.lambda.util

import com.lambda.module.ModuleRegistry.modules

object Diagnostics {
	// ToDo: Expand this to include more information like version, etc.
	fun gatherDiagnostics() = buildString {
		modules.filter { it.isEnabled }
			.forEach { module ->
				append("\t${module.name}")
				module.settings
					.filter { it.isModified }
					.forEach { setting ->
						append("\t\t${setting.name} -> ${setting.value}")
					}
			}
	}
}