
package com.minato.network

import com.minato.Minato.mapper
import com.minato.Minato.mc
import com.minato.config.Config
import com.minato.config.categories.SecretsCategory
import com.minato.core.Loadable
import com.minato.network.api.v1.models.Authentication
import com.minato.network.api.v1.models.Authentication.Data
import com.minato.util.StringUtils.base64UrlDecode
import com.minato.util.collections.updatableLazy

object NetworkHandler : Config(
    "network",
    SecretsCategory
), Loadable {
    var accessToken by setting("access_token", "") { false }; private set

    val isValid: Boolean
        get() = mc.gameProfile.name == auth.value?.data?.name &&
                mc.gameProfile.id == auth.value?.data?.uuid &&
                System.currentTimeMillis() > (auth.value?.expirationDate ?: Long.MAX_VALUE)

    private val auth = updatableLazy {
        val parts = accessToken.split(".")
        if (parts.size != 3) return@updatableLazy null

        val payload = parts[1]
        val data = mapper.readValue(payload.base64UrlDecode(), Data::class.java)

        return@updatableLazy if (System.currentTimeMillis() < data.expirationDate) null
        else data
    }

    fun updateToken(resp: Authentication) {
        accessToken = resp.accessToken
        auth.update()
    }

    override fun load(): String {
        auth.update()

        return auth.value
            ?.let { "Logged you in as ${it.data.name} (${it.data.uuid})" }
            ?: "NetworkManager: You are not authenticated"
    }
}
