package com.lambda.config.groups

import com.lambda.config.Configurable

class BuildSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : BuildConfig {
    enum class Page {
        GENERAL, BREAK, PLACE
    }

    private val page by c.setting("Build Page", Page.GENERAL, "Current page", vis)

    override val pathing by c.setting("Pathing", true, "Path to blocks") { vis() && page == Page.GENERAL }

    override val breakCoolDown by c.setting("Break Cooldown", 0, 0..1000, 1, "Delay between breaking blocks", " ms") { vis() && page == Page.BREAK }
    override val breakConfirmation by c.setting("Break Confirmation", false, "Wait for block break confirmation") { vis() && page == Page.BREAK }
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)") { vis() && page == Page.BREAK }
    override val breaksPerTick by c.setting("Instant Breaks Per Tick", 10, 1..30, 1, "Maximum instant block breaks per tick") { vis() && page == Page.BREAK }
    override val rotateForBreak by c.setting("Rotate For Break", false, "Rotate towards block while breaking") { vis() && page == Page.BREAK }

    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks") { vis() && page == Page.BREAK }
    override val placeCooldown by c.setting("Place Cooldown", 0, 0..1000, 1, "Delay between placing blocks", " ms") { vis() && page == Page.PLACE }

    override val placeConfirmation by c.setting("Place Confirmation", true, "Wait for block placement confirmation") { vis() && page == Page.PLACE }

    override val rotateForPlace by c.setting("Rotate For Place", true, "Rotate towards block while placing") { vis() && page == Page.PLACE }
}
