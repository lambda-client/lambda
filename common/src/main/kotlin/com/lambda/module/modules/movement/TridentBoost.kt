/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.movement

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object TridentBoost : Module(
    name = "TridentBoost",
    description = "Boosts you with tridents",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    @JvmStatic
    val tridentSpeed by setting("Speed Factor", 1.0, 0.1..3.0, 0.1, description = "Speed factor of the trident boost")

    @JvmStatic
    val forceUse by setting("Force Use", true, description = "Try to use the trident outside of water or rain")
}
