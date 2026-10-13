package com.tixevents.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;

import com.tixevents.app.config.SecurityConfig;
import com.tixevents.app.dto.BookingDTO;
import com.tixevents.app.entity.Booking;
import com.tixevents.app.entity.User;
import com.tixevents.app.exception.SeatAlreadyBookedException;
import com.tixevents.app.mapper.BookingDetailMapperImpl;
import com.tixevents.app.mapper.BookingMapperImpl;
import com.tixevents.app.mapper.UserMapperImpl;

@WebMvcTest(controllers = BookingController.class)
@Import({SecurityConfig.class, UserMapperImpl.class, BookingMapperImpl.class, BookingDetailMapperImpl.class})
@DisplayName("BookingController - /api/bookings/**")
class BookingControllerTest extends AbstractControllerTest {

    private static final String OWNER = "owner@x.com";
    private static final String OTHER = "other@x.com";
    private static final String ADMIN = "admin@x.com";

    private static final String CREATE_JSON =
            "{\"seatDetailsIds\":[101,102],\"idempotencyKey\":\"idem-1\",\"eventName\":\"Concert\",\"seatNumbers\":[\"A1\",\"A2\"]}";
    private static final String PAY_JSON =
            "{\"noBooking\":\"BKG-ABC1\",\"isPaid\":true,\"idempotencyKey\":\"pay-1\",\"paymentMethod\":\"VA\"}";

