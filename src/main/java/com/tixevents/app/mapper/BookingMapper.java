package com.tixevents.app.mapper;

import com.tixevents.app.dto.BookingDTO;
import com.tixevents.app.entity.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {BookingDetailMapper.class})
public interface BookingMapper {
    
    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "totalPrice", ignore = true)
    BookingDTO toDto(Booking booking);

    @Mapping(source = "userId", target = "user.id")
    Booking toEntity(BookingDTO bookingDTO);
}
