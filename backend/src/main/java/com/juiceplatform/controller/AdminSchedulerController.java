package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PagedResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.scheduler.DeliverySheetJobResponse;
import com.juiceplatform.dto.scheduler.FreezeJobResponse;
import com.juiceplatform.dto.scheduler.GenerateJobResponse;
import com.juiceplatform.dto.scheduler.SchedulerJobHistoryEntry;
import com.juiceplatform.dto.scheduler.SchedulerRerunRequest;
import com.juiceplatform.entity.DeliverySheetSnapshot;
import com.juiceplatform.entity.SchedulerJobLog;
import com.juiceplatform.repository.SchedulerJobLogRepository;
import com.juiceplatform.service.AuditLogService;
import com.juiceplatform.service.DeliverySheetService;
import com.juiceplatform.service.OrderFreezeService;
import com.juiceplatform.service.OrderGenerationService;
import com.juiceplatform.service.SubscriptionActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;
import com.juiceplatform.security.AuthenticatedUser;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Admin scheduler operation endpoints — Domain 14 from API spec.
 * Reuses existing scheduler services. All reruns are idempotent.
 */
@RestController
@RequestMapping("/api/v1/admin/scheduler")
@RequiredArgsConstructor
public class AdminSchedulerController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderFreezeService orderFreezeService;
    private final OrderGenerationService orderGenerationService;
    private final DeliverySheetService deliverySheetService;
    private final SubscriptionActivationService subscriptionActivationService;
    private final SchedulerJobLogRepository schedulerJobLogRepository;
    private final AuditLogService auditLogService;

    /**
     * POST /api/v1/admin/scheduler/freeze
     * Manually reruns OrderFreezeJob. Idempotent.
     * If targetDate omitted, defaults to next operational delivery date (tomorrow IST).
     */
    @PostMapping("/freeze")
    @Transactional
    public ResponseEntity<ApiResponse<FreezeJobResponse>> rerunFreeze(
            @RequestBody(required = false) SchedulerRerunRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        LocalDate targetDate = resolveDate(request);
        OffsetDateTime ranAt = OffsetDateTime.now(IST);

        OrderFreezeService.FreezeResult result = orderFreezeService.freezeOrdersForDate(targetDate);

        // Audit log — action_type: SCHEDULER_RERUN (BR-AUD-01, db-schema §3.15)
        auditLogService.log("SCHEDULER_RERUN", "scheduler_job", "OrderFreezeJob",
                null,
                java.util.Map.of("targetDate", targetDate.toString(),
                        "ordersLocked", result.ordersLocked()),
                authenticatedUser.getUserId(),
                "Admin rerun of OrderFreezeJob for " + targetDate);

        FreezeJobResponse response = FreezeJobResponse.builder()
                .job("OrderFreezeJob")
                .status("COMPLETED")
                .targetDate(targetDate)
                .ordersLocked(result.ordersLocked())
                .ranAt(ranAt)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/admin/scheduler/generate
     * Manually reruns OrderGenerationJob. Idempotent — no duplicate orders.
     * If targetDate omitted, defaults to next operational delivery date (tomorrow IST).
     */
    @PostMapping("/generate")
    @Transactional
    public ResponseEntity<ApiResponse<GenerateJobResponse>> rerunGenerate(
            @RequestBody(required = false) SchedulerRerunRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        LocalDate targetDate = resolveDate(request);
        OffsetDateTime ranAt = OffsetDateTime.now(IST);

        SubscriptionActivationService.ActivationResult activation =
                subscriptionActivationService.activateEligibleSubscriptions();

        OrderGenerationService.OrderGenerationResult result =
                orderGenerationService.generateOrdersForDate(targetDate);

        // Audit log — action_type: SCHEDULER_RERUN (BR-AUD-01)
        auditLogService.log("SCHEDULER_RERUN", "scheduler_job", "OrderGenerationJob",
                null,
                java.util.Map.of("targetDate", targetDate.toString(),
                        "ordersGenerated", result.ordersCreated(),
                        "subscriptionsActivated", activation.subscriptionsActivated()),
                authenticatedUser.getUserId(),
                "Admin rerun of OrderGenerationJob for " + targetDate);

        GenerateJobResponse response = GenerateJobResponse.builder()
                .job("OrderGenerationJob")
                .status("COMPLETED")
                .targetDate(targetDate)
                .ordersGenerated(result.ordersCreated())
                .subscriptionsActivated(activation.subscriptionsActivated())
                .changeRequestsApplied(0)
                .ranAt(ranAt)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/admin/scheduler/delivery-sheet
     * Manually reruns DeliverySheetGenerationJob. Replaces existing snapshot.
     * If targetDate omitted, defaults to next operational delivery date (tomorrow IST).
     */
    @PostMapping("/delivery-sheet")
    @Transactional
    public ResponseEntity<ApiResponse<DeliverySheetJobResponse>> rerunDeliverySheet(
            @RequestBody(required = false) SchedulerRerunRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        LocalDate targetDate = resolveDate(request);
        OffsetDateTime ranAt = OffsetDateTime.now(IST);

        deliverySheetService.generateSnapshot(targetDate,
                DeliverySheetSnapshot.GeneratedBySource.ADMIN_RERUN,
                authenticatedUser.getUserId());

        // Audit log — action_type: SCHEDULER_RERUN (BR-AUD-01)
        auditLogService.log("SCHEDULER_RERUN", "scheduler_job", "DeliverySheetGenerationJob",
                null,
                java.util.Map.of("targetDate", targetDate.toString()),
                authenticatedUser.getUserId(),
                "Admin rerun of DeliverySheetGenerationJob for " + targetDate);

        DeliverySheetJobResponse response = DeliverySheetJobResponse.builder()
                .job("DeliverySheetGenerationJob")
                .status("COMPLETED")
                .targetDate(targetDate)
                .ranAt(ranAt)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/admin/scheduler/history
     * Returns paginated scheduler job history, newest first.
     * Optional jobName filter.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PagedResponse<SchedulerJobHistoryEntry>>> getHistory(
            @RequestParam(required = false) String jobName,
            @ParameterObject Pageable pageable) {

        Page<SchedulerJobLog> page = (jobName != null && !jobName.isBlank())
                ? schedulerJobLogRepository.findByJobNameOrderByStartedAtDesc(jobName, pageable)
                : schedulerJobLogRepository.findAllByOrderByStartedAtDesc(pageable);

        Page<SchedulerJobHistoryEntry> responsePage = page.map(log -> SchedulerJobHistoryEntry.builder()
                .id(log.getId())
                .jobName(log.getJobName())
                .status(log.getStatus().name())
                .targetDate(log.getJobDate())
                .rowsProcessed(log.getRowsProcessed())
                .errorMessage(log.getErrorMessage())
                .ranAt(log.getStartedAt())
                .build());

        PagedResponse<SchedulerJobHistoryEntry> data = new PagedResponse<>(responsePage.getContent());
        PaginationMeta meta = new PaginationMeta(
                responsePage.getNumber(), responsePage.getSize(), responsePage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }

    private LocalDate resolveDate(SchedulerRerunRequest request) {
        if (request != null && request.getTargetDate() != null) {
            return request.getTargetDate();
        }
        // Default: next operational delivery date = tomorrow IST
        return LocalDate.now(IST).plusDays(1);
    }
}
