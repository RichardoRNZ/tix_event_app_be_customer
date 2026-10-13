package com.tixevents.app.mapper;

import com.tixevents.app.dto.BookingOutboxDTO;
import com.tixevents.app.entity.BookingOutbox;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingOutboxMapper {

    @Mapping(source = "booking.id", target = "bookingId")
    BookingOutboxDTO toDto(BookingOutbox bookingOutbox);

    @Mapping(source = "bookingId", target = "booking.id")
    BookingOutbox toEntity(BookingOutboxDTO bookingOutboxDTO);
}
