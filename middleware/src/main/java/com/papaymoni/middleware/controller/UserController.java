package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.UserProfileDto;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.security.CurrentUser;
import com.papaymoni.middleware.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDto>> getCurrentUser(@AuthenticationPrincipal UserDetails currentUser) {
        // Changed to use the new service method that returns a DTO directly
        UserProfileDto userProfileDto = userService.getUserProfileByUsername(currentUser.getUsername());
        return ResponseEntity.ok(ApiResponse.success(userProfileDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<User>> updateCurrentUser(@AuthenticationPrincipal UserDetails currentUser,
                                                               @RequestBody User userUpdate) {
        User user = userService.getUserByUsername(currentUser.getUsername());

        // Update only allowed fields
        user.setFirstName(userUpdate.getFirstName());
        user.setLastName(userUpdate.getLastName());
        user.setPhoneNumber(userUpdate.getPhoneNumber());

        User updatedUser = userService.updateUser(user);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", updatedUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    // The convertToDto method is removed as it's now in the service layer
}