package org.luckyjourney;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hcz.entity.File;
import org.hcz.entity.work.WorkDraft;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.service.file.FileService;
import org.hcz.service.work.adapter.ImageWorkContentAdapter;
import org.hcz.service.work.adapter.TextWorkContentAdapter;
import org.hcz.service.work.adapter.VideoWorkContentAdapter;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkContentAdapterTests {

    @Mock
    private FileService fileService;

    @Test
    void videoAdapterAcceptsVideoAndImageCover() {
        when(fileService.getOwnedFile(10L, 1L)).thenReturn(file("video/mp4"));
        when(fileService.getOwnedFile(11L, 1L)).thenReturn(file("image/jpeg"));

        new VideoWorkContentAdapter(fileService)
                .validate(WorkDraft.video(10L, 11L), 1L);
    }

    @Test
    void videoAdapterRejectsImageAsVideoSource() {
        when(fileService.getOwnedFile(10L, 1L)).thenReturn(file("image/jpeg"));

        assertThrows(AppException.class, () -> new VideoWorkContentAdapter(fileService)
                .validate(WorkDraft.video(10L, null), 1L));
    }

    @Test
    void imageAdapterKeepsMultipleImageOrder() {
        when(fileService.getOwnedFile(10L, 1L)).thenReturn(file("image/jpeg"));
        when(fileService.getOwnedFile(11L, 1L)).thenReturn(file("image/png"));

        WorkDraft draft = WorkDraft.images(List.of(10L, 11L));
        ImageWorkContentAdapter adapter = new ImageWorkContentAdapter(fileService);

        adapter.validate(draft, 1L);

        assertEquals(List.of(10L, 11L), adapter.assetsForAudit(draft).stream()
                .map(asset -> asset.fileId())
                .toList());
    }

    @Test
    void imageAdapterRejectsDuplicateFileIds() {
        when(fileService.getOwnedFile(10L, 1L)).thenReturn(file("image/jpeg"));

        AppException exception = assertThrows(AppException.class, () -> new ImageWorkContentAdapter(fileService)
                .validate(WorkDraft.images(List.of(10L, 10L)), 1L));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("第 2 张图片与前面的图片重复", exception.getMessage());
        // 重复项不再触发第二次归属查询
        verify(fileService, times(1)).getOwnedFile(10L, 1L);
    }

    @Test
    void imageAdapterRejectsDuplicatesAcrossNonAdjacentPositions() {
        when(fileService.getOwnedFile(10L, 1L)).thenReturn(file("image/jpeg"));
        when(fileService.getOwnedFile(11L, 1L)).thenReturn(file("image/png"));

        AppException exception = assertThrows(AppException.class, () -> new ImageWorkContentAdapter(fileService)
                .validate(WorkDraft.images(List.of(10L, 11L, 10L)), 1L));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("第 3 张图片与前面的图片重复", exception.getMessage());
        verify(fileService, times(2)).getOwnedFile(anyLong(), anyLong());
    }

    @Test
    void textAdapterAcceptsTextWithoutAssets() {
        TextWorkContentAdapter adapter = new TextWorkContentAdapter();

        adapter.validate(WorkDraft.text("hello world"), 1L);

        assertEquals(List.of(), adapter.assetsForAudit(WorkDraft.text("hello world")));
    }

    @Test
    void textAdapterRejectsBlankContent() {
        assertThrows(IllegalArgumentException.class, () -> WorkDraft.text("  "));
    }

    private File file(String format) {
        File file = new File();
        file.setFormat(format);
        return file;
    }
}
