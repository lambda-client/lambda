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

package com.lambda.util

import com.lambda.Lambda
import com.lambda.util.ReflectionUtils.getResources
import com.mojang.serialization.Codec
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.Lifecycle
import net.fabricmc.fabric.api.resource.v1.DataResourceStore
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.command.permission.LeveledPermissionPredicate
import net.minecraft.command.permission.PermissionPredicate
import net.minecraft.entity.Entity
import net.minecraft.fluid.FluidState
import net.minecraft.item.ItemStack
import net.minecraft.loot.LootTable
import net.minecraft.loot.condition.LootCondition
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.loot.context.LootWorldContext
import net.minecraft.loot.function.LootFunctionTypes
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.ReloadableRegistries
import net.minecraft.registry.SimpleRegistry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.entry.RegistryEntryInfo
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.scoreboard.ServerScoreboard
import net.minecraft.server.MinecraftServer
import net.minecraft.server.PlayerConfigEntry
import net.minecraft.server.ServerTask
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.SystemDetails
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import net.minecraft.util.math.random.RandomSequencesState
import net.minecraft.util.profiler.log.DebugSampleLog
import net.minecraft.world.SaveProperties
import net.minecraft.world.World
import net.minecraft.world.biome.Biome
import net.minecraft.world.rule.GameRules
import sun.misc.Unsafe
import tools.jackson.databind.JsonNode
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.Executor
import java.util.jar.JarFile
import java.util.stream.Stream
import com.lambda.config.JsonOps as JacksonJsonOps

