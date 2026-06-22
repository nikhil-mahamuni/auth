# Quberratrix Identity Service

A production-ready Spring Boot WebFlux microservice for handling authentication, authorization, and user management.

## Features
- Fully reactive stack using Spring WebFlux and R2DBC PostgreSQL.
- Cloud-ready architectural boundaries implementing Event Outbox.
- JWT issuing and stateless JWKS validation endpoint (RS256).
- Secure, refresh token rotation dynamically bound to typed client configurations.
- Event-driven notifications via Redpanda/Kafka without DB call blocking.
- Flyway migrations support with structured Enum boundaries constraint hardening.
- Gateway configuration property protections mitigating CORS and unauthorized headers dynamically.
- Deep user, client, and admin capabilities endpoints protected via explicitly checked API rules isolating core DTOs.

## Run locally

```bash
docker-compose up -d # Setup local posgres + redpanda
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Production Tuning
- Always populate configured ENV properties or `.env` files bound to `application.yml` parameters.
- Provide true paths via `IDENTITY_JWT_PRIVATE_KEY_PATH` and `IDENTITY_JWT_PUBLIC_KEY_PATH` representing properly generated PKCS/PEM keypairs for your production signing infrastructure. Disable development Keypair generation!
- Adjust `initial-size` and `max-size` in `application.yml`'s connection pool depending on target RPS.
