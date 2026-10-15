package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.MotionTemplateId
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.families.jump.JumpMovement
import com.lambda.pathing.core.MovementId

class MovementCatalog private constructor(
	val movements: List<Movement>,
	val templates: List<MotionTemplate>,
	private val byId: Map<MovementId, Movement>,
	val options: SimpleMoveOptions,
) {
	operator fun get(id: MovementId): Movement? = byId[id]

	fun supports(id: MovementId): Boolean = id in byId

	companion object {

		val REGISTERED: List<Movement> = listOf(
			WalkMovement,
			JumpMovement,
			NeoMovement,
			DropMovement,
			ClimbMovement,
			LadderCatchMovement,
			SwimMovement,
			DiveMovement,
			SlimeBounceMovement,
			FallMovement,
			HoneySlideMovement,
		)

		fun build(
			costs: CoarseMoveCosts,
			options: SimpleMoveOptions,
			movements: List<Movement> = REGISTERED,
		): MovementCatalog {
			val context = MovementContext(options, costs)
			val owners = LinkedHashMap<MovementId, Movement>()
			val specs = ArrayList<TemplateSpec>()

			val active = movements.filter { movement ->
				val contributed = movement.templates(context)
				if (contributed.isEmpty() && !movement.scans(options)) return@filter false
				owners[movement.id] = movement
				contributed.forEach { owners.putIfAbsent(it.movement, movement) }
				specs += contributed
				true
			}
			val templates = specs.mapIndexed { index, spec -> spec.toTemplate(MotionTemplateId(index)) }
			return MovementCatalog(active, templates, owners, options)
		}
	}
}
