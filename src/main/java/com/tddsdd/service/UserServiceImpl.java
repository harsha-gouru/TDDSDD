// FILE: src/main/java/com/tddsdd/service/UserServiceImpl.java
package com.tddsdd.service;

import com.tddsdd.dto.CreateUserRequest;
import com.tddsdd.dto.UpdateUserRequest;
import com.tddsdd.dto.UserDTO;
import com.tddsdd.entity.User;
import com.tddsdd.exception.DuplicateResourceException;
import com.tddsdd.exception.ResourceNotFoundException;
import com.tddsdd.mapper.UserMapper;
import com.tddsdd.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public UserDTO createUser(CreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);
        return userMapper.toDTO(savedUser);
    }

    @Override
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return userMapper.toDTO(user);
    }

    @Override
    public List<UserDTO> getAllActiveUsers() {
        return userRepository.findByActiveTrue().stream()
                .map(userMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        if (!user.getEmail().equals(request.getEmail()) && 
            userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        User savedUser = userRepository.save(user);
        return userMapper.toDTO(savedUser);
    }

    @Override
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setActive(false);
        userRepository.save(user);
    }
}

