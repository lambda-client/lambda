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

package com.lambda.module.modules.movement.elytrafly

import com.lambda.config.ConfigBlock
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.Muteable
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.lambda.threading.runSafe
import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket

abstract class ElytraFlyMode(
	val flyMode: FlyMode
) : Muteable, Automated by ElytraFly, ConfigBlock {
	override val isMuted get() = ElytraFly.isMuted || ElytraFly.mode != flyMode

	val onEnableListeners = mutableListOf<SafeContext.() -> Unit>()
	val onDisableListeners = mutableListOf<SafeContext.() -> Unit>()
	val onFlagListeners = mutableListOf<SafeContext.() -> Unit>()

	fun onEnable(callback: SafeContext.() -> Unit) { onEnableListeners.add(callback) }
	fun onDisable(callback: SafeContext.() -> Unit) { onDisableListeners.add(callback) }

	fun onFlag(callback: SafeContext.() -> Unit) { onFlagListeners.add(callback) }

	open fun isGliding(): Boolean? = runSafe { player.getFlag(Entity.GLIDING_FLAG_INDEX) }

	protected fun SafeContext.startFlyPacket() =
		connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
}