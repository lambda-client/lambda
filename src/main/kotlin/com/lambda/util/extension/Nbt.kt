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

package com.lambda.util.extension

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtInt
import net.minecraft.nbt.NbtList
import net.minecraft.util.math.Vec3i
import java.util.*

/**
 * Puts a list of integer into the component, this is not the same as an int array
 */
fun NbtCompound.putIntList(key: String, vararg values: Int) {
    put(key, values.fold(NbtList()) { list, value -> list.add(NbtInt.of(value)); list })
}

/**
 * Deletes all the keys in a compound
 */
fun NbtCompound.clear() {
    keys.forEach { remove(it) }
}

/**
 * Retrieves a vector from a tuple
 */
fun NbtCompound.getVector(key: String): Optional<Vec3i> {
    val compound = getCompoundOrEmpty(key)

    val array = getIntArray(key)

    val x = compound.getInt("x")
        .or { compound.getInt("X") }
        .or { array.map { it[0] } }

    val y = compound.getInt("y")
        .or { compound.getInt("Y") }
        .or { array.map { it[1] } }

    val z = compound.getInt("z")
        .or { compound.getInt("Z") }
        .or { array.map { it[2] } }

    return if (x.isPresent && y.isPresent && z.isPresent) Optional.of(Vec3i(x.get(), y.get(), z.get()))
    else Optional.empty()
}
