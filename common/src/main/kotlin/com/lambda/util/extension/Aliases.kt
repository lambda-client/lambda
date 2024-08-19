package com.lambda.util.extension

import com.lambda.interaction.construction.verify.TargetState
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource
import net.minecraft.util.math.BlockPos

typealias CommandBuilder = LiteralArgumentBuilder<CommandSource>
typealias Structure = Map<BlockPos, TargetState>
