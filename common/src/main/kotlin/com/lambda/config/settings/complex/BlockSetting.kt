package com.lambda.config.settings.complex

import com.lambda.config.AbstractSetting
import net.minecraft.block.Block

class BlockSetting(
    override val name: String,
    defaultValue: Block,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Block>(
    defaultValue,
    description,
    visibility
)