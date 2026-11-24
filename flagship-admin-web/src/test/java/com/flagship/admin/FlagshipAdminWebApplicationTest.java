package com.flagship.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FlagshipAdminWebApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Admin Web: /admin/api/config returns UP status and server URL")
    void testConfigEndpoint() throws Exception {
        mockMvc.perform(get("/admin/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("FlagShip Admin Web"));
    }

    @Test
    @DisplayName("Admin Web: Static /index.html is served successfully")
    void testIndexHtml() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }
}
