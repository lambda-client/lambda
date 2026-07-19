
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.event.events.TickEvent
import com.minato.event.events.TickEvent.Companion.ALL_STAGES

class InteractSettings(override val c: Config) : InteractConfig, ConfigBlock {
    override val rotate by c.setting("Rotate For Interact", true, "Rotate towards block while placing")
    override val airPlace by c.setting("Air Place", InteractConfig.AirPlaceMode.Grim, "Allows for placing blocks without adjacent faces")
    override val axisRotateSetting by c.setting("Axis Rotate", true, "Overrides the Rotate For Place setting and rotates the player on each axis to air place rotational blocks") { airPlace.isEnabled }
    override val sorter by c.setting("Interaction Sorter", ActionConfig.SortMode.Tool, "The order in which placements are performed")
    override val tickStageMask by c.setting("Interaction Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which place actions are performed", displayClassName = true)
    override val interactConfirmationMode by c.setting("Interact Confirmation", InteractConfig.InteractConfirmationMode.PlaceThenAwait, "Wait for block placement confirmation")
    override val interactDelay by c.setting("Interact Delay", 0, 0..3, 1, "Tick delay between interacting with another block")
    override val interactionsPerTick by c.setting("Interactions Per Tick", 9, 1..30, 1, "Maximum instant block places per tick")
    override val swing by c.setting("Swing On Interact", true, "Swings the players hand when placing")
    override val swingType by c.setting("Interact Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { swing }
    override val sounds by c.setting("Place Sounds", true, "Plays the placing sounds")
}