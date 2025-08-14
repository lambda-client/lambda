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

package com.lambda.util

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry

object EnchantmentUtils {
    /**
     * Returns the list of enchantments from a given [ItemStack]
     */
    val ItemStack.enchantments: ItemEnchantmentsComponent
        get() = getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)

    /**
     * Returns whether the given [ItemStack] has enchantments
     */
    val ItemStack.hasEnchantments: Boolean
        get() = !getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).isEmpty
                || getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).isEmpty

    /**
     * Returns the given enchantment level from a [net.minecraft.item.ItemStack]
     */
    fun ItemStack.getEnchantment(key: RegistryKey<Enchantment>) =
        getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
            .enchantmentEntries.find { it.key?.matchesKey(key) == true }
            ?.intValue
            ?: 0


    /**
     * Iterates over all the enchantments for the given [ItemStack]
     */
    fun <T> ItemStack.forEachEnchantment(block: (RegistryEntry<Enchantment>, Int) -> T) =
        enchantments.enchantmentEntries.asSequence()
            .map { block(it.key, it.intValue) }

    /**
     * Iterates over all the enchantments of the given [net.minecraft.entity.LivingEntity]'s [EquipmentSlot]
     */
    fun <T> LivingEntity.forEachSlot(
        vararg slots: EquipmentSlot,
        block: (entry: RegistryEntry<Enchantment>, level: Int) -> T
    ) =
        slots.flatMap { getEquippedStack(it).forEachEnchantment(block) }
}
