package com.smolka.utils;

import com.smolka.impl.SudokuImpl;

import java.util.List;
import java.util.Objects;

public record PossibleNumberValidationResult(
        Position forPosition,
        int number,
        boolean isValid,
        List<SegmentValidBatch> segmentValidBatches
) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PossibleNumberValidationResult result = (PossibleNumberValidationResult) o;
        return number == result.number && isValid == result.isValid && Objects.equals(forPosition, result.forPosition) && Objects.equals(segmentValidBatches, result.segmentValidBatches);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forPosition, number, isValid, segmentValidBatches);
    }
}
