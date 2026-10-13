package com.tixevents.app.mapper;

import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDTO toDto(User user);

    @Mapping(target = "password", ignore = true)
    User toEntity(UserDTO userDTO);
}
