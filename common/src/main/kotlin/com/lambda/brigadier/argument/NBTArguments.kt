/*
 * Copyright 2023 The Quilt Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Preserve binary compatibility when moving extensions between files
 */
@file:JvmMultifileClass
@file:JvmName("ArgumentsKt")

package com.lambda.brigadier.argument


import com.lambda.brigadier.*
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
    name: String
): DefaultArgumentConstructor<S, NbtCompoundArgumentType> {
    return com.lambda.brigadier.argument(name, NbtCompoundArgumentType.nbtCompound())
}

/**
 * Creates an NBT element argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> nbtElement(
    name: String
): DefaultArgumentConstructor<S, NbtElementArgumentType> {
    return argument(name, NbtElementArgumentType.nbtElement())
}

/**
 * Creates an NBT path argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> nbtPath(
    name: String
): DefaultArgumentConstructor<S, NbtPathArgumentType> {
    return argument(name, NbtPathArgumentType.nbtPath())
}
