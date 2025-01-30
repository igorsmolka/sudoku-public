package com.smolka.impl;

import com.smolka.Sudoku;
import com.smolka.utils.Cell;
import com.smolka.utils.CellsVariant;
import com.smolka.utils.MissedNumberMetaInfo;
import com.smolka.utils.NumberSequenceVariant;
import com.smolka.utils.Position;
import com.smolka.utils.PositionPotential;
import com.smolka.utils.PositionUtils;
import com.smolka.utils.PossibleNumberValidationResult;
import com.smolka.utils.PossibleNumbersValidationResult;
import com.smolka.utils.RowIndexManager;
import com.smolka.utils.SegmentInfo;
import com.smolka.utils.SegmentValidBatch;
import com.smolka.utils.SubSegmentFillingResult;
import com.smolka.utils.SubSegmentType;
import com.smolka.utils.VariantSelectionElementChain;
import com.smolka.utils.impl.RowIndexManagerImpl;
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

    @Override
    public int[][] getVariant() {
        Set<Cell> cells = getCells(this.field);

        Map<Position, PositionPotential> positionPotentialMap = createNewPotentialMapForCells(cells);

        Map<UUID, SegmentValidBatch> validBatchMap = new HashMap<>();

        Set<Position> positionsToAnalyze = getPositionsForAnalyze(positionPotentialMap);

        List<PossibleNumbersValidationResult> possibleNumbersValidationResults = new ArrayList<>();
        for (Position position : positionsToAnalyze) {
            PossibleNumbersValidationResult possibleNumbersValidationResult = possibleNumbersValidation(position, positionPotentialMap);
            possibleNumbersValidationResults.add(possibleNumbersValidationResult);
            possibleNumbersValidationResult.getAllVariants().forEach(svb -> validBatchMap.put(svb.getUuid(), svb));
        }


//        RowIndexManager rowIndexManager = new RowIndexManagerImpl();
//        rowIndexManager.initializeIndexManager(possibleNumbersValidationResults);

//        Set<Set<UUID>> uuidVariants = rowIndexManager.getBatchesUuidsNotConflictedByRows();
//
//
//        for (Set<UUID> uuidVariant : uuidVariants) {
//            Set<Cell> cellsVariant = new HashSet<>();
//            for (UUID uuid : uuidVariant) {
//                cellsVariant.addAll(validBatchMap.get(uuid).getCellsVariantsOfType(SubSegmentType.ROW).getCells());
//            }
//
//            int[][] copiedField = copyOfField(this.field);
//            fillFieldFromCells(copiedField, cellsVariant);
//            if (checkVariant(copiedField)) {
//                return copiedField;
//            }
//        }

        return new int[0][];
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

    private void fillFieldFromCells(int[][] field, Set<Cell> cells) {
        if (cells == null) {
            return;
        }

        for (Cell cell : cells) {
            field[cell.position().row()][cell.position().column()] = cell.number();
        }
    }

    private PossibleNumbersValidationResult possibleNumbersValidation(Position position,
                                                                      Map<Position, PositionPotential> positionPotentialMap) {
        PositionPotential potential = positionPotentialMap.get(position);
        Set<Integer> possibleNumbers = potential.getPossibleNumbers();

        // получаем сегмент нашей позиции
        Map<Position, PositionPotential> positionSegment = getSegment(position, positionPotentialMap);

        // получаем позиции, сгруппированные по строке опорной позиции - т.е. по сути столбец сегмента
        Map<Position, PositionPotential> positionsByRow = positionSegment.entrySet().stream().filter(p -> Objects.equals(p.getKey().row(), position.row())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // получаем позиции, сгруппированные по столбцу опорной позиции - т.е. по сути строку сегмента
        Map<Position, PositionPotential> positionsByColumn = positionSegment.entrySet().stream().filter(p -> Objects.equals(p.getKey().column(), position.column())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // получаем позиции, сгруппированные по квадрату опорной позиции - из них потом мы уберем те позиции, которые пересекаются со строкой и столбцом
        Map<Position, PositionPotential> positionsBySquare = positionSegment.entrySet().stream().filter(p -> p.getValue().getSquareIndex() == potential.getSquareIndex()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        boolean isValid = false;
        Map<Integer, PossibleNumberValidationResult> possibleNumberValidationResultMap = new HashMap<>();

        // пробегаемся
        for (int possibleNumber : possibleNumbers) {
            PossibleNumberValidationResult possibleNumberValidationResult = possibleNumberValidation(position, possibleNumber, positionsByRow, positionsByColumn, positionsBySquare);
            possibleNumberValidationResultMap.put(possibleNumber, possibleNumberValidationResult);
            // если хотя бы одна возможная цифра валидна - в целом результат валиден
            if (possibleNumberValidationResult.isValid()) {
                isValid = true;
            }
        }

        return new PossibleNumbersValidationResult(position, isValid, possibleNumberValidationResultMap);
    }

    private PossibleNumberValidationResult possibleNumberValidation(Position forPosition,
                                                                    int number,
                                                                    Map<Position, PositionPotential> positionsByRow,
                                                                    Map<Position, PositionPotential> positionsByColumn,
                                                                    Map<Position, PositionPotential> positionsBySquare) {
        Map<Position, PositionPotential> positionsByRowCopyWithoutNumber = copyOfPotentialMap(positionsByRow).entrySet().stream().peek(e -> e.getValue().removePossibleNumber(number)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Position, PositionPotential> positionsByColumnCopyWithoutNumber = copyOfPotentialMap(positionsByColumn).entrySet().stream().peek(e -> e.getValue().removePossibleNumber(number)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Position, PositionPotential> positionsBySquareCopyWithoutNumber = copyOfPotentialMap(positionsBySquare).entrySet().stream().peek(e -> e.getValue().removePossibleNumber(number)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Set<Position> positionsIntersectionsWithSquare = new HashSet<>();

        for (Position positionInRow : positionsByRowCopyWithoutNumber.keySet()) {
            if (positionsBySquareCopyWithoutNumber.remove(positionInRow) != null) {
                positionsIntersectionsWithSquare.add(positionInRow);
            }
        }

        for (Position positionInColumn : positionsByColumnCopyWithoutNumber.keySet()) {
            if (positionsBySquareCopyWithoutNumber.remove(positionInColumn) != null) {
                positionsIntersectionsWithSquare.add(positionInColumn);
            }
        }

        SubSegmentFillingResult columnSubSegmentFillingResult = new SubSegmentFillingResult(SubSegmentType.COLUMN);
        columnSubSegmentFillingResult.setValid(true);
        if (!positionsByRowCopyWithoutNumber.isEmpty()) {
            columnSubSegmentFillingResult = validateSubSegmentAfterDeletion(positionsByRowCopyWithoutNumber, forPosition, number, SubSegmentType.COLUMN);
        }

        if (!columnSubSegmentFillingResult.isValid()) {
            return new PossibleNumberValidationResult(forPosition, number, false, null);
        }

        SubSegmentFillingResult rowSubSegmentFillingResult = new SubSegmentFillingResult(SubSegmentType.ROW);
        rowSubSegmentFillingResult.setValid(true);
        if (!positionsByColumnCopyWithoutNumber.isEmpty()) {
            rowSubSegmentFillingResult = validateSubSegmentAfterDeletion(positionsByColumnCopyWithoutNumber, forPosition, number, SubSegmentType.ROW);
        }

        if (!rowSubSegmentFillingResult.isValid()) {
            return new PossibleNumberValidationResult(forPosition, number, false, null);
        }

        SubSegmentFillingResult squareSubSegmentFillingResult = new SubSegmentFillingResult(SubSegmentType.SQUARE);
        squareSubSegmentFillingResult.setValid(true);
        if (!positionsBySquareCopyWithoutNumber.isEmpty()) {
            squareSubSegmentFillingResult = validateSubSegmentAfterDeletion(positionsBySquareCopyWithoutNumber, forPosition, number, SubSegmentType.SQUARE);
        }

        if (!squareSubSegmentFillingResult.isValid()) {
            return new PossibleNumberValidationResult(forPosition, number, false, null);
        }

        if (squareSubSegmentFillingResult.variantsAreEmpty() || columnSubSegmentFillingResult.variantsAreEmpty() || rowSubSegmentFillingResult.variantsAreEmpty()) {
            if (squareSubSegmentFillingResult.variantsAreEmpty() && columnSubSegmentFillingResult.variantsAreEmpty() && rowSubSegmentFillingResult.variantsAreEmpty()) {
                return new PossibleNumberValidationResult(forPosition, number, true, null);
            }

            if (squareSubSegmentFillingResult.variantsAreEmpty()) {
                if (!rowSubSegmentFillingResult.variantsAreEmpty() && !columnSubSegmentFillingResult.variantsAreEmpty()) {
                    List<SegmentValidBatch> validBatches = getValidBatchesForColumnAndRow(columnSubSegmentFillingResult, rowSubSegmentFillingResult, positionsIntersectionsWithSquare);
                    if (validBatches.isEmpty()) {
                        return new PossibleNumberValidationResult(forPosition, number, false, null);
                    }
                    return new PossibleNumberValidationResult(forPosition, number, true, validBatches);
                }

                if (!rowSubSegmentFillingResult.variantsAreEmpty()) {
                    List<SegmentValidBatch> validBatches = getValidBatchesForColumnOrRow(columnSubSegmentFillingResult);
                    if (validBatches.isEmpty()) {
                        return new PossibleNumberValidationResult(forPosition, number, false, null);
                    }
                    return new PossibleNumberValidationResult(forPosition, number, true, validBatches);
                }

                List<SegmentValidBatch> validBatches = getValidBatchesForColumnOrRow(columnSubSegmentFillingResult);
                if (validBatches.isEmpty()) {
                    return new PossibleNumberValidationResult(forPosition, number, false, null);
                }
                return new PossibleNumberValidationResult(forPosition, number, true, validBatches);
            }

            if (!rowSubSegmentFillingResult.variantsAreEmpty()) {
                List<SegmentValidBatch> validBatches = getValidBatchesForColumnOrRowAndSquare(columnSubSegmentFillingResult, squareSubSegmentFillingResult, positionsIntersectionsWithSquare);
                if (validBatches.isEmpty()) {
                    return new PossibleNumberValidationResult(forPosition, number, false, null);
                }
                return new PossibleNumberValidationResult(forPosition, number, true, validBatches);
            }

            List<SegmentValidBatch> validBatches = getValidBatchesForColumnOrRowAndSquare(columnSubSegmentFillingResult, squareSubSegmentFillingResult, positionsIntersectionsWithSquare);
            if (validBatches.isEmpty()) {
                return new PossibleNumberValidationResult(forPosition, number, false, null);
            }
            return new PossibleNumberValidationResult(forPosition, number, true, validBatches);
        }

        List<SegmentValidBatch> validBatches = getValidBatchesForColumnRowAndSquare(columnSubSegmentFillingResult, rowSubSegmentFillingResult,
                squareSubSegmentFillingResult,
                positionsIntersectionsWithSquare);

        if (validBatches.isEmpty()) {
            return new PossibleNumberValidationResult(forPosition, number, false, null);
        }

        return new PossibleNumberValidationResult(forPosition, number, true, validBatches);
    }

    private List<SegmentValidBatch> getValidBatchesForColumnOrRow(SubSegmentFillingResult subSegmentFillingResult) {
        List<SegmentValidBatch> result = new ArrayList<>();

        Set<CellsVariant> subSegmentVariants = variantsToCells(subSegmentFillingResult);

        if (Objects.equals(subSegmentFillingResult.getType(), SubSegmentType.ROW)) {
            for (CellsVariant subSegmentVariant : subSegmentVariants) {
                result.add(new SegmentValidBatch(null, subSegmentVariant, null));
            }
        } else if (Objects.equals(subSegmentFillingResult.getType(), SubSegmentType.COLUMN)) {
            for (CellsVariant subSegmentVariant : subSegmentVariants) {
                result.add(new SegmentValidBatch(subSegmentVariant, null, null));
            }
        } else {
            throw new RuntimeException("Wrong type");
        }

        return result;
    }

    private List<SegmentValidBatch> getValidBatchesForColumnAndRow(SubSegmentFillingResult columnSubSegmentFillingResult,
                                                                   SubSegmentFillingResult rowSubSegmentFillingResult,
                                                                   Set<Position> positionsIntersectionsWithSquare) {
        List<SegmentValidBatch> result = new ArrayList<>();

        if (!Objects.equals(columnSubSegmentFillingResult.getType(), SubSegmentType.COLUMN) || !Objects.equals(rowSubSegmentFillingResult.getType(), SubSegmentType.ROW)) {
            throw new RuntimeException("Wrong type");
        }

        Set<CellsVariant> columnSubSegmentVariants = variantsToCells(columnSubSegmentFillingResult);
        Set<CellsVariant> rowSubSegmentVariants = variantsToCells(rowSubSegmentFillingResult);

        for (CellsVariant columnSubSegmentVariant : columnSubSegmentVariants) {
            Set<Integer> intersectionsWithSquareForColumn = columnSubSegmentVariant.getCells().stream().filter(cell -> positionsIntersectionsWithSquare.contains(cell.position())).map(Cell::number).collect(Collectors.toSet());

            for (CellsVariant rowSubSegmentVariant : rowSubSegmentVariants) {
                Set<Integer> intersectionsWithSquareForRow = rowSubSegmentVariant.getCells().stream().filter(cell -> positionsIntersectionsWithSquare.contains(cell.position())).map(Cell::number).collect(Collectors.toSet());
                intersectionsWithSquareForRow.retainAll(intersectionsWithSquareForColumn);

                if (!intersectionsWithSquareForRow.isEmpty()) {
                    continue;
                }

                result.add(new SegmentValidBatch(columnSubSegmentVariant, rowSubSegmentVariant, null));
            }
        }

        return result;
    }

    private List<SegmentValidBatch> getValidBatchesForColumnOrRowAndSquare(SubSegmentFillingResult subSegmentFillingResult,
                                                                           SubSegmentFillingResult squareSubSegmentFillingResult,
                                                                           Set<Position> positionsIntersectionsWithSquare) {
        // todo тут подумать еще раз, что если будет слепое пятно в виде строки/столбца, который заполнен и пересечения мы не учитываем с ним. не должны ли possibleNumbers для столбцов и строк быть еще и пересечением с квадраотом???
        List<SegmentValidBatch> result = new ArrayList<>();

        Set<CellsVariant> subSegmentVariants = variantsToCells(subSegmentFillingResult);
        Set<CellsVariant> squareSubSegmentVariants = variantsToCells(squareSubSegmentFillingResult);

        if (!Objects.equals(subSegmentFillingResult.getType(), SubSegmentType.COLUMN) && !Objects.equals(subSegmentFillingResult.getType(), SubSegmentType.ROW)) {
            throw new RuntimeException("Wrong type");
        }

        if (!Objects.equals(squareSubSegmentFillingResult.getType(), SubSegmentType.SQUARE)) {
            throw new RuntimeException("Wrong type");
        }

        boolean isRow = subSegmentFillingResult.getType() == SubSegmentType.ROW;

        for (CellsVariant subSegmentVariant : subSegmentVariants) {
            Set<Integer> intersectionsWithSquare = subSegmentVariant.getCells().stream().filter(cell -> positionsIntersectionsWithSquare.contains(cell.position())).map(Cell::number).collect(Collectors.toSet());

            for (CellsVariant squareSubSegmentVariant : squareSubSegmentVariants) {
                boolean hasDupes = squareSubSegmentVariant.getCells().stream().map(Cell::number).anyMatch(intersectionsWithSquare::contains);
                if (hasDupes) {
                    continue;
                }
                if (isRow) {
                    result.add(new SegmentValidBatch(null, squareSubSegmentVariant, squareSubSegmentVariant));
                    continue;
                }
                result.add(new SegmentValidBatch(squareSubSegmentVariant, null, squareSubSegmentVariant));
            }
        }

        return result;
    }

    private List<SegmentValidBatch> getValidBatchesForColumnRowAndSquare(SubSegmentFillingResult columnSubSegmentFillingResult,
                                                                         SubSegmentFillingResult rowSubSegmentFillingResult,
                                                                         SubSegmentFillingResult squareSubSegmentFillingResult,
                                                                         Set<Position> positionsIntersectionsWithSquare) {
        List<SegmentValidBatch> result = new ArrayList<>();

        Set<CellsVariant> columnSubSegmentVariants = variantsToCells(columnSubSegmentFillingResult);
        Set<CellsVariant> rowSubSegmentVariants = variantsToCells(rowSubSegmentFillingResult);
        Set<CellsVariant> squareSubSegmentVariants = variantsToCells(squareSubSegmentFillingResult);

        if (!Objects.equals(columnSubSegmentFillingResult.getType(), SubSegmentType.COLUMN) || !Objects.equals(rowSubSegmentFillingResult.getType(), SubSegmentType.ROW)) {
            throw new RuntimeException("Wrong type");
        }

        if (!Objects.equals(squareSubSegmentFillingResult.getType(), SubSegmentType.SQUARE)) {
            throw new RuntimeException("Wrong type");
        }

        for (CellsVariant columnSubSegmentVariant : columnSubSegmentVariants) {
            Set<Integer> intersectionsWithSquareByColumn = columnSubSegmentVariant.getCells().stream().filter(cell -> positionsIntersectionsWithSquare.contains(cell.position())).map(Cell::number).collect(Collectors.toSet());

            for (CellsVariant rowSubSegmentVariant : rowSubSegmentVariants) {
                Set<Integer> intersectionsWithSquareByRow = rowSubSegmentVariant.getCells().stream().filter(cell -> positionsIntersectionsWithSquare.contains(cell.position())).map(Cell::number).collect(Collectors.toSet());

                Set<Integer> intersectionsByRowAndColumn = new HashSet<>(intersectionsWithSquareByRow);
                intersectionsByRowAndColumn.retainAll(intersectionsWithSquareByColumn);

                if (!intersectionsByRowAndColumn.isEmpty()) {
                    continue;
                }

                for (CellsVariant squareSubSegmentVariant : squareSubSegmentVariants) {
                    Set<Integer> intersectedNumbersInRowColumnForSquare = new HashSet<>(intersectionsWithSquareByColumn);
                    intersectedNumbersInRowColumnForSquare.addAll(intersectionsWithSquareByRow);

                    boolean hasDupesForSquare = squareSubSegmentVariant.getCells().stream().map(Cell::number).anyMatch(intersectedNumbersInRowColumnForSquare::contains);
                    if (hasDupesForSquare) {
                        continue;
                    }

                    result.add(new SegmentValidBatch(columnSubSegmentVariant, rowSubSegmentVariant, squareSubSegmentVariant));
                }
            }
        }

        return result;
    }

    private Set<CellsVariant> variantsToCells(SubSegmentFillingResult fillingResult) {
        return fillingResult.getNumberSequenceVariant().getKeyValuePairs().stream().map(
                setOfPairs -> setOfPairs.stream().map(pair -> new Cell(pair.getKey(), pair.getValue())).collect(Collectors.toSet())
        ).map(r -> new CellsVariant(r, fillingResult.getType())).collect(Collectors.toSet());
    }

    private SubSegmentFillingResult validateSubSegmentAfterDeletion(Map<Position, PositionPotential> subSegment,
                                                                    Position forPosition,
                                                                    int number,
                                                                    SubSegmentType subSegmentType) {
        // результат с возможными заполнениями подсегмента. инициализируем его переданным типом.
        SubSegmentFillingResult result = new SubSegmentFillingResult(subSegmentType);
        // если подсегмент изначально пуст и в анализе, получается, не нуждается - просто вернем true
        if (subSegment.isEmpty()) {
            result.setValid(true);
            return result;
        }

        // заводим коллекцию, в которой будут складываться ячейки с единственно возможным заполнением
        Set<Cell> mandatoryCells = new HashSet<>();

        // мапа, в которой будет вестись статистика: сколько раз та или иная комбинация встречается на протяжении подсегмента. пригодится.
        Map<Set<Integer>, Integer> combinationCount = new HashMap<>();

        // мапа, в которой мы держим позиции, соотнесенные с единственно возможным вариантом заполнения. пригодится для формирования mandatoryCells.
        Map<Position, Integer> singleElementsMap = new HashMap<>();

        Map<Position, PositionPotential> copyOfSegment = copyOfPotentialMap(subSegment);

        for (Map.Entry<Position, PositionPotential> segmentEntry : copyOfSegment.entrySet()) {
            Position position = segmentEntry.getKey();
            PositionPotential positionPotential = segmentEntry.getValue();

            // ведем статистику встречаемости
            Set<Integer> possibleNumbers = positionPotential.getPossibleNumbers();
            combinationCount.putIfAbsent(possibleNumbers, 0);
            combinationCount.put(possibleNumbers, combinationCount.get(possibleNumbers) + 1);

            // если возможно только одно число - заполняем мапу singleElementsMap
            if (possibleNumbers.size() == 1) {
                singleElementsMap.put(position, possibleNumbers.stream().findFirst().orElseThrow());
            }
        }

        // если встречается хоть одна комбинация, частота которой превышает ее размерность - это значит, что заполнить подсегмент мы уже не можем. вердикт - невалидно.
        for (Map.Entry<Set<Integer>, Integer> combinationCountEntry : combinationCount.entrySet()) {
            Set<Integer> combination = combinationCountEntry.getKey();
            Integer count = combinationCountEntry.getValue();

            if (count > combination.size()) {
                result.setValid(false);
                return result;
            }
        }

        do {
            // здесь мы делаем манипуляции с мапой singleElementsMap, т.е. с позициями, которые имеют только один вариант для заполнения.
            // формируем по ним mandatoryCells, а так же убираем "заполненные" этим числом позиции из анализа + убираем из остальных позиций это число, как возможное
            // т.к. операция может вызвать "цепную реакцию" - делаем это все в цикле.
            Set<Integer> singleElementsSet = new HashSet<>(singleElementsMap.values());

            for (Map.Entry<Position, Integer> singeElementEntry : singleElementsMap.entrySet()) {
                Position position = singeElementEntry.getKey();
                Integer mandatoryNumber = singeElementEntry.getValue();

                mandatoryCells.add(new Cell(position, mandatoryNumber));

                copyOfSegment.remove(position);
            }

            if (copyOfSegment.isEmpty()) {
                break;
            }

            for (PositionPotential potential : copyOfSegment.values()) {
                potential.removePossibleNumbers(singleElementsSet);
                if (potential.getPossibleNumbers().isEmpty()) {
                    // если по итогу манипуляции мы не можем заполнить какую-то позицию - то это тоже нездоровая ситуация. вердикт - невалидно.
                    result.setValid(false);
                    return result;
                }
            }

            singleElementsMap = copyOfSegment.entrySet()
                    .stream()
                    .filter(e -> e.getValue().getPossibleNumbers().size() == 1)
                    .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getPossibleNumbers().stream().findFirst().orElseThrow()));
        } while (!singleElementsMap.isEmpty());


        // проваливаемся в метод, строящий возможные варианты заполнения с опорной точкой и валидируемым числом, учитывая mandatoryCells
        NumberSequenceVariant<Position> numberSequenceVariants = getMainVariantForSubSegment(copyOfSegment, mandatoryCells, forPosition, number);

        result.setNumberSequenceVariant(numberSequenceVariants);

        if (numberSequenceVariants.isEnd()) {
            result.setValid(false);
            return result;
        }

        result.setValid(true);

        return result;
    }

    private NumberSequenceVariant<Position> getMainVariantForSubSegment(Map<Position, PositionPotential> subSegment, Set<Cell> mandatoryCells, Position forPosition, int number) {
        // инициализация "корня"
        NumberSequenceVariant<Position> numberSequenceVariant = new NumberSequenceVariant<>(number, forPosition);
        List<NumberSequenceVariant<Position>> resultsByPossibleNumbersInFirstPositionInSubSegment = new ArrayList<>();

        Map<Position, PositionPotential> subSegmentCopy = copyOfPotentialMap(subSegment);

        // возвращаем mandatoryCell на свои места
        for (Cell mandatoryCell : mandatoryCells) {
            subSegmentCopy.put(mandatoryCell.position(), new PositionPotential(mandatoryCell.number()));
        }

        Position firstPosition = subSegmentCopy.keySet().stream().findFirst().orElseThrow();
        Set<Integer> possibleNumbersForFirstPosition = subSegmentCopy.values().stream().findFirst().map(PositionPotential::getPossibleNumbers).orElseThrow();

        // если подсегмент только из одной позиции - просто добавляем в последовательность возможные цифры из первой позиции, и уходим; дальнейшие действия ни к чему.
        if (subSegmentCopy.size() == 1) {
            for (int possibleNumber : possibleNumbersForFirstPosition) {
                resultsByPossibleNumbersInFirstPositionInSubSegment.add(new NumberSequenceVariant<>(possibleNumber, firstPosition));
            }
            numberSequenceVariant.justAddNewVariantsToBranches(resultsByPossibleNumbersInFirstPositionInSubSegment);
            return numberSequenceVariant;
        }

        // оно нам ни к чему в дальнейшем разборе...
        subSegmentCopy.remove(firstPosition);

        // пробегаемся по возможным цифрам для первой позиции, по каждой получаем варианты
        for (int possibleNumber : possibleNumbersForFirstPosition) {
            NumberSequenceVariant<Position> variant = getVariantOnRootNumberForSubSegment(possibleNumber, firstPosition, subSegmentCopy);
            // если метод вернул null - значит, путной цепочки собрать не вышло. увы. null-ы нам не нужны.
            if (variant != null) {
                resultsByPossibleNumbersInFirstPositionInSubSegment.add(variant);
            }
        }

        // добавляем результаты по цифрам для первой позиции в корень и возвращаем его
        numberSequenceVariant.justAddNewVariantsToBranches(resultsByPossibleNumbersInFirstPositionInSubSegment);
        return numberSequenceVariant;
    }

    private NumberSequenceVariant<Position> getVariantOnRootNumberForSubSegment(int number, Position position, Map<Position, PositionPotential> subSegment) {
        Set<Position> passedPath = new HashSet<>();
        NumberSequenceVariant<Position> result = new NumberSequenceVariant<>(number, position);

        passedPath.add(position);

        List<VariantSelectionElementChain> variantSelectionChain = subSegment.entrySet()
                .stream()
                .map(es -> new VariantSelectionElementChain(es.getKey(), es.getValue(), number))
                .sorted()
                .toList();

        VariantSelectionElementChain firstElement = variantSelectionChain.stream().findFirst().orElseThrow();
        for (int possibleNumberInFirstElement : firstElement.getSortedPossibleNumbers()) {
            result.justAddWithIgnoringSame(possibleNumberInFirstElement, firstElement.getPosition());
        }
        if (result.isEnd()) {
            return null;
        }

        if (variantSelectionChain.size() == 1) {
            return result;
        }

        passedPath.add(firstElement.getPosition());

        for (int i = 0; i < variantSelectionChain.size() - 1; i++) {
            VariantSelectionElementChain leftElement = variantSelectionChain.get(i);
            VariantSelectionElementChain rightElement = variantSelectionChain.get(i + 1);

            Position rightElementPosition = rightElement.getPosition();

            List<Integer> numbersForLeft = leftElement.getSortedPossibleNumbers();
            List<Integer> numbersForRight = rightElement.getSortedPossibleNumbers();

            boolean wasAdding = false;

            if (numbersForRight.size() == 1) {
                wasAdding = result.addVariantToEndOfPathWithIgnoringSame(numbersForRight.stream().findFirst().orElseThrow(), rightElementPosition, passedPath);
                if (!wasAdding) {
                    break;
                }
                passedPath.add(rightElementPosition);
                continue;
            }

            for (int numberInRight : numbersForRight) {
                if ((numbersForLeft.contains(numberInRight) && numbersForLeft.size() == 1)) {
                    continue;
                }
                if (result.addVariantToEndOfPathWithIgnoringSame(numberInRight, rightElementPosition, passedPath)) {
                    wasAdding = true;
                }
            }

            if (!wasAdding) {
                break;
            }

            passedPath.add(rightElementPosition);
        }

        result.removeAllBranchesWithSizeLessThan(subSegment.size() + 1);
        if (result.isEnd()) {
            return null;
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

    private int[][] copyOfField(int[][] field) {
        int[][] copy = new int[sqrN][sqrN];
        for (int i = 0; i < copy.length; i++) {
            System.arraycopy(field[i], 0, copy[i], 0, copy[i].length);
        }

        return copy;
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

    private Set<MissedNumberMetaInfo> getMissedNumbersMetaInfo(Set<Cell> allCells) {
        //todo для строки/столбца в possibleNumbers не должно оказаться числа из связанного квадрата!!!
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
