
package com.minato.util.item

import com.minato.context.SafeContext
import com.minato.util.collections.Cacheable.Companion.cacheable
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.AttributeModifiersComponent
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.screen.slot.Slot

object ItemStackUtils {
    // Note: Client and server entity attributes are not synced; enchantments do not change attributes.
    // All enchantment utils are bound to the server.

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
    val List<Slot>.spaceLeft @JvmName("slotSpaceLeft") get() = sumOf { it.stack.spaceLeft }
    val List<ItemStack>.empty: Int get() = count { it.isEmpty }
    val List<Slot>.empty: Int @JvmName("slotEmpty") get() = count { it.stack.isEmpty }
    val List<ItemStack>.count: Int get() = if (isEmpty()) -1 else sumOf { it.count }
    val List<Slot>.count: Int @JvmName("slotCount") get() = if (isEmpty()) -1 else sumOf { it.stack.count }
    val List<ItemStack>.copy: List<ItemStack> get() = map { it.copy() }

    context(safeContext: SafeContext)
    val ItemStack.slotId get() = safeContext.player.currentScreenHandler.slots.find { it.stack === this }?.id ?: -1
	context(safeContext: SafeContext)
	val ItemStack.inventoryIndex get() = safeContext.player.inventory.getSlotWithStack(this)
	context(safeContext: SafeContext)
	val ItemStack.inventoryIndexOrSelected get() = inventoryIndex.let { if (it == -1) safeContext.player.inventory.selectedSlot else it }

    val List<ItemStack>.compressed: List<ItemStack>
        get() =
            fold(mutableListOf()) { acc, itemStack ->
                acc merge itemStack
                acc
            }

    infix fun List<ItemStack>.merge(other: ItemStack): List<ItemStack> = flatMap { it merge other }

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

    val ItemStack.shulkerBoxContents: List<ItemStack> by cacheable { stack ->
        stack.components.get(DataComponentTypes.CONTAINER)?.stream()?.toList() ?: emptyList()
    }

    val ItemStack.bundleContents: List<ItemStack> by cacheable { stack ->
        stack.components.get(DataComponentTypes.BUNDLE_CONTENTS)?.stream()?.toList() ?: emptyList()
    }

    /**
     * Checks if the given item stacks are equal, including the item count and NBT.
     */
    fun ItemStack?.equal(other: ItemStack?) = ItemStack.areEqual(this, other)
}
