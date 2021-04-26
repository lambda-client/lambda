package org.kamiblue.client.setting

import org.kamiblue.client.LambdaMod
import org.kamiblue.client.setting.configs.NameableConfig

internal object GenericConfig : NameableConfig<GenericConfigClass>(
    "generic",
    "${LambdaMod.DIRECTORY}config/"
)