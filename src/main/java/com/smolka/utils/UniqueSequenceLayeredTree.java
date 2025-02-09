package com.smolka.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class UniqueSequenceLayeredTree<KEY, VALUE> {

    private BiPredicate<KEY, KEY> crossLayerAdjacencyFunction;

    private BiPredicate<KEY, KEY> crossLayerAdjacencyBranchElementFunction;

    private final Map<KEY, Layer> layers = new HashMap<>();

    private final Set<Set<KEY>> adjacencyGroups = new HashSet<>();

    private boolean isInitialized = false;

    public UniqueSequenceLayeredTreeResult<KEY, VALUE> getResult() {
        if (!isInitialized) {
            throw new RuntimeException("Is not initialized");
        }

        UniqueSequenceLayeredTreeResult<KEY, VALUE> result = new UniqueSequenceLayeredTreeResult<>();
        for (Set<KEY> adjacencyGroup : adjacencyGroups) {
            result.putGroupResult(getResultForAdjacencyGroup(adjacencyGroup));
        }

        return result;
    }


    public boolean initialize(UniqueSequenceLayeredTreeInitializationParameters<KEY, VALUE> initializationParameters) {
        this.isInitialized = true;
        this.crossLayerAdjacencyFunction = initializationParameters.getCrossLayerAdjacencyFunction() == null ?  (key, key2) -> !Objects.equals(key, key2) :  initializationParameters.getCrossLayerAdjacencyFunction();
        this.crossLayerAdjacencyBranchElementFunction = initializationParameters.getCrossLayerAdjacencyBranchElementFunction();

        layers.clear();
        adjacencyGroups.clear();

        for (UniqueSequenceLayeredTreeInitializationParameters.LayerInitializationInfo<KEY, VALUE> layerInitializationInfo : initializationParameters.getLayerInitializationInfoList()) {
            createNewLayer(layerInitializationInfo.layerKey(), new HashSet<>(layerInitializationInfo.valuesToInitialize().keySet()));
        }

        for (Layer layer : layers.values()) {
            Set<Layer> adjacentLayers = getAdjacentLayers(layer);
            Set<KEY> newAdjacentLevelsGroup = new HashSet<>(adjacentLayers.stream().map(Layer::getLayerKey).toList());
            newAdjacentLevelsGroup.add(layer.getLayerKey());
            adjacencyGroups.add(newAdjacentLevelsGroup);
        }

        for (UniqueSequenceLayeredTreeInitializationParameters.LayerInitializationInfo<KEY, VALUE> layerInitializationInfo : initializationParameters.getLayerInitializationInfoList()) {
            Layer layer = layers.get(layerInitializationInfo.layerKey());
            if (!layer.initializeBranches(layerInitializationInfo.valuesToInitialize())) {
                this.isInitialized = false;
                return false;
            }
        }

        for (Layer layer : layers.values()) {
            Set<Layer> adjacentLayers = getAdjacentLayers(layer);
            layer.initializeConflictInfo(adjacentLayers, crossLayerAdjacencyBranchElementFunction);
        }

        for (Layer layer : layers.values()) {
            Set<Layer> adjacentLayers = getAdjacentLayers(layer);
            layer.initializeInversionInfo(adjacentLayers);
        }

        return true;
    }

    private AdjacencyGroupResult<KEY, VALUE> getResultForAdjacencyGroup(Set<KEY> adjacencyGroupList) {
        Map<UUID, Branch> allBranches = new HashMap<>();
        for (KEY adjacencyGroupElem : adjacencyGroupList) {
            Layer layer = layers.get(adjacencyGroupElem);
            allBranches.putAll(layer.getBranchesMap());
        }

        AdjacencyGroupResult<KEY, VALUE> result = new AdjacencyGroupResult<>(adjacencyGroupList);

        Graph graph = createGraphForAdjacencyGroup(adjacencyGroupList);
        List<List<UUID>> uuidsBatches = graph.getTraversalResult();

        for (List<UUID> uuidsBatch : uuidsBatches) {
            List<Map<KEY, VALUE>> branchList = new ArrayList<>();
            for (UUID uuid : uuidsBatch) {
                branchList.add(allBranches.get(uuid).getValuesWithKeys());
            }

            result.put(branchList);
        }

        return result;
    }

    private Graph createGraphForAdjacencyGroup(Set<KEY> adjacencyGroup) {
        Map<UUID, GraphNode> createdNodesMap = new HashMap<>();
        Map<UUID, Map<KEY, Set<UUID>>> commonInversionsMap = new HashMap<>();

        List<KEY> layersKeys = adjacencyGroup.stream().toList();
        KEY firstLayerKey = layersKeys.getFirst();
        KEY lastLayerKey = layersKeys.getLast();

        Layer firstLayer = layers.get(firstLayerKey);

        for (int i = 0; i < layersKeys.size(); i++) {
            KEY currentLayerKey = layersKeys.get(i);
            KEY nextLayerKey = currentLayerKey.equals(lastLayerKey) ? null : layersKeys.get(i + 1);
            Layer currentLayer = layers.get(currentLayerKey);
            commonInversionsMap.putAll(currentLayer.getInversionsMap());

            Set<UUID> currentUuids = currentLayer.getAllBranchesUuids();
            for (UUID current : currentUuids) {
                GraphNode currentNode = new GraphNode(nextLayerKey, currentLayerKey, current);
                createdNodesMap.put(currentNode.getUuid(), currentNode);
            }
        }

        for (UUID uuid : createdNodesMap.keySet()) {
            GraphNode graphNode = createdNodesMap.get(uuid);
            if (graphNode.getCurrentLayerKey().equals(lastLayerKey)) {
                continue;
            }
            Map<KEY, Set<UUID>> inversionsForNode = commonInversionsMap.get(graphNode.getUuid());
            if (inversionsForNode == null) {
                continue;
            }
            Set<UUID> uuidsForNextPosition = inversionsForNode.get(graphNode.getNextLayerKey());
            if (uuidsForNextPosition == null) {
                continue;
            }
            for (UUID nextUuid : uuidsForNextPosition) {
                GraphNode nextNode = createdNodesMap.get(nextUuid);
                graphNode.addNextNode(nextNode);
            }
        }

        Graph graph = new Graph(firstLayerKey, layersKeys.size(), commonInversionsMap);
        for (UUID rootUuid : firstLayer.getAllBranchesUuids()) {
            graph.addRootNode(createdNodesMap.get(rootUuid));
        }

        return graph;
    }

    private void createNewLayer(KEY layerKey, Set<KEY> layerStructure) {
        layers.put(layerKey, new Layer(layerKey, layerStructure));
    }

    private Set<Layer> getAdjacentLayers(Layer layer) {
        return layers.values().stream().filter(l -> crossLayerAdjacencyFunction.test(layer.getLayerKey(), l.getLayerKey())).collect(Collectors.toSet());
    }

    private class Layer {

        private final KEY layerKey;

        private final Map<UUID, Branch> branches;

        private final Set<KEY> layerStructure;

        private final Map<KeyValueIndex, Set<UUID>> branchesByKeyValueIndex;

        private final Map<UUID, Map<KEY, Set<UUID>>> conflictMap;

        private final Map<UUID, Map<KEY, Set<UUID>>> inversionsMap;

        public Layer(KEY layerKey, Set<KEY> layerStructure) {
            this.layerKey = layerKey;
            this.layerStructure = layerStructure;
            this.layerStructure.add(layerKey);
            this.branches = new HashMap<>();
            this.branchesByKeyValueIndex = new HashMap<>();
            this.conflictMap = new HashMap<>();
            this.inversionsMap = new HashMap<>();
        }

        public Map<UUID, Branch> getBranchesMap() {
            return branches;
        }

        public Map<UUID, Map<KEY, Set<UUID>>> getInversionsMap() {
            return inversionsMap;
        }

        public Set<UUID> getAllBranchesUuids() {
            return branches.keySet();
        }

        public boolean initializeBranches(Map<KEY, Set<VALUE>> values) {
            if (!Objects.equals(values.keySet(), layerStructure)) {
                throw new RuntimeException("Map doesn't correspond the structure");
            }

            List<Map<KEY, VALUE>> variants = VariantsUtils.getAllVariants(values);
            if (variants.isEmpty()) {
                return false;
            }

            for (Map<KEY, VALUE> variant : variants) {
                Branch newBranch = new Branch(this.layerKey, variant);
                branches.put(newBranch.getBranchKey(), newBranch);
                putBranchInKeyValueIndex(newBranch);
            }
            return true;
        }

        public void initializeConflictInfo(Set<Layer> adjacentLayers, BiPredicate<KEY, KEY> crossLayerAdjacencyBranchElementFunction) {
            for (Layer adjacentLayer : adjacentLayers) {
                if (adjacentLayer.isEmpty()) {
                    continue;
                }

                for (KeyValueIndex keyValueIndex : branchesByKeyValueIndex.keySet()) {
                    KEY adjacentKey = adjacentLayer.layerStructure.stream().filter(k -> crossLayerAdjacencyBranchElementFunction.test(keyValueIndex.key, k)).findFirst().orElse(null);
                    if (adjacentKey == null) {
                        continue;
                    }

                    VALUE valueFromCurrentIndex = keyValueIndex.value;

                    Set<UUID> currentUuids = branchesByKeyValueIndex.get(keyValueIndex);

                    KeyValueIndex adjacentKeyValueIndex = new KeyValueIndex(adjacentKey, valueFromCurrentIndex);
                    Set<UUID> conflictedUuids = adjacentLayer.branchesByKeyValueIndex.get(adjacentKeyValueIndex);
                    if (conflictedUuids == null) {
                        continue;
                    }

                    for (UUID currentUuid : currentUuids) {
                        putInConflictMap(currentUuid, adjacentLayer.layerKey, conflictedUuids);
                    }

                    for (UUID conflictedUuid : conflictedUuids) {
                        adjacentLayer.putInConflictMap(conflictedUuid, layerKey, currentUuids);
                    }
                }
            }
        }

        public void initializeInversionInfo(Set<Layer> adjacentLayers) {
            for (Layer adjacentLayer : adjacentLayers) {
                for (Branch branch : branches.values()) {
                    Set<UUID> compatibleBranchesFromAdjacentLayer = new HashSet<>(adjacentLayer.getAllBranchesUuids());
                    Set<UUID> dangerousBranchesForAdjacentLayerInCurrentBranch = conflictMap.get(branch.getBranchKey()).get(adjacentLayer.getLayerKey()) == null ? new HashSet<>() : conflictMap.get(branch.getBranchKey()).get(adjacentLayer.getLayerKey());
                    compatibleBranchesFromAdjacentLayer.removeAll(dangerousBranchesForAdjacentLayerInCurrentBranch);

                    inversionsMap.putIfAbsent(branch.getBranchKey(), new HashMap<>());
                    inversionsMap.get(branch.getBranchKey()).put(adjacentLayer.getLayerKey(), compatibleBranchesFromAdjacentLayer);
                }
            }
        }

        public void putInConflictMap(UUID branchInCurrentLayer, KEY otherLayerKey, Set<UUID> conflictedBranchesUuids) {
            conflictMap.putIfAbsent(branchInCurrentLayer, new HashMap<>());
            conflictMap.get(branchInCurrentLayer).putIfAbsent(otherLayerKey, new HashSet<>());
            conflictMap.get(branchInCurrentLayer).get(otherLayerKey).addAll(conflictedBranchesUuids);
        }

        public KEY getLayerKey() {
            return layerKey;
        }

        public boolean isEmpty() {
            return branches.isEmpty();
        }

        private void putBranchInKeyValueIndex(Branch branch) {
            for (Map.Entry<KEY, VALUE> valuesWithKeys : branch.getValuesWithKeys().entrySet()) {
                KEY key = valuesWithKeys.getKey();
                VALUE value = valuesWithKeys.getValue();

                KeyValueIndex keyValueIndex = new KeyValueIndex(key, value);
                branchesByKeyValueIndex.putIfAbsent(keyValueIndex, new HashSet<>());
                branchesByKeyValueIndex.get(keyValueIndex).add(branch.getBranchKey());
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Layer layer = (Layer) o;
            return Objects.equals(layerKey, layer.layerKey);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(layerKey);
        }
    }

    private class Branch {

        private final KEY layerKey;

        private final UUID branchKey;

        private final Map<KEY, VALUE> valuesWithKeys;

        public Branch(KEY layerKey, Map<KEY, VALUE> valuesWithKeys) {
            this.branchKey = UUID.randomUUID();
            this.layerKey = layerKey;
            this.valuesWithKeys = valuesWithKeys;
        }

        public KEY getLayerKey() {
            return layerKey;
        }

        public UUID getBranchKey() {
            return branchKey;
        }

        public Map<KEY, VALUE> getValuesWithKeys() {
            return valuesWithKeys;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Branch branch = (Branch) o;
            return Objects.equals(branchKey, branch.branchKey);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(branchKey);
        }
    }

    private class KeyValueIndex {

        private final KEY key;

        private final VALUE value;

        public KeyValueIndex(KEY key, VALUE value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            KeyValueIndex that = (KeyValueIndex) o;
            return Objects.equals(key, that.key) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }
    }

    private class Graph {

        private final KEY rootLayerKey;

        private final int layersCount;

        private final List<GraphNode> rootNodes;

        private final Map<UUID, Map<KEY, Set<UUID>>> inversionsMap;

        public Graph(KEY rootLayerKey, int layersCount, Map<UUID, Map<KEY, Set<UUID>>> inversionsMap) {
            this.rootLayerKey = rootLayerKey;
            this.layersCount = layersCount;
            this.rootNodes = new ArrayList<>();
            this.inversionsMap = inversionsMap;
        }

        public void addRootNode(GraphNode rootNode) {
            assert rootNode.getCurrentLayerKey().equals(rootLayerKey);

            rootNodes.add(rootNode);
        }

        public List<List<UUID>> getTraversalResult() {
            List<List<UUID>> result = new ArrayList<>();

            for (GraphNode rootNode : rootNodes) {
                GraphStep step = new GraphStep(rootNode);
                GraphStep localResult = step(step);

                result.addAll(localResult.resultSet);
            }

            return result;
        }

        private GraphStep step(GraphStep step) {
            step.addCurrentUuidToPassedPath();

            GraphNode currentNode = step.currentNode;
            if (currentNode.isEnd()) {
                if (step.passedPath.size() == layersCount) {
                    step.addPassedPathToResultSet();
                }
                return step;
            }

            Map<KEY, Set<UUID>> inversionsForCurrent = inversionsMap.get(step.currentNode.getUuid());
            if (inversionsForCurrent == null) {
                return step;
            }
            if (step.actualInversions == null) {
                step.setActualInversions(inversionsForCurrent);
            } else {
                Map<KEY, Set<UUID>> newInversions = new HashMap<>();
                for (Map.Entry<KEY, Set<UUID>> entry : step.actualInversions.entrySet()) {
                    KEY keyFromActual = entry.getKey();
                    Set<UUID> inversionFromActual = entry.getValue();

                    if (inversionFromActual == null || inversionFromActual.isEmpty()) {
                        continue;
                    }

                    Set<UUID> inversionForCurrent = inversionsForCurrent.get(keyFromActual);

                    if (inversionForCurrent == null || inversionForCurrent.isEmpty()) {
                        continue;
                    }

                    Set<UUID> intersections = new HashSet<>(inversionFromActual);
                    intersections.retainAll(inversionForCurrent);

                    if (intersections.isEmpty()) {
                        return step;
                    }

                    newInversions.put(keyFromActual, intersections);
                }
                step.setActualInversions(newInversions);
            }

            KEY nextLayer = currentNode.getNextLayerKey();
            Set<UUID> nextUuidsToStep = step.actualInversions.get(nextLayer);
            if (nextUuidsToStep == null) {
                return step;
            }

            Map<UUID, GraphNode> nextNodes = currentNode.getNextNodes();

            for (UUID nextUuidToStep : nextUuidsToStep) {
                GraphNode newGraphNode = nextNodes.get(nextUuidToStep);
                if (newGraphNode == null) {
                    continue;
                }
                GraphStep newStep = step.copyWithNewUuidAndNextNodes(newGraphNode);
                step(newStep);
            }

            return step;
        }

        private class GraphStep {

            private Map<KEY, Set<UUID>> actualInversions;

            private List<List<UUID>> resultSet;

            private final Set<UUID> passedPath;

            private final GraphNode currentNode;

            public GraphStep(GraphNode currentNode) {
                this.currentNode = currentNode;
                this.resultSet = new ArrayList<>();
                this.passedPath = new HashSet<>();
            }

            public void setActualInversions(Map<KEY, Set<UUID>> actualInversions) {
                this.actualInversions = actualInversions;
            }

            public GraphStep copyWithNewUuidAndNextNodes(GraphNode newNode) {
                GraphStep copy = new GraphStep(newNode);
                copy.passedPath.addAll(passedPath);
                copy.resultSet = resultSet;
                copy.actualInversions = safeCopyOfActualInversions(actualInversions);

                return copy;
            }

            public void addCurrentUuidToPassedPath() {
                passedPath.add(currentNode.getUuid());
            }

            public void addPassedPathToResultSet() {
                resultSet.add(new ArrayList<>(passedPath));
            }

            private Map<KEY, Set<UUID>> safeCopyOfActualInversions(Map<KEY, Set<UUID>> actualInversions) {
                Map<KEY, Set<UUID>> copy = new HashMap<>();
                for (Map.Entry<KEY, Set<UUID>> actualInversionEntry : actualInversions.entrySet()) {
                    KEY key = actualInversionEntry.getKey();
                    Set<UUID> uuids = actualInversionEntry.getValue();

                    copy.put(key, new HashSet<>(uuids));
                }

                return copy;
            }
        }
    }

    protected class GraphNode {

        private final UUID uuid;

        private final KEY currentLayerKey;

        private final KEY nextLayerKey;

        private final Map<UUID, GraphNode> nextNodes;

        public GraphNode(KEY nextLayerKey, KEY currentLayerKey, UUID uuid) {
            this.nextLayerKey = nextLayerKey;
            this.currentLayerKey = currentLayerKey;
            this.uuid = uuid;
            this.nextNodes = new HashMap<>();
        }

        public void addNextNode(GraphNode nextNode) {
            if (nextLayerKey == null) {
                throw new RuntimeException("Can not add such node");
            }
            assert nextNode.currentLayerKey.equals(nextLayerKey);

            nextNodes.put(nextNode.getUuid(), nextNode);
        }

        public UUID getUuid() {
            return uuid;
        }

        public KEY getCurrentLayerKey() {
            return currentLayerKey;
        }

        public KEY getNextLayerKey() {
            return nextLayerKey;
        }

        public Map<UUID, GraphNode> getNextNodes() {
            return nextNodes;
        }

        public boolean isEnd() {
            return nextLayerKey == null || nextNodes.isEmpty();
        }
    }
}
