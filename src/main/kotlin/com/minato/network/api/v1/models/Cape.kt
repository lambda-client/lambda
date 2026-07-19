
package com.minato.network.api.v1.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.minato.network.MinatoAPI
import java.util.*

class Cape(
    @JsonProperty("uuid")
    val uuid: UUID,

    @JsonProperty("type")
    val id: String,
) {
    val url: String
        get() = "${MinatoAPI.capes}/$id.png"

    override fun toString() = "Cape(uuid=$uuid, id=$id, url=$url)"
}
