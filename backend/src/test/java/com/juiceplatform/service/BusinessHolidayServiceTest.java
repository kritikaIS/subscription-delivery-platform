package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.holiday.AddHolidayRequest;
import com.juiceplatform.dto.holiday.HolidayResponse;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.BusinessHolidayRepository;
import com.juiceplatform.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class BusinessHolidayServiceTest extends AbstractIntegrationTest {

    @Autowired BusinessHolidayService holidayService;
    @Autowired OrderGenerationService orderGenerationService;
    @Autowired BusinessHolidayRepository holidayRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired TestDataFactory factory;

    User admin;
    User customer;

    @BeforeEach
    void setUp() {
        admin = factory.createAdmin();
        customer = factory.createCustomer();
        factory.createAddress(customer.getId());
    }

    // ─── Holiday creation ────────────────────────────────────────────────────

    @Test
    void addHoliday_happyPath_persistsRecord() {
        LocalDate futureDate = LocalDate.now().plusDays(30);
        AddHolidayRequest request = new AddHolidayRequest(futureDate, "Test Holiday");

        HolidayResponse response = holidayService.addHoliday(request, admin.getId());

        assertThat(response.getId()).isNotNull();
        assertThat(response.getDate()).isEqualTo(futureDate);
        assertThat(response.getName()).isEqualTo("Test Holiday");
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(holidayRepository.existsByHolidayDate(futureDate)).isTrue();
    }

    @Test
    void addHoliday_duplicateDate_throws409() {
        LocalDate futureDate = LocalDate.now().plusDays(30);
        holidayService.addHoliday(new AddHolidayRequest(futureDate, "First"), admin.getId());

        assertThatThrownBy(() ->
                holidayService.addHoliday(new AddHolidayRequest(futureDate, "Second"), admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("HOLIDAY_ALREADY_EXISTS");
    }

    @Test
    void listHolidays_returnsAllHolidays() {
        holidayService.addHoliday(new AddHolidayRequest(LocalDate.now().plusDays(10), "H1"), admin.getId());
        holidayService.addHoliday(new AddHolidayRequest(LocalDate.now().plusDays(20), "H2"), admin.getId());

        var page = holidayService.listHolidays(PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    // ─── Holiday deletion ────────────────────────────────────────────────────

    @Test
    void deleteHoliday_futureDate_hardDeletes() {
        LocalDate futureDate = LocalDate.now().plusDays(30);
        HolidayResponse created = holidayService.addHoliday(
                new AddHolidayRequest(futureDate, "Future Holiday"), admin.getId());

        var result = holidayService.deleteHoliday(created.getId());

        assertThat(result.isDeleted()).isTrue();
        assertThat(holidayRepository.existsByHolidayDate(futureDate)).isFalse();
    }

    @Test
    void deleteHoliday_pastDate_throws400_HOLIDAY_IMMUTABLE() {
        // Directly insert a past holiday bypassing service validation
        var holiday = new com.juiceplatform.entity.BusinessHoliday();
        holiday.setHolidayDate(LocalDate.now().minusDays(1));
        holiday.setName("Past Holiday");
        holiday.setCreatedBy(admin.getId());
        var saved = holidayRepository.save(holiday);

        assertThatThrownBy(() -> holidayService.deleteHoliday(saved.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("HOLIDAY_IMMUTABLE");
    }

    @Test
    void deleteHoliday_today_throws400_HOLIDAY_IMMUTABLE() {
        var holiday = new com.juiceplatform.entity.BusinessHoliday();
        holiday.setHolidayDate(LocalDate.now());
        holiday.setName("Today Holiday");
        holiday.setCreatedBy(admin.getId());
        var saved = holidayRepository.save(holiday);

        assertThatThrownBy(() -> holidayService.deleteHoliday(saved.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("HOLIDAY_IMMUTABLE");
    }

    @Test
    void deleteHoliday_notFound_throws404() {
        assertThatThrownBy(() -> holidayService.deleteHoliday(java.util.UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RESOURCE_NOT_FOUND");
    }

    // ─── Scheduler integration ───────────────────────────────────────────────

    @Test
    void orderGeneration_onHoliday_skipsAllOrders() {
        var product = factory.createProduct(2500L);
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        // Configure the delivery date as a holiday
        holidayService.addHoliday(new AddHolidayRequest(deliveryDate, "Test Holiday"), admin.getId());

        OrderGenerationService.OrderGenerationResult result =
                orderGenerationService.generateOrdersForDate(deliveryDate);

        // No orders created — holiday skips generation (BR-ORD-03, BR-HOL-02)
        assertThat(result.ordersCreated()).isZero();
        assertThat(result.activeSubscriptionsProcessed()).isZero();

        // No order rows in DB for this date
        var orders = orderRepository.findByCustomerIdOrderByDeliveryDateDesc(
                customer.getId(), PageRequest.of(0, 10));
        assertThat(orders.getTotalElements()).isZero();
    }

    @Test
    void orderGeneration_notHoliday_createsOrders() {
        var product = factory.createProduct(2500L);
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        LocalDate deliveryDate = LocalDate.now().plusDays(1);
        // No holiday configured for this date

        OrderGenerationService.OrderGenerationResult result =
                orderGenerationService.generateOrdersForDate(deliveryDate);

        assertThat(result.ordersCreated()).isEqualTo(1);
    }

    @Test
    void isHoliday_returnsCorrectly() {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        assertThat(holidayService.isHoliday(futureDate)).isFalse();

        holidayService.addHoliday(new AddHolidayRequest(futureDate, "Holiday"), admin.getId());
        assertThat(holidayService.isHoliday(futureDate)).isTrue();
    }
}
