package com.juiceplatform;

import com.juiceplatform.entity.DeliveryAddress;
import com.juiceplatform.entity.DeliveryRecord;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.User;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.repository.DeliveryAddressRepository;
import com.juiceplatform.repository.DeliveryRecordRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.SubscriptionRepository;
import com.juiceplatform.repository.UserRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;
@Component
public class TestDataFactory {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;

    public TestDataFactory(UserRepository userRepository,
                           ProductRepository productRepository,
                           SubscriptionRepository subscriptionRepository,
                           OrderRepository orderRepository,
                           DeliveryRecordRepository deliveryRecordRepository,
                           WalletLedgerRepository walletLedgerRepository,
                           DeliveryAddressRepository deliveryAddressRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.orderRepository = orderRepository;
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.walletLedgerRepository = walletLedgerRepository;
        this.deliveryAddressRepository = deliveryAddressRepository;
    }

    public User createCustomer() {
        User user = new User();
        user.setName("Test Customer");
        user.setEmail("test+" + UUID.randomUUID() + "@example.com");
        user.setRole(User.UserRole.CUSTOMER);
        user.setAuthProvider(User.AuthProvider.GOOGLE);
        user.setGoogleId("google-" + UUID.randomUUID());
        user.setOnboardingCompleted(true);
        user.setIsActive(true);
        return userRepository.save(user);
    }

    public User createAdmin() {
        User user = new User();
        user.setName("Test Admin");
        user.setPhone("admin-" + UUID.randomUUID().toString().substring(0, 8));
        user.setRole(User.UserRole.ADMIN);
        user.setAuthProvider(User.AuthProvider.ADMIN_PASSWORD);
        user.setOnboardingCompleted(true);
        user.setIsActive(true);
        return userRepository.save(user);
    }

    public DeliveryAddress createAddress(UUID customerId) {
        DeliveryAddress address = new DeliveryAddress();
        address.setCustomerId(customerId);
        address.setLine1("42 MG Road");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPincode("560001");
        return deliveryAddressRepository.save(address);
    }

    public Product createProduct(long pricePaise) {
        Product product = new Product();
        product.setName("Test Juice " + UUID.randomUUID());
        product.setPricePerUnitPaise(pricePaise);
        product.setIsAvailable(true);
        return productRepository.save(product);
    }

    public Subscription createActiveSubscription(UUID customerId, UUID productId, int quantity) {
        Subscription sub = new Subscription();
        sub.setCustomerId(customerId);
        sub.setProductId(productId);
        sub.setQuantity(quantity);
        sub.setStartDate(LocalDate.now().minusDays(1));
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        sub.setCreatedBy(customerId);
        return subscriptionRepository.save(sub);
    }

    public Subscription createPendingStartSubscription(UUID customerId, UUID productId, int quantity) {
        Subscription sub = new Subscription();
        sub.setCustomerId(customerId);
        sub.setProductId(productId);
        sub.setQuantity(quantity);
        // start_date = today so activateEligibleSubscriptions() (start_date <= today) picks it up
        sub.setStartDate(LocalDate.now());
        sub.setStatus(Subscription.SubscriptionStatus.PENDING_START);
        sub.setCreatedBy(customerId);
        return subscriptionRepository.save(sub);
    }

    public Order createLockedOrder(UUID customerId, UUID subscriptionId, UUID productId,
                                   long unitPricePaise, int quantity, LocalDate deliveryDate) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setSubscriptionId(subscriptionId);
        order.setProductId(productId);
        order.setDeliveryLine1("42 MG Road");
        order.setDeliveryCity("Bengaluru");
        order.setDeliveryState("Karnataka");
        order.setDeliveryPincode("560001");
        order.setDeliveryDate(deliveryDate);
        order.setQuantity(quantity);
        order.setUnitPricePaise(unitPricePaise);
        order.setTotalAmountPaise(unitPricePaise * quantity);
        order.setStatus(Order.OrderStatus.LOCKED);
        order.setIdempotencyKey("sub_" + subscriptionId + "_" + deliveryDate);
        return orderRepository.save(order);
    }

    public DeliveryRecord createPendingDeliveryRecord(UUID orderId, LocalDate deliveryDate) {
        DeliveryRecord record = new DeliveryRecord();
        record.setOrderId(orderId);
        record.setDeliveryDate(deliveryDate);
        record.setDeliveryWindow("Morning");
        record.setStatus(DeliveryRecord.DeliveryRecordStatus.PENDING);
        return deliveryRecordRepository.save(record);
    }

    public WalletLedger creditWallet(UUID customerId, long amountPaise, UUID adminId) {
        long currentBalance = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);

        WalletLedger entry = new WalletLedger();
        entry.setCustomerId(customerId);
        entry.setEntryType(WalletLedger.EntryType.CREDIT);
        entry.setSourceType(WalletLedger.SourceType.ADMIN_CREDIT);
        entry.setAmountPaise(amountPaise);
        entry.setRunningBalancePaise(currentBalance + amountPaise);
        entry.setCreatedByUserId(adminId);
        return walletLedgerRepository.save(entry);
    }
}
