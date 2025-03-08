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

package com.lambda.util.extension

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtInt
import net.minecraft.nbt.NbtList
import net.minecraft.util.math.Vec3i

/**
 * Puts a list of integer into the component, this is not the same as an int array
 */
fun NbtCompound.putIntList(key: String, vararg values: Int) {
    put(key, values.fold(NbtList()) { list, value -> list.add(NbtInt.of(value)); list })
}

/**
 * Retrieves a vector from a tuple
 */
fun NbtCompound.getVector(key: String): Vec3i {
    val compound = getCompound(key)

    var x = compound.getInt("x")
    var y = compound.getInt("y")
    var z = compound.getInt("z")

    if (x == 0 && y == 0 && z == 0) {
        x = compound.getInt("X")
        y = compound.getInt("Y")
        z = compound.getInt("Z")
    }

    if (compound.isEmpty) {
        val arr = getIntArray(key)
        if (arr.size == 3) {
            x = arr[0]
            y = arr[1]
            z = arr[2]
        }
    }

    return Vec3i(x, y, z)
}
