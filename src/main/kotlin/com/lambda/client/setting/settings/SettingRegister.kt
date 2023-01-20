package com.lambda.client.setting.settings

import com.lambda.client.setting.settings.impl.number.DoubleSetting
import com.lambda.client.setting.settings.impl.number.FloatSetting
import com.lambda.client.setting.settings.impl.number.IntegerSetting
import com.lambda.client.setting.settings.impl.other.BindSetting
import com.lambda.client.setting.settings.impl.other.ColorSetting
import com.lambda.client.setting.settings.impl.primitive.BooleanSetting
import com.lambda.client.setting.settings.impl.primitive.EnumSetting
import com.lambda.client.setting.settings.impl.primitive.StringSetting
import com.lambda.client.util.Bind
import com.lambda.client.util.color.ColorHolder
import java.util.function.BooleanSupplier

/**
 * Setting register overloading
 *
 * @param T Type to have extension function for registering setting
 */
interface SettingRegister<T : Any> {

    /** Integer Setting */
    fun T.setting(
        name: String,
        value: Int,
        range: IntRange,
        step: Int,
        visibility: () -> Boolean = { true },
        consumer: (prev: Int, input: Int) -> Int = { _, input -> input },
        description: String = "",
        unit: String = "",
        fineStep: Int = step,
        formatter: (Int) -> String = { i -> "$i"},
        ) = setting(IntegerSetting(name, value, range, step, visibility, consumer, description, unit, fineStep, formatter))

    /** Double Setting */
    fun T.setting(
        name: String,
        value: Double,
        range: ClosedFloatingPointRange<Double>,
        step: Double,
        visibility: () -> Boolean = { true },
        consumer: (prev: Double, input: Double) -> Double = { _, input -> input },
        description: String = "",
        unit: String = "",
        fineStep: Double = step,
        formatter: (Double) -> String = { d -> "$d"},
        ) = setting(DoubleSetting(name, value, range, step, visibility, consumer, description, unit, fineStep, formatter))

    /** Float Setting */
    fun T.setting(
        name: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        step: Float,
        visibility: () -> Boolean = { true },
        consumer: (prev: Float, input: Float) -> Float = { _, input -> input },
        description: String = "",
        unit: String = "",
        fineStep: Float = step,
        formatter: (Float) -> String = { f -> "$f"},
        ) = setting(FloatSetting(name, value, range, step, visibility, consumer, description, unit, fineStep, formatter))

    /** Bind Setting */
    fun T.setting(
        name: String,
        value: Bind,
        visibility: () -> Boolean = { true },
        description: String = "",
        formatter: (Bind) -> String = { b -> "$b"},
        ) = setting(BindSetting(name, value, visibility, description, formatter))

    /** Color Setting */
    fun T.setting(
        name: String,
        value: ColorHolder,
        hasAlpha: Boolean = true,
        visibility: () -> Boolean = { true },
        description: String = "",
        formatter: (ColorHolder) -> String = { c -> "$c"},
        ) = setting(ColorSetting(name, value, hasAlpha, visibility, description, formatter))

    /** Boolean Setting */
    fun T.setting(
        name: String,
        value: Boolean,
        visibility: () -> Boolean = { true },
        consumer: (prev: Boolean, input: Boolean) -> Boolean = { _, input -> input },
        description: String = "",
        formatter: (Boolean) -> String = { b -> "$b"},
        ) = setting(BooleanSetting(name, value, visibility, consumer, description, formatter))

    /** Enum Setting */
    fun <E : Enum<E>> T.setting(
        name: String,
        value: E,
        visibility: () -> Boolean = { true },
        consumer: (prev: E, input: E) -> E = { _, input -> input },
        description: String = "",
        unit: String = "",
        formatter: (E) -> String = { e -> "$e"},
        ) = setting(EnumSetting(name, value, visibility, consumer, description, unit, formatter))

    /** String Setting */
    fun T.setting(
        name: String,
        value: String,
        visibility: () -> Boolean = { true },
        consumer: (prev: String, input: String) -> String = { _, input -> input },
        description: String = "",
        formatter: (String) -> String = { s -> s },
        ) = setting(StringSetting(name, value, visibility, consumer, description, formatter))
    /* End of setting registering */

    fun <T : Any> AbstractSetting<T>.atValue(page: T): () -> Boolean = {
        this.value == page
    }

    fun <T : Any> AbstractSetting<T>.atValue(page: T, block: BooleanSupplier): () -> Boolean = {
        this.value == page && block.asBoolean
    }

    /**
     * Register a setting
     *
     * @param S Type of the setting
     * @param setting Setting to register
     *
     * @return [setting]
     */
    fun <S : AbstractSetting<*>> T.setting(setting: S): S
}