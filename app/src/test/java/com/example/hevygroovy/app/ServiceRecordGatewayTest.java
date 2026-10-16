package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.service.PersonalRecordService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceRecordGatewayTest {
    @Test void usesTheLocalUserAndCopiesTheSavedRecordResponse() {
        AppServices services = mock(AppServices.class);
        PersonalRecordService records = mock(PersonalRecordService.class);
        when(services.getPersonalRecords()).thenReturn(records);
        List<PersonalRecord> source = new ArrayList<>();
        source.add(new PersonalRecord(RecordType.MOST_REPS, 9, "Bench", 3, 4, 5, 12, 100, Unit.LB, 12, 1000));
        when(records.getExerciseRecords(42, 9)).thenReturn(source);
        AtomicReference<List<PersonalRecord>> result = new AtomicReference<>();
        new ServiceRecordGateway(new AppWorker(Runnable::run, Runnable::run, () -> services, () -> 42))
                .loadExercise(9, new RecordGateway.Callback() {
                    public void onSuccess(List<PersonalRecord> value) { result.set(value); }
                    public void onFailure(Exception error) { fail(error); }
                });
        source.clear();
        verify(records).getExerciseRecords(42, 9);
        assertEquals(1, result.get().size());
        assertThrows(UnsupportedOperationException.class, () -> result.get().clear());
    }
}
