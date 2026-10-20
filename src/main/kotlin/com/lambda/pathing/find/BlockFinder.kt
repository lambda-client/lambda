package com.lambda.pathing.find

import com.lambda.config.automation.AutomationConfig
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.pathing.core.Stance
import com.lambda.pathing.find.AccessStances.liveSection
import com.lambda.pathing.find.AccessStances.liveView
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.session.CoarseService
import com.lambda.pathing.session.PathingManager
import com.lambda.pathing.session.PathingRequest
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.PlanningJourney
import com.lambda.pathing.session.PlanningPreparationResult
import com.lambda.pathing.session.preparePlanning
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.snapshot.BlockPhysicsTable
import com.lambda.pathing.world.snapshot.HazardRules
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.store.BaritoneCache
import com.lambda.pathing.world.store.TerrainStores
import com.lambda.threading.runIO
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeGameScheduled
import com.lambda.util.CommunicationUtils.info
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import java.util.function.Predicate
import kotlin.math.sqrt

object BlockFinder : Loadable {
	private var job: FindJob? = null

	fun find(description: String, predicate: Predicate<CachedBlockPosition>, follow: Boolean = false) {
		cancel()
		runSafe {
			val next = FindJob(BlockQuery(description, world, predicate), follow)
			job = next
			with(next) { search() }
		}
	}

	fun cancel() {
		job?.cancel()
		job = null
	}

	fun status(): String = job?.status() ?: "No block search is running."

	init {
		listen<TickEvent.Pre> {
			val current = job ?: return@listen
			if (!with(current) { tick() } && job === current) job = null
		}
		listenUnsafe<ConnectionEvent.Disconnect> { cancel() }
	}

	private class Choice(val block: FoundBlock, val stance: Stance)

	private class Probe(val journey: PlanningJourney, val choices: List<Choice>, val start: Stance)

	private class FindJob(private val query: BlockQuery, private val follow: Boolean) {
		private val excluded = HashSet<BlockPos>()
		private var failures = 0
		private var searching = false
		private var choice: Choice? = null
		private var request: PathingRequest? = null
		private var verified = false
		private var reached = 0

		@Volatile
		private var cancelled = false

		@Volatile
		private var probe: Probe? = null

		fun cancel() {
			cancelled = true
			probe?.journey?.cancel()
		}

		fun status(): String {
			probe?.let { return "Comparing the paths to the ${it.choices.size} nearest ${query.description}..." }
			val current = choice ?: return "Searching for ${query.description}..."
			if (follow) return "Following ${query.description}: $reached reached, walking onto the one at ${current.block.pos.toShortString()}."
			return "Walking to the ${query.description} at ${current.block.pos.toShortString()} (${current.block.source.label}), standing at ${current.stance}."
		}

		fun SafeContext.search() {
			searching = true
			choice = null
			request = null
			val origin = player.pos
			val config = AutomationConfig.DEFAULT.pathingConfig
			val hazards = config.hazards
			val minY = world.bottomY
			val maxY = world.bottomY + world.height - 1
			val loaded = loadedChoices(hazards, minY, maxY)
			val store = TerrainStores.directoryOf(world)
			val baritone = if (config.useBaritoneCache) BaritoneCache.of(world) else null
			val chunks = loadedChunks()
			val skipped = HashSet(excluded)
			val bound = if (loaded.size < COMPARED_TARGETS) Double.POSITIVE_INFINITY else loaded.last().block.distanceSquared
			if (bound <= nearestUnloaded(origin, chunks)) {
				found(loaded)
				return
			}
			runIO {
				val cached = runCatching {
					TerrainStores.sync(store)
					val cache = CachedBlocks(store, baritone, chunks, minY, maxY)
					choose(origin, skipped, bound, { nearest -> cache.scan(query, nearest) { cancelled } }) { target ->
						AccessStances.view(target, minY, maxY) { cache.section(it, hazards) }
					}
				}.getOrDefault(emptyList())
				val nearest = (loaded + cached).sortedBy { it.block.distanceSquared }.take(COMPARED_TARGETS)
				runSafeGameScheduled { found(nearest) }
			}
		}

