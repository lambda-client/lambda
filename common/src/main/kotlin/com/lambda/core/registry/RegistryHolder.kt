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

package com.lambda.core.registry

import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import java.util.function.Supplier


class RegistryHolder<T> internal constructor(
    val id: Identifier,
    var value: T,
) : Supplier<T> {
    private var holder: RegistryEntry<T>? = null

    override fun get(): T {
        with(holder) {
            checkNotNull(this) { "RegistryHolder not populated" }

            return value()
        }
    }

    fun handleRegister(registry: RegistryWrapper<*>) {
        holder = registry.registerForHolder(id, value)
    }
}
