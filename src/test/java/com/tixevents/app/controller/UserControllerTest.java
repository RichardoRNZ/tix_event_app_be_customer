package com.tixevents.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.config.SecurityConfig;
import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.mapper.BookingDetailMapperImpl;
import com.tixevents.app.mapper.BookingMapperImpl;
import com.tixevents.app.mapper.UserMapperImpl;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, UserMapperImpl.class, BookingMapperImpl.class, BookingDetailMapperImpl.class})
@DisplayName("UserController - /api/users/**")
class UserControllerTest extends AbstractControllerTest {

    private static final String BLOCK_JSON = "{\"id\":2,\"blocked\":true}";

    // =====================================================================
    @Nested
    @DisplayName("GET /api/users/")
    class ListUsers {

        @Test
        @DisplayName("401 - unauthenticated")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/users/")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 - admin gets page of users without password")
        void admin() throws Exception {
            when(userQueryService.findAll(anyMap(), any()))
                    .thenReturn(new PageImpl<>(List.of(userEntity(2L, "u2@x.com", false)), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/users/").param("name", "User 2").with(user("admin@x.com").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].email").value("u2@x.com"))
                    .andExpect(jsonPath("$.content[0].password").doesNotExist());
        }

        @Test
        @DisplayName("[BUG-01] regular user must NOT be able to list all users (missing admin authorization)")
        void regularUserForbidden() throws Exception {
            when(userQueryService.findAll(anyMap(), any()))
                    .thenReturn(new PageImpl<>(List.of(userEntity(2L, "u2@x.com", false)), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/users/").with(user("user@x.com").roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("[BUG-15] GET /api/users (without trailing slash) should be routed, not 404")
        void withoutTrailingSlash() throws Exception {
            when(userQueryService.findAll(anyMap(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/users").with(user("admin@x.com").roles("ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /api/users/find")
    class FindUser {

        @Test
        @DisplayName("200 - admin finds user by criteria")
        void admin() throws Exception {
            when(userQueryService.getUser(anyMap())).thenReturn(userEntity(2L, "u2@x.com", false));

            mockMvc.perform(get("/api/users/find").param("email", "u2@x.com").with(user("admin@x.com").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(2));
        }

        @Test
        @DisplayName("[BUG-01] regular user must NOT be able to look up other users")
        void regularUserForbidden() throws Exception {
            when(userQueryService.getUser(anyMap())).thenReturn(userEntity(2L, "u2@x.com", false));

            mockMvc.perform(get("/api/users/find").param("email", "u2@x.com").with(user("user@x.com").roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("[BUG-06] user not found should return 404, not 500")
        void notFound() throws Exception {
            when(userQueryService.getUser(anyMap())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with given criteria"));

            mockMvc.perform(get("/api/users/find").param("email", "none@x.com").with(user("admin@x.com").roles("ADMIN")))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("PATCH /api/users/block-status")
    class BlockStatus {

        @Test
        @DisplayName("401 - unauthenticated")
        void unauthenticated() throws Exception {
            mockMvc.perform(patch("/api/users/block-status").contentType(MediaType.APPLICATION_JSON).content(BLOCK_JSON))
                    .andExpect(status().isUnauthorized());
            verify(userService, never()).updateBlockedStatus(any());
        }

        @Test
        @DisplayName("200 - admin blocks a user")
        void admin() throws Exception {
            UserDTO dto = new UserDTO();
            dto.setId(2L);
            dto.setBlocked(true);
            when(userService.updateBlockedStatus(any())).thenReturn(dto);

            mockMvc.perform(patch("/api/users/block-status").with(user("admin@x.com").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(BLOCK_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.blocked").value(true));
        }

        @Test
        @DisplayName("[BUG-01] regular user must NOT be able to block/unblock users (privilege escalation)")
        void regularUserForbidden() throws Exception {
            mockMvc.perform(patch("/api/users/block-status").with(user("user@x.com").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content(BLOCK_JSON))
                    .andExpect(status().isForbidden());
            verify(userService, never()).updateBlockedStatus(any());
        }
    }
}
