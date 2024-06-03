# QKL: Brigadier

**Document author**: June (Cypher121)  
**Module authors**: June (Cypher121), Oliver-makes-code

## Summary

The Brigadier package provides a Kotlin DSL for easier creation of commands, safe access
to command arguments, and shortcuts for common patterns, such as optional arguments,
chains of required arguments, and common context element like player or world.

## Usage

### Recommended imports

Much of QKL Brigadier is built on extension and top-level functions, and as such many
symbols need to be imported. It is often easier to import all of them beforehand than
to rely on IDE import resolution for every symbol:

```kotlin
import org.quiltmc.qkl.library.brigadier.*
import com.lambda.brigadier.argument.*
import org.quiltmc.qkl.library.brigadier.util.*
```

### Samples

More detailed examples of commands using the available DSL tools can be found in
[the samples file](../../../../../samples/qkl/brigadier/BrigadierDslSamples.kt).

### Creating a command

Commands are created using the `register` extension on `CommandDispatcher`. For most usages
an instance of the dispatcher can be obtained using the `CommandRegistrationCallback.EVENT`
QSL event or its matching QKL extension.

```kotlin
registerEvents {
    onCommandRegistration {
        register("example_command") {
            //required chain
            required(blockPos("pos"), intRange("range")) { getPos, getIntRange ->
                //optional argument
                optional(literal("ping")) { ping ->
                    execute {
                        //shorthand for source.sendFeedback 
                        sendFeedback(
                            buildText {
                                val sourceName = entity?.displayName ?: "stranger"
                                literal("Hello $sourceName! ")

                                //accessing an argument with a special requirement (block pos must be loaded) 
                                val state = world.getBlockState(getPos().requireLoaded())
                                val id = Registry.BLOCK.getId(state.block)
                                literal("Selected block was $id. ")

                                //accessing a normal argument 
                                val range = getIntRange().value()
                                val random = Random.nextInt(range.min ?: 0, (range.max ?: 100) + 1)
                                literal("Random number is $random.")

                                //check if optional present 
                                if (ping != null) {
                                    literal(" Pong!")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
```

### Custom argument types

To add support for a new argument type, up to three things must be added:

* Argument descriptor types
* A function returning an argument constructor
* A function retrieving a value from an argument reader

All of which are normally simply single-line wrappers around the argument type's own methods.

#### Argument descriptor types

Descriptor types allow matching the function creating the type to functions that are allowed to decode it.

For most types there is either only one way to create it (e.g. IdentifierArgumentType), one way to read it
(e.g. StringArgumentType), or both (most existing types), so the matching is unnecessary. In such cases,
it is enough to use `DefaultArgumentDescriptor<T>` where `T` is the `ArgumentType` in question
(e.g. `DefaultArgumentDescriptor<StringArgumentType>` for strings).

Some arguments, however, may represent multiple separate paths between creating and reading the argument.
`EntityArgumentType` is one such type:

* `EntityArgumentType.entity` creates an argument for a single entity to be read using `EntityArgumentType.getEntity`
* `EntityArgumentType.entities` creates an argument for multiple entities to be read using
  `EntityArgumentType.getEntities` or `EntityArgumentType.getOptionalEntities`

In other words, `EntityArgumentType` is two different types pretending to be one. As such, it gets two descriptors:

```kotlin
object SingleEntityArgumentDescriptor : ArgumentDescriptor<EntityArgumentType>
object ListEntityArgumentDescriptor : ArgumentDescriptor<EntityArgumentType>
```

These descriptors' types will be used by the constructor and reader functions to distinguish the single-entity
path from the multiple-entities path. Similarly, any custom argument type with multiple paths can create multiple
descriptors.

Additionally, if any data needs to be stored to correctly parse the argument later, it can be stored within
instances of the descriptor. For example, one of the subtypes of QSL's `EnumArgumentType` requires storing
the Class instance of the enum and has the following descriptor:

```kotlin
class TypedEnumArgumentDescriptor<T : Enum<T>>(val type: Class<T>) : ArgumentDescriptor<EnumArgumentType>
```

#### Argument constructor functions

The next step is to create an argument constructor function for every method of creating the argument type.
All these functions need to do is create an instance of the argument type and an instance of the descriptor,
and pass those along with the name to QKL's `argument` function:

