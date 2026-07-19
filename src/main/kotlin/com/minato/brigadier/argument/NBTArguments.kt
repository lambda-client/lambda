
/*
 * Preserve binary compatibility when moving extensions between files
 */
@file:JvmMultifileClass
@file:JvmName("ArgumentsKt")

package com.minato.brigadier.argument


import com.minato.brigadier.ArgumentReader
import com.minato.brigadier.BrigadierDsl
import com.minato.brigadier.DefaultArgumentConstructor
import com.minato.brigadier.DefaultArgumentReader
import com.minato.brigadier.argument
import com.minato.brigadier.assumeSourceNotUsed
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
