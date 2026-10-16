package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Internal helpers; table names and selections are supplied only by repository code. */
final class SQLiteRows {
    private SQLiteRows() { }

    static long save(SQLiteDatabase db, String table, long id, ContentValues values) {
        if (id == 0) { return db.insertOrThrow(table, null, values); }
        if (id < 0 || db.update(table, values, "id = ?", new String[]{Long.toString(id)}) != 1) {
            throw new IllegalStateException("Row not found in " + table);
        }
        return id;
    }

    static <T> List<T> query(SQLiteDatabase db, String table, String selection,
                             String[] args, String order, Function<Cursor, T> mapper) {
        List<T> result = new ArrayList<>();
        try (Cursor cursor = db.query(table, null, selection, args, null, null, order)) {
            while (cursor.moveToNext()) { result.add(mapper.apply(cursor)); }
        }
        return result;
    }
}
