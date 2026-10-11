package com.lambda.pathing.world.store

import com.lambda.util.FolderRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.util.WorldSavePath
import net.minecraft.world.World

object TerrainStores {
	private var open: TerrainStore? = null

	fun of(world: World): TerrainStore {
		val dimension = world.registryKey.value
		val directory = FolderRegistry.cache.resolve("terrain").resolve(worldName()).resolve(dimension.namespace).resolve(dimension.path)
		open?.takeIf { it.directory == directory }?.let { return it }
		close()
		return TerrainStore(directory).also { open = it }
	}

	fun close() {
		open?.close()
		open = null
	}

	private fun worldName(): String {
		val client = MinecraftClient.getInstance()
		val server = client.server
		val name = when {
			server != null -> "${server.getSavePath(WorldSavePath.ROOT).normalize().fileName}-%08x".format(server.overworld.seed.hashCode())
			else -> client.currentServerEntry?.address ?: "unknown"
		}
		return name.lowercase().replace(UNSAFE, "_")
	}

	private val UNSAFE = Regex("[^a-z0-9._-]")
}
