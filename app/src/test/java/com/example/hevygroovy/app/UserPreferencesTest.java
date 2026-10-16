package com.example.hevygroovy.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.hevygroovy.entity.enums.Unit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserPreferencesTest {
    @Test void constructionDoesNotOpenPreferencesAndMissingValueDefaultsToPounds() {
        Context context = mock(Context.class);
        UserPreferences store = new UserPreferences(context);
        verifyNoInteractions(context);
        SharedPreferences preferences = mock(SharedPreferences.class);
        when(context.getSharedPreferences("local_profile", Context.MODE_PRIVATE)).thenReturn(preferences);
        when(preferences.getString("default_weight_unit", "LB")).thenReturn("LB");
        assertEquals(Unit.LB, store.readDefaultUnit());
    }

    @Test void readsPersistedUnitAndFallsBackForUnsupportedOrMissingEnumValues() {
        Context context = mock(Context.class);
        SharedPreferences preferences = mock(SharedPreferences.class);
        when(context.getSharedPreferences("local_profile", Context.MODE_PRIVATE)).thenReturn(preferences);
        when(preferences.getString("default_weight_unit", "LB")).thenReturn("KG", "STONE", null);
        UserPreferences store = new UserPreferences(context);
        assertEquals(Unit.KG, store.readDefaultUnit());
        assertEquals(Unit.LB, store.readDefaultUnit());
        assertEquals(Unit.LB, store.readDefaultUnit());
        verify(context, times(1)).getSharedPreferences("local_profile", Context.MODE_PRIVATE);
    }

    @Test void savesUnitWithCommitAndReportsPersistenceFailure() {
        Context context = mock(Context.class);
        SharedPreferences preferences = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(context.getSharedPreferences("local_profile", Context.MODE_PRIVATE)).thenReturn(preferences);
        when(preferences.edit()).thenReturn(editor);
        when(editor.putString("default_weight_unit", "KG")).thenReturn(editor);
        when(editor.commit()).thenReturn(true, false);
        UserPreferences store = new UserPreferences(context);
        store.setDefaultUnit(Unit.KG);
        assertThrows(IllegalStateException.class, () -> store.setDefaultUnit(Unit.KG));
        verify(editor, times(2)).commit();
        verify(editor, never()).apply();
    }

    @Test void nullUnitFailsBeforeOpeningPreferences() {
        Context context = mock(Context.class);
        assertThrows(NullPointerException.class, () -> new UserPreferences(context).setDefaultUnit(null));
        verifyNoInteractions(context);
    }
}
