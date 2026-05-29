/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.config.settings.collections

import com.lambda.Lambda.typeFactory
import com.lambda.config.Setting
import com.lambda.config.serializers.BlockSerializer
import com.lambda.gui.dsl.ImGuiBuilder
import net.minecraft.block.Block

class BlockCollectionSetting(
	immutableCollection: Collection<Block>,
	defaultValue: MutableCollection<Block>,
) : CollectionSetting<Block>(
	defaultValue,
	immutableCollection,
	typeFactory.constructCollectionType(MutableCollection::class.java, Block::class.java),
	serialize = true,
) {
	context(_: Setting<*, MutableCollection<Block>>)
	override fun ImGuiBuilder.buildLayout() = buildDualPane("block") { BlockSerializer.stringify(it) }
}