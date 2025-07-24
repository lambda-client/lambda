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

package com.lambda.brigadier.argument

import com.lambda.brigadier.ArgumentReader
import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentDescriptor
import com.lambda.brigadier.DefaultArgumentReader
import com.lambda.brigadier.argument
import com.lambda.brigadier.assumeSourceNotUsed
import net.minecraft.advancement.AdvancementEntry
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.command.argument.RegistryKeyArgumentType
import net.minecraft.loot.condition.LootCondition
import net.minecraft.loot.function.LootFunction
import net.minecraft.recipe.RecipeEntry
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier

/**
 * Reads the [Identifier] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see IdentifierArgumentType.getIdentifier
 */
@JvmName("valueIdentifierArg")
@BrigadierDsl
fun DefaultArgumentReader<IdentifierArgumentType>.value(): Identifier {
    return IdentifierArgumentType.getIdentifier(context.assumeSourceNotUsed(), name)
}

/**
 * Reads the [Identifier] value from the
 * argument in the receiver [ArgumentReader]
 * as an [AdvancementEntry].
 *
 * @see RegistryKeyArgumentType.getAdvancementEntry
 */
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                IdentifierArgumentType
                >
        >.asAdvancement(): AdvancementEntry {
    return RegistryKeyArgumentType.getAdvancementEntry(context, name)
}

/**
 * Reads the [Identifier] value from the
 * argument in the receiver [ArgumentReader]
 * as a [LootCondition].
 *
 * @see IdentifierArgumentType.getPredicateArgument
 */
//@BrigadierDsl
//fun ArgumentReader<
//        ServerCommandSource,
//        DefaultArgumentDescriptor<
//                IdentifierArgumentType
//                >
//        >.asPredicate(): LootCondition {
//    return IdentifierArgumentType.getPredicateArgument(context, name)
//}

/**
 * Reads the [Identifier] value from the
 * argument in the receiver [ArgumentReader]
 * as a [LootFunction].
 *
 * @see IdentifierArgumentType.getItemModifierArgument
 */
//@BrigadierDsl
//fun ArgumentReader<
//        ServerCommandSource,
//        DefaultArgumentDescriptor<
//                IdentifierArgumentType
//                >
//        >.asItemModifier(): LootFunction {
//    return IdentifierArgumentType.getItemModifierArgument(context, name)
//}

/**
 * Reads the [Identifier] value from the
 * argument in the receiver [ArgumentReader]
 * as a [RecipeEntry].
 *
 * @see IdentifierArgumentType.getRecipeArgument
 */
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                IdentifierArgumentType
                >
        >.asRecipe(): RecipeEntry<*> {
    return RegistryKeyArgumentType.getRecipeEntry(context, name)
}

/**
 * Creates an identifier argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> identifier(
    name: String,
): DefaultArgumentConstructor<S, IdentifierArgumentType> {
    return argument(name, IdentifierArgumentType.identifier())
}
