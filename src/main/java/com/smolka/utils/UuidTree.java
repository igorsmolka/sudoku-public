package com.smolka.utils;

public class UuidTree {

    private final UuidElement rootElement;

    public UuidTree(UuidElement rootElement, int requiredSize) {
        this.rootElement = rootElement;
        this.rootElement.removeAllBranchesWithSizeLessThan(requiredSize);
    }

    public UuidElement getRootElement() {
        return rootElement;
    }

    public boolean rootIsEmpty() {
        return rootElement == null || rootElement.isEnd();
    }
}
