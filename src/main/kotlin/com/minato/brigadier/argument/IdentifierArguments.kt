
package com.minato.brigadier.argument

import com.minato.brigadier.ArgumentReader
import com.minato.brigadier.BrigadierDsl
import com.minato.brigadier.DefaultArgumentConstructor
import com.minato.brigadier.DefaultArgumentDescriptor
import com.minato.brigadier.DefaultArgumentReader
import com.minato.brigadier.argument
import com.minato.brigadier.assumeSourceNotUsed
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
