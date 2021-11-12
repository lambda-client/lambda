package com.lambda.client.manager.managers

import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.manager.Manager
import com.lambda.client.util.threads.BackgroundScope
import java.net.InetSocketAddress
import java.net.Socket

object PluginManager : Manager {
    init {
        BackgroundScope.launchLooping("plugin", 1000L) {
            LambdaClickGui.updatePlugins()
        }
    }
}