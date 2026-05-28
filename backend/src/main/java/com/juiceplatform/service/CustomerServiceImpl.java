package com.juiceplatform.service;

import com.juiceplatform.dto.customer.CustomerProfileResponse;
import com.juiceplatform.dto.customer.UpdateAddressRequest;
import com.juiceplatform.dto.customer.UpdateAddressResponse;
import com.juiceplatform.entity.DeliveryAddress;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.DeliveryAddressRepository;
import com.juiceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final UserRepository userRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final WalletService walletService;

    /**
     * Returns the authenticated customer's full profile.
     * address is null when onboarding is not yet complete (API spec §2.3 note).
     */
    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(UUID customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Customer not found", HttpStatus.NOT_FOUND));

        // Address is null when onboarding is incomplete (API spec §2.3)
        CustomerProfileResponse.AddressDto addressDto = null;
        if (customer.getOnboardingCompleted()) {
            addressDto = deliveryAddressRepository.findByCustomerId(customerId)
                    .map(addr -> CustomerProfileResponse.AddressDto.builder()
                            .id(addr.getId())
                            .line1(addr.getLine1())
                            .line2(addr.getLine2())
                            .city(addr.getCity())
                            .state(addr.getState())
                            .pincode(addr.getPincode())
                            .deliveryNotes(addr.getDeliveryNotes())
                            .build())
                    .orElse(null);
        }

        // Wallet summary — always present regardless of onboarding status
        long balance = walletService.getCurrentBalance(customerId);
        long threshold = 20_000L;
        CustomerProfileResponse.WalletDto walletDto = CustomerProfileResponse.WalletDto.builder()
                .balancePaise(balance)
                .lowBalanceWarning(balance < threshold)
                .lowBalanceThresholdPaise(threshold)
                .build();

        return CustomerProfileResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .onboardingComplete(customer.getOnboardingCompleted())
                .address(addressDto)
                .wallet(walletDto)
                .createdAt(customer.getCreatedAt())
                .build();
    }

    /**
     * Updates the customer's delivery address immediately.
     * No cutoff rule (BR-ONB-03, BR-CUT-05).
     * Existing order address snapshots are immutable and unaffected (BR-ONB-04).
     * Requires onboarding to be complete — address record must already exist.
     */
    @Override
    @Transactional
    public UpdateAddressResponse updateAddress(UUID customerId, UpdateAddressRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Customer not found", HttpStatus.NOT_FOUND));

        if (!customer.getOnboardingCompleted()) {
            throw new BusinessException("ONBOARDING_INCOMPLETE",
                    "Complete onboarding before updating your address", HttpStatus.FORBIDDEN);
        }

        // Fetch existing address — must exist after onboarding is complete
        DeliveryAddress address = deliveryAddressRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Delivery address not found", HttpStatus.NOT_FOUND));

        // Update fields in-place (same row, same id — BR-ONB-03: applies immediately)
        address.setLine1(request.getLine1());
        address.setLine2(request.getLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPincode(request.getPincode());
        address.setDeliveryNotes(request.getDeliveryNotes());
        address = deliveryAddressRepository.save(address);

        return UpdateAddressResponse.builder()
                .id(address.getId())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .city(address.getCity())
                .state(address.getState())
                .pincode(address.getPincode())
                .deliveryNotes(address.getDeliveryNotes())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}