    private static PageImpl<BookingDTO> onePage() {
        BookingDTO dto = new BookingDTO();
        dto.setId(1L);
        dto.setNoBooking("BKG-ABC1");
        return new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /api/bookings")
    class CreateBooking {

        @Test
        @DisplayName("401 - requires authentication")
        void createUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                    .andExpect(status().isUnauthorized());
            verify(bookingService, never()).createBooking(any(), any());
        }

        @Test
        @DisplayName("201 - creates booking for authenticated user")
        void createSuccess() throws Exception {
            Booking b = booking(1L, userEntity(1L, OWNER, false), "PENDING");
            when(bookingService.createBooking(any(), eq(OWNER))).thenReturn(b);

            mockMvc.perform(post("/api/bookings").with(user(OWNER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.noBooking").value("BKG-ABC1"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.totalPrice").value(500000))
                    .andExpect(jsonPath("$.bookingDetails[0].seatNumber").value("A1"));
        }

        @Test
        @DisplayName("400 - empty seatDetailsIds")
        void createEmptySeats() throws Exception {
            mockMvc.perform(post("/api/bookings").with(user(OWNER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"seatDetailsIds\":[],\"idempotencyKey\":\"k\"}"))
                    .andExpect(status().isBadRequest());
            verify(bookingService, never()).createBooking(any(), any());
        }

        @Test
        @DisplayName("400 - missing idempotencyKey")
        void createMissingIdempotencyKey() throws Exception {
            mockMvc.perform(post("/api/bookings").with(user(OWNER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"seatDetailsIds\":[1]}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("[BUG-06] seat already booked should return 409 CONFLICT, not 500")
        void createSeatAlreadyBooked() throws Exception {
            when(bookingService.createBooking(any(), eq(OWNER)))
                    .thenThrow(new SeatAlreadyBookedException("One or more seats are already booked: [101]"));

            mockMvc.perform(post("/api/bookings").with(user(OWNER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /api/bookings/payment")
    class Payment {

        @Test
        @DisplayName("401 - requires authentication")
        void payUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/bookings/payment").contentType(MediaType.APPLICATION_JSON).content(PAY_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 - successful payment returns CONFIRMED booking")
        void paySuccess() throws Exception {
            when(bookingService.handlePayment(any(), any())).thenReturn(booking(1L, userEntity(1L, OWNER, false), "CONFIRMED"));

            mockMvc.perform(post("/api/bookings/payment").with(user(OWNER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content(PAY_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("400 - missing noBooking / isPaid / idempotencyKey")
        void payValidation() throws Exception {
            mockMvc.perform(post("/api/bookings/payment").with(user(OWNER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"VA\"}"))
                    .andExpect(status().isBadRequest());
            verify(bookingService, never()).handlePayment(any(), any());
        }

        @Test
        @DisplayName("[BUG-06] booking not found should return 404, not 500")
        void payNotFound() throws Exception {
            when(bookingService.handlePayment(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: BKG-ABC1"));

            mockMvc.perform(post("/api/bookings/payment").with(user(OWNER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content(PAY_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("[BUG-06] invalid state (already FAILED/EXPIRED) should return 409, not 500")
        void payInvalidState() throws Exception {
            when(bookingService.handlePayment(any(), any())).thenThrow(new IllegalStateException("Booking cannot be paid because status is EXPIRED"));

            mockMvc.perform(post("/api/bookings/payment").with(user(OWNER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content(PAY_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("[BUG-03] user must not be able to pay/cancel another user's booking (IDOR)")
        void payOtherUsersBooking() throws Exception {
            when(bookingService.handlePayment(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden access to this booking"));

            mockMvc.perform(post("/api/bookings/payment").with(user(OTHER).roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"noBooking\":\"BKG-ABC1\",\"isPaid\":false,\"idempotencyKey\":\"x\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /api/bookings/{id}")
    class GetBooking {

        @Test
        @DisplayName("200 - owner can read own booking")
        void owner() throws Exception {
            when(bookingQueryService.findById(1L)).thenReturn(booking(1L, userEntity(1L, OWNER, false), "PENDING"));

            mockMvc.perform(get("/api/bookings/1").with(user(OWNER).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("403 - other user cannot read booking")
        void otherUser() throws Exception {
            when(bookingQueryService.findById(1L)).thenReturn(booking(1L, userEntity(1L, OWNER, false), "PENDING"));

            mockMvc.perform(get("/api/bookings/1").with(user(OTHER).roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 - admin (ROLE_ADMIN) can read any booking")
        void admin() throws Exception {
            when(bookingQueryService.findById(1L)).thenReturn(booking(1L, userEntity(1L, OWNER, false), "PENDING"));

            mockMvc.perform(get("/api/bookings/1").with(user(ADMIN).roles("ADMIN")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("401 - unauthenticated")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/bookings/1")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[BUG-06] non-existing booking should return 404, not 500")
        void notFound() throws Exception {
            when(bookingQueryService.findById(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with id: 99"));

            mockMvc.perform(get("/api/bookings/99").with(user(OWNER).roles("USER")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("[BUG-07] non-numeric id should return 400, not 500")
        void nonNumericId() throws Exception {
            mockMvc.perform(get("/api/bookings/abc").with(user(OWNER).roles("USER")))
                    .andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /api/bookings/user/{userId}")
    class UserHistory {

        @Test
        @DisplayName("200 - user can read own history (search param forwarded)")
        void ownHistory() throws Exception {
            when(userRepository.findByEmail(OWNER)).thenReturn(Optional.of(userEntity(1L, OWNER, false)));
            when(bookingQueryService.getUserBookingHistory(eq(1L), eq("BKG"), any())).thenReturn(onePage());

            mockMvc.perform(get("/api/bookings/user/1").param("noBooking", "BKG").with(user(OWNER).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].noBooking").value("BKG-ABC1"));
        }

        @Test
        @DisplayName("403 - non-admin cannot read another user's history")
        void otherHistoryForbidden() throws Exception {
            when(userRepository.findByEmail(OTHER)).thenReturn(Optional.of(userEntity(2L, OTHER, false)));

            mockMvc.perform(get("/api/bookings/user/1").with(user(OTHER).roles("USER")))
                    .andExpect(status().isForbidden());
            verify(bookingQueryService, never()).getUserBookingHistory(any(), any(), any());
        }

        @Test
        @DisplayName("200 - admin can read another user's history")
        void adminCanRead() throws Exception {
            when(userRepository.findByEmail(ADMIN)).thenReturn(Optional.of(userEntity(9L, ADMIN, true)));
            when(bookingQueryService.getUserBookingHistory(eq(1L), isNull(), any())).thenReturn(onePage());

            mockMvc.perform(get("/api/bookings/user/1").with(user(ADMIN).roles("ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /api/bookings/my-history")
    class MyHistory {

        @Test
        @DisplayName("200 - uses current user id and default sort createdDate DESC")
        void myHistory() throws Exception {
            when(userRepository.findByEmail(OWNER)).thenReturn(Optional.of(userEntity(1L, OWNER, false)));
            when(bookingQueryService.getUserBookingHistory(eq(1L), isNull(), any())).thenReturn(onePage());

            mockMvc.perform(get("/api/bookings/my-history").with(user(OWNER).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(bookingQueryService).getUserBookingHistory(eq(1L), isNull(), captor.capture());
            assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("createdDate").getDirection());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /api/bookings/admin  &  /api/bookings/booking/all")
    class AdminList {

        @ParameterizedTest
        @ValueSource(strings = {"/api/bookings/admin", "/api/bookings/booking/all"})
        @DisplayName("403 - non-admin")
        void nonAdmin(String path) throws Exception {
            when(userRepository.findByEmail(OWNER)).thenReturn(Optional.of(userEntity(1L, OWNER, false)));

            mockMvc.perform(get(path).with(user(OWNER).roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @ParameterizedTest
        @ValueSource(strings = {"/api/bookings/admin", "/api/bookings/booking/all"})
        @DisplayName("200 - admin")
        void admin(String path) throws Exception {
            when(userRepository.findByEmail(ADMIN)).thenReturn(Optional.of(userEntity(9L, ADMIN, true)));
            when(bookingQueryService.getAllBookingsForAdmin(any(), any())).thenReturn(onePage());

            mockMvc.perform(get(path).param("noBooking", "abc").with(user(ADMIN).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].noBooking").value("BKG-ABC1"));
        }
    }
}
