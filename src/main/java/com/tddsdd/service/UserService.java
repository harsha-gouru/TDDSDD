package com.tddsdd.service;

import com.tddsdd.dto.CreateUserRequest;
import com.tddsdd.dto.UpdateUserRequest;
import com.tddsdd.dto.UserDTO;

import java.util.List;

/**
 * UserService interface — this is part of the CONTRACT.
 * Tests are written against this interface.
 * AI agents will generate the implementation (UserServiceImpl).
 */
public interface UserService {

    UserDTO createUser(CreateUserRequest request);

    UserDTO getUserById(Long id);

    List<UserDTO> getAllActiveUsers();

    UserDTO updateUser(Long id, UpdateUserRequest request);

    void deleteUser(Long id);
}
