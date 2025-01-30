package com.smolka.utils;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class UuidElement {

    private final Position position;

    private final UUID uuid;

    private final List<UuidElement> nextVertexes;

    public UuidElement(Position position, UUID uuid) {
        this.position = position;
        this.uuid = uuid;
        this.nextVertexes = new ArrayList<>();
    }

    public void addNewVertex(UuidElement graphVertex) {
        this.nextVertexes.add(graphVertex);
    }

    public Position getPosition() {
        return position;
    }

    public UUID getUuid() {
        return uuid;
    }

    public List<UuidElement> getNextVertexes() {
        return nextVertexes;
    }

    public boolean isEnd() {
        return nextVertexes.isEmpty();
    }

    public Set<Set<UUID>> getUuidVariants() {
        Set<Set<UUID>> result = new HashSet<>();
        UUID currentCell = uuid;

        for (UuidElement branch : nextVertexes) {
            if (branch.isEnd()) {
                Set<UUID> singleSet = new HashSet<>();
                singleSet.add(branch.uuid);
                singleSet.add(currentCell);
                result.add(singleSet);
                continue;
            }
            Set<Set<UUID>> resultsFromBranch = branch.getUuidVariants();
            for (Set<UUID> resultFromBranch : resultsFromBranch) {
                Set<UUID> appendedWithCurrentCell = new HashSet<>(resultFromBranch);
                appendedWithCurrentCell.add(currentCell);
                result.add(appendedWithCurrentCell);
            }
        }

        if (result.isEmpty()) {
            Set<UUID> singleSet = new HashSet<>();
            singleSet.add(uuid);
            result.add(singleSet);
        }

        return result;
    }

    public void removeAllBranchesWithSizeLessThan(int size) {
        if (size == 1 || isEnd()) {
            return;
        }
        Set<UuidElement> toDelete = new HashSet<>();
        for (UuidElement branch : nextVertexes) {
            int counter = 1;
            boolean result = branch.removeRecursion(size, counter);
            if (!result) {
                toDelete.add(branch);
            }
        }
        nextVertexes.removeAll(toDelete);
    }

    private boolean removeRecursion(int size, int counter) {
        counter++;

        if (isEnd()) {
            return counter >= size;
        }

        boolean result = false;
        Set<UuidElement> toDelete = new HashSet<>();
        for (UuidElement branch : nextVertexes) {
            result = branch.removeRecursion(size, counter);
            if (!result) {
                toDelete.add(branch);
            }
        }

        nextVertexes.removeAll(toDelete);

        return !isEnd();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UuidElement that = (UuidElement) o;
        return Objects.equals(position, that.position) && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, uuid);
    }
}
