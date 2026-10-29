package org.hcz.comment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hcz.common.vo.BasePage;
import org.hcz.comment.dto.WorkCommentVO;
import org.hcz.comment.service.WorkCommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicCommentControllerApiTests {

    @Mock
    private WorkCommentService commentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PublicCommentController controller = new PublicCommentController();
        ReflectionTestUtils.setField(controller, "commentService", commentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getApprovedCommentsBindsWorkIdAndPagination() throws Exception {
        Page<WorkCommentVO> page = new Page<>(3, 7, 0);
        when(commentService.pageApproved(eq(88L), any(BasePage.class))).thenReturn(page);

        mockMvc.perform(get("/slowfoot/index/comment/work/88")
                        .param("page", "3")
                        .param("limit", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.current").value(3))
                .andExpect(jsonPath("$.data.size").value(7))
                .andExpect(jsonPath("$.data.total").value(0));

        ArgumentCaptor<BasePage> requestPage = ArgumentCaptor.forClass(BasePage.class);
        verify(commentService).pageApproved(eq(88L), requestPage.capture());
        assertEquals(3L, requestPage.getValue().getPage());
        assertEquals(7L, requestPage.getValue().getLimit());
    }
}
