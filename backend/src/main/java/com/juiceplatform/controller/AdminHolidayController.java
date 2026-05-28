package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PagedResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.holiday.AddHolidayRequest;
import com.juiceplatform.dto.holiday.DeleteHolidayResponse;
import com.juiceplatform.dto.holiday.HolidayResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.BusinessHolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/holidays")
@RequiredArgsConstructor
public class AdminHolidayController {

    private final BusinessHolidayService holidayService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<HolidayResponse>>> listHolidays(
            @ParameterObject Pageable pageable) {

        Page<HolidayResponse> page = holidayService.listHolidays(pageable);

        PagedResponse<HolidayResponse> data = new PagedResponse<>(page.getContent());
        PaginationMeta meta = new PaginationMeta(page.getNumber(), page.getSize(), page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HolidayResponse>> addHoliday(
            @RequestBody @Valid AddHolidayRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        HolidayResponse response = holidayService.addHoliday(request, authenticatedUser.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<DeleteHolidayResponse>> deleteHoliday(
            @PathVariable UUID id) {

        DeleteHolidayResponse response = holidayService.deleteHoliday(id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
