package com.smolka.utils.impl;

import com.smolka.utils.Cell;
import com.smolka.utils.CellsVariant;
import com.smolka.utils.Position;
import com.smolka.utils.PossibleNumberValidationResult;
import com.smolka.utils.PossibleNumbersValidationResult;
import com.smolka.utils.RowIndexManager;
import com.smolka.utils.SubSegmentType;
import com.smolka.utils.UuidElement;
import com.smolka.utils.UuidTree;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


public class RowIndexManagerImpl implements RowIndexManager {

    protected final Map<UUID, UUID> batchRowMap = new HashMap<>();

    protected final Map<UUID, Position> mainPositionIndex = new HashMap<>();

    protected final Map<Position, Set<UUID>> mainPositions = new HashMap<>();

    protected final Map<UUID, CellsVariant> cellsVariantIndex = new HashMap<>();

    protected final Map<ValueIndexKey, Map<Position, Set<UUID>>> valuesIndex = new HashMap<>();

    protected SafetyMatrix safetyMatrix;

    @Override
    public void initializeIndexManager(List<PossibleNumbersValidationResult> validationResults) {
        for (PossibleNumbersValidationResult validationResult : validationResults) {
            if (!validationResult.isValid()) {
                continue;
            }
            mainPositions.put(validationResult.forPosition(), new HashSet<>());
            for (Map.Entry<Integer, PossibleNumberValidationResult> validationResultByNumberEntry : validationResult.validationResultByNumber().entrySet()) {
                PossibleNumberValidationResult validationResultByNumber = validationResultByNumberEntry.getValue();
                if (!validationResultByNumber.isValid()) {
                    continue;
                }
                List<CellsVariant> cellsVariantsFromSquares = validationResultByNumber.segmentValidBatches().stream().map(sb -> sb.getCellsVariantsOfType(SubSegmentType.ROW)).filter(Objects::nonNull).toList();
                for (CellsVariant cellsVariant : cellsVariantsFromSquares) {
                    batchRowMap.put(cellsVariant.getUuid(), cellsVariant.getBatchUUID());
                    mainPositions.get(validationResult.forPosition()).add(cellsVariant.getUuid());
                    cellsVariantIndex.put(cellsVariant.getUuid(), cellsVariant);
                    mainPositionIndex.put(cellsVariant.getUuid(), validationResult.forPosition());

                    putCellsVariantToInversionMap(cellsVariant, validationResult.forPosition());
                }
            }
        }

        this.safetyMatrix = createSafetyMatrix();
    }

    @Override
    public Set<Set<UUID>> getBatchesUuidsNotConflictedByRows() {
        Set<Set<UUID>> rowsResult = new HashSet<>();
        List<UuidTree> uuidTrees = getUuidTrees();

        for (UuidTree tree : uuidTrees) {
            rowsResult.addAll(tree.getRootElement().getUuidVariants());
        }

        return rowsResult.stream()
                .map(s -> s.stream().map(batchRowMap::get).collect(Collectors.toSet()))
                .collect(Collectors.toSet());
    }

    private List<UuidTree> getUuidTrees() {
        if (safetyMatrix == null) {
            throw new RuntimeException("IndexManager is not initialized");
        }

        return createGraphsBySafetyMatrix(safetyMatrix);
    }


    protected Map<Integer, Integer> cellsVariantToPositionMap(CellsVariant cellsVariant) {
        Map<Integer, Integer> positionMap = new HashMap<>();
        for (Cell cell : cellsVariant.getCells()) {
            positionMap.put(cell.position().row(), cell.number());
        }
        return positionMap;
    }

