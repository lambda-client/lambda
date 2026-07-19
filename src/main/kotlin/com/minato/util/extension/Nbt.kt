
package com.minato.util.extension

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
