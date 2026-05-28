package com.juiceplatform.repository;

import com.juiceplatform.entity.SchedulerJobLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchedulerJobLogRepository extends JpaRepository<SchedulerJobLog, UUID> {

    Optional<SchedulerJobLog> findByJobNameAndJobDate(String jobName, LocalDate jobDate);

    Page<SchedulerJobLog> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<SchedulerJobLog> findByJobNameOrderByStartedAtDesc(String jobName, Pageable pageable);
}
