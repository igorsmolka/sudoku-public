package com.smolka.utils;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VariantsUtils {

    public static <K, V> List<Map<K, V>> getAllVariants(Map<K, Set<V>> keyValuesMap) {
        List<Map<K, V>> result = new ArrayList<>();
        Map<K, V> bufMap = new HashMap<>();
        Set<V> bufSet = new HashSet<>();

        List<Pair<K, List<V>>> content = keyValuesMap.entrySet().stream()
                        .map(es -> Pair.of(es.getKey(), es.getValue().stream().toList()))
                        .toList();

        variantsFillingInRecursion(content, result, bufMap, bufSet, 0, 0);

        return result;
    }

    private static <K, V> void variantsFillingInRecursion(List<Pair<K, List<V>>> content,
                                                          List<Map<K, V>> result,
                                                          Map<K, V> bufMap,
                                                          Set<V> bufSet,
                                                          int currentLevelIndex,
                                                          int currentValueIndex) {
        Pair<K, List<V>> pair = content.get(currentLevelIndex);
        K key = pair.getKey();
        List<V> values = pair.getValue();

        boolean isLastLevel = currentLevelIndex == content.size() - 1;
        boolean isLastValueInLevel = currentValueIndex == values.size() - 1;

        V currValue = values.get(currentValueIndex);

        if (!bufSet.contains(currValue)) {
            bufMap.put(key, currValue);

            if (isLastLevel) {
                if (bufMap.size() == content.size()) {
                    result.add(new HashMap<>(bufMap));
                }

                if (!isLastValueInLevel) {
                    variantsFillingInRecursion(content, result, new HashMap<>(bufMap), new HashSet<>(bufSet), currentLevelIndex, currentValueIndex + 1);
                }
                return;
            }

            Set<V> newBufSet = new HashSet<>(bufSet);
            newBufSet.add(currValue);

            variantsFillingInRecursion(content, result, new HashMap<>(bufMap), newBufSet, currentLevelIndex + 1, 0);
        }

        if (!isLastValueInLevel) {
            variantsFillingInRecursion(content, result, new HashMap<>(bufMap), new HashSet<>(bufSet), currentLevelIndex, currentValueIndex + 1);
        }
    }
}
