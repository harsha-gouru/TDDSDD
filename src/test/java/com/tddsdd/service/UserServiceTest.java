package com.tddsdd.service;

import com.tddsdd.dto.CreateUserRequest;
import com.tddsdd.dto.UpdateUserRequest;
import com.tddsdd.dto.UserDTO;
import com.tddsdd.entity.User;
import com.tddsdd.exception.DuplicateResourceException;
import com.tddsdd.exception.ResourceNotFoundException;
import com.tddsdd.mapper.UserMapper;
import com.tddsdd.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl.
 * These tests define the expected behavior — AI agents must generate
 * an implementation that makes ALL these tests pass.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userServiceImpl;

    private User sampleUser;
    private UserDTO sampleUserDTO;
    private CreateUserRequest createRequest;
    private UpdateUserRequest updateRequest;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .active(true)
                .build();

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

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create user successfully")
        void createUser_success() {
            when(userRepository.findByEmail(createRequest.getEmail()))
                    .thenReturn(Optional.empty());
            when(userMapper.toEntity(createRequest)).thenReturn(sampleUser);
            when(userRepository.save(any(User.class))).thenReturn(sampleUser);
            when(userMapper.toDTO(sampleUser)).thenReturn(sampleUserDTO);

            UserDTO result = userServiceImpl.createUser(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("John Doe");
            assertThat(result.getEmail()).isEqualTo("john@example.com");
            assertThat(result.isActive()).isTrue();

            verify(userRepository).findByEmail(createRequest.getEmail());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when email already exists")
        void createUser_duplicateEmail_throwsException() {
            when(userRepository.findByEmail(createRequest.getEmail()))
                    .thenReturn(Optional.of(sampleUser));

            assertThatThrownBy(() -> userServiceImpl.createUser(createRequest))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(userRepository).findByEmail(createRequest.getEmail());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("should return user when found")
        void getUserById_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userMapper.toDTO(sampleUser)).thenReturn(sampleUserDTO);

            UserDTO result = userServiceImpl.getUserById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("John Doe");

            verify(userRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void getUserById_notFound_throwsException() {
            when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userServiceImpl.getUserById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(userRepository).findById(999L);
        }
    }

    @Nested
    @DisplayName("getAllActiveUsers")
    class GetAllActiveUsers {

        @Test
        @DisplayName("should return all active users")
        void getAllActiveUsers_success() {
            User secondUser = User.builder()
                    .id(2L).name("Jane Doe").email("jane@example.com").active(true).build();
            UserDTO secondUserDTO = UserDTO.builder()
                    .id(2L).name("Jane Doe").email("jane@example.com").active(true).build();

            when(userRepository.findByActiveTrue())
                    .thenReturn(List.of(sampleUser, secondUser));
            when(userMapper.toDTO(sampleUser)).thenReturn(sampleUserDTO);
            when(userMapper.toDTO(secondUser)).thenReturn(secondUserDTO);

            List<UserDTO> result = userServiceImpl.getAllActiveUsers();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("John Doe");
            assertThat(result.get(1).getName()).isEqualTo("Jane Doe");

            verify(userRepository).findByActiveTrue();
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("should update user successfully")
        void updateUser_success() {
            User updatedUser = User.builder()
                    .id(1L).name("Jane Doe").email("jane@example.com").active(true).build();
            UserDTO updatedUserDTO = UserDTO.builder()
                    .id(1L).name("Jane Doe").email("jane@example.com").active(true).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);
            when(userMapper.toDTO(updatedUser)).thenReturn(updatedUserDTO);

            UserDTO result = userServiceImpl.updateUser(1L, updateRequest);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Jane Doe");
            assertThat(result.getEmail()).isEqualTo("jane@example.com");

            verify(userRepository).findById(1L);
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("should soft-delete user by setting active to false")
        void deleteUser_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenReturn(sampleUser);

            userServiceImpl.deleteUser(1L);

            assertThat(sampleUser.isActive()).isFalse();
            verify(userRepository).findById(1L);
            verify(userRepository).save(sampleUser);
        }
    }
}
