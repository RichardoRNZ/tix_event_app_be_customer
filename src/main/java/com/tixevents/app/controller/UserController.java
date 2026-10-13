package com.tixevents.app.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.mapper.UserMapper;
import com.tixevents.app.service.UserService;
import com.tixevents.app.service.query.UserQueryService;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService service;
    private final UserQueryService queryService;
    private final UserMapper userMapper;

    @GetMapping({"", "/"})
    public ResponseEntity<Page<UserDTO>> getAllUsers(@RequestParam Map<String, Object> params, Pageable pageable) {
        log.info("Get all users request received with params: {}", params);
        Page<UserDTO> users = queryService.findAll(params, pageable).map(userMapper::toDto);
        log.info("Get all users returned {} record(s)", users.getNumberOfElements());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/find")
    public ResponseEntity<UserDTO> getUser(@RequestParam Map<String, Object> params) {
        log.info("Find user request received with params: {}", params);
        return ResponseEntity.ok(userMapper.toDto(queryService.getUser(params)));
    }

    @PatchMapping("/block-status")
    public ResponseEntity<UserDTO> updateBlockStatus(@RequestBody UserDTO dto) {
        log.info("Update block status request received for userId: {}", dto.getId());
        if (dto.getId() == null) {
            log.warn("Update block status rejected: userId is null");
            return ResponseEntity.badRequest().build();
        }
        UserDTO updated = service.updateBlockedStatus(dto);
        log.info("Block status updated for userId: {}", dto.getId());
        return ResponseEntity.ok(updated);
    }
}
