package com.smolka.utils;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class NumberSequenceVariant<KEY> {
    private final int number;
    private final KEY key;
    private final List<NumberSequenceVariant<KEY>> branches = new ArrayList<>();

    public NumberSequenceVariant(int number, KEY key) {
        this.number = number;
        this.key = key;
    }

    public boolean isEnd() {
        return branches.isEmpty();
    }

    public void justAddNewVariantsToBranches(List<NumberSequenceVariant<KEY>> newVariants) {
        branches.addAll(newVariants);
    }

    public void justAddWithIgnoringSame(int number, KEY key) {
        if (number == this.number) {
            return;
        }
        branches.add(new NumberSequenceVariant<>(number, key));
    }

    public boolean addVariantToEndOfPathWithIgnoringSame(int number, KEY keyOfVariant, Set<KEY> passedPath) {
        if (number == this.number) {
            return false;
        }

        Set<KEY> pathWithoutCurrentPosition = new HashSet<>(passedPath);

        boolean wasDeletedInPath = pathWithoutCurrentPosition.remove(key);

        if (!wasDeletedInPath) {
            return false;
        }

        if (pathWithoutCurrentPosition.isEmpty()) {
            branches.add(new NumberSequenceVariant<>(number, keyOfVariant));
            return true;
        }

        boolean wasAdding = false;

        for (NumberSequenceVariant<KEY> child : branches) {
            if (child.addVariantToEndOfPathWithIgnoringSame(number, keyOfVariant, pathWithoutCurrentPosition)) {
                wasAdding = true;
            }
        }

        return wasAdding;
    }

    /**
     * Удаление всех веток, размер которых не соответствует.
     */
    public void removeAllBranchesWithSizeLessThan(int size) {
        if (size == 1 || isEnd()) {
            return;
        }

        Set<NumberSequenceVariant<KEY>> toDelete = new HashSet<>();
        for (NumberSequenceVariant<KEY> branch : branches) {
            int counter = 1;
            boolean result = branch.removeRecursion(size, counter);
            if (!result) {
                toDelete.add(branch);
            }
        }

        branches.removeAll(toDelete);
    }

    public Set<Set<Pair<KEY, Integer>>> getKeyValuePairs() {
        Set<Set<Pair<KEY, Integer>>> result = new HashSet<>();
        Pair<KEY, Integer> currentCell = Pair.of(key, number);

        for (NumberSequenceVariant<KEY> branch : branches) {
            if (branch.isEnd()) {
                Set<Pair<KEY, Integer>> singleSet = new HashSet<>();
                singleSet.add(Pair.of(branch.key, branch.number));
                singleSet.add(currentCell);
                result.add(singleSet);
                continue;
            }
            Set<Set<Pair<KEY, Integer>>> resultsFromBranch = branch.getKeyValuePairs();
            for (Set<Pair<KEY, Integer>> resultFromBranch : resultsFromBranch) {
                Set<Pair<KEY, Integer>> appendedWithCurrentCell = new HashSet<>(resultFromBranch);
                appendedWithCurrentCell.add(currentCell);
                result.add(appendedWithCurrentCell);
            }
        }

        if (result.isEmpty()) {
            Set<Pair<KEY, Integer>> singleSet = new HashSet<>();
            singleSet.add(Pair.of(key, number));
            result.add(singleSet);
        }

        return result;
    }

    private boolean removeRecursion(int size, int counter) {
        counter++;

        if (isEnd()) {
            return counter >= size;
        }

        boolean result = false;
        Set<NumberSequenceVariant<KEY>> toDelete = new HashSet<>();
        for (NumberSequenceVariant<KEY> branch : branches) {
            result = branch.removeRecursion(size, counter);
            if (!result) {
                toDelete.add(branch);
            }
        }

        branches.removeAll(toDelete);

        return !isEnd();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        NumberSequenceVariant<KEY> that = (NumberSequenceVariant) o;
        return number == that.number && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, key);
    }
}