class DummyLootServerWorld private constructor() : ServerWorld(
	null,
	Executor { },
	null,
	null,
	OVERWORLD,
	null,
	false,
	0L,
	emptyList(),
	false,
	null
) {
	private var clientWorld: ClientWorld? = null
	private var lootServer: MinecraftServer? = null
	private var emptyScoreboard: ServerScoreboard? = null

	override fun getServer(): MinecraftServer = checkNotNull(lootServer)

	override fun getEnabledFeatures(): FeatureSet = delegate.enabledFeatures
	override fun getScoreboard(): ServerScoreboard = checkNotNull(emptyScoreboard)
	override fun getRegistryManager(): DynamicRegistryManager = delegate.registryManager
	override fun getBlockState(pos: BlockPos): BlockState = delegate.getBlockState(pos)
	override fun getBlockEntity(pos: BlockPos): BlockEntity? = delegate.getBlockEntity(pos)
	override fun getFluidState(pos: BlockPos): FluidState? = delegate.getFluidState(pos)
	override fun isPosLoaded(pos: BlockPos): Boolean = delegate.isPosLoaded(pos)
	override fun getTimeOfDay(): Long = delegate.timeOfDay
	override fun isRaining(): Boolean = delegate.isRaining
	override fun isThundering(): Boolean = delegate.isThundering
	override fun getRandom(): Random = delegate.random
	override fun getBiome(pos: BlockPos): RegistryEntry<Biome> = delegate.getBiome(pos)

	private val delegate: ClientWorld
		get() = checkNotNull(clientWorld)

	fun createBlockLootContext(
		state: BlockState,
		pos: BlockPos,
		tool: ItemStack,
		entity: Entity? = null,
		blockEntity: BlockEntity? = null,
		luck: Float = 0f
	): LootWorldContext {
		return createBlockLootContextBuilder(pos, tool, entity, blockEntity, luck)
			.add(LootContextParameters.BLOCK_STATE, state)
			.build(LootContextTypes.BLOCK)
	}

	fun createBlockLootContextBuilder(
		pos: BlockPos,
		tool: ItemStack,
		entity: Entity? = null,
		blockEntity: BlockEntity? = null,
		luck: Float = 0f
	): LootWorldContext.Builder {
		val builder = LootWorldContext.Builder(this)
			.add(LootContextParameters.ORIGIN, pos.toCenterPos())
			.add(LootContextParameters.TOOL, tool)
			.luck(luck)

		entity?.let { builder.addOptional(LootContextParameters.THIS_ENTITY, it) }
		blockEntity?.let { builder.addOptional(LootContextParameters.BLOCK_ENTITY, it) }

		return builder
	}

	private class LootLookupServer : MinecraftServer(
		null,
		null,
		null,
		null,
		null,
		null,
		null,
		null
	) {
		private var registries: ReloadableRegistries.Lookup? = null
		private var playerLimit: Int = 0

		private var attachedSaveProperties: SaveProperties? = null

		fun attach(
			registryManager: DynamicRegistryManager,
			saveProperties: SaveProperties
		) {
			registries = ReloadableRegistries.Lookup(ClientLootRegistries.lookup(registryManager))
			this.attachedSaveProperties = saveProperties
		}

		override fun getReloadableRegistries(): ReloadableRegistries.Lookup = checkNotNull(registries)
		override fun getSaveProperties(): SaveProperties = checkNotNull(attachedSaveProperties)
		protected override fun setupServer(): Boolean = false
		protected override fun canExecute(task: ServerTask): Boolean = false
		override fun getThread(): Thread = Thread.currentThread()
		override fun getOpPermissionLevel(): LeveledPermissionPredicate = error("Loot-only server")
		override fun getFunctionPermissions(): PermissionPredicate = error("Loot-only server")
		override fun shouldBroadcastRconToOps(): Boolean = false
		protected override fun getDebugSampleLog(): DebugSampleLog = error("Loot-only server")
		override fun shouldPushTickTimeLog(): Boolean = false
		override fun addExtraSystemDetails(details: SystemDetails): SystemDetails = details
		override fun isDedicated(): Boolean = false
		override fun getRateLimit(): Int = 0
		override fun isUsingNativeTransport(): Boolean = false
		override fun isRemote(): Boolean = false
		override fun shouldBroadcastConsoleToOps(): Boolean = false
		override fun isHost(entry: PlayerConfigEntry): Boolean = false
		override fun getMaxPlayerCount(): Int = playerLimit
		override fun <T : Any> getOrThrow(key: DataResourceStore.Key<T>): T = error("Loot-only server")
	}

	companion object {
		private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let { field ->
			field.isAccessible = true
			field.get(null) as Unsafe
		}

		fun create(clientWorld: ClientWorld): DummyLootServerWorld {
			val server = unsafe.allocateInstance(LootLookupServer::class.java) as LootLookupServer
			val integratedServer = MinecraftClient.getInstance().server
			val saveProperties = integratedServer?.saveProperties
				?: clientSaveProperties(clientWorld)
			server.attach(clientWorld.registryManager, saveProperties)

			val world = unsafe.allocateInstance(DummyLootServerWorld::class.java) as DummyLootServerWorld
			world.copyWorldFieldsFrom(clientWorld)
			setInstanceField(world, ServerWorld::class.java, "server", server)
			setInstanceField(world, ServerWorld::class.java, "randomSequences", RandomSequencesState())
			world.clientWorld = clientWorld
			world.lootServer = server
			world.emptyScoreboard = ServerScoreboard(server)
			return world
		}

		private fun setInstanceField(target: Any, owner: Class<*>, name: String, value: Any) {
			val field = owner.getDeclaredField(name)
			check(field.trySetAccessible()) { "Cannot access ${owner.simpleName}.$name" }
			field.set(target, value)
		}

		private fun clientSaveProperties(clientWorld: ClientWorld): SaveProperties = Proxy.newProxyInstance(
			SaveProperties::class.java.classLoader,
			arrayOf(SaveProperties::class.java)
		) { proxy, method, arguments ->
			when (method.name) {
				"getGameRules" -> GameRules(clientWorld.enabledFeatures)
				"getDifficulty" -> clientWorld.difficulty
				"getEnabledFeatures" -> clientWorld.enabledFeatures
				"getServerBrands", "getRemovedFeatures" -> emptySet<String>()
				"getLevelName" -> "Client prediction"
				"getVersion" -> 0
				"isHardcore", "areCommandsAllowed", "isDifficultyLocked", "isModded", "isFlatWorld", "isDebugWorld" -> false
				"getLifecycle" -> Lifecycle.stable()
				"equals" -> proxy === arguments?.firstOrNull()
				"hashCode" -> System.identityHashCode(proxy)
				"toString" -> "ClientSaveProperties"
				else -> null
			}
		} as SaveProperties
	}

	private object ClientLootRegistries {
		private data class DataResource(
			val id: Identifier,
			val classpathPath: String,
			val serverResourceId: Identifier? = null
		)

		private var cachedRegistryManager: DynamicRegistryManager? = null
		private var cachedLookup: RegistryWrapper.WrapperLookup? = null

		@Synchronized
		fun lookup(registryManager: DynamicRegistryManager): RegistryWrapper.WrapperLookup {
			if (cachedRegistryManager === registryManager) return checkNotNull(cachedLookup)

			val lootTableRegistry = SimpleRegistry(RegistryKeys.LOOT_TABLE, Lifecycle.stable())
			val predicateRegistry = SimpleRegistry(RegistryKeys.PREDICATE, Lifecycle.stable())
			val itemModifierRegistry = SimpleRegistry(RegistryKeys.ITEM_MODIFIER, Lifecycle.stable())
			val lootTables = (findResources("loot_table") + blockLootTableResources()).distinctBy { it.id }
			val predicates = findResources("predicate")
			val itemModifiers = findResources("item_modifier")

			require(lootTables.isNotEmpty()) { "No loot table resources were found on the client classpath" }
			createReferences(lootTableRegistry, RegistryKeys.LOOT_TABLE, lootTables)
			createReferences(predicateRegistry, RegistryKeys.PREDICATE, predicates)
			createReferences(itemModifierRegistry, RegistryKeys.ITEM_MODIFIER, itemModifiers)

			val lookup = RegistryWrapper.WrapperLookup.of(
				Stream.concat(
					registryManager.stream(),
					Stream.of(lootTableRegistry, predicateRegistry, itemModifierRegistry)
				)
			)
			val ops = RegistryOps.of(JacksonJsonOps.UNCOMPRESSED, lookup)

			load(lootTableRegistry, RegistryKeys.LOOT_TABLE, lootTables, LootTable.CODEC, ops)
			load(predicateRegistry, RegistryKeys.PREDICATE, predicates, LootCondition.CODEC, ops)
			load(itemModifierRegistry, RegistryKeys.ITEM_MODIFIER, itemModifiers, LootFunctionTypes.CODEC, ops)

			cachedRegistryManager = registryManager
			cachedLookup = lookup
			Lambda.LOG.info(
				"Loaded {} client loot tables, {} predicates and {} item modifiers",
				lootTableRegistry.size(),
				predicateRegistry.size(),
				itemModifierRegistry.size()
			)
			return lookup
		}

		private fun findResources(registryFolder: String): List<DataResource> =
			(	serverResources(registryFolder) + getResources("**/data/**/*.json") { resource ->
				resource.path.endsWith(".json") && "/$registryFolder/" in resource.path
			}.mapNotNull { resource ->
				val dataPath = resource.path.substringAfter("data/", missingDelimiterValue = "")
				if (dataPath.isEmpty()) return@mapNotNull null

				val namespace = dataPath.substringBefore('/')
				val resourcePath = dataPath.substringAfter("/$registryFolder/", missingDelimiterValue = "")
				if (resourcePath.isEmpty()) return@mapNotNull null

				val id = Identifier.tryParse("$namespace:${resourcePath.removeSuffix(".json")}") ?: return@mapNotNull null
				DataResource(id, "data/$namespace/$registryFolder/${id.path}.json")
			} + classpathResources(registryFolder))
				.distinctBy { it.id }

		private fun serverResources(registryFolder: String): List<DataResource> {
			val resourceManager = MinecraftClient.getInstance().server?.resourceManager ?: return emptyList()
			return resourceManager.findResources(registryFolder) { path -> path.path.endsWith(".json") }
				.keys
				.mapNotNull { resourceId ->
					val path = resourceId.path
						.removePrefix("$registryFolder/")
						.removeSuffix(".json")
					val id = Identifier.tryParse("${resourceId.namespace}:$path") ?: return@mapNotNull null
					DataResource(id, "data/${id.namespace}/$registryFolder/${id.path}.json", resourceId)
				}
		}

		private fun classpathResources(registryFolder: String): List<DataResource> {
			val classLoader = DummyLootServerWorld::class.java.classLoader
			val owningJars = Registries.BLOCK.asSequence()
				.mapNotNull { block -> block.lootTableKey.orElse(null)?.value }
				.distinct()
				.mapNotNull { id ->
					val tablePath = "data/${id.namespace}/loot_table/${id.path}.json"
					classLoader.getResource(tablePath)?.let(::jarPath)
				}
				.distinct()

			return owningJars.flatMap { jarPath ->
				runCatching {
					JarFile(jarPath.toFile()).use { jar ->
						jar.entries().asSequence()
							.filter { !it.isDirectory && it.name.endsWith(".json") && "/$registryFolder/" in it.name }
							.mapNotNull { entry ->
								val dataPath = entry.name.substringAfter("data/", missingDelimiterValue = "")
								if (dataPath.isEmpty()) return@mapNotNull null

								val namespace = dataPath.substringBefore('/')
								val resourcePath = dataPath.substringAfter("/$registryFolder/", missingDelimiterValue = "")
								if (resourcePath.isEmpty()) return@mapNotNull null

								val id = Identifier.tryParse("$namespace:${resourcePath.removeSuffix(".json")}")
									?: return@mapNotNull null
								DataResource(id, "data/$namespace/$registryFolder/${id.path}.json")
							}
							.toList()
					}
				}.getOrElse { exception ->
					Lambda.LOG.debug("Couldn't enumerate loot data in {}", jarPath, exception)
					emptyList()
				}
			}.toList()
		}

		private fun jarPath(url: URL) = runCatching {
			when (val connection = url.openConnection()) {
				is JarURLConnection -> Paths.get(connection.jarFileURL.toURI())
				else -> null
			}
		}.getOrNull()

		/** ClassGraph does not enumerate Minecraft's bundled data resources, so seed block tables from the block registry. */
		private fun blockLootTableResources(): List<DataResource> =
			Registries.BLOCK.asSequence()
				.mapNotNull { block -> block.lootTableKey.orElse(null)?.value }
				.distinct()
				.map { id -> DataResource(id, "data/${id.namespace}/loot_table/${id.path}.json") }
				.toList()

		private fun <T : Any> load(
			registry: SimpleRegistry<T>,
			registryKey: RegistryKey<net.minecraft.registry.Registry<T>>,
			resources: List<DataResource>,
			codec: Codec<T>,
			ops: DynamicOps<JsonNode>
		) {
			val classLoader = DummyLootServerWorld::class.java.classLoader
			for (resource in resources) {
				val key = RegistryKey.of(registryKey, resource.id)
				val json = readJson(resource, classLoader) ?: continue

				val result = codec.parse(ops, json)
				val value = result.resultOrPartial { message ->
					Lambda.LOG.warn("Couldn't decode {} {}: {}", registryKey.value, resource.id, message)
			}.orElse(null) ?: continue
				registry.add(key, value, RegistryEntryInfo.DEFAULT)
			}
		}

		private fun readJson(resource: DataResource, classLoader: ClassLoader): JsonNode? {
			val serverResource = MinecraftClient.getInstance().server?.resourceManager
				?.getResource(resource.serverResourceId ?: resource.id)
				?.orElse(null)
			if (serverResource != null) {
				return serverResource.inputStream.use(Lambda.mapper::readTree)
			}
			return classLoader.getResourceAsStream(resource.classpathPath)
				?.use(Lambda.mapper::readTree)
		}

		private fun <T : Any> createReferences(
			registry: SimpleRegistry<T>,
			registryKey: RegistryKey<net.minecraft.registry.Registry<T>>,
			resources: List<DataResource>
		) {
			resources.forEach { resource -> registry.getOrCreateEntry(RegistryKey.of(registryKey, resource.id)) }
		}
	}

	private fun copyWorldFieldsFrom(source: ClientWorld) {
		for (field in World::class.java.declaredFields) {
			if (Modifier.isStatic(field.modifiers)) continue
			check(field.trySetAccessible()) { "Cannot access ${World::class.java.simpleName}.${field.name}" }
			field.set(this, field.get(source))
		}
	}
}
