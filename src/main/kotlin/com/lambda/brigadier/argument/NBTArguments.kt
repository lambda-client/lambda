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

/*
 * Preserve binary compatibility when moving extensions between files
 */
@file:JvmMultifileClass
@file:JvmName("ArgumentsKt")

package com.lambda.brigadier.argument


import com.lambda.brigadier.ArgumentReader
import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentReader
import com.lambda.brigadier.argument
import com.lambda.brigadier.assumeSourceNotUsed
import net.minecraft.command.argument.NbtCompoundArgumentType
import net.minecraft.command.argument.NbtElementArgumentType
import net.minecraft.command.argument.NbtPathArgumentType
import net.minecraft.command.argument.NbtPathArgumentType.NbtPath
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement

/**
 * Reads the [NbtCompound] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see NbtCompoundArgumentType.getNbtCompound
 */
@JvmName("valueNbtCompoundArg")
@BrigadierDsl
fun DefaultArgumentReader<NbtCompoundArgumentType>.value(): NbtCompound {
	return NbtCompoundArgumentType.getNbtCompound(context, name)
}

/**
 * Reads the [NbtElement] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see NbtElementArgumentType.getNbtElement
 */
@JvmName("valueNbtElementArg")
@BrigadierDsl
fun DefaultArgumentReader<NbtElementArgumentType>.value(): NbtElement {
	return NbtElementArgumentType.getNbtElement(context, name)
}

/**
 * Reads the [NbtPath] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see NbtPathArgumentType.getNbtPath
 */
@JvmName("valueNbtPathArg")
@BrigadierDsl
fun DefaultArgumentReader<NbtPathArgumentType>.value(): NbtPath {
	return NbtPathArgumentType.getNbtPath(context.assumeSourceNotUsed(), name)
}

/**
 * Creates a nbt compound argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> nbtCompound(
	name: String,
): DefaultArgumentConstructor<S, NbtCompoundArgumentType> {
	return argument(name, NbtCompoundArgumentType.nbtCompound())
}

/**
 * Creates an NBT element argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> nbtElement(
	name: String,
): DefaultArgumentConstructor<S, NbtElementArgumentType> {
	return argument(name, NbtElementArgumentType.nbtElement())
}

/**
 * Creates an NBT path argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> nbtPath(
	name: String,
): DefaultArgumentConstructor<S, NbtPathArgumentType> {
	return argument(name, NbtPathArgumentType.nbtPath())
}
