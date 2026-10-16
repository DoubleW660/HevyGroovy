package com.example.hevygroovy.persistence;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.model.TemplateExerciseItemModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class SQLiteTemplateExerciseRepositoryTest {
    @Test void detailRowsUseOneQueryAndPreserveMissingRest() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        Cursor cursor = mock(Cursor.class);
        when(db.rawQuery(anyString(), any(String[].class))).thenReturn(cursor);
        when(cursor.moveToNext()).thenReturn(true, true, false);
        when(cursor.getLong(0)).thenReturn(3L, 4L);
        when(cursor.getLong(1)).thenReturn(9L, 10L);
        when(cursor.getInt(2)).thenReturn(0, 1);
        when(cursor.isNull(3)).thenReturn(true, false);
        when(cursor.getInt(3)).thenReturn(90);
        when(cursor.getString(4)).thenReturn("Bench", "Squat");
        List<TemplateExerciseItemModel> rows = new SQLiteTemplateExerciseRepository(db).findDetailsByTemplateWorkoutId(7L);
        assertEquals(2, rows.size());
        assertNull(rows.get(0).getRestTargetSeconds());
        assertEquals(90, rows.get(1).getRestTargetSeconds());
        assertEquals("Bench", rows.get(0).getExerciseTitle());
        verify(db).rawQuery(contains("JOIN exercise"), aryEq(new String[]{"7"}));
        verifyNoMoreInteractions(db);
        verify(cursor).close();
    }
}
