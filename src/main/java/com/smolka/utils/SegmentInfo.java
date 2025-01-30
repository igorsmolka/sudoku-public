package com.smolka.utils;

import com.smolka.impl.SudokuImpl;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record SegmentInfo(Position startingPoint,
                          Set<Integer> possibleNumbers,
                          Set<Position> segmentPositions) {

    public SegmentInfo safeCopy() {
        return new SegmentInfo(
                startingPoint,
                new HashSet<>(possibleNumbers),
                new HashSet<>(segmentPositions)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SegmentInfo that = (SegmentInfo) o;
        return Objects.equals(startingPoint, that.startingPoint);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(startingPoint);
    }

    public boolean positionInSegment(Position position) {
        return segmentPositions.contains(position);
    }
}