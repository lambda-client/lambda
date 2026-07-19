
package com.minato.interaction.handlers.breaking

import com.minato.interaction.managers.breaking.BreakInfo

sealed class RebreakResult {
	data object Ignored : RebreakResult()

	data object Rebroke : RebreakResult()

	class StillBreaking(
		val breakInfo: BreakInfo
	) : RebreakResult()
}