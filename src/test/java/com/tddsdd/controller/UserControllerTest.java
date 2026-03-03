package com.tddsdd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tddsdd.dto.CreateUserRequest;
import com.tddsdd.dto.UpdateUserRequest;
import com.tddsdd.dto.UserDTO;
import com.tddsdd.exception.DuplicateResourceException;
import com.tddsdd.exception.ResourceNotFoundException;
import com.tddsdd.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UserController.
 * These tests define the REST API contract — AI agents must generate
 * a UserController that makes ALL these tests pass.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserDTO sampleUserDTO;
    private CreateUserRequest createRequest;
    private UpdateUserRequest updateRequest;

    @BeforeEach
    void setUp() {
        sampleUserDTO = UserDTO.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .active(true)
                .build();

        createRequest = CreateUserRequest.builder()
                .name("John Doe")
                .email("john@example.com")
                .build();

        updateRequest = UpdateUserRequest.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .build();
    }

    @Test
    @DisplayName("POST /api/users - should create user and return 201")
    void createUser_shouldReturn201() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenReturn(sampleUserDTO);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")))
                .andExpect(jsonPath("$.active", is(true)));

        verify(userService).createUser(any(CreateUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/users - should return 409 for duplicate email")
    void createUser_duplicateEmail_shouldReturn409() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new DuplicateResourceException("User", "email", "john@example.com"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/users/{id} - should return user and 200")
    void getUserById_shouldReturn200() throws Exception {
        when(userService.getUserById(1L)).thenReturn(sampleUserDTO);

        mockMvc.perform(get("/api/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));

        verify(userService).getUserById(1L);
    }

    @Test
    @DisplayName("GET /api/users/{id} - should return 404 when not found")
    void getUserById_notFound_shouldReturn404() throws Exception {
        when(userService.getUserById(999L))
                .thenThrow(new ResourceNotFoundException("User", 999L));

        mockMvc.perform(get("/api/users/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/users - should return all active users and 200")
    void getAllActiveUsers_shouldReturn200() throws Exception {
        UserDTO secondUser = UserDTO.builder()
                .id(2L).name("Jane Doe").email("jane@example.com").active(true).build();

        when(userService.getAllActiveUsers())
                .thenReturn(List.of(sampleUserDTO, secondUser));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("John Doe")))
                .andExpect(jsonPath("$[1].name", is("Jane Doe")));

        verify(userService).getAllActiveUsers();
    }

    @Test
    @DisplayName("PUT /api/users/{id} - should update user and return 200")
    void updateUser_shouldReturn200() throws Exception {
        UserDTO updatedDTO = UserDTO.builder()
                .id(1L).name("Jane Doe").email("jane@example.com").active(true).build();

        when(userService.updateUser(eq(1L), any(UpdateUserRequest.class)))
                .thenReturn(updatedDTO);

        mockMvc.perform(put("/api/users/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Jane Doe")))
                .andExpect(jsonPath("$.email", is("jane@example.com")));

        verify(userService).updateUser(eq(1L), any(UpdateUserRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - should soft-delete and return 204")
    void deleteUser_shouldReturn204() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(1L);
    }
}
