package com.smolka.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PossibleNumbersValidationResult(
        Position forPosition,
        boolean isValid,
        Map<Integer, PossibleNumberValidationResult> validationResultByNumber
) {

    public List<SegmentValidBatch> getAllVariants() {
        List<SegmentValidBatch> result = new ArrayList<>();

        for (PossibleNumberValidationResult possibleNumberValidationResult : validationResultByNumber.values()) {
            if (!possibleNumberValidationResult.isValid()) {
                continue;
            }
            result.addAll(possibleNumberValidationResult.segmentValidBatches());
        }

        return result;
    }

    public int getAllVariantsCount() {
        int count = 0;

        for (PossibleNumberValidationResult possibleNumberValidationResult : validationResultByNumber.values()) {
            if (!possibleNumberValidationResult.isValid()) {
                continue;
            }
            count += possibleNumberValidationResult.segmentValidBatches().size();
        }

        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PossibleNumbersValidationResult that = (PossibleNumbersValidationResult) o;
        return isValid == that.isValid && Objects.equals(forPosition, that.forPosition) && Objects.equals(validationResultByNumber, that.validationResultByNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forPosition, isValid, validationResultByNumber);
    }
}