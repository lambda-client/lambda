package com.lambda.config.settings.complex

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting
import net.minecraft.util.math.BlockPos

class BlockPosSetting(
    override val name: String,
    defaultValue: BlockPos,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<BlockPos>(
    defaultValue,
    TypeToken.get(BlockPos::class.java).type,
    description,
    visibility
)
