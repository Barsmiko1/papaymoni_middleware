package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.model.Notification;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.security.CurrentUser;
import com.papaymoni.middleware.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Get all notifications for the current user
     * @param currentUser The authenticated user
     * @return List of notifications
     */
    @GetMapping
    public ResponseEntity<ApiResponse> getUserNotifications(@CurrentUser User currentUser) {
        if (currentUser == null) {
            log.error("User is null in getUserNotifications");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required", null));
        }

        try {
            log.debug("Getting notifications for user ID: {}", currentUser.getId());
            List<Notification> notifications = notificationService.getUserNotifications(currentUser);
            return ResponseEntity.ok(new ApiResponse(true, "User notifications retrieved successfully", notifications));
        } catch (Exception e) {
            log.error("Error retrieving user notifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "An unexpected error occurred: " + e.getMessage(), null));
        }
    }

    /**
     * Get unread notifications for the current user
     * @param currentUser The authenticated user
     * @return List of unread notifications
     */
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse> getUnreadNotifications(@CurrentUser User currentUser) {
        if (currentUser == null) {
            log.error("User is null in getUnreadNotifications");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required", null));
        }

        try {
            log.debug("Getting unread notifications for user ID: {}", currentUser.getId());
            List<Notification> notifications = notificationService.getUserUnreadNotifications(currentUser);
            return ResponseEntity.ok(new ApiResponse(true, "Unread notifications retrieved successfully", notifications));
        } catch (Exception e) {
            log.error("Error retrieving unread notifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "An unexpected error occurred: " + e.getMessage(), null));
        }
    }

    /**
     * Get count of unread notifications for the current user
     * @param currentUser The authenticated user
     * @return Count of unread notifications
     */
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse> getUnreadNotificationCount(@CurrentUser User currentUser) {
        if (currentUser == null) {
            log.error("User is null in getUnreadNotificationCount");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required", null));
        }

        try {
            log.debug("Getting unread notification count for user ID: {}", currentUser.getId());
            long count = notificationService.countUnreadNotifications(currentUser);
            return ResponseEntity.ok(new ApiResponse(true, "Unread notification count retrieved successfully", count));
        } catch (Exception e) {
            log.error("Error retrieving unread notification count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "An unexpected error occurred: " + e.getMessage(), null));
        }
    }

    /**
     * Mark a notification as read
     * @param id The notification ID
     * @param currentUser The authenticated user
     * @return Success response
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse> markNotificationAsRead(
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        if (currentUser == null) {
            log.error("User is null in markNotificationAsRead");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required", null));
        }

        try {
            log.debug("Marking notification ID: {} as read for user ID: {}", id, currentUser.getId());

            // Verify ownership by first getting the notifications for this user
            List<Notification> userNotifications = notificationService.getUserNotifications(currentUser);
            boolean belongsToUser = userNotifications.stream()
                    .anyMatch(notification -> notification.getId().equals(id));

            if (!belongsToUser) {
                log.warn("User ID: {} attempted to mark notification ID: {} as read, but it doesn't belong to them", currentUser.getId(), id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse(false, "You do not have permission to access this notification", null));
            }

            notificationService.markNotificationAsRead(id);
            return ResponseEntity.ok(new ApiResponse(true, "Notification marked as read successfully", null));
        } catch (Exception e) {
            log.error("Error marking notification as read", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "An unexpected error occurred: " + e.getMessage(), null));
        }
    }

    /**
     * Mark all notifications as read for the current user
     * @param currentUser The authenticated user
     * @return Success response
     */
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse> markAllNotificationsAsRead(@CurrentUser User currentUser) {
        if (currentUser == null) {
            log.error("User is null in markAllNotificationsAsRead");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required", null));
        }

        try {
            log.debug("Marking all notifications as read for user ID: {}", currentUser.getId());
            List<Notification> unreadNotifications = notificationService.getUserUnreadNotifications(currentUser);
            for (Notification notification : unreadNotifications) {
                notificationService.markNotificationAsRead(notification.getId());
            }
            return ResponseEntity.ok(new ApiResponse(true, "All notifications marked as read successfully", null));
        } catch (Exception e) {
            log.error("Error marking all notifications as read", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "An unexpected error occurred: " + e.getMessage(), null));
        }
    }

    /**
     * Process a notification (currently just marks it as read)
     * @param id The notification ID
     * @param currentUser The authenticated user
     * @return Success response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> processNotification(
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        if (currentUser == null) {
            log.error("User is null in processNotification");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required", null));
        }

        try {
            log.debug("Processing notification ID: {} for user ID: {}", id, currentUser.getId());

            // Verify ownership by first getting the notifications for this user
            List<Notification> userNotifications = notificationService.getUserNotifications(currentUser);
            boolean belongsToUser = userNotifications.stream()
                    .anyMatch(notification -> notification.getId().equals(id));

            if (!belongsToUser) {
                log.warn("User ID: {} attempted to process notification ID: {}, but it doesn't belong to them", currentUser.getId(), id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse(false, "You do not have permission to access this notification", null));
            }

            // Just mark as read for now
            notificationService.markNotificationAsRead(id);
            return ResponseEntity.ok(new ApiResponse(true, "Notification processed successfully", null));
        } catch (Exception e) {
            log.error("Error processing notification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "An unexpected error occurred: " + e.getMessage(), null));
        }
    }
}