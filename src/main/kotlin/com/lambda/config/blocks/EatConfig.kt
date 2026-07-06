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

package com.lambda.config.blocks

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.interaction.inventory.StackSelection.Companion.selectStack
import com.lambda.threading.runSafe
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.item.ItemUtils.nutrition
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

interface EatConfig {
    val eatOnHunger: Boolean
    val minFoodLevel: Int
    val nutritiousFood: Collection<Item>
    val saturated: Saturation

    val eatOnFire: Boolean
    val resistanceFood: Collection<Item>

    val eatOnDamage: Boolean
    val minDamage: Int
    val regenerationFood: Collection<Item>

    val selectionPriority: SelectionPriority
    val ignoreBadFood: Boolean
    val badFood: Collection<Item>

    enum class Saturation(
        override val displayName: String,
        override val description: String
    ): NamedEnum, Describable {
        EatSmart("Eat Smart", "Eats until the next food would exceed the hunger limit."),
        EatUntilFull("Eat Until Full", "Eats food until the hunger bar is completely full. May waste some food."),
    }

    @Suppress("unused")
    enum class SelectionPriority(
        val comparator: Comparator<ItemStack>,
        override val displayName: String,
        override val description: String
    ): NamedEnum, Describable {
        LeastNutritious(
            compareBy { it.item.nutrition },
            "Least Nutritious",
            "Eats food items with the least nutritional value."
        ),
        MostNutritious(
            compareByDescending { it.item.nutrition },
            "Most Nutritious",
            "Eats food items with the most nutritional value."
        )
    }

    enum class Reason(val message: (ItemStack) -> String) {
        None({ "Waiting for reason to eat..." }),
        Hunger({ "Eating ${it.item.name.string} due to Hunger" }),
        Damage({ "Eating ${it.item.name.string} due to Damage" }),
        Fire({ "Eating ${it.item.name.string} due to Fire" });

        fun shouldEat() = this != None

        context(c: Automated)
        fun shouldKeepEating(stack: ItemStack?) = runSafe {
            if (stack == null || stack.isEmpty) return@runSafe false
            when(this@Reason) {
                Hunger -> when(c.eatConfig.saturated) {
                    Saturation.EatSmart -> stack.item.nutrition + player.hungerManager.foodLevel <= 20
                    Saturation.EatUntilFull -> player.hungerManager.isNotFull
                }
                Damage -> !player.hasStatusEffect(StatusEffects.REGENERATION)
                Fire -> !player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)
                None -> false
            }
        } ?: false

        context(c: Automated)
        fun selector() = selectStack(sorter = c.eatConfig.selectionPriority.comparator) {
            when(this@Reason) {
                None -> any()
                Hunger -> isOneOfItems(c.eatConfig.nutritiousFood)
                Damage -> isOneOfItems(c.eatConfig.regenerationFood)
                Fire -> isOneOfItems(c.eatConfig.resistanceFood)
            } and if (c.eatConfig.ignoreBadFood) isNoneOfItems(c.eatConfig.badFood) else any()
        }
    }

    companion object {
        fun AutomatedSafeContext.reasonEating() = when {
            eatConfig.eatOnHunger && player.hungerManager.foodLevel <= eatConfig.minFoodLevel -> Reason.Hunger
            eatConfig.eatOnDamage && player.health <= eatConfig.minDamage && !player.hasStatusEffect(StatusEffects.REGENERATION) -> Reason.Damage
            eatConfig.eatOnFire && player.isOnFire && !player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) -> Reason.Fire
            else -> Reason.None
        }
    }
}