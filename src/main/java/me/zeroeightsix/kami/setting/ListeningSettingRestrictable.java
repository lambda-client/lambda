package me.zeroeightsix.kami.setting;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public class ListeningSettingRestrictable<T> extends SettingRestrictable<T> {

    BiConsumer<T, T> consumer;

    public ListeningSettingRestrictable(T value, Predicate<T> restriction, BiConsumer<T, T> consumer) {
        super(value, restriction);
        this.consumer = consumer;
    }

    @Override
    public BiConsumer<T, T> changeListener() {
        return consumer;
    }

}
