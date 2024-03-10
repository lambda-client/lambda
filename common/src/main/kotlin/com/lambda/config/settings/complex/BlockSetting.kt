package com.lambda.config.settings.complex

import com.lambda.config.AbstractSetting
import net.minecraft.block.Block

class BlockSetting(
    override val name: String,
    defaultValue: Block,
    visibility: () -> Boolean,
    description: String,
) : AbstractSetting<Block>(
    defaultValue,
    visibility,
    description
)