package org.hcz.service.work.adapter;

import org.hcz.entity.work.WorkAsset;
import org.hcz.entity.work.WorkDraft;
import org.hcz.entity.work.WorkType;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


// Validates text-仅 作品 并 exposes no file assets 用于 media auditing.

@Service
public class TextWorkContentAdapter implements WorkContentAdapter {

    @Override
    public WorkType type() {
        return WorkType.TEXT;
    }

    @Override
    public void validate(WorkDraft draft, Long ownerId) {
        if (draft == null || draft.type() != WorkType.TEXT) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "work type is not text");
        }
        if (!draft.assets().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "text work cannot contain file assets");
        }
        if (draft.textContent() == null || draft.textContent().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "text work content cannot be blank");
        }
    }

    @Override
    public List<WorkAsset> assetsForAudit(WorkDraft draft) {
        validateType(draft);
        return Collections.emptyList();
    }

    private void validateType(WorkDraft draft) {
        if (draft == null || draft.type() != WorkType.TEXT) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "work type is not text");
        }
    }
}
