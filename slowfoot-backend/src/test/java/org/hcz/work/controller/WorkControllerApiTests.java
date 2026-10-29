package org.hcz.work.controller;

import org.hcz.common.exception.ExceptionController;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.work.service.WorkService;
import org.hcz.common.util.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkControllerApiTests {

    private static final long USER_ID = 42L;

    @Mock
    private WorkService workService;

    @Mock
    private AliyunOssFileService fileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WorkController controller = new WorkController(workService, fileService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ExceptionController())
                .build();
        UserHolder.set(USER_ID);
    }

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void putLikeReturnsTheChangedState() throws Exception {
        when(workService.setLike(73L, true)).thenReturn(true);

        mockMvc.perform(put("/slowfoot/work/like/73"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        verify(workService).setLike(73L, true);
    }

    @Test
    void deleteWorkPassesThePathIdAndAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/slowfoot/work/73"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(workService).deleteMine(73L, USER_ID);
    }

    @Test
    void invalidImagePublishBodyReturnsValidationContractWithoutCallingService() throws Exception {
        mockMvc.perform(post("/slowfoot/work/image")
                        .contentType("application/json")
                        .content("{\"title\":\" \",\"typeId\":0,\"imageFileIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());

        verifyNoInteractions(workService, fileService);
    }
}
