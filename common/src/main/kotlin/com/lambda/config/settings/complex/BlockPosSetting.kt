package com.lambda.config.settings.complex

import com.lambda.config.AbstractSetting
import net.minecraft.util.math.BlockPos

class BlockPosSetting(
    override val name: String,
    defaultValue: BlockPos,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<BlockPos>(
    defaultValue,
    description,
    visibility
)