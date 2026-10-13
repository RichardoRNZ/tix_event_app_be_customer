package com.tixevents.app.mapper;

import com.tixevents.app.dto.BookingDetailDTO;
import com.tixevents.app.entity.BookingDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingDetailMapper {

    @Mapping(source = "booking.id", target = "bookingId")
    BookingDetailDTO toDto(BookingDetail bookingDetail);

    @Mapping(source = "bookingId", target = "booking.id")
    BookingDetail toEntity(BookingDetailDTO bookingDetailDTO);
}