		private fun SafeContext.loadedChoices(hazards: HazardRules, minY: Int, maxY: Int): List<Choice> {
			val table = BlockPhysicsTable.of(hazards)
			val sections = HashMap<Long, ImmutableSnapshotSection?>()
			val radius = MinecraftClient.getInstance().options.clampedViewDistance + Vanilla.CHUNK_MAP_MARGIN
			return choose(player.pos, HashSet(excluded), Double.POSITIVE_INFINITY, { nearest -> LoadedBlocks.scan(world, player.pos, radius, query, nearest) }) { target ->
				AccessStances.view(target, minY, maxY) { key -> if (key in sections) sections[key] else liveSection(table, key).also { sections[key] = it } }
			}
		}

		private fun choose(
			origin: Vec3d,
			skipped: MutableSet<BlockPos>,
			bound: Double,
			scan: (NearestBlocks) -> Unit,
			view: (BlockPos) -> CoarseVoxelView,
		): List<Choice> {
			val chosen = ArrayList<Choice>()
			repeat(CANDIDATE_BATCHES) {
				val nearest = NearestBlocks(origin, COMPARED_TARGETS, skipped)
				scan(nearest)
				val found = nearest.sorted().takeWhile { it.distanceSquared < bound }
				if (found.isEmpty() || cancelled) return chosen
				for (block in found) {
					AccessStances.choose(view(block.pos), block.pos, origin, on = follow)?.let { chosen += Choice(block, it) }
					skipped += block.pos
					if (chosen.size == COMPARED_TARGETS) return chosen
				}
			}
			return chosen
		}

		private fun SafeContext.nearestUnloaded(origin: Vec3d, loaded: LongOpenHashSet): Double {
			val radius = MinecraftClient.getInstance().options.clampedViewDistance + Vanilla.CHUNK_MAP_MARGIN + 1
			var nearest = Double.POSITIVE_INFINITY
			for (chunkX in player.chunkPos.x - radius..player.chunkPos.x + radius) for (chunkZ in player.chunkPos.z - radius..player.chunkPos.z + radius) {
				if (ChunkPos.toLong(chunkX, chunkZ) in loaded) continue
				val dx = origin.x - origin.x.coerceIn((chunkX shl 4) + 0.5, (chunkX shl 4) + 15.5)
				val dz = origin.z - origin.z.coerceIn((chunkZ shl 4) + 0.5, (chunkZ shl 4) + 15.5)
				nearest = minOf(nearest, dx * dx + dz * dz)
			}
			return nearest
		}

		private fun SafeContext.loadedChunks(): LongOpenHashSet {
			val radius = MinecraftClient.getInstance().options.clampedViewDistance + Vanilla.CHUNK_MAP_MARGIN
			val centreX = player.chunkPos.x
			val centreZ = player.chunkPos.z
			val chunks = LongOpenHashSet()
			for (chunkX in centreX - radius..centreX + radius) for (chunkZ in centreZ - radius..centreZ + radius) {
				if (world.chunkManager.isChunkLoaded(chunkX, chunkZ)) chunks.add(ChunkPos.toLong(chunkX, chunkZ))
			}
			return chunks
		}

		private fun SafeContext.found(nearest: List<Choice>) {
			if (cancelled) return
			searching = false
			if (nearest.isEmpty()) {
				if (follow && reached > 0) info("Reached $reached ${query.description}; no unvisited one is left.", PATHING_SOURCE)
				else info("Found no ${query.description} to stand next to in loaded chunks, Lambda's terrain cache or Baritone's cache.", PATHING_SOURCE)
				cancelled = true
				return
			}
			if (nearest.size == 1) {
				walk(nearest.first(), "the only one found")
				return
			}
			compare(nearest)
		}

