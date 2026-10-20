package com.lambda.pathing.world.snapshot

data class DoorToggle(val closed: SnapshotBlockPhysics, val opened: SnapshotBlockPhysics, val pairedY: Int, val open: Boolean) {
	fun physics(open: Boolean): SnapshotBlockPhysics = (if (open) opened else closed).copy(toggle = copy(open = open))

	val toggled: SnapshotBlockPhysics by lazy(LazyThreadSafetyMode.PUBLICATION) { physics(!open) }

	val current: SnapshotBlockPhysics get() = if (open) opened else closed
}
