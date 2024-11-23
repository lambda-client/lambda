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

package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object CameraTweaks : Module(
    name = "CameraTweaks",
    description = "Adjusts camera settings",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    @JvmStatic
    val camDistance by setting("Camera Distance", 4.0f, 1.0f..20.0f, 0.1f)

    @JvmStatic
    val noClipCam by setting("No Clip Camera", true)
}
