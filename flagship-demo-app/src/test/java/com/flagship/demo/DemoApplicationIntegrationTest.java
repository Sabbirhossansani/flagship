package com.flagship.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Demo: /checkout endpoint evaluates @FeatureGuard and fallback")
    void checkoutEndpoint_fallbackInvoked() throws Exception {
        mockMvc.perform(get("/checkout").param("cartId", "cart-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value("cart-999"))
                .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.containsString("Legacy Multi-Step Checkout")));
    }

    @Test
    @DisplayName("Demo: /features endpoint evaluates flags using FlagShipClient")
    void featuresEndpoint_returnsEvaluations() throws Exception {
        mockMvc.perform(get("/features").param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.newCheckoutFlow").value(false))
                .andExpect(jsonPath("$.welcomeMessage").value("Welcome!"));
    }

    @Test
    @DisplayName("Demo: /actuator/flagship endpoint exposes starter metadata")
    void actuatorFlagshipEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/flagship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.projectKey").value("demo-project"));
    }
}
