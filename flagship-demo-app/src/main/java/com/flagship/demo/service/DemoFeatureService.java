package com.flagship.demo.service;

import com.flagship.starter.annotation.FeatureGuard;
import org.springframework.stereotype.Service;

@Service
public class DemoFeatureService {

    @FeatureGuard(value = "new-checkout-flow", fallbackMethod = "legacyCheckout")
    public String processCheckout(String cartId) {
        return "Modern 1-Click Checkout processed cart " + cartId;
    }

    public String legacyCheckout(String cartId) {
        return "Legacy Multi-Step Checkout processed cart " + cartId;
    }
}
