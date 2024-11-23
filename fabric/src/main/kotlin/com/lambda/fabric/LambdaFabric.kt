/*
 * Copyright 2024 Lambda
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

package com.lambda.fabric

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.AgnosticRegistries
import net.fabricmc.api.ClientModInitializer
import net.minecraft.registry.Registries
import net.minecraft.registry.SimpleRegistry

object LambdaFabric : ClientModInitializer {
    override fun onInitializeClient() {
        Lambda.initialize {
            Registries.REGISTRIES.forEach(AgnosticRegistries::dump)
            LOG.info("$MOD_NAME Fabric $VERSION initialized.")
        }
    }
}
