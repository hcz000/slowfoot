package org.hcz.notification.controller;

import org.hcz.common.vo.BasePage;
import org.hcz.common.util.UserHolder;
import org.hcz.notification.service.NotificationService;
import org.hcz.notification.service.NotificationSseHub;
import org.hcz.common.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slowfoot/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationSseHub notificationSseHub;

    @GetMapping(value = "/events", produces = "text/event-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events() {
        return notificationSseHub.subscribe(UserHolder.get());
    }

    @GetMapping
    public ApiResponse page(BasePage basePage) {
        return ApiResponse.ok().data(notificationService.page(UserHolder.get(), basePage));
    }

    @GetMapping("/unread/count")
    public ApiResponse unreadCount() {
        return ApiResponse.ok().data(notificationService.unreadCount(UserHolder.get()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse markRead(@PathVariable Long id) {
        notificationService.markRead(id, UserHolder.get());
        return ApiResponse.ok();
    }

    @PutMapping("/read/all")
    public ApiResponse markAllRead() {
        return ApiResponse.ok().data(notificationService.markAllRead(UserHolder.get()));
    }
}
