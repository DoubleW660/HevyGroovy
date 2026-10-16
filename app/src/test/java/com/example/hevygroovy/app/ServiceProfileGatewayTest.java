package com.example.hevygroovy.app;

import com.example.hevygroovy.entity.enums.Unit;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ServiceProfileGatewayTest {
    @Test void preferenceReadsRunOnWorkerAndResultsArePostedToMain() {
        AppServices services = mock(AppServices.class);
        UserPreferences preferences = mock(UserPreferences.class);
        when(preferences.readDefaultUnit()).thenReturn(Unit.KG);
        ArrayDeque<Runnable> background = new ArrayDeque<>();
        ArrayDeque<Runnable> main = new ArrayDeque<>();
        AppWorker worker = new AppWorker(background::add, main::add, () -> services, () -> 1L);
        ProfileGateway.Callback callback = mock(ProfileGateway.Callback.class);
        new ServiceProfileGateway(worker, preferences).load(callback);
        verifyNoInteractions(preferences, callback);
        background.remove().run();
        verify(preferences).readDefaultUnit();
        verifyNoInteractions(callback);
        main.remove().run();
        verify(callback).onSuccess(Unit.KG);
    }

    @Test void savedValueReturnsDirectlyWithoutARefreshThatCouldReportASecondFailure() {
        UserPreferences preferences = mock(UserPreferences.class);
        ProfileGateway.Callback callback = mock(ProfileGateway.Callback.class);
        new ServiceProfileGateway(worker(), preferences).setDefaultUnit(Unit.KG, callback);
        verify(preferences).setDefaultUnit(Unit.KG);
        verifyNoMoreInteractions(preferences);
        verify(callback).onSuccess(Unit.KG);
        verify(callback, never()).onFailure(any());
    }

    @Test void failedSaveIsReportedWithoutSuccess() {
        UserPreferences preferences = mock(UserPreferences.class);
        IllegalStateException failure = new IllegalStateException("Disk full");
        doThrow(failure).when(preferences).setDefaultUnit(Unit.KG);
        ProfileGateway.Callback callback = mock(ProfileGateway.Callback.class);
        new ServiceProfileGateway(worker(), preferences).setDefaultUnit(Unit.KG, callback);
        verify(callback).onFailure(failure);
        verify(callback, never()).onSuccess(any());
    }

    private AppWorker worker() {
        return new AppWorker(Runnable::run, Runnable::run, () -> mock(AppServices.class), () -> 1L);
    }
}
