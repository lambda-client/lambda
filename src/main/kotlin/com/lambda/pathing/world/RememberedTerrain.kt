package com.lambda.pathing.world

import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance

interface RememberedTerrain {
	fun remembers(box: CellBox): Boolean

	fun recall(around: Stance): List<CellBox>

	fun forget(): Boolean
}
