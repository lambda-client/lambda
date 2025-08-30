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

import com.lambda.util.collections.Cacheable.Companion.cacheable
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.AttributeModifiersComponent
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.registry.entry.RegistryEntry

object ItemStackUtils {
    // FixMe: Change this fucking retarded stuff when mojang wake up from their coma and realize they fucked this shit up
    //  - The client and the server entity attributes are not synced,
    //  - Enchantments do not change attributes,
    //  - All enchantment utils are bound to the server

    fun LivingEntity.attributeBaseValue(attribute: RegistryEntry<EntityAttribute>) =
        if (attributes.hasAttribute(attribute)) getAttributeBaseValue(attribute) else 0.0

    fun AttributeModifiersComponent.filteredApply(base: Double, slot: EquipmentSlot, vararg attributes: RegistryEntry<EntityAttribute>): Double =
        modifiers.filter { attributes.contains(it.attribute) && it.slot.matches(slot) }
            .foldRight(base) { entry, acc ->
                val v = entry.modifier.value

                acc + when (entry.modifier.operation) {
                    EntityAttributeModifier.Operation.ADD_VALUE -> v
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE -> v * base
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL -> v * acc
                }
            }


    /**
     * Returns the attack damage for the given [stack], the value is affected by potion effects and enchantments
     */
    fun LivingEntity.attackDamage(stack: ItemStack = mainHandStack) =
        stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
            .filteredApply(attributeBaseValue(EntityAttributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND, EntityAttributes.ATTACK_DAMAGE)

    /**
     * Returns the attack speed for the given [stack], the value is affected by potion effects
     *
     * The value represents the number of attacks-per-tick
     *
     * To get the number of ticks per attack do the following:
     * ```
     * ticks = 1 / speed * 20
     * ```
     */
    fun LivingEntity.attackSpeed(stack: ItemStack = mainHandStack) =
        stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
            .filteredApply(attributeBaseValue(EntityAttributes.ATTACK_SPEED), EquipmentSlot.MAINHAND, EntityAttributes.ATTACK_SPEED)

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
