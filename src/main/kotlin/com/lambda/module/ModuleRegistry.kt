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

package com.lambda.module

import com.lambda.core.Loadable
import com.lambda.util.reflections.getInstances

object ModuleRegistry : Loadable {
    override val priority = 1

    val modules = getInstances<Module>()
        .sortedBy { it.name }

    val moduleNameMap = modules.associateBy { it.name }

    override fun load() =
        "Loaded ${modules.size} modules with ${modules.sumOf { it.settings.size }} settings"
}
