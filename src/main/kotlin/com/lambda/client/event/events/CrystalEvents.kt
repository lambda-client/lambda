package com.lambda.client.event.events

import com.lambda.client.event.Event
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.module.modules.combat.BedAura

class CrystalEvent {
    /**
     * Sent when the client should place a crystal
     * @param destination The BlockPos
     *
     * NOTE: This can also use used by the [BedAura] module
     */
    class PlaceEvent(val destination: CombatManager.CrystalPlaceInfo) : Event
    /**
     * Sent when the client should break a crystal
     * @param target The CombatManager.Crystal
     *
     * NOTE: This can also use used by the [BedAura] module
     */
    class BreakEvent(val target: CombatManager.Crystal) : Event
}