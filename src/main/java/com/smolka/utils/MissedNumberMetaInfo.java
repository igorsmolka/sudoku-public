package com.smolka.utils;

import java.util.Objects;
import java.util.Set;

public record MissedNumberMetaInfo(
        int number,
        Set<Integer> missedInRows,
        Set<Integer> missedInColumns,
        Set<Integer> missedInSquares,
        Set<Position> potentialPositions
) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MissedNumberMetaInfo that = (MissedNumberMetaInfo) o;
        return number == that.number;
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    public void addPotentialPositions(Set<Position> potentialPositions) {
        this.potentialPositions.addAll(potentialPositions);
    }
}