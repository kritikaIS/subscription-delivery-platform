package com.juiceplatform.security;

import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class OnboardingInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 1. Ensure the user is actually authenticated
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser authUser) {

            // 2. We only care about checking onboarding for CUSTOMERS
            if ("CUSTOMER".equals(authUser.getRole())) {
                
                // 3. Fetch the fresh DB record to check onboarding status
                User user = userRepository.findById(authUser.getUserId())
                        .orElseThrow(() -> new BusinessException("UNAUTHORIZED", 
                                "User not found", HttpStatus.UNAUTHORIZED));

                if (!Boolean.TRUE.equals(user.getOnboardingCompleted())) {
                    // 4. Throw a 403 Forbidden if they haven't finished onboarding
                    throw new BusinessException("ONBOARDING_REQUIRED",
                            "Customer onboarding is incomplete. Please complete your profile first.",
                            HttpStatus.FORBIDDEN);
                }
            }
        }
        
        // Let the request proceed to the Controller
        return true; 
    }
}
