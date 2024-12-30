package com.lambda.interaction.material.transfer

import com.lambda.context.SafeContext
import com.lambda.interaction.material.StackSelection
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

class InventoryChanges(
    ctx: SafeContext
) : MutableMap<Int, MutableList<Pair<ItemStack, ItemStack>>> by HashMap() {

    private var slots = ctx.player.currentScreenHandler.slots
    private val originalStacks = slots.map { it.stack.copy() } // Snapshot of the initial state

    /**
     * Detect and store changes directly in the map.
     */
    fun detectChanges() {
        slots.forEachIndexed { index, slot ->
            val originalStack = originalStacks[index]
            val updatedStack = slot.stack
            if (!ItemStack.areEqual(originalStack, updatedStack)) {
                getOrPut(index) { mutableListOf() }.add(originalStack to updatedStack.copy())
            }
        }
    }

    /**
     * Create a new `InventoryChanges` object that merges this changes object with another one.
     *
     * @param other Another `InventoryChanges` instance to merge with.
     * @return A new `InventoryChanges` instance containing merged entries.
     */
    infix fun merge(other: InventoryChanges) {
        other.forEach { (key, value) ->
            getOrPut(key) { mutableListOf() }.addAll(value)
        }
    }

    fun fulfillsSelection(to: List<Slot>, selection: StackSelection): Boolean {
        val targetSlots = selection.filterSlots(to).map { it.id }
        return filter { it.key in targetSlots }.entries.sumOf { (_, changes) ->
            changes.lastOrNull()?.second?.count ?: 0
        } >= selection.count
    }

    override fun toString() =
        if (entries.isEmpty()) {
            "No changes detected"
        } else {
            entries.joinToString("\n") { key ->
                "#${key.key}: ${key.value.joinToString { "${it.first} -> ${it.second}" }}"
            }
        }
}