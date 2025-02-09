package com.smolka.utils;

import java.util.ArrayList;
import java.util.List;

public class UniqueSequenceLayeredTreeResult<KEY, VALUE> {

    private final List<AdjacencyGroupResult<KEY, VALUE>> adjacencyGroupResults;

    public UniqueSequenceLayeredTreeResult() {
        this.adjacencyGroupResults = new ArrayList<>();
    }

    public void putGroupResult(AdjacencyGroupResult<KEY, VALUE> adjacencyGroupResult) {
        adjacencyGroupResults.add(adjacencyGroupResult);
    }

    public List<AdjacencyGroupResult<KEY,VALUE>> getAdjacencyGroupResults() {
        return adjacencyGroupResults;
    }
}
