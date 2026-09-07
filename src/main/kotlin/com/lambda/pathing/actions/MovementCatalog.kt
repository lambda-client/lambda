package com.lambda.pathing.actions

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.actions.families.ClimbMovement
import com.lambda.pathing.actions.families.DropMovement
import com.lambda.pathing.actions.families.jump.JumpMovement
import com.lambda.pathing.actions.families.LadderCatchMovement
import com.lambda.pathing.actions.families.SlimeBounceMovement
import com.lambda.pathing.actions.families.WalkMovement

class MovementCatalog private constructor(
    val movements: List<Movement>,
    val templates: List<MotionTemplate>,
    private val byId: Map<MovementId, Movement>,
) {

    operator fun get(id: MovementId): Movement? = byId[id]

    fun supports(id: MovementId): Boolean = id in byId

    companion object {

        /**
         * The default provider set. Not a registry to extend: [build] takes `movements`
         * as a plain list, and that parameter is the injection point for a different or
         * additional [Movement] (see MovementCatalogTest's TeleportMovement).
         */
        val REGISTERED: List<Movement> = listOf(
            WalkMovement,
            JumpMovement,
            DropMovement,
            ClimbMovement,
            LadderCatchMovement,
            SlimeBounceMovement,
        )

        /** Builds the catalog from [movements]; pass a different list to inject providers. */
        fun build(
            costs: CoarseMoveCosts,
            options: SimpleMoveOptions = SimpleMoveOptions(),
            ballistics: BallisticProfile = BallisticProfile.VANILLA,
            movements: List<Movement> = REGISTERED,
        ): MovementCatalog {
            val context = MovementContext(options, costs, ballistics)
            val owners = LinkedHashMap<MovementId, Movement>()
            val specs = ArrayList<TemplateSpec>()

            val active = movements.filter { movement ->
                val contributed = movement.templates(context)
                if (contributed.isEmpty()) return@filter false
                owners[movement.id] = movement
                contributed.forEach { owners.putIfAbsent(it.movement, movement) }
                specs += contributed
                true
            }
            val templates = specs.mapIndexed { index, spec -> spec.toTemplate(MotionTemplateId(index)) }
            return MovementCatalog(active, templates, owners)
        }
    }
}
