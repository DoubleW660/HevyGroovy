package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.dto.AddTemplateExerciseRequest;
import com.example.hevygroovy.dto.CreateTemplateRequest;
import com.example.hevygroovy.dto.UpdateTemplateRequest;
import com.example.hevygroovy.model.TemplateDetailModel;
import com.example.hevygroovy.model.TemplateSummaryModel;
import com.example.hevygroovy.service.TemplateService;
import java.util.List;

/** The delegate's repositories must share this database. Invoke off the UI thread. */
public final class TransactionalTemplateService implements TemplateService {
    private final TemplateService delegate;
    private final SQLiteTransactions transactions;

    public TransactionalTemplateService(SQLiteDatabase db, TemplateService delegate) {
        this.delegate = delegate;
        transactions = new SQLiteTransactions(db);
    }

    @Override
    public TemplateSummaryModel createTemplate(long userId, CreateTemplateRequest request) {
        return transactions.run(() -> delegate.createTemplate(userId, request));
    }

    @Override
    public TemplateSummaryModel updateTemplate(long userId, long templateId, UpdateTemplateRequest request) {
        return transactions.run(() -> delegate.updateTemplate(userId, templateId, request));
    }

    @Override
    public void addExerciseToTemplate(long userId, AddTemplateExerciseRequest request) {
        transactions.run(() -> { delegate.addExerciseToTemplate(userId, request); return null; });
    }

    @Override
    public void removeExerciseFromTemplate(long userId, long rowId, long templateId) {
        transactions.run(() -> { delegate.removeExerciseFromTemplate(userId, rowId, templateId); return null; });
    }

    @Override
    public void moveExerciseInTemplate(long userId, long rowId, int orderIndex) {
        transactions.run(() -> { delegate.moveExerciseInTemplate(userId, rowId, orderIndex); return null; });
    }

    @Override
    public TemplateDetailModel getTemplateDetail(long userId, long templateId) {
        return transactions.run(() -> delegate.getTemplateDetail(userId, templateId));
    }

    @Override
    public List<TemplateSummaryModel> listTemplates(long userId, boolean includeArchived) {
        return transactions.run(() -> delegate.listTemplates(userId, includeArchived));
    }

    @Override
    public void archiveTemplate(long userId, long templateId) {
        transactions.run(() -> { delegate.archiveTemplate(userId, templateId); return null; });
    }

    @Override
    public void restoreTemplate(long userId, long templateId) {
        transactions.run(() -> { delegate.restoreTemplate(userId, templateId); return null; });
    }
}
