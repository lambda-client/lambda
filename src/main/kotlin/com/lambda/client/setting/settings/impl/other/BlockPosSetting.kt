package com.lambda.client.setting.settings.impl.other

import com.lambda.client.setting.settings.MutableSetting
import net.minecraft.util.math.BlockPos

class BlockPosSetting(
    name: String,
    value: BlockPos,
    visibility: () -> Boolean = { true },
    description: String = ""
) : MutableSetting<BlockPos>(name, value, visibility, { _, input -> input }, description, unit = "")