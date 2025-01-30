package com.smolka.utils;

import java.util.HashSet;
import java.util.Set;

public class PositionPotential {

    private final Set<Integer> possibleNumbers;

    private SegmentInfo segmentInfo;

    private int squareIndex;

    public PositionPotential(int possibleNumber) {
        Set<Integer> setWithSingleNumber = new HashSet<>();
        setWithSingleNumber.add(possibleNumber);

        this.possibleNumbers = setWithSingleNumber;
    }

    public PositionPotential(Set<Integer> possibleNumbers, int squareIndex) {
        this.possibleNumbers = possibleNumbers;
        this.squareIndex = squareIndex;
    }

    public PositionPotential(Set<Integer> possibleNumbers, SegmentInfo segmentInfo, int squareIndex) {
        this.possibleNumbers = possibleNumbers;
        this.segmentInfo = segmentInfo;
        this.squareIndex = squareIndex;
    }

    public PositionPotential safeCopy() {
        return new PositionPotential(new HashSet<>(possibleNumbers), segmentInfo != null ? segmentInfo.safeCopy() : segmentInfo, squareIndex);
    }

    public void addPossibleNumber(int possibleNumber) {
        possibleNumbers.add(possibleNumber);
    }

    public void removePossibleNumber(int possibleNumber) {
        possibleNumbers.remove(possibleNumber);
    }

    public void removePossibleNumbers(Set<Integer> possibleNumbers) {
        this.possibleNumbers.removeAll(possibleNumbers);
    }

    public void setSegment(SegmentInfo segment) {
        this.segmentInfo = segment;
    }

    public Set<Integer> getPossibleNumbers() {
        return possibleNumbers;
    }

    public SegmentInfo getSegmentInfo() {
        return segmentInfo;
    }

    public int getSquareIndex() {
        return squareIndex;
    }
}