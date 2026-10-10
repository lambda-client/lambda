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
fun DefaultArgumentReader<ItemPredicateArgumentType>.value(): Predicate<ItemStack> =
    ItemPredicateArgumentType.getItemStackPredicate(context.assumeSourceNotUsed(), name)

/**
 * Reads the integer value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see ItemSlotArgumentType.getItemSlot
 */
@JvmName("valueItemSlotArg")
@BrigadierDsl
fun DefaultArgumentReader<ItemSlotArgumentType>.value(): Int =
    ItemSlotArgumentType.getItemSlot(context.assumeSourceNotUsed(), name)

/**
 * Reads the [ItemStackArgument] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see ItemStackArgumentType.getItemStackArgument
 */
@JvmName("valueItemStackArg")
@BrigadierDsl
fun DefaultArgumentReader<ItemStackArgumentType>.value(): ItemStackArgument =
    ItemStackArgumentType.getItemStackArgument(context, name)

/**
 * Creates an item predicate argument with [name] as the parameter name.
 *
 * @param context The command build context
 */
@BrigadierDsl
fun <S> itemPredicate(
    name: String,
    context: CommandRegistryAccess,
): DefaultArgumentConstructor<S, ItemPredicateArgumentType> =
    argument(name, ItemPredicateArgumentType.itemPredicate(context))

/**
 * Creates an item slot argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> itemSlot(
    name: String,
): DefaultArgumentConstructor<S, ItemSlotArgumentType> =
    argument(name, ItemSlotArgumentType.itemSlot())

/**
 * Creates an item stack argument with [name] as the parameter name.
 *
 * @param context The command build context
 */
@BrigadierDsl
fun <S> itemStack(
    name: String,
    context: CommandRegistryAccess,
): DefaultArgumentConstructor<S, ItemStackArgumentType> =
    argument(name, ItemStackArgumentType.itemStack(context))
