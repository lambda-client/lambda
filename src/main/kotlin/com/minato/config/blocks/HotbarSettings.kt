
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.event.events.TickEvent
import com.minato.event.events.TickEvent.Companion.ALL_STAGES

class HotbarSettings(override val c: Config) : HotbarConfig, ConfigBlock {
    override val swapMode by c.setting("Swap Mode", HotbarConfig.SwapMode.Temporary)
    override val keepTicks by c.setting("Keep Ticks", 1, 0..20, 1, "The number of ticks to keep the current hotbar selection active", " ticks") { swapMode == HotbarConfig.SwapMode.Temporary }
    override val swapDelay by c.setting("Swap Delay", 0, 0..3, 1, "The number of ticks delay before allowing another hotbar selection swap", " ticks")
    override val swapsPerTick by c.setting("Swaps Per Tick", 3, 1..10, 1, "The number of hotbar selection swaps that can take place each tick") { swapDelay <= 0 }
    override val swapPause by c.setting("Swap Pause", 0, 0..20, 1, "The delay in ticks to pause actions after switching to the slot", " ticks")
    override val tickStageMask by c.setting("Hotbar Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which hotbar actions are performed", displayClassName = true)
}