package com.lambda.client.module.modules.player

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.mixin.world.MixinItemBlock

/**
 * @see MixinItemBlock.ignoreSetBlockState
 */
object NoGhostBlocks : Module(
    name = "NoGhostBlocks",
    alias = arrayOf("NoGlitchBlocks"),
    description = "Syncs block interactions for strict environments",
    category = Category.PLAYER
)