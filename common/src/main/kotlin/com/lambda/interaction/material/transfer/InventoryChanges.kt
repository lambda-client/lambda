package com.lambda.interaction.material.transfer

import com.lambda.interaction.material.StackSelection
import com.lambda.util.item.ItemStackUtils.equal
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

/**
 * A class representing changes in an inventory's state over time. It acts as a tracker for
 * detecting, storing, and merging differences between the original and updated states of
 * inventory slots. This class extends a map-like structure, where the key is the slot index
 * and the value is a list of pairs representing the original and updated states of an inventory slot.
 *
 * Example:
 * ```
 * #0: 64 obsidian -> 0 air, 0 air -> 64 obsidian
 * #36: 12 observer -> 11 observer
 * ```
 * - Where `#0` is the slot id, first it was emptied and then got `64 obsidian` again
 * - Where `#36` is the slot id, that was reduced by `1 observer`
 *
 * @property slots A list of `Slot` objects representing the current inventory slots being tracked.
 * Defaults to an empty list.
 */
class InventoryChanges(
    private var slots: List<Slot>,
) : MutableMap<Int, MutableList<Pair<ItemStack, ItemStack>>> by HashMap() {
    private val originalStacks = slots.map { it.stack.copy() }

    /**
     * Detect and store changes directly in the map.
     */
    fun detectChanges() {
        require(slots.isNotEmpty()) { "Cannot detect changes on an empty slots list" }
        slots.forEachIndexed { index, slot ->
            val originalStack = originalStacks[index]
            val updatedStack = slot.stack
            if (!originalStack.equal(updatedStack)) {
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
        require(slots.isNotEmpty()) { "Cannot merge changes to an empty slots list" }
        other.forEach { (key, value) ->
            getOrPut(key) { mutableListOf() }.addAll(value)
        }
    }

    /**
     * Evaluates if the current inventory changes fulfill the given selection requirement.
     *
     * @param to A list of `Slot` objects to evaluate for the selection criteria.
     * @param selection A `StackSelection` object that specifies the selection criteria, including the
     *                  target items and their required count.
     * @return `true` if the total count of matching items across the filtered slots meets or exceeds
     *         the required count specified in the `StackSelection`; `false` otherwise.
     */
    fun fulfillsSelection(to: List<Slot>, selection: StackSelection): Boolean {
        require(slots.isNotEmpty()) { "Cannot evaluate selection on an empty slots list" }
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