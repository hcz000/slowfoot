package org.hcz.controller;

import org.hcz.entity.vo.BasePage;
import org.hcz.util.UserHolder;
import org.hcz.service.notification.NotificationService;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/luckyjourney/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

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
