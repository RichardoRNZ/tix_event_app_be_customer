package com.tixevents.app.mapper;

import com.tixevents.app.dto.TransactionDTO;
import com.tixevents.app.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(source = "booking.id", target = "bookingId")
    TransactionDTO toDto(Transaction transaction);

    @Mapping(source = "bookingId", target = "booking.id")
    Transaction toEntity(TransactionDTO transactionDTO);
}
