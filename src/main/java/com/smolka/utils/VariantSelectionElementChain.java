package com.smolka.utils;

import java.util.List;

public class VariantSelectionElementChain implements Comparable<VariantSelectionElementChain> {

    private final Position position;

    private final List<Integer> sortedPossibleNumbers;

    public VariantSelectionElementChain(Position position, PositionPotential positionPotential, int excludeNumber) {
        this.position = position;
        PositionPotential potentialCopy = positionPotential.safeCopy();

        this.sortedPossibleNumbers = potentialCopy.getPossibleNumbers().stream().filter(n -> n != excludeNumber).sorted().toList();
    }

    @Override
    public int compareTo(VariantSelectionElementChain o) {
        int first = sortedPossibleNumbers.size();
        int second = o.sortedPossibleNumbers.size();

        return Integer.compare(first, second);
    }

    public Position getPosition() {
        return position;
    }

    public List<Integer> getSortedPossibleNumbers() {
        return sortedPossibleNumbers;
    }
}