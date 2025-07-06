package com.papaymoni.middleware.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(CurrentUser.class) != null &&
                parameter.getParameterType().equals(User.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            log.error("Authentication is null in CurrentUserArgumentResolver");
            return null;
        }

        log.debug("Authentication: {}, Principal type: {}",
                authentication.getName(),
                authentication.getPrincipal() != null ?
                        authentication.getPrincipal().getClass().getName() : "null");

        String username;
        if (authentication.getPrincipal() instanceof UserDetails) {
            username = ((UserDetails) authentication.getPrincipal()).getUsername();
            log.debug("Username extracted from UserDetails: {}", username);
        } else {
            username = authentication.getName();
            log.debug("Username extracted from Authentication name: {}", username);
        }

        try {
            User user = userRepository.findByUsername(username)
                    .orElse(null);

            if (user == null) {
                log.error("User not found for username: {}", username);
            } else {
                log.debug("Found user: ID={}, Username={}", user.getId(), user.getUsername());
            }

            return user;
        } catch (Exception e) {
            log.error("Error resolving user for username: {}", username, e);
            return null;
        }
    }
}