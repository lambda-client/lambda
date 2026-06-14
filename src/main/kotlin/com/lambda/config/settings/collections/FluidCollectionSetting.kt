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
import com.lambda.config.Config
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.config.serializers.FluidSerializer
import com.lambda.gui.dsl.ImGuiBuilder
import net.minecraft.fluid.Fluid

class FluidCollectionSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<CollectionSetting<Fluid>, MutableCollection<Fluid>>,
	visibility: () -> Boolean,
	immutableCollection: Collection<Fluid>,
	defaultValue: MutableCollection<Fluid>,
) : CollectionSetting<Fluid>(
	name,
	description,
	config,
	layer,
	visibility,
	defaultValue,
	immutableCollection,
	typeFactory.constructCollectionType(MutableCollection::class.java, Fluid::class.java),
	serialize = true,
) {
	override fun ImGuiBuilder.buildLayout() = buildDualPane("block") { FluidSerializer.stringify(it) }
}