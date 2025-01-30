package com.smolka.utils;

import java.util.Objects;
import java.util.UUID;

public class SegmentValidBatch {

    private final UUID uuid;
    private final CellsVariant columnSubSegmentVariant;
    private final CellsVariant rowSubSegmentVariant;
    private final CellsVariant squareSubSegmentVariant;

    public SegmentValidBatch(CellsVariant columnSubSegmentVariant, CellsVariant rowSubSegmentVariant, CellsVariant squareSubSegmentVariant) {
        this.uuid = UUID.randomUUID();

        if (columnSubSegmentVariant != null) {
            assert columnSubSegmentVariant.getType() == SubSegmentType.COLUMN;
            columnSubSegmentVariant.setBatchUUID(uuid);
        }

        if (rowSubSegmentVariant != null) {
            assert rowSubSegmentVariant.getType() == SubSegmentType.ROW;
            rowSubSegmentVariant.setBatchUUID(uuid);
        }

        if (squareSubSegmentVariant != null) {
            assert squareSubSegmentVariant.getType() == SubSegmentType.SQUARE;
            squareSubSegmentVariant.setBatchUUID(uuid);
        }


        this.columnSubSegmentVariant = columnSubSegmentVariant;
        this.rowSubSegmentVariant = rowSubSegmentVariant;
        this.squareSubSegmentVariant = squareSubSegmentVariant;
    }

    public CellsVariant getCellsVariantsOfType(SubSegmentType type) {
        switch (type) {
            case ROW -> {
                return rowSubSegmentVariant;
            }
            case COLUMN -> {
                return columnSubSegmentVariant;
            }
            case SQUARE -> {
                return squareSubSegmentVariant;
            }
            default -> {
                return null;
            }
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public CellsVariant getColumnSubSegmentVariant() {
        return columnSubSegmentVariant;
    }

    public CellsVariant getRowSubSegmentVariant() {
        return rowSubSegmentVariant;
    }

    public CellsVariant getSquareSubSegmentVariant() {
        return squareSubSegmentVariant;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SegmentValidBatch that = (SegmentValidBatch) o;
        return Objects.equals(columnSubSegmentVariant, that.columnSubSegmentVariant) && Objects.equals(rowSubSegmentVariant, that.rowSubSegmentVariant) && Objects.equals(squareSubSegmentVariant, that.squareSubSegmentVariant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnSubSegmentVariant, rowSubSegmentVariant, squareSubSegmentVariant);
    }
}