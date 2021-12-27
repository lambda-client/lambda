package com.lambda.client.util

import com.lambda.client.LambdaMod
import java.net.URL

object KamiCheck {
    var isKami: Boolean = false
    var didDisplayWarning: Boolean = false
    fun runCheck() {
        val kamiCheckList: List<URL> = this.javaClass.classLoader.getResources("org/kamiblue/client/KamiMod.class").toList()
        if (kamiCheckList.isNotEmpty()) {
            LambdaMod.LOG.error("KAMI Blue detected!")
            isKami = true
        }
    }
}