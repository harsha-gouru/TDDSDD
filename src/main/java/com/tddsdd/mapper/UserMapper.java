package com.tddsdd.mapper;

import com.tddsdd.dto.CreateUserRequest;
import com.tddsdd.dto.UserDTO;
import com.tddsdd.entity.User;
import org.springframework.stereotype.Component;

/**
 * UserMapper — manual mapper (part of the contract).
 * Maps between Entity and DTO layers.
 */
@Component
public class UserMapper {

    public UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .active(user.isActive())
                .build();
    }

    public User toEntity(CreateUserRequest request) {
        if (request == null) {
            return null;
        }
        return User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .active(true)
                .build();
    }
}
