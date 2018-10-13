package me.zeroeightsix.kami.setting.builder.numerical;

import me.zeroeightsix.kami.setting.impl.numerical.BoxedFloatSavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.impl.numerical.BoxedNumberSavableListeningNamedSettingRestrictable;

/**
 * Created by 086 on 13/10/2018.
 */
public class FloatSettingBuilder extends NumericalSettingBuilder<Float> {
    @Override
    public BoxedNumberSavableListeningNamedSettingRestrictable build() {
        return new BoxedFloatSavableListeningNamedSettingRestrictable(initialValue, predicate(), consumer(), name, visibilityPredicate());
    }
}
