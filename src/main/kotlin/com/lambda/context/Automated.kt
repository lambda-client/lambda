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

package com.lambda.context

import com.lambda.config.blocks.BreakConfig
import com.lambda.config.blocks.BuildConfig
import com.lambda.config.blocks.EatConfig
import com.lambda.config.blocks.HotbarConfig
import com.lambda.config.blocks.InteractConfig
import com.lambda.config.blocks.InventoryConfig
import com.lambda.config.blocks.PathingConfig
import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.config.blocks.RotationConfig

interface Automated {
	val buildConfig: BuildConfig
	val breakConfig: BreakConfig
	val interactConfig: InteractConfig
	val rotationConfig: RotationConfig
	val inventoryConfig: InventoryConfig
	val hotbarConfig: HotbarConfig
	val eatConfig: EatConfig
	val pathingConfig: PathingConfig
	val pathingRenderConfig: PathingRenderConfig
}