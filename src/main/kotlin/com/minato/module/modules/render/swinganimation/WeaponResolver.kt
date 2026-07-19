
package com.minato.module.modules.render.swinganimation

import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.tag.ItemTags
import java.util.function.Predicate

/**
 * Resolve [WeaponType] từ [ItemStack] đang cầm.
 * Cho phép đăng ký thêm vũ khí modded qua [register] mà không cần sửa code module.
 *
 * Sử dụng Items constants và ItemTags thay vì class type checks
 * để tương thích với Yarn mappings 1.21.
 */
object WeaponResolver {
    private val customWeapons = mutableMapOf<Predicate<ItemStack>, WeaponType>()

    /**
     * Đăng ký vũ khí custom từ modded items.
     * Example: `WeaponResolver.register({ it.isIn(ItemTags.create("mod:spears")) }, WeaponType.SPEAR)`
     */
    fun register(predicate: Predicate<ItemStack>, type: WeaponType) {
        customWeapons[predicate] = type
    }

    /**
     * Resolve weapon type từ item stack.
     * Kiểm tra custom weapons trước, sau đó fallback về vanilla logic.
     */
    fun resolve(item: ItemStack): WeaponType {
        if (item.isEmpty) return WeaponType.FIST

        // Check custom registered weapons first
        customWeapons.entries.forEach { (predicate, type) ->
            if (predicate.test(item)) return type
        }

        val i = item.item

        // Direct item comparison for weapons that have unique Items constants
        when (i) {
            Items.MACE -> return WeaponType.MACE
            Items.TRIDENT -> return WeaponType.TRIDENT
            Items.BOW -> return WeaponType.BOW
            Items.CROSSBOW -> return WeaponType.CROSSBOW
        }

        // Tag-based detection for sword/axe variants
        return when {
            item.isIn(ItemTags.SWORDS) -> WeaponType.SWORD
            item.isIn(ItemTags.AXES) -> WeaponType.AXE
            else -> WeaponType.FIST
        }
    }

    fun clearCustom() {
        customWeapons.clear()
    }
}
