
package com.minato.config.blocks

import com.minato.context.Automated
import com.minato.context.AutomatedSafeContext
import com.minato.interaction.material.StackSelection.Companion.selectStack
import com.minato.threading.runSafe
import com.minato.util.Describable
import com.minato.util.NamedEnum
import com.minato.util.item.ItemUtils.nutrition
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