
package com.minato.util

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
