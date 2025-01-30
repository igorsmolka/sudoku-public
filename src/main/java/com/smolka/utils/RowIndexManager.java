package com.smolka.utils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RowIndexManager {

    void initializeIndexManager(List<PossibleNumbersValidationResult> validationResults);

    Set<Set<UUID>> getBatchesUuidsNotConflictedByRows();
}
