package dev.engine.core.scene;

import dev.engine.core.transaction.Transaction;

import java.util.List;

/**
 * Provides the Renderer access to drain transactions from a scene.
 * This is the only way to get transactions — the method is not on the
 * user-facing AbstractScene API.
 */
public final class SceneAccess {

    private SceneAccess() {}

    /** Drains pending transactions from a scene. For Renderer use only. */
    public static List<Transaction> drainTransactions(AbstractScene scene) {
        return scene.drainTransactions();
    }
}
