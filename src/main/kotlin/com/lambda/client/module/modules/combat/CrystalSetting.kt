package com.lambda.client.module.modules.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.CrystalManager.updatePlaceList
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.isActiveOrFalse
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraftforge.fml.common.gameevent.TickEvent

object CrystalSetting : Module(
    name = "CrystalSetting",
    description = "Settings for explosive combat modules",
    category = Category.COMBAT,
    showOnArray = false,
    alwaysEnabled = true
) {
    private val page by setting("Page", Page.GENERAL)

    private val motionPrediction by setting("Motion Prediction", true, { page == Page.GENERAL })
    private val pingSync by setting("Ping Sync", true, { page == Page.GENERAL && motionPrediction })
    private val ticksAhead by setting("Ticks Ahead", 5, 0..20, 1, { page == Page.GENERAL && motionPrediction && !pingSync })

    private val searchRange by setting("Search Range", 5.0f, 1.0f..6.0f, 0.1f, { page == Page.CRYSTAL }, description = "The range for placement")
    private val placeRepartition by setting("Place Repartition", false, { page == Page.CRYSTAL }, description = "Whether or not to equally repart the placements")
    private val placeRepartitionDistance by setting("Place Repartition Distance", 2.0f, 1.0f..4.0f, 0.1f, { page == Page.CRYSTAL && placeRepartition }, description = "The distance between each crystals")
    private val crystalVersion by setting("Crystal Version", Version._1_12_2)
    private val rayTrace by setting("Raytrace Placement", false, description = "Whether or not to raytrace the difference between two vectors")


    private val jobMap = hashMapOf<(SafeClientEvent) -> Unit, Job?>(
        { it: SafeClientEvent -> it.updatePlaceList(
            searchRange,
            if (placeRepartition) placeRepartitionDistance else 0.0f,
            rayTrace,
            if (motionPrediction && !pingSync) ticksAhead else 0
        ) } to null,
    )

    override fun isActive() = CrystalAura.isActive() || BedAura.isActive() || CrystalBasePlace.isActive()


    init {
        safeListener<TickEvent.ClientTickEvent>(6000) {
            for ((function, future) in jobMap) {
                if (future.isActiveOrFalse) continue
                jobMap[function] = defaultScope.launch { function(this@safeListener) }
            }
        }
    }


    private enum class Page {
        GENERAL, CRYSTAL
    }

    private enum class Version(string: String) {
        _1_12_2(string = "1.12.2"),
        _1_13plus(string = "1.13+")
    }
}