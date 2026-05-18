/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.world

import com.lambda.config.ConfigEditor.hideAllBlocksExcept
import com.lambda.config.Group
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.KeybindSetting.Companion.onPress
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.SoundHandler.playSound
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.player.RotationUtils.lookAtEntity
import com.lambda.util.world.closestEntity
import net.minecraft.block.Blocks
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object AutoVillagerCycle : Module(
	name = "AutoVillagerCycle",
	description = "Automatically cycles librarian villagers with lecterns until a desired enchanted book is found",
	tag = ModuleTag.World
) {
	private val allEnchantments = ArrayList<String>()

	private val lecternPos by setting("Lectern Pos", BlockPos.ORIGIN, "Position where the lectern should be placed/broken")
	private val logFoundBooks by setting("Log Found Books", true, "Log all enchanted books found during cycling")
	private val interactDelay by setting("Interact Delay", 20, 1..40, 1, "Ticks to wait before interacting with the villager", " ticks")
	private val breakDelay by setting("Break Delay", 5, 1..20, 1, "Ticks to wait after breaking the lectern", " ticks")
	private val searchRange by setting("Search Range", 5.0, 1.0..10.0, 0.5, "Range to search for nearby villagers", " blocks")
	private val startCyclingBind by setting("Start Cycling", Bind.EMPTY, "Press to start/stop cycling")
		.onPress {
			if (cycleState != CycleState.Idle) {
				info("Stopped villager cycling.")
				switchState(CycleState.Idle)
			} else {
				info("Started villager cycling.")
				buildTask?.cancel()
				buildTask = null
				switchState(CycleState.PlaceLectern)
			}
		}

	private const val EnchantmentsGroup = "Enchantments"
	@Group(EnchantmentsGroup) private val desiredEnchantments by setting("Desired Enchantments", emptySet(), allEnchantments)
	@Group(EnchantmentsGroup) private val minLevel by setting("Min Level", 1, 1..5, 1, "Minimum enchantment level to look for")

	private var cycleState = CycleState.Idle
	private var tickCounter = 0

	private var buildTask: Task<*>? = null

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllBlocksExcept(::rotationConfig, ::inventoryConfig, ::breakConfig, ::interactConfig, ::buildConfig)
			}

		onEnable {
			allEnchantments.clear()
			allEnchantments.addAll(getEnchantmentList())
			cycleState = CycleState.Idle
			tickCounter = 0
		}

		onDisable {
			cycleState = CycleState.Idle
			tickCounter = 0
			buildTask?.cancel()
			buildTask = null
		}

		listen<TickEvent.Pre> {
			tickCounter++

			if (allEnchantments.isEmpty()) {
				allEnchantments.addAll(getEnchantmentList()) // Have to load enchantments after we loaded into a world
			}

			when (cycleState) {
				CycleState.Idle -> {}
				CycleState.PlaceLectern -> handlePlaceLectern()
				CycleState.WaitLectern -> {}
				CycleState.OpenVillager -> handleOpenVillager()
				CycleState.BreakLectern -> handleBreakLectern()
				CycleState.WaitBreak -> {}
			}
		}

		listen<PacketEvent.Receive.Pre> { event ->
			if (event.packet !is SetTradeOffersS2CPacket) return@listen
			if (cycleState != CycleState.OpenVillager) return@listen

			val tradeOfferPacket = event.packet
			val trades = tradeOfferPacket.offers
			if (trades.isEmpty()) {
				logError("Villager has no trades!")
				switchState(CycleState.Idle)
				return@listen
			}

			var bookFound = false
			for (offer in trades) {
				if (offer.isDisabled) continue

				val sellItem = offer.sellItem
				if (sellItem.item != Items.ENCHANTED_BOOK) continue

				if (logFoundBooks) {
					val storedEnchantments = sellItem.get(DataComponentTypes.STORED_ENCHANTMENTS)
					val foundEnchantments = mutableListOf<String>()
					for (entry in storedEnchantments?.enchantmentEntries ?: emptyList()) {
						foundEnchantments.add(entry.key.value().description().string)
					}
					if (foundEnchantments.isNotEmpty()) {
						bookFound = true
						info("Found book(s): ${foundEnchantments.joinToString(", ")}")
					}
				}

				findDesiredEnchantment(sellItem)?.let {
					info("Found desired enchantment: ${it.description().string}!")
					playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP)
					switchState(CycleState.Idle)
					return@listen
				}
			}
			if (!bookFound && logFoundBooks) {
				info("No books found")
			}

			// No desired enchantment found, break lectern and try again
			tickCounter = 0
			switchState(CycleState.BreakLectern)
		}
	}

	private fun SafeContext.getEnchantmentList(): MutableList<String> {
		val enchantments = ArrayList<String>()
		for (foo in world.registryManager.getOrThrow(RegistryKeys.ENCHANTMENT)) {
			enchantments.add(foo.description.string)
		}
		return enchantments
	}

	private fun SafeContext.handlePlaceLectern() {
		player.closeHandledScreen()

		if (desiredEnchantments.isEmpty()) {
			logError("No desired enchantments set!")
			switchState(CycleState.Idle)
			return
		}

		if (lecternPos == BlockPos.ORIGIN) {
			logError("Lectern position is not set!")
			switchState(CycleState.Idle)
			return
		}

		val state = blockState(lecternPos)

		if (!state.isEmpty) {
			if (state.isOf(Blocks.LECTERN)) {
				switchState(CycleState.OpenVillager)
				return
			}
			logError("Block at lectern position is not air or a lectern!")
			switchState(CycleState.Idle)
			return
		}

		runSafeAutomated {
			buildTask = lecternPos.toStructure(TargetState.Block(Blocks.LECTERN))
				.toBlueprint()
				.build(finishOnDone = true)
				.finally {
					switchState(CycleState.OpenVillager)
				}
				.run()
		}
		switchState(CycleState.WaitLectern)
	}

	private fun SafeContext.handleOpenVillager() {
		if (tickCounter < interactDelay) return

		if (buildTask?.state == Task.State.Running) {
			return
		}

		// Verify lectern is still present
		val state = blockState(lecternPos)
		if (state.isEmpty) {
			tickCounter = 0
			switchState(CycleState.PlaceLectern)
			return
		}
		if (!state.isOf(Blocks.LECTERN)) {
			logError("Block at lectern position is not a lectern!")
			switchState(CycleState.Idle)
			return
		}

		val villager = closestEntity<VillagerEntity>(searchRange)
		if (villager == null) {
			logError("No villager found nearby!")
			switchState(CycleState.Idle)
			return
		}

		runSafeAutomated {
			lookAtEntity(villager)?.let {
				val done = rotationRequest {
					rotation(it.rotation)
				}.submit().done
				if (done) {
					interaction.interactEntityAtLocation(player, villager, it.hit as EntityHitResult?, Hand.MAIN_HAND)
					interaction.interactEntity(player, villager, Hand.MAIN_HAND)
					player.swingHand(Hand.MAIN_HAND)
					tickCounter = 0
				}
			}
		}
	}

	private fun SafeContext.handleBreakLectern() {
		if (player.currentScreenHandler != player.playerScreenHandler) {
			player.closeHandledScreen()
		}

		if (tickCounter < breakDelay) return

		val state = blockState(lecternPos)

		if (!state.isEmpty) {
			buildTask = runSafeAutomated {
				lecternPos.toStructure(TargetState.Empty)
					.build(finishOnDone = true)
					.finally {
						switchState(CycleState.PlaceLectern)
					}
					.run()
			}
			switchState(CycleState.WaitBreak)
			return
		}
		switchState(CycleState.PlaceLectern)
	}

	private fun findDesiredEnchantment(itemStack: ItemStack): Enchantment? {
		if (desiredEnchantments.isEmpty()) return null

		val enchantments = itemStack.get(DataComponentTypes.STORED_ENCHANTMENTS) ?: return null
		enchantments.enchantmentEntries.forEach { (entry, level) ->
			val enchantmentName = entry.value().description().string
			if (desiredEnchantments.any { it.equals(enchantmentName, ignoreCase = true) && level >= minLevel }) {
				return entry.value()
			}
		}
		return null
	}

	private fun switchState(newState: CycleState) {
		if (cycleState != newState) {
			tickCounter = 0
		}
		cycleState = newState
	}

	private enum class CycleState {
		Idle,
		PlaceLectern,
		OpenVillager,
		WaitLectern,
		BreakLectern,
		WaitBreak
	}
}