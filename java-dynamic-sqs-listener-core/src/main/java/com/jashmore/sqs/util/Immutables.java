package com.jashmore.sqs.util;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class used to provide helper methods to construct Immutable implementations of data structures.
 *
 * <p>This has been implemented again to reduce dependencies on other modules, more specifically Guava.
 */
@UtilityClass
public final class Immutables {
    /**
     * Build an immutable map for the key value pairs provided.
     *
     * @param key   key of the single map element
     * @param value value of the single map element
     * @param <K>   the type of the key in the map
     * @param <V>   the type of the value in the map
     * @return an immutable map
     */
    public static <K, V> Map<K, V> immutableMap(final K key, final V value) {
        final Map<K, V> mutableMap = new HashMap<>();
        mutableMap.put(key, value);
        return Collections.unmodifiableMap(mutableMap);
    }

    /**
     * Build an immutable list for the collection of items provided.
     *
     * @param items the items to include in the list
     * @param <T>   the type of the items in the list
     * @return an immutable list containing all of the items
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> List<T> immutableList(final T... items) {
        return Arrays.stream(items)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

    }

    /**
     * Build an immutable set for the collection of items provided.
     *
     * @param items the items to include in the set
     * @param <T>   the type of the items in the set
     * @return an immutable set containing all of the items
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Set<T> immutableSet(final T... items) {
        return Arrays.stream(items)
                .collect(collectingAndThen(toSet(), Collections::unmodifiableSet));
    }
}
