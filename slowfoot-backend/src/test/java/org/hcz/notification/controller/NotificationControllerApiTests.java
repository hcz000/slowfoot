package org.hcz.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hcz.notification.entity.Notification;
import org.hcz.common.exception.ExceptionController;
import org.hcz.common.exception.ErrorCode;
import org.hcz.notification.service.NotificationService;
import org.hcz.notification.service.NotificationSseHub;
import org.hcz.common.util.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerApiTests {

    private static final long USER_ID = 42L;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationSseHub notificationSseHub;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController();
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
        ReflectionTestUtils.setField(controller, "notificationSseHub", notificationSseHub);
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
    void getUnreadCountReturnsTheNotificationCount() throws Exception {
        when(notificationService.unreadCount(USER_ID)).thenReturn(3L);

        mockMvc.perform(get("/slowfoot/notification/unread/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errorCode").value("OK"))
                .andExpect(jsonPath("$.data").value(3));

        verify(notificationService).unreadCount(USER_ID);
    }

    @Test
    void getPageBindsPaginationAndReturnsNotificationRows() throws Exception {
        Page<Notification> page = new Page<>(2, 5, 1);
        Notification notification = new Notification();
        notification.setId(101L);
        notification.setContent("A work was liked");
        page.setRecords(java.util.List.of(notification));
        when(notificationService.page(eq(USER_ID), any())).thenReturn(page);

        mockMvc.perform(get("/slowfoot/notification")
                        .param("page", "2")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].id").value(101));

        ArgumentCaptor<org.hcz.common.vo.BasePage> requestPage =
                ArgumentCaptor.forClass(org.hcz.common.vo.BasePage.class);
        verify(notificationService).page(eq(USER_ID), requestPage.capture());
        org.junit.jupiter.api.Assertions.assertEquals(2L, requestPage.getValue().getPage());
        org.junit.jupiter.api.Assertions.assertEquals(5L, requestPage.getValue().getLimit());
    }

    @Test
    void putReadUsesPathIdAndAuthenticatedUser() throws Exception {
        mockMvc.perform(put("/slowfoot/notification/{id}/read", 101L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errorCode").value("OK"));

        verify(notificationService).markRead(101L, USER_ID);
    }

    @Test
    void putReadAllReturnsTheNumberOfUpdatedNotifications() throws Exception {
        when(notificationService.markAllRead(USER_ID)).thenReturn(4);

        mockMvc.perform(put("/slowfoot/notification/read/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(4));

        verify(notificationService).markAllRead(USER_ID);
    }

    @Test
    void putReadMapsMissingNotificationToNotFoundResponse() throws Exception {
        doThrow(new org.hcz.common.exception.AppException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found"))
                .when(notificationService).markRead(404L, USER_ID);

        mockMvc.perform(put("/slowfoot/notification/404/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }

    @Test
    void getEventsSubscribesAuthenticatedUserAndStartsEventStream() throws Exception {
        when(notificationSseHub.subscribe(USER_ID)).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/slowfoot/notification/events")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(notificationSseHub).subscribe(USER_ID);
    }
}
