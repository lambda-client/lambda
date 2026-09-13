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

package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.ModuleTag

object Bobbing : Module(
	name = "Bobbing",
	description = "Modifies vanilla view bobbing when the player walks or runs",
	tag = ModuleTag.RENDER
) {
	val magnitude by setting("Magnitude", 1.0, 0.0..2.0, 0.01)
	val speed by setting("Speed", 1.0, 0.0..2.0, 0.01)
}