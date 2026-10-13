package com.tixevents.app.service;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.entity.User;
import com.tixevents.app.mapper.UserMapper;
import com.tixevents.app.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserDTO getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + email));
    }

    public UserDTO updateBlockedStatus(UserDTO userDto) {
        User user = userRepository.findById(userDto.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + userDto.getId()));
        user.setBlocked(userDto.getBlocked());
        if (Boolean.TRUE.equals(userDto.getBlocked())) {
            user.setBlockedTime(LocalDateTime.now());
        } else {
            user.setBlockedTime(null);
            user.setFailedLoginCount(0);
        }
        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }
        
}
