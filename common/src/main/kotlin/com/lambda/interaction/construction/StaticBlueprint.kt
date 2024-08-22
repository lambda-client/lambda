package com.lambda.interaction.construction

import com.lambda.util.extension.Structure

data class StaticBlueprint(
    override val structure: Structure
) : Blueprint() {
    companion object {
        fun Structure.toBlueprint() = StaticBlueprint(this)
    }
}
