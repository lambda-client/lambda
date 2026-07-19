
package com.minato.interaction.managers.interacting

import com.minato.config.blocks.InteractConfig
import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.context.BuildContext
import com.minato.interaction.construction.simulation.context.InteractContext
import com.minato.interaction.managers.ActionInfo
import net.minecraft.util.math.BlockPos

data class InteractInfo(
	override val context: InteractContext,
	override val pendingInteractionsList: MutableCollection<BuildContext>,
	val onPlace: (SafeContext.(BlockPos) -> Unit)?,
	val interactConfig: InteractConfig
) : ActionInfo