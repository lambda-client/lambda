
package com.minato.interaction.managers

import com.minato.interaction.construction.simulation.context.BuildContext

/**
 * A simple interface to provide a basic object to hold key information that managers might need if information
 * must persist longer than the request.
 */
interface ActionInfo {
	val context: BuildContext
	val pendingInteractionsList: MutableCollection<BuildContext>
}
