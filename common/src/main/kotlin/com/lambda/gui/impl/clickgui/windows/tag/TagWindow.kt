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

package com.lambda.gui.impl.clickgui.windows.tag

import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.gui.impl.hudgui.LambdaHudGui
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag

class TagWindow(
    val tag: ModuleTag,
    owner: AbstractClickGui,
) : ModuleWindow(tag.name, gui = owner) {
    val isHudWindow = gui is LambdaHudGui
    private val rawFilter = { m: Module -> m is HudModule }
    private val filter get() = if (isHudWindow) rawFilter else { m: Module -> !rawFilter(m) }

    override fun getModuleList() = ModuleRegistry.modules
        .filter { it.defaultTags.firstOrNull() == tag && filter(it) }
}
