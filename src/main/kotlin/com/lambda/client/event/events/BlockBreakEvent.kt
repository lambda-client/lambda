package com.lambda.client.event.events

import net.minecraft.util.math.BlockPos
import org.kamiblue.client.event.Event

class BlockBreakEvent(val breakerID: Int, val position: BlockPos, val progress: Int) : Event