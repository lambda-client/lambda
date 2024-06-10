package com.lambda.config.groups

import com.lambda.config.Configurable

class BuildSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : BuildConfig {
    override val breakCoolDown by c.setting("Break Cooldown", 0, 0..1000, 1, "Delay between breaking blocks", " ms", vis)
    override val placeCooldown by c.setting("Place Cooldown", 0, 0..1000, 1, "Delay between placing blocks", " ms", vis)
    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks", vis)
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)", vis)
    override val pathing by c.setting("Pathing", true, "Path to blocks", vis)
    override val breaksPerTick by c.setting("Instant Breaks Per Tick", 10, 1..30, 1, "Maximum instant block breaks per tick", "", vis)
    override val rotateForBreak by c.setting("Rotate For Break", false, "Rotate towards block while breaking", vis)
    override val swingHand by c.setting("Swing Hand", true, "Swing hand on interactions", vis)
}