
package com.minato.brigadier.argument

import com.minato.brigadier.ArgumentReader
import com.minato.brigadier.BrigadierDsl
import com.minato.brigadier.DefaultArgumentConstructor
import com.minato.brigadier.DefaultArgumentReader
import com.minato.brigadier.argument
import com.minato.brigadier.assumeSourceNotUsed
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
fun DefaultArgumentReader<BlockPredicateArgumentType>.value(): Predicate<CachedBlockPosition> {
    return BlockPredicateArgumentType.getBlockPredicate(context.assumeSourceNotUsed(), name)
}

/**
 * Reads the [BlockStateArgument] value of the argument in
 * the receiver [ArgumentReader].
 *
 * @see BlockStateArgumentType.getBlockState
 */
@BrigadierDsl
fun DefaultArgumentReader<BlockStateArgumentType>.value(): BlockStateArgument {
    return BlockStateArgumentType.getBlockState(context.assumeSourceNotUsed(), name)
}

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
): DefaultArgumentConstructor<S, BlockPredicateArgumentType> {
    return argument(name, BlockPredicateArgumentType.blockPredicate(registryAccess))
}

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
): DefaultArgumentConstructor<S, BlockStateArgumentType> {
    return argument(name, BlockStateArgumentType.blockState(registryAccess))
}
