package me.zeroeightsix.kami.setting.builder;

import com.google.common.base.MoreObjects;
import me.zeroeightsix.kami.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 13/10/2018.
 */
public abstract class SettingBuilder<T> {

    protected String name;
    protected T initialValue;
    protected BiConsumer<T, T> consumer;
    protected List<Predicate<T>> predicateList = new ArrayList<>();
    private Predicate<T> visibilityPredicate;

    public SettingBuilder<T> withValue(T value) {
        this.initialValue = value;
        return this;
    }

    protected Predicate<T> predicate() {
        return predicateList.isEmpty() ? (t) -> true : (t) -> predicateList.stream().allMatch(tPredicate -> tPredicate.test(t));
    }

    protected Predicate<T> visibilityPredicate() {
        return MoreObjects.firstNonNull(visibilityPredicate, t -> true);
    }

    protected BiConsumer<T, T> consumer() {
        return MoreObjects.firstNonNull(consumer, (a, b) -> {});
    }

    public SettingBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public abstract Setting build();

}
