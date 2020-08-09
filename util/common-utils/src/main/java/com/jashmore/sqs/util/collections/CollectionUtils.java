package com.jashmore.sqs.util.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Collection helper functions that can be used instead of having a Guava dependency.
 */
@UtilityClass
public class CollectionUtils {

    public <T> List<T> immutableListOf(T element) {
        return Collections.singletonList(element);
    }

    public <T> List<T> immutableListOf(T one, T two) {
        return Collections.unmodifiableList(Arrays.asList(one, two));
    }

    public <T> List<T> immutableListOf(T one, T two, T three) {
        return Collections.unmodifiableList(Arrays.asList(one, two, three));
    }

    public <T> List<T> immutableListOf(T one, T two, T three, T four) {
        return Collections.unmodifiableList(Arrays.asList(one, two, three, four));
    }

    public <T> List<T> immutableListOf(T one, T two, T three, T four, T five) {
        return Collections.unmodifiableList(Arrays.asList(one, two, three, four, five));
    }

    public <T> List<T> immutableListFrom(Collection<T> one, Collection<T> two) {
        final List<T> underlyingList = new ArrayList<>(one.size() + two.size());
        underlyingList.addAll(one);
        underlyingList.addAll(two);
        return Collections.unmodifiableList(underlyingList);
    }
}
