package com.smolka.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdjacencyGroupResult<KEY, VALUE> {

    private final Set<KEY> adjacencyGroup;

    private final List<List<Map<KEY, VALUE>>> result;

    public AdjacencyGroupResult(Set<KEY> adjacencyGroup) {
        this.adjacencyGroup = adjacencyGroup;
        this.result = new ArrayList<>();
    }

    public void put(List<Map<KEY, VALUE>> elem) {
        result.add(elem);
    }

    public Set<KEY> getAdjacencyGroup() {
        return adjacencyGroup;
    }

    public List<List<Map<KEY, VALUE>>> getResult() {
        return result;
    }
}
