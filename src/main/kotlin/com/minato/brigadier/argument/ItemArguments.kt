
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
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.ItemPredicateArgumentType
import net.minecraft.command.argument.ItemSlotArgumentType
import net.minecraft.command.argument.ItemStackArgument
import net.minecraft.command.argument.ItemStackArgumentType
import net.minecraft.item.ItemStack
import java.util.function.Predicate

/**
 * Reads the [ItemStack] predicate value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see ItemPredicateArgumentType.getItemStackPredicate
 */
@JvmName("valueItemPredicateArg")
@BrigadierDsl
fun DefaultArgumentReader<ItemPredicateArgumentType>.value(): Predicate<ItemStack> {
    return ItemPredicateArgumentType.getItemStackPredicate(context.assumeSourceNotUsed(), name)
}

/**
 * Reads the integer value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see ItemSlotArgumentType.getItemSlot
 */
@JvmName("valueItemSlotArg")
@BrigadierDsl
fun DefaultArgumentReader<ItemSlotArgumentType>.value(): Int {
    return ItemSlotArgumentType.getItemSlot(context.assumeSourceNotUsed(), name)
}

/**
 * Reads the [ItemStackArgument] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see ItemStackArgumentType.getItemStackArgument
 */
@JvmName("valueItemStackArg")
@BrigadierDsl
fun DefaultArgumentReader<ItemStackArgumentType>.value(): ItemStackArgument {
    return ItemStackArgumentType.getItemStackArgument(context, name)
}

/**
 * Creates an item predicate argument with [name] as the parameter name.
 *
 * @param context The command build context
 */
@BrigadierDsl
fun <S> itemPredicate(
    name: String,
    context: CommandRegistryAccess,
): DefaultArgumentConstructor<S, ItemPredicateArgumentType> {
    return argument(name, ItemPredicateArgumentType.itemPredicate(context))
}

/**
 * Creates an item slot argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> itemSlot(
    name: String,
): DefaultArgumentConstructor<S, ItemSlotArgumentType> {
    return argument(name, ItemSlotArgumentType.itemSlot())
}

/**
 * Creates an item stack argument with [name] as the parameter name.
 *
 * @param context The command build context
 */
@BrigadierDsl
fun <S> itemStack(
    name: String,
    context: CommandRegistryAccess,
): DefaultArgumentConstructor<S, ItemStackArgumentType> {
    return argument(name, ItemStackArgumentType.itemStack(context))
}
