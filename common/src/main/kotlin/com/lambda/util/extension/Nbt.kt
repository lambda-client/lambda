package com.lambda.util.extension

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtInt
import net.minecraft.nbt.NbtList

/**
 * Puts a list of integer into the component, this is not the same as an int array
 */
fun NbtCompound.putIntList(key: String, vararg values: Int) {
    put(key, values.fold(NbtList()) { list, value -> list.add(NbtInt.of(value)); list })
}
