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

package com.lambda.module

import com.lambda.core.Loadable
import com.lambda.util.reflections.getInstances
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

/**
 * The [ModuleRegistry] object is responsible for managing all [Module] instances in the system.
 */
object ModuleRegistry : Loadable {
    val modules = getInstances<Module> { forPackages("com.lambda.module.modules") }.toMutableList()

    val moduleNames: Set<String>
        get() = modules.map { it.name }.toSet()

    override fun load(): String {
        return "Registered ${modules.size} modules with ${modules.sumOf { it.settings.size }} settings"
    }
}
