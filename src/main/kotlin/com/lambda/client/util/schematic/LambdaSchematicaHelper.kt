package com.lambda.client.util.schematic

import com.github.lunatrius.schematica.Schematica
import com.github.lunatrius.schematica.proxy.ClientProxy

object LambdaSchematicaHelper {
    val isSchematicaPresent: Boolean
        get() = try {
            Class.forName(Schematica::class.java.name)
            true
        } catch (ex: ClassNotFoundException) {
            false
        } catch (ex: NoClassDefFoundError) {
            false
        }

    val loadedSchematic: Schematic?
        get() {
            return ClientProxy.schematic?.let {
                SchematicAdapter(it)
            }
        }
}