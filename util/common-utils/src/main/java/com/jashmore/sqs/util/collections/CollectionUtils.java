package com.jashmore.sqs.util.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
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

    public <T, S> Map<T, S> immutableMapOf(T keyOne, S valueOne, T keyTwo, S valueTwo) {
        final Map<T, S> internalMap = new HashMap<>();
        internalMap.put(keyOne, valueOne);
        internalMap.put(keyTwo, valueTwo);
        return Collections.unmodifiableMap(internalMap);
    }

    /**
     * Helper collector that will convert a {@link java.util.stream.Stream} containing {@link Map.Entry}s to a {@link Map} from the entry key to value.
     *
     * @param <K> the type of the key for the map
     * @param <V> the type of the value for the map
     * @return a collector for collecting the stream
     */
    public <K, V> Collector<Map.Entry<K, V>, ?, Map<K, V>> pairsToMap() {
        return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
