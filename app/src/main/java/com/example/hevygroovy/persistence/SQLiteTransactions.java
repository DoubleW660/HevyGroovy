package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import java.util.function.Supplier;

/** Execute service work synchronously on the thread owning the transaction. */
final class SQLiteTransactions {
    private final SQLiteDatabase db;

    SQLiteTransactions(SQLiteDatabase db) {
        this.db = db;
    }

    <T> T run(Supplier<T> operation) {
        db.beginTransactionNonExclusive();
        return complete(operation);
    }

    /** API 34 fallback preserves a snapshot but still reserves the writer slot.
     * Single-query reads should execute directly, without this wrapper.
     */
    <T> T read(Supplier<T> operation) {
        if (Build.VERSION.SDK_INT >= 35) {
            db.beginTransactionReadOnly();
        } else {
            db.beginTransactionNonExclusive();
        }
        return complete(operation);
    }

    private <T> T complete(Supplier<T> operation) {
        try {
            T result = operation.get();
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }
}
