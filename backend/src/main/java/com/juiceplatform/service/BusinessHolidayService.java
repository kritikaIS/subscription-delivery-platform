package com.juiceplatform.service;

import com.juiceplatform.dto.holiday.AddHolidayRequest;
import com.juiceplatform.dto.holiday.DeleteHolidayResponse;
import com.juiceplatform.dto.holiday.HolidayResponse;
import com.juiceplatform.entity.BusinessHoliday;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.BusinessHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Business holiday management.
 * BR-HOL-01: Managed by admin manually, one at a time.
 * BR-GEN-01: Only future holidays may be hard deleted; historical records are immutable.
 */
@Service
@RequiredArgsConstructor
public class BusinessHolidayService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final BusinessHolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public Page<HolidayResponse> listHolidays(Pageable pageable) {
        return holidayRepository.findAllByOrderByHolidayDateAsc(pageable)
                .map(this::toResponse);
    }

    @Transactional
    public HolidayResponse addHoliday(AddHolidayRequest request, UUID adminId) {
        // Duplicate date check
        if (holidayRepository.existsByHolidayDate(request.getDate())) {
            throw new BusinessException("HOLIDAY_ALREADY_EXISTS",
                    "A holiday is already configured for this date", HttpStatus.CONFLICT);
        }

        BusinessHoliday holiday = new BusinessHoliday();
        holiday.setHolidayDate(request.getDate());
        holiday.setName(request.getName());
        holiday.setCreatedBy(adminId);
        holiday = holidayRepository.save(holiday);

        return toResponse(holiday);
    }

    @Transactional
    public DeleteHolidayResponse deleteHoliday(UUID holidayId) {
        BusinessHoliday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Holiday not found: " + holidayId, HttpStatus.NOT_FOUND));

        // Only future holidays may be hard deleted (BR-GEN-01)
        LocalDate today = LocalDate.now(IST);
        if (!holiday.getHolidayDate().isAfter(today)) {
            throw new BusinessException("HOLIDAY_IMMUTABLE",
                    "Past or current business holidays cannot be deleted", HttpStatus.BAD_REQUEST);
        }

        holidayRepository.delete(holiday);

        return DeleteHolidayResponse.builder()
                .id(holidayId)
                .deleted(true)
                .build();
    }

    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsByHolidayDate(date);
    }

    private HolidayResponse toResponse(BusinessHoliday holiday) {
        return HolidayResponse.builder()
                .id(holiday.getId())
                .date(holiday.getHolidayDate())
                .name(holiday.getName())
                .createdAt(holiday.getCreatedAt())
                .build();
    }
}
