package com.lambda.pathing.world.snapshot

internal sealed interface SnapshotCaptureResult {
	data class Progress(val capturedCells: Long, val totalCells: Long) : SnapshotCaptureResult
	data class Complete(val snapshot: SnapshotSimulationEnvironment) : SnapshotCaptureResult
	data class Failed(val message: String) : SnapshotCaptureResult
}
