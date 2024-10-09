package com.lambda.interaction.construction

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.util.FolderRegister
import net.minecraft.registry.Registries
import net.minecraft.structure.StructureTemplateManager

object StructureManager : Loadable {
    // ToDo: Rewrite the StructureTemplateManager: Remove clutter, clean file structure
    lateinit var templateManager: StructureTemplateManager

    override fun load(): String {
        templateManager = StructureTemplateManager(
            mc.resourceManager,
            mc.levelStorage.createSession(FolderRegister.structure.path),
            mc.dataFixer,
            Registries.BLOCK.readOnlyWrapper
        )

        return "StructureManager loaded ${templateManager.streamTemplates().count()} templates"
    }
}