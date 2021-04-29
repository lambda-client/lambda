package com.lambda.client.event.events

import com.lambda.client.event.Event
import net.minecraft.util.math.BlockPos

class BlockBreakEvent(val breakerID: Int, val position: BlockPos, val progress: Int) : Event