    private List<UuidTree> createGraphsBySafetyMatrix(SafetyMatrix safetyMatrix) {
        Position firstPosition = mainPositions.keySet().stream().findFirst().orElseThrow();
        Set<UUID> firstBatch = mainPositions.get(firstPosition);

        List<UuidTree> uuidGraphs = new ArrayList<>();
        for (UUID uuid : firstBatch) {
            UuidTree newTree = new UuidTree(createGraphVertex(safetyMatrix.getElement(uuid), mainPositions, safetyMatrix), mainPositions.size());
            if (!newTree.rootIsEmpty()) {
                uuidGraphs.add(newTree);
            }
        }

        return uuidGraphs;
    }

    private UuidElement createGraphVertex(SafetyMatrixElement currentElement, Map<Position, Set<UUID>> mainPositions, SafetyMatrix safetyMatrix) {
        Position rootElementPosition = currentElement.mainPosition;
        UUID rootElementUuid = currentElement.uuid;
        Map<Position, Set<UUID>> mainPositionsCopy = getCopyOfPositionMap(mainPositions);

        Map<Position, Set<UUID>> exclusions = currentElement.getDangerousUuidsByPositions();
        UuidElement vertex = new UuidElement(rootElementPosition, rootElementUuid);
        for (Position positionOfExclusions : exclusions.keySet()) {
            if (mainPositionsCopy.containsKey(positionOfExclusions)) {
                mainPositionsCopy.get(positionOfExclusions).removeAll(exclusions.get(positionOfExclusions));
            }
        }

        mainPositionsCopy.remove(rootElementPosition);

        if (mainPositionsCopy.values().stream().anyMatch(Set::isEmpty)) {
            // неудача
            return new UuidElement(rootElementPosition, rootElementUuid);
        }

        for (Position mainPosition : mainPositionsCopy.keySet()) {
            Set<UUID> uuidsForMainPosition = mainPositionsCopy.get(mainPosition);
            for (UUID uuidForMainPosition : uuidsForMainPosition) {
                UuidElement resultForUuid = createGraphVertex(safetyMatrix.getElement(uuidForMainPosition), mainPositionsCopy, safetyMatrix);
                vertex.addNewVertex(resultForUuid);
            }
        }

        return vertex;
    }

    private void putCellsVariantToInversionMap(CellsVariant cellsVariant, Position position) {
        Map<Integer, Integer> positionAndValueMap = cellsVariantToPositionMap(cellsVariant);

        positionAndValueMap.forEach((key, value) -> {
            ValueIndexKey valueIndexKey = new ValueIndexKey(key, value);

            valuesIndex.putIfAbsent(valueIndexKey, new HashMap<>());
            valuesIndex.get(valueIndexKey).putIfAbsent(position, new HashSet<>());
            valuesIndex.get(valueIndexKey).get(position).add(cellsVariant.getUuid());
        });
    }

    private SafetyMatrix createSafetyMatrix() {
        SafetyMatrix safetyMatrix = new SafetyMatrix();

        Map<Integer, Map<Integer, Map<Position, Set<UUID>>>> map = new HashMap<>();

        for (Map.Entry<ValueIndexKey, Map<Position, Set<UUID>>> entry : valuesIndex.entrySet()) {
            int positionIndex = entry.getKey().positionIndex;
            int value = entry.getKey().number;

            map.putIfAbsent(positionIndex, new HashMap<>());
            map.get(positionIndex).putIfAbsent(value, entry.getValue());
        }

        for (Map.Entry<Integer, Map<Integer, Map<Position, Set<UUID>>>> entry : map.entrySet()) {
            safetyMatrix.addSectionToMatrix(entry.getKey(), entry.getValue());
        }

        return safetyMatrix;
    }

    private Map<Position, Set<UUID>> getCopyOfPositionMap(Map<Position, Set<UUID>> positionMap) {
        Map<Position, Set<UUID>> result = new HashMap<>();
        for (Map.Entry<Position, Set<UUID>> mainPositionsEntry : positionMap.entrySet()) {
            result.put(mainPositionsEntry.getKey(), new HashSet<>(mainPositionsEntry.getValue()));
        }

        return result;
    }

    protected static class SafetyMatrix {

        private final Map<UUID, SafetyMatrixElement> safetyMatrixElements;

