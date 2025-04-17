/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.material.transfer.transaction

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.transfer.InventoryTransaction
import net.minecraft.util.Hand

class DropItemInHandTransaction @Ta5kBuilder constructor(
    private val entireStack: Boolean = false,
) : InventoryTransaction() {
    override val name: String get() = "Dropping ${if (entireStack) "stack" else "item"} in hand"

    init {
        listen<TickEvent.Pre> {
            if (!player.isSpectator && player.dropSelectedItem(entireStack)) {
                player.swingHand(Hand.MAIN_HAND)
            }
            finish()
        }
    }
}