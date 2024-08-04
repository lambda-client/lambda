package com.lambda.core.registry

import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier

interface RegistryWrapper<T> {
    fun <T> registerForHolder(id: Identifier?, value: T): RegistryEntry<T>
}