        public SafetyMatrix() {
            this.safetyMatrixElements = new HashMap<>();
        }

        public SafetyMatrixElement getElement(UUID uuid) {
            return safetyMatrixElements.get(uuid);
        }

        public void addSectionToMatrix(int positionIndex, Map<Integer, Map<Position, Set<UUID>>> valuesAndUuidsByMainPositionMap) {
            for (int v : valuesAndUuidsByMainPositionMap.keySet()) {
                List<Pair<Position, Set<UUID>>> positionAndUuids = valuesAndUuidsByMainPositionMap.get(v).entrySet()
                        .stream()
                        .map(es -> Pair.of(es.getKey(), es.getValue()))
                        .toList();

                for (int i = 0; i < positionAndUuids.size() - 1; i++) {
                    Position currentPosition = positionAndUuids.get(i).getLeft();
                    Set<UUID> currentUuids = positionAndUuids.get(i).getRight();

                    for (int j = i + 1; j < positionAndUuids.size(); j++) {
                        Position otherPosition = positionAndUuids.get(j).getLeft();
                        Set<UUID> otherUuids = positionAndUuids.get(j).getRight();

                        for (UUID currentUuid : currentUuids) {
                            SafetyMatrixElement currentSafetyElement = safetyMatrixElements.computeIfAbsent(currentUuid, u -> new SafetyMatrixElement(u, currentPosition));
                            for (UUID otherUuid : otherUuids) {
                                SafetyMatrixElement otherSafetyElement = safetyMatrixElements.computeIfAbsent(otherUuid, u -> new SafetyMatrixElement(u, otherPosition));
                                currentSafetyElement.addDangerousElementForPositionIndex(positionIndex, otherSafetyElement);
                                otherSafetyElement.addDangerousElementForPositionIndex(positionIndex, currentSafetyElement);
                            }
                        }
                    }
                }
            }
        }
    }

    protected static class SafetyMatrixElement {

        private final UUID uuid;

        private final Position mainPosition;

        private final Map<Integer, Map<Position, Set<SafetyMatrixElement>>> dangerousElementsForThisElementByPositionIndexAndMainPosition;

        public SafetyMatrixElement(UUID uuid, Position mainPosition) {
            this.uuid = uuid;
            this.mainPosition = mainPosition;
            this.dangerousElementsForThisElementByPositionIndexAndMainPosition = new HashMap<>();
        }

        public void addDangerousElementForPositionIndex(int positionIndex, SafetyMatrixElement safeElement) {
            this.dangerousElementsForThisElementByPositionIndexAndMainPosition.putIfAbsent(positionIndex, new HashMap<>());
            this.dangerousElementsForThisElementByPositionIndexAndMainPosition.get(positionIndex).putIfAbsent(safeElement.mainPosition, new HashSet<>());
            this.dangerousElementsForThisElementByPositionIndexAndMainPosition.get(positionIndex).get(safeElement.mainPosition).add(safeElement);
        }

        public Map<Position, Set<UUID>> getDangerousUuidsByPositions() {
            Map<Position, Set<UUID>> result = new HashMap<>();

            dangerousElementsForThisElementByPositionIndexAndMainPosition.values().forEach(map -> {
                map.forEach((key, value) -> {
                    result.putIfAbsent(key, new HashSet<>());
                    result.get(key).addAll(value.stream().map(s -> s.uuid).collect(Collectors.toSet()));
                });
            });

            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            SafetyMatrixElement that = (SafetyMatrixElement) o;
            return Objects.equals(uuid, that.uuid) && Objects.equals(mainPosition, that.mainPosition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, mainPosition);
        }
    }

    protected record ValueIndexKey(
            int positionIndex,
            int number
    ) {

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ValueIndexKey valueIndexKey = (ValueIndexKey) o;
            return number == valueIndexKey.number && positionIndex == valueIndexKey.positionIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(positionIndex, number);
        }
    }
}
