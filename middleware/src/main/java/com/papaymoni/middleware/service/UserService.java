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
//}


package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.UserProfileDto;
import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.model.User;

import java.util.List;

public interface UserService {
    User registerUser(UserRegistrationDto registrationDto);
    User getUserById(Long id);
    User getUserByUsername(String username);
    User getUserByEmail(String email);
    List<User> getAllUsers();
    User updateUser(User user);
    void deleteUser(Long id);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    User getUserByUsernameOrEmail(String usernameOrEmail);

    // New method for optimized user profile retrieval
    UserProfileDto getUserProfileByUsername(String username);
}