```kotlin
//arguments using default descriptors can use shorter versions and typealiases
@BrigadierDsl
fun <S> identifier(
    name: String
): DefaultArgumentConstructor<S, IdentifierArgumentType> {
    return argument(name, IdentifierArgumentType.identifier())
}

//two ways to create an entity argument
@BrigadierDsl
fun <S> entity(
    name: String
): RequiredArgumentConstructor<S, SingleEntityArgumentDescriptor> {
    return argument(name, EntityArgumentType.entity(), SingleEntityArgumentDescriptor)
}

@BrigadierDsl
fun <S> entities(
    name: String
): RequiredArgumentConstructor<S, ListEntityArgumentDescriptor> {
    return argument(name, EntityArgumentType.entities(), ListEntityArgumentDescriptor)
}
```

#### Argument reader functions

The last step is to create an extension function that can read the data from an ArgumentReader,
which wraps together the `CommandContext`, the descriptor passed in when creating the argument,
and the argument name:

```kotlin
//A reader using the default descriptor for StringArgumentType and usable on any context
//If creating multiple functions with the same name, give them different JVM names
@JvmName("valueStringArg")
@BrigadierDsl
fun DefaultArgumentReader<StringArgumentType>.value(): String {
    return StringArgumentType.getString(context, name)
}

//A reader for multiple entities argument
//Uses the List descriptor and can only be called for ServerCommandSource contexts
@JvmName("optionalEntityArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        ListEntityArgumentDescriptor
        >.optional(): Collection<Entity> {
    return EntityArgumentType.getOptionalEntities(context, name)
}

//A reader for string-based EnumArgumentType
//Similar to above, but callable on any context
@JvmName("valueEnumStringArg")
@BrigadierDsl
fun ArgumentReader<*, StringEnumArgumentDescriptor>.value(): String {
    return EnumArgumentType.getEnum(context, name)
}
```

### Custom command sources

Many arguments will work regardless of command source, but some use information specific to ServerCommandSource,
and will not by default be usable for a custom source. The only change needed to fix that is to implement
argument reader functions as above for those argument descriptors and the custom command source.

## Implementation

Information below describes the internal implementation of the brigadier package
and is intended for contributors and maintainers of the library.

### Argument lifecycle

The arguments are constructed and parsed in several stages:

1) An argument descriptor is defined as described above
2) `argument(name, type, descriptor)` wraps the name, descriptor and `RequiredArgumentBuilder` as
   an `ArgumentConstructor`
    * `literal(name)` is the sole exception, instead using `LiteralArgumentBuilder`
3) `required(...)` or `optional(...)` converts the constructor to a `CommandArgument` and registers it.
    * `required(...)` simply runs the child lambda on the argument builder, then attaches it to the parent with `.then`
    * `optional(...)` does the same, but also runs the lambda on the parent directly with a `null` argument
4) The argument passed to the child is `ArgumentAccessor` -- a lambda that can be thought of as a
   partially applied `ArgumentReader` -- which, if present, can be executed on the `CommandContext`
   to create an `ArgumentReader`
5) Having collected name and descriptor in step 2, required-ness or existence of optional in step 3, and finally
   the context in step 4, the `ArgumentReader` is now ready to provide a value through the applicable extension
   functions.

### Future improvement with context receivers

Should context receivers be stabilized, `CommandContext` can be removed from `ArgumentReader`, eliminating the need to
defer creating it
and allowing it to be passed to lambdas instead of `ArgumentAccessor`. The command context would then be passed in as a
context receiver instead, e.g.:

```kotlin
@JvmName("valueEnumClassArg")
@BrigadierDsl
context(CommandContext<*>)
fun <T : Enum<T>> ArgumentReader<TypedEnumArgumentDescriptor<T>>.value(): T {
    return EnumArgumentType.getEnumConstant(
        this@CommandContext,
        name,
        argumentDescriptor.type
    )
}
```

The use-site would then change as well:

```kotlin
//before
required(blockPos("pos")) { getPos ->
    execute {
        getPos().value()
    }
}

//after
required(blockPos("pos")) { pos ->
    execute {
        pos.value()
    }
}
```