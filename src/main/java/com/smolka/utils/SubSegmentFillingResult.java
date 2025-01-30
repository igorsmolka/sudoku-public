package com.smolka.utils;

public class SubSegmentFillingResult {

    private boolean isValid;

    private NumberSequenceVariant<Position> numberSequenceVariant;

    private SubSegmentType type;

    public SubSegmentFillingResult(SubSegmentType type) {
        this.type = type;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public boolean variantsAreEmpty() {
        return numberSequenceVariant == null || numberSequenceVariant.isEnd();
    }

    public boolean isValid() {
        return isValid;
    }

    public SubSegmentType getType() {
        return type;
    }

    public NumberSequenceVariant<Position> getNumberSequenceVariant() {
        return numberSequenceVariant;
    }

    public void setNumberSequenceVariant(NumberSequenceVariant<Position> numberSequenceVariant) {
        this.numberSequenceVariant = numberSequenceVariant;
    }
}