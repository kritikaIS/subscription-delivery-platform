package com.juiceplatform.service;

import com.juiceplatform.dto.onboarding.OnboardingRequest;
import com.juiceplatform.dto.onboarding.OnboardingResponse;
import com.juiceplatform.entity.DeliveryAddress;
import com.juiceplatform.entity.User;
import com.juiceplatform.repository.DeliveryAddressRepository;
import com.juiceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final UserRepository userRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;

    @Override
    @Transactional
    public OnboardingResponse completeOnboarding(UUID customerId, OnboardingRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + customerId));

        // If onboarding is already complete, return existing data without modification
        if (customer.getOnboardingCompleted()) {
            DeliveryAddress existingAddress = deliveryAddressRepository.findByCustomerId(customerId)
                    .orElseThrow(() -> new IllegalStateException("Onboarding marked complete but no address found"));

            return buildResponse(customer, existingAddress);
        }

        // Update customer phone
        customer.setPhone(request.getPhone());
        customer.setOnboardingCompleted(true);
        userRepository.save(customer);

        // Create delivery address
        OnboardingRequest.AddressRequest addressReq = request.getAddress();
        DeliveryAddress address = new DeliveryAddress();
        address.setCustomerId(customerId);
        address.setLine1(addressReq.getLine1());
        address.setLine2(addressReq.getLine2());
        address.setCity(addressReq.getCity());
        address.setState(addressReq.getState());
        address.setPincode(addressReq.getPincode());
        address.setDeliveryNotes(addressReq.getDeliveryNotes());
        address = deliveryAddressRepository.save(address);

        return buildResponse(customer, address);
    }

    private OnboardingResponse buildResponse(User customer, DeliveryAddress address) {
        return OnboardingResponse.builder()
                .customerId(customer.getId())
                .phone(customer.getPhone())
                .onboardingComplete(customer.getOnboardingCompleted())
                .address(OnboardingResponse.AddressResponse.builder()
                        .id(address.getId())
                        .line1(address.getLine1())
                        .line2(address.getLine2())
                        .city(address.getCity())
                        .state(address.getState())
                        .pincode(address.getPincode())
                        .deliveryNotes(address.getDeliveryNotes())
                        .build())
                .build();
    }
}
