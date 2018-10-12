package me.zeroeightsix.kami.setting.builder.numerical;

import me.zeroeightsix.kami.setting.boxed.numerical.BoxedFloatSavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.boxed.numerical.BoxedNumberSavableListeningNamedSettingRestrictable;

/**
 * Created by 086 on 13/10/2018.
 */
public class FloatSettingBuilder extends NumericalSettingBuilder<Float> {
    @Override
    public BoxedNumberSavableListeningNamedSettingRestrictable build() {
        return new BoxedFloatSavableListeningNamedSettingRestrictable(initialValue, predicate(), consumer(), name);
    }
}
