package com.smolka.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

public class UniqueSequenceLayeredTreeInitializationParameters<KEY, VALUE> {

    private BiPredicate<KEY, KEY> crossLayerAdjacencyFunction;

    private final BiPredicate<KEY, KEY> crossLayerAdjacencyBranchElementFunction;

    private final List<LayerInitializationInfo<KEY, VALUE>> layerInitializationInfoList;

    public UniqueSequenceLayeredTreeInitializationParameters(BiPredicate<KEY, KEY> crossLayerAdjacencyBranchElementFunction) {
        this.crossLayerAdjacencyBranchElementFunction = crossLayerAdjacencyBranchElementFunction;
        this.layerInitializationInfoList = new ArrayList<>();
    }

    public UniqueSequenceLayeredTreeInitializationParameters(BiPredicate<KEY, KEY> crossLayerAdjacencyFunction, BiPredicate<KEY, KEY> crossLayerAdjacencyBranchElementFunction) {
        this.crossLayerAdjacencyFunction = crossLayerAdjacencyFunction;
        this.crossLayerAdjacencyBranchElementFunction = crossLayerAdjacencyBranchElementFunction;
        this.layerInitializationInfoList = new ArrayList<>();
    }

    public void addLayerInitializationInfo(KEY layerKey, Map<KEY, Set<VALUE>> valuesToInitialize) {
        layerInitializationInfoList.add(new LayerInitializationInfo<>(layerKey, valuesToInitialize));
    }

    public BiPredicate<KEY,KEY> getCrossLayerAdjacencyFunction() {
        return crossLayerAdjacencyFunction;
    }

    public BiPredicate<KEY,KEY> getCrossLayerAdjacencyBranchElementFunction() {
        return crossLayerAdjacencyBranchElementFunction;
    }

    public List<LayerInitializationInfo<KEY, VALUE>> getLayerInitializationInfoList() {
        return layerInitializationInfoList;
    }

    public record LayerInitializationInfo<KEY, VALUE>(KEY layerKey, Map<KEY, Set<VALUE>> valuesToInitialize) {
    }
}
