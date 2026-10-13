package com.tixevents.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.entity.User;
import com.tixevents.app.mapper.UserMapperImpl;
import com.tixevents.app.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - Unit Tests")
class UserServiceTest {

    @Mock private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, new UserMapperImpl());
    }

    @Test
    @DisplayName("getUserByEmail returns UserDTO when user found")
    void testGetUserByEmailSuccess() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setName("John");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDTO result = userService.getUserByEmail("user@example.com");

        assertEquals("user@example.com", result.getEmail());
        assertEquals("John", result.getName());
    }

    @Test
    @DisplayName("getUserByEmail throws 404 when user not found")
    void testGetUserByEmailNotFound() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            userService.getUserByEmail("nonexistent@example.com")
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("updateBlockedStatus updates blocked flag and saves user")
    void testUpdateBlockedStatusSuccess() {
        User user = new User();
        user.setId(2L);
        user.setEmail("target@example.com");
        user.setBlocked(false);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserDTO input = new UserDTO();
        input.setId(2L);
        input.setBlocked(true);

        UserDTO result = userService.updateBlockedStatus(input);

        assertTrue(result.getBlocked());
        assertTrue(user.getBlocked());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateBlockedStatus throws 404 when target user does not exist")
    void testUpdateBlockedStatusNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        UserDTO input = new UserDTO();
        input.setId(999L);
        input.setBlocked(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            userService.updateBlockedStatus(input)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
