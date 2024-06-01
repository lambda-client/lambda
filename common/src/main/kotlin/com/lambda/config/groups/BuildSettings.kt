package com.lambda.config.groups

import com.lambda.config.Configurable

class BuildSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : BuildConfig {
    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks", vis)
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)", vis)
    override val pathing by c.setting("Pathing", true, "Path to blocks", vis)
    override val interactLimit by c.setting("Interaction Limit", 15, 1..100, 1, "Max interactions per tick", " i/t", vis)
    override val breakInstantAtOnce by c.setting("Break Instant At Once", true, "Break all instant blocks at once", vis)
    override val rotateForBreak by c.setting("Rotate For Break", false, "Rotate towards block while breaking", vis)
    override val swingHand by c.setting("Swing Hand", true, "Swing hand on interactions", vis)
    override val particlesOnBreak by c.setting("Particles On Break", true, "Show particles when breaking blocks", vis)
}