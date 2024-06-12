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
 * @see ItemPredicateArgumentType.getItemPredicate
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
    context: CommandRegistryAccess
): DefaultArgumentConstructor<S, ItemPredicateArgumentType> {
    return argument(name, ItemPredicateArgumentType.itemPredicate(context))
}

/**
 * Creates an item slot argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> itemSlot(
    name: String
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
    context: CommandRegistryAccess
): DefaultArgumentConstructor<S, ItemStackArgumentType> {
    return argument(name, ItemStackArgumentType.itemStack(context))
}