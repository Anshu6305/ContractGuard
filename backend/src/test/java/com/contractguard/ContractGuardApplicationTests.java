package com.contractguard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: fails if the Spring context cannot start -- a missing bean, a
 * bad @Value, a circular dependency. Cheap and catches a lot.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContractGuardApplicationTests {

    @Test
    void contextLoads() {
    }
}
