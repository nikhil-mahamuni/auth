package com.quberratrix.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.r2dbc.url=r2dbc:postgresql://localhost:5432/testdb",
    "identity.jwt.dev-generate-keypair=true",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class IdentityServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
