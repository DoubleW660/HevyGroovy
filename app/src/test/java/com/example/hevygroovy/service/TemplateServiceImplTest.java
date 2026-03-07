package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.AddTemplateExerciseRequest;
import com.example.hevygroovy.dto.CreateTemplateRequest;
import com.example.hevygroovy.dto.UpdateTemplateRequest;
import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.model.TemplateDetailModel;
import com.example.hevygroovy.model.TemplateSummaryModel;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    private TemplateWorkoutRepository templateWorkoutRepository;
    private TemplateExerciseRepository templateExerciseRepository;
    private ExerciseService exerciseService;

    private TemplateServiceImpl templateService;

    @BeforeEach
    void setUp() {
        templateWorkoutRepository = mock(TemplateWorkoutRepository.class);
        templateExerciseRepository = mock(TemplateExerciseRepository.class);
        exerciseService = mock(ExerciseService.class);

        templateService = new TemplateServiceImpl(
                templateWorkoutRepository,
                templateExerciseRepository,
                exerciseService
        );
    }

    @Test
    void createTemplate_shouldCreateAndReturnSummary() {
        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setTitle("  Push   Day  ");
        request.setDescription("  Chest and triceps  ");

        TemplateWorkout saved = new TemplateWorkout();
        saved.setId(1L);
        saved.setTitle("Push Day");
        saved.setDescription("Chest and triceps");
        saved.setArchived(false);

        when(templateWorkoutRepository.save(any(TemplateWorkout.class))).thenReturn(saved);

        TemplateSummaryModel result = templateService.createTemplate(request);

        assertNotNull(result);
        assertEquals(1L, result.getTemplateWorkoutId());
        assertEquals("Push Day", result.getTitle());
        assertEquals("Chest and triceps", result.getDescription());
        assertFalse(result.isArchived());

        ArgumentCaptor<TemplateWorkout> captor = ArgumentCaptor.forClass(TemplateWorkout.class);
        verify(templateWorkoutRepository).save(captor.capture());

        TemplateWorkout toSave = captor.getValue();
        assertEquals("Push Day", toSave.getTitle());
        assertEquals("Chest and triceps", toSave.getDescription());
        assertFalse(toSave.isArchived());
        assertTrue(toSave.getCreatedAtEpochMillis() > 0);
    }

    @Test
    void updateTemplate_shouldUpdateTitleAndDescription() {
        TemplateWorkout existing = new TemplateWorkout();
        existing.setId(10L);
        existing.setTitle("Old");
        existing.setDescription("Old Desc");
        existing.setArchived(false);

        UpdateTemplateRequest request = new UpdateTemplateRequest();
        request.setTitle("  New   Title ");
        request.setDescription("  New Desc  ");

        when(templateWorkoutRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(templateWorkoutRepository.save(any(TemplateWorkout.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateSummaryModel result = templateService.updateTemplate(10L, request);

        assertEquals("New Title", result.getTitle());
        assertEquals("New Desc", result.getDescription());

        verify(templateWorkoutRepository).save(existing);
    }

    @Test
    void addExerciseToTemplate_shouldAppendToEndWhenOrderIndexIsNull() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(5L);
        template.setArchived(false);

        AddTemplateExerciseRequest request = new AddTemplateExerciseRequest();
        request.setTemplateId(5L);
        request.setExerciseId(20L);
        request.setOrderIndex(null);
        request.setRestTimeSeconds(null);

        when(templateWorkoutRepository.findById(5L)).thenReturn(Optional.of(template));
        when(templateExerciseRepository.findMaxOrderIndex(5L)).thenReturn(2);

        templateService.addExerciseToTemplate(request);

        verify(exerciseService).requireActiveExercise(20L);

        ArgumentCaptor<TemplateExercise> captor = ArgumentCaptor.forClass(TemplateExercise.class);
        verify(templateExerciseRepository).save(captor.capture());

        TemplateExercise saved = captor.getValue();
        assertEquals(5L, saved.getTemplateWorkoutId());
        assertEquals(20L, saved.getExerciseId());
        assertEquals(3, saved.getOrderIndex());
        assertEquals(0, saved.getRestTimeSeconds());
    }

    @Test
    void addExerciseToTemplate_shouldStartAtZeroWhenTemplateHasNoExercises() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(5L);
        template.setArchived(false);

        AddTemplateExerciseRequest request = new AddTemplateExerciseRequest();
        request.setTemplateId(5L);
        request.setExerciseId(20L);
        request.setOrderIndex(null);
        request.setRestTimeSeconds(90);

        when(templateWorkoutRepository.findById(5L)).thenReturn(Optional.of(template));
        when(templateExerciseRepository.findMaxOrderIndex(5L)).thenReturn(null);

        templateService.addExerciseToTemplate(request);

        ArgumentCaptor<TemplateExercise> captor = ArgumentCaptor.forClass(TemplateExercise.class);
        verify(templateExerciseRepository).save(captor.capture());

        TemplateExercise saved = captor.getValue();
        assertEquals(0, saved.getOrderIndex());
        assertEquals(90, saved.getRestTimeSeconds());
    }

    @Test
    void archiveTemplate_shouldMarkTemplateArchived() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(1L);
        template.setArchived(false);

        when(templateWorkoutRepository.findById(1L)).thenReturn(Optional.of(template));

        templateService.archiveTemplate(1L);

        assertTrue(template.isArchived());
        verify(templateWorkoutRepository).save(template);
    }

    @Test
    void restoreTemplate_shouldMarkTemplateActive() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(1L);
        template.setArchived(true);

        when(templateWorkoutRepository.findById(1L)).thenReturn(Optional.of(template));

        templateService.restoreTemplate(1L);

        assertFalse(template.isArchived());
        verify(templateWorkoutRepository).save(template);
    }

    @Test
    void listTemplates_shouldMapRepositoryResults() {
        TemplateWorkout a = new TemplateWorkout();
        a.setId(1L);
        a.setTitle("Push");
        a.setDescription("Chest");
        a.setArchived(false);

        TemplateWorkout b = new TemplateWorkout();
        b.setId(2L);
        b.setTitle("Legs");
        b.setDescription("Quads");
        b.setArchived(true);

        when(templateWorkoutRepository.findAll()).thenReturn(List.of(a, b));

        List<TemplateSummaryModel> result = templateService.listTemplates(true);

        assertEquals(2, result.size());
        assertEquals("Push", result.get(0).getTitle());
        assertEquals("Legs", result.get(1).getTitle());
        assertTrue(result.get(1).isArchived());
    }

    @Test
    void getTemplateDetail_shouldReturnSortedExercises() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(100L);
        template.setTitle("Upper");
        template.setDescription("Upper body");
        template.setArchived(false);

        TemplateExercise second = new TemplateExercise();
        second.setId(2L);
        second.setTemplateWorkoutId(100L);
        second.setExerciseId(22L);
        second.setOrderIndex(1);
        second.setRestTimeSeconds(120);

        TemplateExercise first = new TemplateExercise();
        first.setId(1L);
        first.setTemplateWorkoutId(100L);
        first.setExerciseId(11L);
        first.setOrderIndex(0);
        first.setRestTimeSeconds(60);

        Exercise exercise1 = new Exercise();
        exercise1.setId(11L);
        exercise1.setTitle("Bench Press");

        Exercise exercise2 = new Exercise();
        exercise2.setId(22L);
        exercise2.setTitle("Barbell Row");

        when(templateWorkoutRepository.findById(100L)).thenReturn(Optional.of(template));
        when(templateExerciseRepository.findByTemplateWorkoutId(100L))
                .thenReturn(new ArrayList<>(List.of(second, first)));

        when(exerciseService.requireExercise(11L)).thenReturn(exercise1);
        when(exerciseService.requireExercise(22L)).thenReturn(exercise2);

        TemplateDetailModel result = templateService.getTemplateDetail(100L);

        assertNotNull(result);
        assertEquals(100L, result.getTemplateWorkoutId());
        assertEquals("Upper", result.getTitle());
        assertEquals(2, result.getExercises().size());

        assertEquals(0, result.getExercises().get(0).getOrderIndex());
        assertEquals("Bench Press", result.getExercises().get(0).getExerciseTitle());

        assertEquals(1, result.getExercises().get(1).getOrderIndex());
        assertEquals("Barbell Row", result.getExercises().get(1).getExerciseTitle());
    }

    @Test
    void removeExerciseFromTemplate_shouldDeleteAndShiftLaterExercisesDown() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(50L);
        template.setArchived(false);

        TemplateExercise toRemove = new TemplateExercise();
        toRemove.setId(200L);
        toRemove.setTemplateWorkoutId(50L);
        toRemove.setExerciseId(1L);
        toRemove.setOrderIndex(1);

        TemplateExercise keep0 = new TemplateExercise();
        keep0.setId(201L);
        keep0.setTemplateWorkoutId(50L);
        keep0.setOrderIndex(0);

        TemplateExercise shift2 = new TemplateExercise();
        shift2.setId(202L);
        shift2.setTemplateWorkoutId(50L);
        shift2.setOrderIndex(2);

        TemplateExercise shift3 = new TemplateExercise();
        shift3.setId(203L);
        shift3.setTemplateWorkoutId(50L);
        shift3.setOrderIndex(3);

        when(templateWorkoutRepository.findById(50L)).thenReturn(Optional.of(template));
        when(templateExerciseRepository.findById(200L)).thenReturn(Optional.of(toRemove));
        when(templateExerciseRepository.findByTemplateWorkoutId(50L))
                .thenReturn(new ArrayList<>(List.of(keep0, shift2, shift3)));
        when(templateExerciseRepository.save(any(TemplateExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        templateService.removeExerciseFromTemplate(200L, 50L);

        verify(templateExerciseRepository).delete(200L);
        assertEquals(1, shift2.getOrderIndex());
        assertEquals(2, shift3.getOrderIndex());

        verify(templateExerciseRepository, times(2)).save(any(TemplateExercise.class));
    }

    @Test
    void moveExerciseInTemplate_shouldMoveUpAndShiftCrossedRangeDown() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(60L);
        template.setArchived(false);

        TemplateExercise moved = new TemplateExercise();
        moved.setId(300L);
        moved.setTemplateWorkoutId(60L);
        moved.setOrderIndex(2);

        TemplateExercise e0 = new TemplateExercise();
        e0.setId(301L);
        e0.setTemplateWorkoutId(60L);
        e0.setOrderIndex(0);

        TemplateExercise e1 = new TemplateExercise();
        e1.setId(302L);
        e1.setTemplateWorkoutId(60L);
        e1.setOrderIndex(1);

        when(templateExerciseRepository.findById(300L)).thenReturn(Optional.of(moved));
        when(templateWorkoutRepository.findById(60L)).thenReturn(Optional.of(template));
        when(templateExerciseRepository.findByTemplateWorkoutId(60L))
                .thenReturn(new ArrayList<>(List.of(e0, e1, moved)));
        when(templateExerciseRepository.save(any(TemplateExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        templateService.moveExerciseInTemplate(300L, 0);

        assertEquals(0, moved.getOrderIndex());
        assertEquals(1, e0.getOrderIndex());
        assertEquals(2, e1.getOrderIndex());
    }

    @Test
    void moveExerciseInTemplate_shouldMoveDownAndShiftCrossedRangeUp() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(70L);
        template.setArchived(false);

        TemplateExercise moved = new TemplateExercise();
        moved.setId(400L);
        moved.setTemplateWorkoutId(70L);
        moved.setOrderIndex(0);

        TemplateExercise e1 = new TemplateExercise();
        e1.setId(401L);
        e1.setTemplateWorkoutId(70L);
        e1.setOrderIndex(1);

        TemplateExercise e2 = new TemplateExercise();
        e2.setId(402L);
        e2.setTemplateWorkoutId(70L);
        e2.setOrderIndex(2);

        when(templateExerciseRepository.findById(400L)).thenReturn(Optional.of(moved));
        when(templateWorkoutRepository.findById(70L)).thenReturn(Optional.of(template));
        when(templateExerciseRepository.findByTemplateWorkoutId(70L))
                .thenReturn(new ArrayList<>(List.of(moved, e1, e2)));
        when(templateExerciseRepository.save(any(TemplateExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        templateService.moveExerciseInTemplate(400L, 2);

        assertEquals(2, moved.getOrderIndex());
        assertEquals(0, e1.getOrderIndex());
        assertEquals(1, e2.getOrderIndex());
    }

    @Test
    void moveExerciseInTemplate_shouldDoNothingWhenPositionIsUnchanged() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(80L);
        template.setArchived(false);

        TemplateExercise moved = new TemplateExercise();
        moved.setId(500L);
        moved.setTemplateWorkoutId(80L);
        moved.setOrderIndex(1);

        TemplateExercise e0 = new TemplateExercise();
        e0.setId(501L);
        e0.setTemplateWorkoutId(80L);
        e0.setOrderIndex(0);

        when(templateExerciseRepository.findById(500L)).thenReturn(Optional.of(moved));
        when(templateWorkoutRepository.findById(80L)).thenReturn(Optional.of(template));
        when(templateExerciseRepository.findByTemplateWorkoutId(80L))
                .thenReturn(new ArrayList<>(List.of(e0, moved)));

        templateService.moveExerciseInTemplate(500L, 1);

        verify(templateExerciseRepository, never()).save(any());
    }

    @Test
    void moveExerciseInTemplate_shouldThrowWhenTargetIndexOutOfBounds() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(90L);
        template.setArchived(false);

        TemplateExercise moved = new TemplateExercise();
        moved.setId(600L);
        moved.setTemplateWorkoutId(90L);
        moved.setOrderIndex(0);

        TemplateExercise e1 = new TemplateExercise();
        e1.setId(601L);
        e1.setTemplateWorkoutId(90L);
        e1.setOrderIndex(1);

        when(templateExerciseRepository.findById(600L)).thenReturn(Optional.of(moved));
        when(templateWorkoutRepository.findById(90L)).thenReturn(Optional.of(template));
        when(templateExerciseRepository.findByTemplateWorkoutId(90L))
                .thenReturn(new ArrayList<>(List.of(moved, e1)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> templateService.moveExerciseInTemplate(600L, 5));

        assertEquals("Position is not available", ex.getMessage());
    }
}