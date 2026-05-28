package com.juiceplatform.repository;

import com.juiceplatform.entity.BusinessHoliday;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessHolidayRepository extends JpaRepository<BusinessHoliday, UUID> {

    Optional<BusinessHoliday> findByHolidayDate(LocalDate holidayDate);

    boolean existsByHolidayDate(LocalDate holidayDate);

    Page<BusinessHoliday> findAllByOrderByHolidayDateAsc(Pageable pageable);
}
