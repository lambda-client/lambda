
package com.minato.module.modules.combat

import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.settings.complex.Bind
import com.minato.config.settings.complex.KeybindSetting.Companion.onPress
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.EnchantmentUtils.getEnchantment
import com.minato.util.item.ItemUtils.armorSlot
import com.minato.util.player.SlotUtils.armorSlots
import com.minato.util.player.SlotUtils.hotbarAndInventorySlots
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.tag.ItemTags
import net.minecraft.screen.slot.Slot

@Suppress("unused")
object AutoArmor : Module(
	name = "AutoArmor",
	description = "Automatically equips armor",
	tag = ModuleTag.COMBAT
) {
	var elytraPriority by setting("Elytra Priority", true, "Prioritizes elytra's over other armor pieces in the chest slot")
	private val toggleElytraPriority by setting("Toggle Elytra Priority", Bind.EMPTY)
		.onPress { elytraPriority = !elytraPriority }
	private val minDurabilityPercentage by setting("Min Durability", 5, 0..100, 1, "Minimum durability percentage before being swapped for a new piece", "%")
	private val headProtection by setting("Preferred Head Protection", Protection.Protection)
	private val chestProtection by setting("Preferred Chest Protection", Protection.Protection)
	private val legProtection by setting("Preferred Leg Protection", Protection.BlastProtection)
	private val feetProtection by setting("Preferred Feet Protection", Protection.Protection)
	private val ignoreBinding by setting("Ignore Binding", true, "Ignores curse of binding armor pieces")

	private var tickedThisTick = false

	var overriddenElytraPriority: Boolean? = null

	val SORTER = compareByDescending<Slot> {
		if (it.stack.isDamageable && 1 - (it.stack.damage.toFloat() / it.stack.maxDamage) < minDurabilityPercentage.toFloat() / 100)
			-Double.MAX_VALUE
		else 0.0
	}.thenByDescending {
		if (overriddenElytraPriority ?: elytraPriority) {
			if (it.stack.item == Items.ELYTRA) 1.0
			else 0.0
		} else 0.0
	}.thenByDescending {
		it.stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, null)
			?.modifiers
			?.find { modifier -> modifier.attribute == EntityAttributes.ARMOR }
			?.modifier?.value
			?: 0.0
	}.thenByDescending {
		it.stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, null)
			?.modifiers
			?.find { modifier -> modifier.attribute == EntityAttributes.ARMOR_TOUGHNESS }
			?.modifier?.value
			?: 0.0
	}.thenByDescending {
		val stack = it.stack
		when {
			stack.isIn(ItemTags.FOOT_ARMOR) -> stack.getEnchantment(feetProtection.enchant)
			stack.isIn(ItemTags.LEG_ARMOR) -> stack.getEnchantment(legProtection.enchant)
			stack.isIn(ItemTags.CHEST_ARMOR) -> stack.getEnchantment(chestProtection.enchant)
			else -> stack.getEnchantment(headProtection.enchant)
		}
	}.thenByDescending { slot ->
		Protection.entries.fold(0) { acc, protection ->
			acc + slot.stack.getEnchantment(protection.enchant)
		}
	}.thenByDescending { slot ->
		slot.stack.getEnchantment(Enchantments.UNBREAKING) +
				slot.stack.getEnchantment(Enchantments.MENDING)
	}

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::inventoryConfig)
			}

		listen<TickEvent.Pre> {
			if (!tickedThisTick) tick()
		}

		listen<TickEvent.Post>({ Int.MIN_VALUE }) {
			tickedThisTick = false
		}
	}

	fun SafeContext.tick() {
		tickedThisTick = true
		val armorSlots = player.armorSlots

		val swappable = player.hotbarAndInventorySlots
			.filter { it.stack.isEquipable && (!ignoreBinding || it.stack.getEnchantment(Enchantments.BINDING_CURSE) <= 0) }
			.sortedWith(SORTER)
			.distinctBy { it.stack.armorSlot }

		val swaps = mutableListOf<Pair<Slot, Slot>>()
		armorSlots.forEach { equipped ->
			val new = swappable.find { new ->
				equipped.canInsert(new.stack) && SORTER.compare(equipped, new) > 0
			} ?: return@forEach

			swaps.add(Pair(new, equipped))
		}

		if (swaps.isEmpty()) return
		inventoryRequest {
			swaps.forEach {
				moveSlot(it.first.id, it.second.id)
				if (!it.second.stack.isEmpty) pickup(it.first.id)
			}
		}.submit()
	}

	context(safeContext: SafeContext)
	private val ItemStack.isEquipable
		get() = safeContext.player.armorSlots.any { it.canInsert(this) }

	private enum class Protection(val enchant: RegistryKey<Enchantment>) {
		Protection(Enchantments.PROTECTION),
		BlastProtection(Enchantments.BLAST_PROTECTION),
		ProjectileProtection(Enchantments.PROJECTILE_PROTECTION),
		FireProtection(Enchantments.FIRE_PROTECTION);
	}
}