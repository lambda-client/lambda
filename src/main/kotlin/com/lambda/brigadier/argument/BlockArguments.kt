/*
 * Copyright 2026 Lambda
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

package com.lambda.brigadier.argument

import com.lambda.brigadier.ArgumentReader
import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentReader
import com.lambda.brigadier.argument
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
@BrigadierDsl
fun DefaultArgumentReader<BlockPredicateArgumentType>.value(): Predicate<CachedBlockPosition> =
    BlockPredicateArgumentType.getBlockPredicate(context.assumeSourceNotUsed(), name)

/**
 * Reads the [BlockStateArgument] value of the argument in
 * the receiver [ArgumentReader].
 *
 * @see BlockStateArgumentType.getBlockState
 */
@BrigadierDsl
fun DefaultArgumentReader<BlockStateArgumentType>.value(): BlockStateArgument =
    BlockStateArgumentType.getBlockState(context.assumeSourceNotUsed(), name)

/**
 * Creates a block predicate argument with [name] as the parameter name.
 *
 * @param name The name of the argument
 * @param registryAccess The command registry access
 */
@BrigadierDsl
fun <S> blockPredicate(
    name: String,
    registryAccess: CommandRegistryAccess,
): DefaultArgumentConstructor<S, BlockPredicateArgumentType> =
    argument(name, BlockPredicateArgumentType.blockPredicate(registryAccess))

/**
 * Creates a block state argument with [name] as the parameter name.
 *
 * @param name The name of the argument
 * @param registryAccess The command registry access
 */
@BrigadierDsl
fun <S> blockState(
    name: String,
    registryAccess: CommandRegistryAccess,
): DefaultArgumentConstructor<S, BlockStateArgumentType> =
    argument(name, BlockStateArgumentType.blockState(registryAccess))
