package com.quberratrix.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.r2dbc.url=r2dbc:postgresql://localhost:5432/testdb",
    "security.jwt.dev-generate-keypair=true",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class IdentityServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
