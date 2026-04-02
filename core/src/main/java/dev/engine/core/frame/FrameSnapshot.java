package dev.engine.core.frame;

import dev.engine.core.transaction.Transaction;

import java.util.Collections;
import java.util.List;

public record FrameSnapshot(long frameNumber, List<Transaction> transactions) {

    public FrameSnapshot {
        transactions = Collections.unmodifiableList(transactions);
    }
}
