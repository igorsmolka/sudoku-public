package com.smolka.impl;

import com.smolka.Sudoku;
import com.smolka.utils.Cell;
import com.smolka.utils.MissedNumberMetaInfo;
import com.smolka.utils.Position;
import com.smolka.utils.PositionPotential;
import com.smolka.utils.PositionUtils;
import com.smolka.utils.SegmentInfo;
import com.smolka.utils.UniqueSequenceLayeredTree;
import com.smolka.utils.UniqueSequenceLayeredTreeInitializationParameters;
import com.smolka.utils.UniqueSequenceLayeredTreeResult;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SudokuImpl implements Sudoku {

    private static final int EMPTY_ELEM = 0;

    private final int n;

    private final int sqrN;

    private final int checksum;

    private final int[][] field;

    public SudokuImpl(int n, int[][] field) {
        assert field.length != 0;
        for (int[] row : field) {
            assert row.length == field.length;
        }
        this.n = n;
        this.field = field;
        this.sqrN = n * n;
        this.checksum = (sqrN * (sqrN + 1)) / 2;
        assert field.length == sqrN;
    }

    @Override
    public int[][] getVariant() {
        Set<Cell> cells = getCells(this.field);

        Map<Position, PositionPotential> positionPotentialMap = createNewPotentialMapForCells(cells);

        Set<Position> positionsToAnalyze = getPositionsForAnalyze(positionPotentialMap);

        UniqueSequenceLayeredTreeInitializationParameters<Position, Integer> rowVariantsInitializingParams = new UniqueSequenceLayeredTreeInitializationParameters<>((p1, p2) -> p1.column() == p2.column());

        for (Position positionToAnalyze : positionsToAnalyze) {
            Map<Position, PositionPotential> positionSegment = getSegment(positionToAnalyze, positionPotentialMap);
            positionSegment.put(positionToAnalyze, positionPotentialMap.get(positionToAnalyze));

            Map<Position, Set<Integer>> positionsByColumnWithPossibleNumbers = positionSegment.entrySet()
                    .stream()
                    .filter(p -> Objects.equals(p.getKey().row(), positionToAnalyze.row()))
                    .collect(Collectors.toMap(Map.Entry::getKey, es -> es.getValue().getPossibleNumbers()));

            rowVariantsInitializingParams.addLayerInitializationInfo(positionToAnalyze, positionsByColumnWithPossibleNumbers);
        }

        UniqueSequenceLayeredTree<Position, Integer> rowVariantsTree = new UniqueSequenceLayeredTree<>();
        boolean rowInitializedCorrectly = rowVariantsTree.initialize(rowVariantsInitializingParams);

        if (!rowInitializedCorrectly) {
            return null;
        }

        UniqueSequenceLayeredTreeResult<Position, Integer> resultFromTree = rowVariantsTree.getResult();
        if (resultFromTree.getAdjacencyGroupResults().isEmpty()) {
            return null;
        }

        // по строкам/столбцам может быть только одна группа смежности уровней
        int[][] result = null;
        for (List<Map<Position, Integer>> variants : resultFromTree.getAdjacencyGroupResults().getFirst().getResult()) {
            int[][] copy = copyOfField(this.field);
            for (Map<Position, Integer> variantForRow : variants) {
                fillFieldFromMap(copy, variantForRow);
            }
            if (checkVariant(copy)) {
                result = copy;
                break;
            }
        }

        return result;
    }

    @Override
    public boolean checkVariant(int[][] variant) {
        if (variant.length != field.length) {
            return false;
        }
        for (int[] variantRow : variant) {
            if (variantRow.length != field.length) {
                return false;
            }
        }

        SudokuImpl variantInSudoku = new SudokuImpl(n, variant);

        for (int i = 0; i < sqrN; i++) {
            if (isNumbersMissedByCoordinatesInMatrix(getNotEmptyElementsWithCoordinatesForRow(i), variant)
                    || isNumbersMissedByCoordinatesInMatrix(getNotEmptyElementsWithCoordinatesForColumn(i), variant)
                    || isNumbersMissedByCoordinatesInMatrix(getNotEmptyElementsWithCoordinatesForSquare(i), variant)) {
                return false;
            }

            int sumByRow = variantInSudoku.getNotEmptyElementsWithCoordinatesForRow(i).stream().map(Pair::getKey).reduce(Integer::sum).orElse(0);
            int sumByColumn = variantInSudoku.getNotEmptyElementsWithCoordinatesForColumn(i).stream().map(Pair::getKey).reduce(Integer::sum).orElse(0);
            int sumBySquare = variantInSudoku.getNotEmptyElementsWithCoordinatesForSquare(i).stream().map(Pair::getKey).reduce(Integer::sum).orElse(0);

            if (sumByRow != checksum || sumByColumn != checksum || sumBySquare != checksum) {
                return false;
            }
        }

        return true;
    }


    private Set<Position> getPositionsForAnalyze(Map<Position, PositionPotential> positionPotentialMap) {
        Set<Position> result = new HashSet<>();

        // получаем все свободные строки
        Set<Integer> rowsToFeel = positionPotentialMap.keySet().stream().map(Position::row).collect(Collectors.toSet());

        // т.к. мы будем проводить манипуляции с мапой, которые не должны отразиться на изначальном объекте - получаем ее копию
        Map<Position, PositionPotential> positionPositionPotentialCopy = copyOfPotentialMap(positionPotentialMap);

        for (int row : rowsToFeel) {
            // получаем первую позицию с данной строкой; если таковых нет - доудалялись. придется восстанавливать мапу, откинув только уже выбранные опорные точки.
            Position firstPositionWithRow = positionPositionPotentialCopy.keySet().stream().filter(p -> p.row() == row).findFirst().orElse(null);
            if (firstPositionWithRow == null) {
                positionPositionPotentialCopy = copyOfPotentialMap(positionPotentialMap);
                for (Position position : result) {
                    positionPositionPotentialCopy.remove(position);
                }
                firstPositionWithRow = positionPositionPotentialCopy.keySet().stream().filter(p -> p.row() == row).findFirst().orElseThrow();
            }

            // добавляем в результат первую позицию по строке
            result.add(firstPositionWithRow);

            Set<Position> positionsToDelete = new HashSet<>();
            positionsToDelete.add(firstPositionWithRow);

            int squareOfPosition = positionPositionPotentialCopy.get(firstPositionWithRow).getSquareIndex();

            // все связанные по квадрату, столбцу или строке позиции убираем из рассмотрения - пока не расставим все точки либо пока не останется места для распределения на следующие строки
            Position finalFirstPositionWithRow = firstPositionWithRow;
            Set<Position> connectedPositions = positionPositionPotentialCopy.entrySet().stream().filter(es -> {
                Position position = es.getKey();
                PositionPotential positionPotential = es.getValue();

                return position.row() == finalFirstPositionWithRow.row() || position.column() == finalFirstPositionWithRow.column() || positionPotential.getSquareIndex() == squareOfPosition;
            }).map(Map.Entry::getKey).collect(Collectors.toSet());

            // удаляем
            positionsToDelete.addAll(connectedPositions);

            for (Position positionToDelete : positionsToDelete) {
                positionPositionPotentialCopy.remove(positionToDelete);
            }
        }

        return result;
    }

    private Map<Position, PositionPotential> copyOfPotentialMap(Map<Position, PositionPotential> positionPotentialMap) {
        Map<Position, PositionPotential> copy = new HashMap<>();
        for (Map.Entry<Position, PositionPotential> entry : positionPotentialMap.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().safeCopy());
        }

        return copy;
    }

    private void fillFieldFromMap(int[][] field, Map<Position, Integer> valuesKeysMap) {
        if (valuesKeysMap == null) {
            return;
        }

        for (Position position : valuesKeysMap.keySet()) {
            field[position.row()][position.column()] = valuesKeysMap.get(position);
        }
    }

    private Map<Position, PositionPotential> getSegment(Position position, Map<Position, PositionPotential> positionPotentialMap) {
        SegmentInfo segment = getPositionSegment(position, positionPotentialMap);

        Set<Position> positionsFromSegment = positionPotentialMap.keySet().stream().filter(segment::positionInSegment).collect(Collectors.toSet());

        Map<Position, PositionPotential> result = new HashMap<>();

        for (Map.Entry<Position, PositionPotential> entry : positionPotentialMap.entrySet()) {
            Position key = entry.getKey();
            if (!positionsFromSegment.contains(key)) {
                continue;
            }
            PositionPotential value = entry.getValue();

            result.put(key, value);
        }

        return result;
    }

    private Map<Position, PositionPotential> createNewPotentialMapForCells(Set<Cell> cells) {
        Set<MissedNumberMetaInfo> missedNumbersMetaInfo = getMissedNumbersMetaInfo(cells);
        Map<Position, PositionPotential> positionPotentialMap = getMapWithPositionsPotentials(missedNumbersMetaInfo);
        fillSegmentsForAllPositions(positionPotentialMap);

        return positionPotentialMap;
    }

    private void fillSegmentsForAllPositions(Map<Position, PositionPotential> positionsPotentialMap) {
        for (Map.Entry<Position, PositionPotential> entry : positionsPotentialMap.entrySet()) {
            Position position = entry.getKey();
            PositionPotential positionPotential = entry.getValue();

            positionPotential.setSegment(getPositionSegment(position, positionsPotentialMap));
        }
    }

    private SegmentInfo getPositionSegment(Position position, Map<Position, PositionPotential> positionsPotentialMap) {
        Set<Position> positionsForSegment = getPotentialPositionsInRow(positionsPotentialMap, position);
        positionsForSegment.addAll(getPotentialPositionsInColumn(positionsPotentialMap, position));
        positionsForSegment.addAll(getPotentialPositionsWithCopiedNumbersInSquare(positionsPotentialMap, position));

        Set<Integer> potentialNumbers = new HashSet<>();
        positionsForSegment.forEach(pos -> potentialNumbers.addAll(positionsPotentialMap.get(pos).getPossibleNumbers()));

        return new SegmentInfo(position, potentialNumbers, positionsForSegment);
    }

    private Set<Position> getPotentialPositionsWithCopiedNumbersInSquare(Map<Position, PositionPotential> positionAndPossibleNumbersMap, Position currentPosition) {
        int squareIndex = getSquareIndexByPosition(currentPosition);

        Set<Position> resultSet = new HashSet<>();
        for (Map.Entry<Position, PositionPotential> entry : positionAndPossibleNumbersMap.entrySet()) {
            Position position = entry.getKey();
            if (!Objects.equals(position, currentPosition) && PositionUtils.positionInSquare(position, squareIndex, n)) {
                resultSet.add(position);
            }
        }

        return resultSet;
    }

    private Set<Position> getPotentialPositionsInColumn(Map<Position, PositionPotential> positionAndPossibleNumbersMap, Position currentPosition) {
        Set<Position> resultSet = new HashSet<>();
        for (Map.Entry<Position, PositionPotential> entry : positionAndPossibleNumbersMap.entrySet()) {
            Position position = entry.getKey();

            if (!Objects.equals(position, currentPosition) && position.column() == currentPosition.column()) {
                resultSet.add(position);
            }
        }

        return resultSet;
    }

    private Set<Position> getPotentialPositionsInRow(Map<Position, PositionPotential> positionAndPossibleNumbersMap, Position currentPosition) {
        Set<Position> resultSet = new HashSet<>();
        for (Map.Entry<Position, PositionPotential> entry : positionAndPossibleNumbersMap.entrySet()) {
            Position position = entry.getKey();

            if (!Objects.equals(position, currentPosition) && position.row() == currentPosition.row()) {
                resultSet.add(position);
            }
        }

        return resultSet;
    }

    private Map<Position, PositionPotential> getMapWithPositionsPotentials(Set<MissedNumberMetaInfo> missedNumbersMetaInfo) {
        Map<Position, PositionPotential> result = new HashMap<>();
        for (MissedNumberMetaInfo missedNumberMetaInfo : missedNumbersMetaInfo) {
            int number = missedNumberMetaInfo.number();

            for (Position position : missedNumberMetaInfo.potentialPositions()) {
                result.putIfAbsent(position, new PositionPotential(new HashSet<>(), getSquareIndexByPosition(position)));
                result.get(position).addPossibleNumber(number);
            }
        }

        return result;
    }

    private Set<Cell> getCells(int[][] field) {
        Set<Cell> cells = new HashSet<>();

        for (int i = 0; i < field.length; i++) {
            for (int j = 0; j < field[i].length; j++) {
                int number = field[i][j];
                cells.add(new Cell(new Position(i, j), number != EMPTY_ELEM ? number : null));
            }
        }

        return cells;
    }

    private int[][] copyOfField(int[][] field) {
        int[][] copy = new int[sqrN][sqrN];
        for (int i = 0; i < copy.length; i++) {
            System.arraycopy(field[i], 0, copy[i], 0, copy[i].length);
        }

        return copy;
    }

    private Set<MissedNumberMetaInfo> getMissedNumbersMetaInfo(Set<Cell> allCells) {
        Map<Integer, MissedNumberMetaInfo> map = new HashMap<>();

        for (int i = 0; i < sqrN; i++) {
            Set<Integer> missedNumbersInRow = getMissedNumbersForRow(i, allCells);
            Set<Integer> missedNumbersInColumn = getMissedNumbersForColumn(i, allCells);
            Set<Integer> missedNumbersInSquare = getMissedNumbersForSquare(i, allCells);

            for (int missedNumberInRow : missedNumbersInRow) {
                map.putIfAbsent(missedNumberInRow, new MissedNumberMetaInfo(missedNumberInRow, new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>()));
                MissedNumberMetaInfo missedNumberMetaInfo = map.get(missedNumberInRow);
                missedNumberMetaInfo.missedInRows().add(i);
            }

            for (int missedNumberInColumn : missedNumbersInColumn) {
                map.putIfAbsent(missedNumberInColumn, new MissedNumberMetaInfo(missedNumberInColumn, new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>()));
                MissedNumberMetaInfo missedNumberMetaInfo = map.get(missedNumberInColumn);
                missedNumberMetaInfo.missedInColumns().add(i);
            }

            for (int missedNumberInSquare : missedNumbersInSquare) {
                map.putIfAbsent(missedNumberInSquare, new MissedNumberMetaInfo(missedNumberInSquare, new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>()));
                MissedNumberMetaInfo missedNumberMetaInfo = map.get(missedNumberInSquare);
                missedNumberMetaInfo.missedInSquares().add(i);
            }
        }

        for (MissedNumberMetaInfo metaInfo : map.values()) {
            Set<Position> potentialPositions = new HashSet<>();
            for (int row : metaInfo.missedInRows()) {
                Set<Integer> potentialColumns = getFreeColumnsInRow(row, allCells);
                potentialColumns.retainAll(metaInfo.missedInColumns());
                Set<Integer> squaresInRowContains = PositionUtils.squaresInRow(row, n).stream()
                        .filter(squareIndex -> !metaInfo.missedInSquares().contains(squareIndex))
                        .collect(Collectors.toSet());

                for (int column : potentialColumns) {
                    Position potentialPosition = new Position(row, column);
                    if (PositionUtils.positionInAnySquare(potentialPosition, squaresInRowContains, n)) {
                        continue;
                    }
                    potentialPositions.add(potentialPosition);
                }
            }

            metaInfo.addPotentialPositions(potentialPositions);
        }

        return new HashSet<>(map.values());
    }

    private Set<Integer> getMissedNumbersForRow(int row, Set<Cell> allCells) {
        Set<Integer> hints = allCells.stream().filter(cell -> cell.position().row() == row && !cell.isEmpty()).map(Cell::number).collect(Collectors.toSet());
        return IntStream.range(1, sqrN + 1).filter(e -> !hints.contains(e)).boxed().collect(Collectors.toSet());
    }

    private Set<Integer> getMissedNumbersForColumn(int column, Set<Cell> allCells) {
        Set<Integer> hints = allCells.stream().filter(cell -> cell.position().column() == column && !cell.isEmpty()).map(Cell::number).collect(Collectors.toSet());
        return IntStream.range(1, sqrN + 1).filter(e -> !hints.contains(e)).boxed().collect(Collectors.toSet());
    }

    private Set<Integer> getMissedNumbersForSquare(int square, Set<Cell> allCells) {
        Set<Integer> hints = getNotEmptyCellsForSquare(square, allCells).stream().map(Cell::number).collect(Collectors.toSet());
        return IntStream.range(1, sqrN + 1).filter(e -> !hints.contains(e)).boxed().collect(Collectors.toSet());
    }

    private Integer getSquareIndexByPosition(Position position) {
        Set<Integer> squaresByRow = PositionUtils.squaresInRow(position.row(), n);
        Set<Integer> squaresByColumn = PositionUtils.squaresInColumn(position.column(), n);

        squaresByColumn.retainAll(squaresByRow);

        return squaresByColumn.stream().findFirst().orElse(null);
    }



    private Set<Integer> getFreeColumnsInRow(int row, Set<Cell> allCells) {
        Set<Integer> freeColumns = new HashSet<>();
        Map<Position, Cell> allCellsAsMap = allCells.stream().collect(Collectors.toMap(Cell::position, v -> v));

        for (int column = 0; column < field.length; column++) {
            Cell currentCell = allCellsAsMap.get(new Position(row, column));

            if (currentCell.isEmpty()) {
                freeColumns.add(column);
            }
        }

        return freeColumns;
    }

    private Set<Cell> getNotEmptyCellsForSquare(int squareIndex, Set<Cell> allCells) {
        Set<Cell> result = new HashSet<>();

        Position startPos = PositionUtils.getStartCoordinatesBySquareIndex(squareIndex, n);

        int fromRow = startPos.row();
        int fromColumn = startPos.column();

        int toRow = fromRow + n;
        int toColumn = fromColumn + n;

        Map<Position, Cell> allCellsAsMap = allCells.stream().collect(Collectors.toMap(Cell::position, v -> v));

        for (int k = 1; k <= n; k++) {
            for (int row = fromRow; row < toRow; row++) {
                for (int column = fromColumn; column < toColumn; column++) {
                    Cell currentCell = allCellsAsMap.get(new Position(row, column));
                    if (!currentCell.isEmpty()) {
                        result.add(currentCell);
                    }
                }
            }

            fromRow++;
        }

        return result;
    }

    private Set<Pair<Integer, Position>> getNotEmptyElementsWithCoordinatesForSquare(int squareIndex) {
        Set<Pair<Integer, Position>> result = new HashSet<>();
        Position startPos = PositionUtils.getStartCoordinatesBySquareIndex(squareIndex, n);

        int fromRow = startPos.row();
        int fromColumn = startPos.column();

        int toRow = fromRow + n;
        int toColumn = fromColumn + n;

        for (int k = 1; k <= n; k++) {
            for (int row = fromRow; row < toRow; row++) {
                for (int column = fromColumn; column < toColumn; column++) {
                    int elem = field[row][column];
                    if (elem != EMPTY_ELEM) {
                        result.add(Pair.of(elem, new Position(row, column)));
                    }
                }
            }

            fromRow++;
        }

        return result;
    }

    private Set<Pair<Integer, Position>> getNotEmptyElementsWithCoordinatesForRow(int rowIndex) {
        assert rowIndex >= 0;
        assert field.length > rowIndex;

        Set<Pair<Integer, Position>> result = new HashSet<>();

        for (int column = 0; column < field.length; column++) {
            int elem = field[rowIndex][column];
            if (elem != EMPTY_ELEM) {
                result.add(Pair.of(elem, new Position(rowIndex, column)));
            }
        }

        return result;
    }

    private Set<Pair<Integer, Position>> getNotEmptyElementsWithCoordinatesForColumn(int colIndex) {
        assert colIndex >= 0;
        assert field.length > colIndex;

        Set<Pair<Integer, Position>> result = new HashSet<>();
        for (int row = 0; row < field.length; row++) {
            int elem = field[row][colIndex];
            if (elem != EMPTY_ELEM) {
                result.add(Pair.of(elem, new Position(row, colIndex)));
            }
        }

        return result;
    }



    private boolean isNumbersMissedByCoordinatesInMatrix(Set<Pair<Integer, Position>> numbersWithPositions, int[][] matrix) {
        for (Pair<Integer, Position> numberWithCoordinates : numbersWithPositions) {
            int number = numberWithCoordinates.getKey();
            Position pos = numberWithCoordinates.getValue();
            int row = pos.row();
            int column = pos.column();

            if (!Objects.equals(matrix[row][column], number)) {
                return true;
            }
        }

        return false;
    }
}
