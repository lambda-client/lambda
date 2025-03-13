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

object NoRender : Module(
    name = "NoRender",
    description = "Disables rendering of certain things",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    @JvmStatic
    val noDarkness by setting("No Darkness", true)

    @JvmStatic
    val noBurning by setting("No Burning Overlay", true)

    @JvmStatic
    val fireOverlayYOffset by setting("Fire Overlay Y Offset", -0.3, -0.8..0.0, 0.1) { !noBurning }

    @JvmStatic
    val noUnderwater by setting("No Underwater Overlay", true)

    @JvmStatic
    val noInWall by setting("No In Wall Overlay", true)

    @JvmStatic
    val noChatVerificationToast by setting("No Chat Verification Toast", true)
}
