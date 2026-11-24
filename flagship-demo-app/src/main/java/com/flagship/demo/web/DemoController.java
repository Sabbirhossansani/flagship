package com.flagship.demo.web;

import com.flagship.core.model.EvaluationContext;
import com.flagship.demo.service.DemoFeatureService;
import com.flagship.starter.client.FlagShipClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DemoController {

    private final DemoFeatureService featureService;
    private final FlagShipClient flagShipClient;

    public DemoController(DemoFeatureService featureService, FlagShipClient flagShipClient) {
        this.featureService = featureService;
        this.flagShipClient = flagShipClient;
    }

    @GetMapping("/checkout")
    public Map<String, Object> checkout(@RequestParam(defaultValue = "cart-123") String cartId) {
        String result = featureService.processCheckout(cartId);
        return Map.of(
                "cartId", cartId,
                "result", result
        );
    }

    @GetMapping("/features")
    public Map<String, Object> features(@RequestParam(defaultValue = "anonymous") String userId) {
        EvaluationContext context = EvaluationContext.builder().userKey(userId).build();
        boolean checkoutV2 = flagShipClient.isEnabled("new-checkout-flow", context, false);
        String welcomeMessage = flagShipClient.getString("welcome-message", context, "Welcome!");

        return Map.of(
                "userId", userId,
                "newCheckoutFlow", checkoutV2,
                "welcomeMessage", welcomeMessage
        );
    }
}
