package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.customer.CustomerProfileResponse;
import com.juiceplatform.dto.customer.UpdateAddressRequest;
import com.juiceplatform.dto.customer.UpdateAddressResponse;
import com.juiceplatform.entity.DeliveryAddress;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.DeliveryAddressRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CustomerServiceTest extends AbstractIntegrationTest {

    @Autowired CustomerService customerService;
    @Autowired TestDataFactory factory;
    @Autowired UserRepository userRepository;
    @Autowired DeliveryAddressRepository deliveryAddressRepository;
    @Autowired OrderRepository orderRepository;

    User customer;
    User admin;

    @BeforeEach
    void setUp() {
        customer = factory.createCustomer();
        admin = factory.createAdmin();
    }

    // ─── GET /api/v1/customer/me ─────────────────────────────────────────────

    @Test
    void getProfile_onboardedCustomer_returnsFullProfile() {
        factory.createAddress(customer.getId());
        factory.creditWallet(customer.getId(), 50_000L, admin.getId());

        CustomerProfileResponse profile = customerService.getProfile(customer.getId());

        assertThat(profile.getId()).isEqualTo(customer.getId());
        assertThat(profile.getName()).isEqualTo(customer.getName());
        assertThat(profile.getEmail()).isEqualTo(customer.getEmail());
        assertThat(profile.isOnboardingComplete()).isTrue();
        assertThat(profile.getCreatedAt()).isNotNull();

        // Address present
        assertThat(profile.getAddress()).isNotNull();
        assertThat(profile.getAddress().getLine1()).isEqualTo("42 MG Road");
        assertThat(profile.getAddress().getCity()).isEqualTo("Bengaluru");

        // Wallet present
        assertThat(profile.getWallet()).isNotNull();
        assertThat(profile.getWallet().getBalancePaise()).isEqualTo(50_000L);
        assertThat(profile.getWallet().isLowBalanceWarning()).isFalse();
        assertThat(profile.getWallet().getLowBalanceThresholdPaise()).isEqualTo(20_000L);
    }

    @Test
    void getProfile_onboardingIncomplete_addressIsNull() {
        // Customer with onboardingCompleted = false (no address)
        customer.setOnboardingCompleted(false);
        userRepository.save(customer);

        CustomerProfileResponse profile = customerService.getProfile(customer.getId());

        assertThat(profile.isOnboardingComplete()).isFalse();
        assertThat(profile.getAddress()).isNull();
        // Wallet still present
        assertThat(profile.getWallet()).isNotNull();
        assertThat(profile.getWallet().getBalancePaise()).isZero();
    }

    @Test
    void getProfile_noWalletEntries_balanceIsZero() {
        factory.createAddress(customer.getId());

        CustomerProfileResponse profile = customerService.getProfile(customer.getId());

        assertThat(profile.getWallet().getBalancePaise()).isZero();
        assertThat(profile.getWallet().isLowBalanceWarning()).isTrue(); // 0 < 20000
    }

    @Test
    void getProfile_lowBalance_warningIsTrue() {
        factory.createAddress(customer.getId());
        factory.creditWallet(customer.getId(), 10_000L, admin.getId()); // < 20000

        CustomerProfileResponse profile = customerService.getProfile(customer.getId());

        assertThat(profile.getWallet().isLowBalanceWarning()).isTrue();
    }

    @Test
    void getProfile_sufficientBalance_warningIsFalse() {
        factory.createAddress(customer.getId());
        factory.creditWallet(customer.getId(), 25_000L, admin.getId()); // > 20000

        CustomerProfileResponse profile = customerService.getProfile(customer.getId());

        assertThat(profile.getWallet().isLowBalanceWarning()).isFalse();
    }

    @Test
    void getProfile_phoneIsIncluded() {
        factory.createAddress(customer.getId());
        customer.setPhone("9876543210");
        userRepository.save(customer);

        CustomerProfileResponse profile = customerService.getProfile(customer.getId());

        assertThat(profile.getPhone()).isEqualTo("9876543210");
    }

    // ─── PUT /api/v1/customer/address ────────────────────────────────────────

    @Test
    void updateAddress_happyPath_updatesAddressImmediately() {
        factory.createAddress(customer.getId());

        UpdateAddressRequest request = new UpdateAddressRequest(
                "10 Brigade Road", "Floor 2", "Bengaluru", "Karnataka", "560025", "Ring the bell");

        UpdateAddressResponse response = customerService.updateAddress(customer.getId(), request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getLine1()).isEqualTo("10 Brigade Road");
        assertThat(response.getLine2()).isEqualTo("Floor 2");
        assertThat(response.getCity()).isEqualTo("Bengaluru");
        assertThat(response.getState()).isEqualTo("Karnataka");
        assertThat(response.getPincode()).isEqualTo("560025");
        assertThat(response.getDeliveryNotes()).isEqualTo("Ring the bell");
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateAddress_persistsToDatabase() {
        factory.createAddress(customer.getId());

        UpdateAddressRequest request = new UpdateAddressRequest(
                "New Street", null, "Mumbai", "Maharashtra", "400001", null);

        customerService.updateAddress(customer.getId(), request);

        DeliveryAddress saved = deliveryAddressRepository.findByCustomerId(customer.getId()).orElseThrow();
        assertThat(saved.getLine1()).isEqualTo("New Street");
        assertThat(saved.getCity()).isEqualTo("Mumbai");
        assertThat(saved.getState()).isEqualTo("Maharashtra");
        assertThat(saved.getPincode()).isEqualTo("400001");
        assertThat(saved.getLine2()).isNull();
    }

    @Test
    void updateAddress_sameAddressId_notCreatingNewRow() {
        DeliveryAddress original = factory.createAddress(customer.getId());

        UpdateAddressRequest request = new UpdateAddressRequest(
                "Updated Line1", null, "Pune", "Maharashtra", "411001", null);

        UpdateAddressResponse response = customerService.updateAddress(customer.getId(), request);

        // Same address row — id must not change
        assertThat(response.getId()).isEqualTo(original.getId());

        // Only one address row for this customer
        long count = deliveryAddressRepository.findAll().stream()
                .filter(a -> a.getCustomerId().equals(customer.getId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void updateAddress_onboardingIncomplete_throws403() {
        customer.setOnboardingCompleted(false);
        userRepository.save(customer);

        UpdateAddressRequest request = new UpdateAddressRequest(
                "Line1", null, "City", "State", "560001", null);

        assertThatThrownBy(() -> customerService.updateAddress(customer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ONBOARDING_INCOMPLETE");
    }

    @Test
    void updateAddress_doesNotModifyExistingOrderAddressSnapshot() {
        factory.createAddress(customer.getId());
        Product product = factory.createProduct(2500L);
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        // Create a locked order — address snapshot is captured at order creation time
        Order order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));

        String originalLine1 = order.getDeliveryLine1();
        String originalCity = order.getDeliveryCity();

        // Update address
        UpdateAddressRequest request = new UpdateAddressRequest(
                "Completely New Address", null, "New City", "New State", "999999", null);
        customerService.updateAddress(customer.getId(), request);

        // Existing order snapshot must be unchanged (BR-ONB-04)
        Order unchanged = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(unchanged.getDeliveryLine1()).isEqualTo(originalLine1);
        assertThat(unchanged.getDeliveryCity()).isEqualTo(originalCity);
    }

    @Test
    void updateAddress_futureOrderGenerationUsesNewAddress() {
        factory.createAddress(customer.getId());

        // Update address
        UpdateAddressRequest request = new UpdateAddressRequest(
                "Future Street", null, "Future City", "Future State", "123456", "Future notes");
        customerService.updateAddress(customer.getId(), request);

        // Verify the address in DB is the new one (future order generation reads from DB)
        DeliveryAddress current = deliveryAddressRepository.findByCustomerId(customer.getId()).orElseThrow();
        assertThat(current.getLine1()).isEqualTo("Future Street");
        assertThat(current.getCity()).isEqualTo("Future City");
    }

    @Test
    void updateAddress_nullDeliveryNotes_isAllowed() {
        factory.createAddress(customer.getId());

        UpdateAddressRequest request = new UpdateAddressRequest(
                "Line1", null, "City", "State", "560001", null);

        UpdateAddressResponse response = customerService.updateAddress(customer.getId(), request);

        assertThat(response.getDeliveryNotes()).isNull();
    }
}
