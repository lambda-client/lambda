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
    val ItemStack.enchantments: ItemEnchantmentsComponent
        get() = getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)

    /**
     * Returns whether the given [ItemStack] has enchantments, including enchantments on books
     */
    val ItemStack.hasEnchantments: Boolean
        get() = !getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).isEmpty
                || !getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).isEmpty

    fun ItemStack.getEnchantment(key: RegistryKey<Enchantment>) =
        enchantments.enchantmentEntries.find { it.key?.matchesKey(key) == true }?.intValue ?: 0

    fun ItemStack.getEnchantment(entry: RegistryEntry<Enchantment>) =
        enchantments.enchantmentEntries.find { it == entry }?.intValue ?: 0

    fun <T> ItemStack.forEachEnchantment(block: (RegistryEntry<Enchantment>, Int) -> T) =
        enchantments.enchantmentEntries.map { block(it.key, it.intValue) }

    fun <T> LivingEntity.forEachSlot(
        vararg slots: EquipmentSlot,
        block: (entry: RegistryEntry<Enchantment>, level: Int) -> T
    ) = slots.flatMap { getEquippedStack(it).forEachEnchantment(block) }
}
