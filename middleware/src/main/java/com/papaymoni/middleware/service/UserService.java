//package com.papaymoni.middleware.service;
//
//import com.papaymoni.middleware.dto.UserProfileDto;
//import com.papaymoni.middleware.dto.UserRegistrationDto;
//import com.papaymoni.middleware.model.User;
//
//import java.util.List;
//
//public interface UserService {
//    User registerUser(UserRegistrationDto registrationDto);
//    User getUserById(Long id);
//    User getUserByUsername(String username);
//    User getUserByEmail(String email);
//    List<User> getAllUsers();
//    User updateUser(User user);
//    void deleteUser(Long id);
//    boolean existsByUsername(String username);
//    boolean existsByEmail(String email);
//    User getUserByUsernameOrEmail(String usernameOrEmail);
//
//    // New method for optimized user profile retrieval
//    UserProfileDto getUserProfileByUsername(String username);
//}


package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.dto.UserProfileDto;
import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.model.User;

import java.util.List;

public interface UserService {
    /**
     * Register a new user with basic information
     * @param registrationDto the registration data
     * @return the created user
     */
    User registerUser(UserRegistrationDto registrationDto);

    /**
     * Register a new user with enhanced information including BVN and date of birth
     * @param registrationDto the enhanced registration data
     * @return the created user
     */
    User registerEnhancedUser(UserRegistrationDto registrationDto);

    /**
     * Get a user by their ID
     * @param id the user ID
     * @return the user
     */
    User getUserById(Long id);

    /**
     * Get a user by their username
     * @param username the username
     * @return the user
     */
    User getUserByUsername(String username);

    /**
     * Get a user by their email
     * @param email the email
     * @return the user
     */
    User getUserByEmail(String email);

    /**
     * Get all users in the system
     * @return list of all users
     */
    List<User> getAllUsers();

    /**
     * Update a user's information
     * @param user the user with updated information
     * @return the updated user
     */
    User updateUser(User user);

    /**
     * Delete a user by their ID
     * @param id the user ID
     */
    void deleteUser(Long id);

    /**
     * Check if a username already exists
     * @param username the username to check
     * @return true if the username exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if an email already exists
     * @param email the email to check
     * @return true if the email exists
     */
    boolean existsByEmail(String email);

    /**
     * Check if a BVN already exists
     * @param bvn the BVN to check
     * @return true if the BVN exists
     */
    boolean existsByBvn(String bvn);

    /**
     * Get a user by either their username or email
     * @param usernameOrEmail the username or email
     * @return the user
     */
    User getUserByUsernameOrEmail(String usernameOrEmail);

    /**
     * Update a user's email verification status
     * @param email the email to update
     * @param status the verification status
     * @return the updated user
     */
    User updateEmailVerificationStatus(String email, boolean status);

    /**
     * Update a user's BVN verification status
     * @param bvn the BVN to update
     * @param status the verification status
     * @return the updated user
     */
    User updateBvnVerificationStatus(String bvn, boolean status);

    /**
     * Get optimized user profile by username
     * @param username the username
     * @return the user profile DTO
     */
    UserProfileDto getUserProfileByUsername(String username);
}