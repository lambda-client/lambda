package com.lambda.pathing.world

import com.lambda.pathing.core.CellBox

interface RememberedTerrain {
	fun remembers(box: CellBox): Boolean

	fun forget(): Boolean
}
