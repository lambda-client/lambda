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
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.BlockPredicateArgumentType
import net.minecraft.command.argument.BlockStateArgument
import net.minecraft.command.argument.BlockStateArgumentType
import java.util.function.Predicate

/**
 * Reads the block predicate value of the argument in
 * the receiver [ArgumentReader].
 *
 * @see BlockPredicateArgumentType.getBlockPredicate
 */
@JvmName("valueBlockPredicateArg")
@BrigadierDsl
fun DefaultArgumentReader<BlockPredicateArgumentType>.value(): Predicate<CachedBlockPosition> {
    return BlockPredicateArgumentType.getBlockPredicate(context.assumeSourceNotUsed(), name)
}

/**
 * Reads the [BlockStateArgument] value of the argument in
 * the receiver [ArgumentReader].
 *
 * @see BlockStateArgumentType.getBlockState
 */
@JvmName("valueBlockStateArg")
@BrigadierDsl
fun DefaultArgumentReader<BlockStateArgumentType>.value(): BlockStateArgument {
    return BlockStateArgumentType.getBlockState(context.assumeSourceNotUsed(), name)
}

/**
 * Creates a block predicate argument with [name] as the parameter name.
 *
 * @param context The command build context
 */
@BrigadierDsl
fun <S> blockPredicate(
    name: String,
    registryAccess: CommandRegistryAccess
): DefaultArgumentConstructor<S, BlockPredicateArgumentType> {
    return argument(name, BlockPredicateArgumentType.blockPredicate(registryAccess))
}

/**
 * Creates a block state argument with [name] as the parameter name.
 *
 * @param context The command build context
 */
@BrigadierDsl
fun <S> blockState(
    name: String,
    registryAccess: CommandRegistryAccess
): DefaultArgumentConstructor<S, BlockStateArgumentType> {
    return argument(name, BlockStateArgumentType.blockState(registryAccess))
}
