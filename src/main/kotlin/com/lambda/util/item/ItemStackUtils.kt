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

package com.lambda.util.item

import com.lambda.context.SafeContext
import com.lambda.util.collections.Cacheable.Companion.cacheable
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.AttributeModifiersComponent
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack

object ItemStackUtils {
    // FixMe: Change this fucking retarded stuff when mojang wake up from their coma and realize they fucked this shit up
    //  - The client and the server entity attributes are not synced,
    //  - Enchantments do not change attributes,
    //  - All enchantment utils are bound to the server

    /**
     * Returns the attack damage for the given [stack], the value is affected by potion effects and enchantments
     */
    fun SafeContext.attackDamage(entity: LivingEntity = player, stack: ItemStack = entity.mainHandStack) =
        entity.attackDamage(stack)

    /**
     * Returns the attack damage for the given [stack], the value is affected by potion effects and enchantments
     */
    fun LivingEntity.attackDamage(stack: ItemStack = mainHandStack) =
        (stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
            .modifiers.find { it.attribute == EntityAttributes.ATTACK_DAMAGE }?.modifier?.value ?: 0.0) +
                getAttributeValue(EntityAttributes.ATTACK_DAMAGE)

    /**
     * Returns the attack speed for the given [stack], the value is affected by potion effects
     *
     * The value represents the number of attacks-per-tick
     */
    fun SafeContext.attackSpeed(entity: LivingEntity = player, stack: ItemStack = entity.mainHandStack) =
        entity.attackSpeed(stack)

    /**
     * Returns the attack speed for the given [stack], the value is affected by potion effects
     *
     * The value represents the number of attacks-per-tick
     */
    fun LivingEntity.attackSpeed(stack: ItemStack = mainHandStack) =
        (stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
            .modifiers.find { it.attribute == EntityAttributes.ATTACK_SPEED }?.modifier?.value ?: 0.0) +
                getAttributeValue(EntityAttributes.ATTACK_SPEED)

    /**
     * Returns the mining speed for the given [stack], the value is affected by potion effects and enchantments
     */
    fun SafeContext.miningSpeed(entity: LivingEntity = player, stack: ItemStack = entity.mainHandStack) =
        entity.miningSpeed(stack)

    /**
     * Returns the mining speed for the given [stack], the value is affected by potion effects and enchantments
     */
    fun LivingEntity.miningSpeed(stack: ItemStack = mainHandStack) =
        (stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
            .modifiers.find {
                it.attribute == EntityAttributes.MINING_EFFICIENCY ||
                        it.attribute == EntityAttributes.SUBMERGED_MINING_SPEED
            }?.modifier?.value ?: 0.0) +
                if (isSubmergedInWater) getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED)
                else getAttributeValue(EntityAttributes.MINING_EFFICIENCY)

    val ItemStack.spaceLeft get() = maxCount - count
    val ItemStack.hasSpace get() = spaceLeft > 0
    val List<ItemStack>.spaceLeft get() = sumOf { it.spaceLeft }
    val List<ItemStack>.empty: Int get() = count { it.isEmpty }
    val List<ItemStack>.count: Int get() = sumOf { it.count }
    val List<ItemStack>.copy: List<ItemStack> get() = map { it.copy() }

    val List<ItemStack>.compressed: List<ItemStack>
        get() =
            fold(mutableListOf()) { acc, itemStack ->
                acc merge itemStack
                acc
            }

    infix fun List<ItemStack>.merge(other: ItemStack): List<ItemStack> {
        return flatMap {
            it merge other
        }
    }

    infix fun ItemStack.merge(other: ItemStack): List<ItemStack> {
        if (!isStackable || !other.isStackable) {
            return listOf(this, other)
        }

        val newCount = count + other.count
        if (newCount <= maxCount) {
            return listOf(copyWithCount(newCount))
        }
        val remainder = newCount - maxCount
        return listOf(copyWithCount(maxCount), copyWithCount(remainder))
    }

    // TODO: Find another way
    val ItemStack.shulkerBoxContents: List<ItemStack> by cacheable { stack ->
        /*BlockItem.getBlockEntityNbt(stack)?.takeIf {
            it.contains("Items", NbtElement.LIST_TYPE.toInt())
        }?.let {
            val list = DefaultedList.ofSize(27, ItemStack.EMPTY)
            Inventories.readNbt(it, list)
            list
        } ?: */emptyList()
    }

    /**
     * Checks if the given item stacks are equal, including the item count and NBT.
     *
     * @param other The other item stack to compare with.
     * @return `true` if the item stacks are equal, `false` otherwise.
     * @see ItemStack.areItemsEqual Checks if the items in two item stacks are equal.
     * @see ItemStack.canCombine Checks if two item stacks can be combined into one stack.
     */
    fun ItemStack?.equal(other: ItemStack?) = ItemStack.areEqual(this, other)
}