		private fun SafeContext.compare(nearest: List<Choice>) {
			val primary = nearest.first()
			val prepared = preparePlanning(player, PathingRequest(AutomationConfig.DEFAULT, primary.stance), primary.stance, emptyList())
			if (prepared !is PlanningPreparationResult.Ready) {
				walk(primary, "the nearest by distance")
				return
			}
			val preparation = prepared.preparation.copy(alternatives = nearest.drop(1).map { it.stance }, routeOnly = true)
			val journey = PlanningJourney.open(preparation, player)
			val started = Probe(journey, nearest, preparation.start)
			probe = started
			val targets = nearest.mapTo(HashSet()) { it.stance }
			runIO {
				val frame = runCatching { journey.coarse.awaitRoute(0, started.start, { cancelled || probe !== started }) { it in targets } }.getOrNull()
				runSafeGameScheduled { compared(started, frame) }
			}
		}

		private fun SafeContext.compared(done: Probe, frame: CoarseService.Frame?) {
			done.journey.cancel()
			if (probe !== done) return
			probe = null
			if (cancelled) return
			val end = frame?.route?.goal
			val nearest = done.choices.firstOrNull { it.stance == end }
			if (frame == null || nearest == null) walk(done.choices.first(), "the nearest by distance; no path compared")
			else walk(nearest, "the nearest by path of the ${done.choices.size} closest, about ${(frame.route.lowerBoundTicks / Vanilla.TICKS_PER_SECOND).toInt()} s away")
		}

		private fun SafeContext.walk(next: Choice, why: String) {
			val distance = sqrt(next.block.distanceSquared).toInt()
			info("Found ${query.description} at ${next.block.pos.toShortString()} in ${next.block.source.label}, $distance blocks away ($why); walking ${if (next.stance == Stance(next.block.pos.x, next.block.pos.y, next.block.pos.z)) "onto" else "next to"} it.", PATHING_SOURCE)
			route(next)
		}

		private fun route(next: Choice) {
			choice = next
			verified = next.block.source == BlockSource.LOADED
			request = PathingManager.route(AutomationConfig.DEFAULT, listOf(next.stance))
		}

		fun SafeContext.tick(): Boolean {
			if (cancelled) return false
			probe?.let { running ->
				running.journey.world.advance()
				return true
			}
			if (searching) return true
			val current = choice ?: return false
			val target = current.block.pos
			if (world.chunkManager.isChunkLoaded(target.x shr 4, target.z shr 4)) {
				if (!query.matches(world.getBlockState(target))) return retry(current, "is gone")
				if (!verified && AccessStances.loaded(target, world.chunkManager::isChunkLoaded)) {
					verified = true
					val view = liveView(target)
					if (!Footing.standsAt(view, current.stance)) {
						val stance = AccessStances.choose(view, target, player.pos, on = follow) ?: return retry(current, "has no place to stand beside it")
						route(Choice(current.block, stance))
						return true
					}
				}
			}
			val request = request ?: return false
			return when (val state = PathingManager.stateOf(request)) {
				null, State.Idle -> false
				is State.Complete -> {
					if (!follow) {
						info("Next to the ${query.description} at ${target.toShortString()}.", PATHING_SOURCE)
						return false
					}
					excluded += target
					reached++
					failures = 0
					search()
					true
				}
				is State.Failed -> retry(current, "is out of reach (${state.reason})")
				else -> true
			}
		}

		private fun SafeContext.retry(current: Choice, why: String): Boolean {
			excluded += current.block.pos
			if (++failures >= MAX_TARGETS) {
				info("The ${query.description} at ${current.block.pos.toShortString()} $why; giving up after $failures targets.", PATHING_SOURCE)
				return false
			}
			info("The ${query.description} at ${current.block.pos.toShortString()} $why; looking for the next nearest.", PATHING_SOURCE)
			search()
			return true
		}
	}

	private const val COMPARED_TARGETS = 16

	private const val CANDIDATE_BATCHES = 4

	private const val MAX_TARGETS = 5

}
