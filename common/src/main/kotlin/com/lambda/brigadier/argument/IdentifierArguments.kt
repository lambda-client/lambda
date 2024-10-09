package com.lambda.brigadier.argument

/*
 * Copyright 2024 The Quilt Project
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

import com.lambda.brigadier.*
import com.lambda.brigadier.assumeSourceNotUsed
import net.minecraft.advancement.AdvancementEntry
import net.minecraft.command.argument.IdentifierArgumentType
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
 * @see IdentifierArgumentType.getAdvancementArgument
 */
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                IdentifierArgumentType
                >
        >.asAdvancement(): AdvancementEntry {
    return IdentifierArgumentType.getAdvancementArgument(context, name)
}

/**
 * Reads the [Identifier] value from the
 * argument in the receiver [ArgumentReader]
 * as a [LootCondition].
 *
 * @see IdentifierArgumentType.getPredicateArgument
 */
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                IdentifierArgumentType
                >
        >.asPredicate(): LootCondition {
    return IdentifierArgumentType.getPredicateArgument(context, name)
}

/**
 * Reads the [Identifier] value from the
 * argument in the receiver [ArgumentReader]
 * as a [LootFunction].
 *
 * @see IdentifierArgumentType.getItemModifierArgument
 */
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                IdentifierArgumentType
                >
        >.asItemModifier(): LootFunction {
    return IdentifierArgumentType.getItemModifierArgument(context, name)
}

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
    return IdentifierArgumentType.getRecipeArgument(context, name)
}

/**
 * Creates an identifier argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> identifier(
    name: String
): DefaultArgumentConstructor<S, IdentifierArgumentType> {
    return argument(name, IdentifierArgumentType.identifier())
}