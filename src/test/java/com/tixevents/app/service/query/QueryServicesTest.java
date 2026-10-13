package com.tixevents.app.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.dto.BookingDTO;
import com.tixevents.app.entity.Booking;
import com.tixevents.app.entity.User;
import com.tixevents.app.mapper.BookingDetailMapperImpl;
import com.tixevents.app.mapper.BookingMapper;
import com.tixevents.app.mapper.BookingMapperImpl;
import com.tixevents.app.repository.BookingRepository;
import com.tixevents.app.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Query Services - BookingQueryService & UserQueryService")
class QueryServicesTest {

    @Nested
    @DisplayName("BookingQueryService")
    class BookingQueryServiceTests {

        @Mock private BookingRepository bookingRepository;
        private BookingMapper bookingMapper;
        private BookingQueryService bookingQueryService;

        @BeforeEach
        void setUp() throws Exception {
            BookingDetailMapperImpl detailMapper = new BookingDetailMapperImpl();
            BookingMapperImpl mapper = new BookingMapperImpl();
            var field = BookingMapperImpl.class.getDeclaredField("bookingDetailMapper");
            field.setAccessible(true);
            field.set(mapper, detailMapper);
            this.bookingMapper = mapper;
            this.bookingQueryService = new BookingQueryService(bookingRepository, bookingMapper);
        }

        @Test
        @DisplayName("findById returns Booking when found")
        void testFindByIdSuccess() {
            Booking b = new Booking();
            b.setId(10L);
            b.setNoBooking("BKG-10");
            when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));

            Booking result = bookingQueryService.findById(10L);
            assertEquals(10L, result.getId());
        }

        @Test
        @DisplayName("findById throws ResponseStatusException when not found")
        void testFindByIdNotFound() {
            when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResponseStatusException.class, () -> bookingQueryService.findById(999L));
        }

        @Test
        @DisplayName("getUserBookingHistory trims noBooking query param")
        void testGetUserBookingHistory() {
            User user = new User();
            user.setId(1L);

            Booking b = new Booking();
            b.setId(10L);
            b.setUser(user);
            b.setNoBooking("BKG-10");
            b.setDate(LocalDateTime.now());
            b.setStatus("CONFIRMED");

            Page<Booking> page = new PageImpl<>(List.of(b));
            when(bookingRepository.findByUserIdAndNoBooking(eq(1L), eq("BKG"), any(Pageable.class)))
                    .thenReturn(page);

            Page<BookingDTO> result = bookingQueryService.getUserBookingHistory(1L, "  BKG  ", PageRequest.of(0, 10));

            assertEquals(1, result.getTotalElements());
            assertEquals("BKG-10", result.getContent().get(0).getNoBooking());
        }

        @Test
        @DisplayName("getAllBookingsForAdmin passes null searchParam when blank")
        void testGetAllBookingsForAdminBlankParam() {
            Page<Booking> page = new PageImpl<>(List.of());
            when(bookingRepository.findAllByNoBooking(isNull(), any(Pageable.class))).thenReturn(page);

            Page<BookingDTO> result = bookingQueryService.getAllBookingsForAdmin("   ", PageRequest.of(0, 10));

            assertEquals(0, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("UserQueryService")
    class UserQueryServiceTests {

        @Mock private UserRepository userRepository;
        private UserQueryService userQueryService;

        @BeforeEach
        void setUp() {
            userQueryService = new UserQueryService(userRepository);
        }

        @Test
        @DisplayName("findAll executes specification search")
        void testFindAll() {
            User u = new User();
            u.setId(1L);
            u.setEmail("u@example.com");

            when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(u)));

            Page<User> result = userQueryService.findAll(Map.of("email", "u@example.com"), PageRequest.of(0, 10));

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("getUser throws RuntimeException when no user matches criteria")
        void testGetUserNotFound() {
            when(userRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () ->
                userQueryService.getUser(Map.of("email", "notfound@example.com"))
            );
        }
    }
}
