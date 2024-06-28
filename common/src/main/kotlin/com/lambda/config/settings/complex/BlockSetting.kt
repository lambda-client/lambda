package com.lambda.config.settings.complex

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting
import net.minecraft.block.Block

class BlockSetting(
    override val name: String,
    defaultValue: Block,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Block>(
    defaultValue,
    TypeToken.get(Block::class.java).type,
    description,
    visibility
)
