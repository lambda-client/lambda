package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import java.awt.Color

object GuiSettings : Module(
    name = "HUD",
    description = "Visual behaviour configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val page by setting("Page", Page.General)

    private val scaleSetting by setting("Scale", 1.0, 0.5..3.0, 0.01, visibility = { page == Page.General })

    private val primaryColor by setting("Primary Color", Color(130, 200, 255), visibility = { page == Page.Colors })
    private val secondaryColor by setting("Secondary Color", Color(225, 130, 225), visibility = { page == Page.Colors && shade })
    val backgroundColor by setting("Background Color", Color(0, 0, 0, 80), visibility = { page == Page.Colors })
    val glow by setting("Glow", true, visibility = { page == Page.Colors })
    val shade by setting("Shade Color", true, visibility = { page == Page.Colors })
    val colorWidth by setting("Color Width", 40.0, 1.0..100.0, 1.0, visibility = { page == Page.Colors && shade })
    val colorHeight by setting("Color Height", 40.0, 1.0..100.0, 1.0, visibility = { page == Page.Colors && shade })
    val colorSpeed by setting("Color Speed", 1.0, 0.1..10.0, 0.1, visibility = { page == Page.Colors && shade })

    val mainColor: Color get() = if (shade) Color.WHITE else primaryColor

    val shadeColor1 get() = primaryColor
    val shadeColor2 get() = secondaryColor


    enum class Page {
        General,
        Colors
    }

    val scale get() = scaleSetting * 2
}