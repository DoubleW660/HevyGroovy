package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import java.util.function.Supplier;

/** Execute service work synchronously on the thread owning the transaction. */
final class SQLiteTransactions {
    private final SQLiteDatabase db;

    SQLiteTransactions(SQLiteDatabase db) {
        this.db = db;
    }

    <T> T run(Supplier<T> operation) {
        db.beginTransaction();
        try {
            T result = operation.get();
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }
}
