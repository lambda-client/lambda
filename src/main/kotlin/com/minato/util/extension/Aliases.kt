
package com.minato.util.extension

import com.minato.interaction.construction.verify.TargetState
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource
import net.minecraft.util.math.BlockPos

typealias CommandBuilder = LiteralArgumentBuilder<CommandSource>
typealias Structure = Map<BlockPos, TargetState>

fun Structure.move(offset: BlockPos): Structure =
    mapKeys { (pos, _) -> pos.add(offset) }

fun Structure.moveX(x: Int): Structure =
    mapKeys { (pos, _) -> pos.east(x) }

fun Structure.moveY(y: Int): Structure =
    mapKeys { (pos, _) -> pos.up(y) }

fun Structure.moveZ(z: Int): Structure =
    mapKeys { (pos, _) -> pos.south(z) }
