
package com.minato.context

import com.minato.config.blocks.BreakConfig
import com.minato.config.blocks.BuildConfig
import com.minato.config.blocks.EatConfig
import com.minato.config.blocks.HotbarConfig
import com.minato.config.blocks.InteractConfig
import com.minato.config.blocks.InventoryConfig
import com.minato.config.blocks.RotationConfig

interface Automated {
	val buildConfig: BuildConfig
	val breakConfig: BreakConfig
	val interactConfig: InteractConfig
	val rotationConfig: RotationConfig
	val inventoryConfig: InventoryConfig
	val hotbarConfig: HotbarConfig
	val eatConfig: EatConfig
}