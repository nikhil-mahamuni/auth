# Quberratrix Identity Service

A production-ready Spring Boot WebFlux microservice for handling authentication, authorization, and user management.

## Features
- Fully reactive stack using Spring WebFlux and R2DBC
- Cloud-ready PostgreSQL integration
- JWT issuing and JWKS validation endpoint (RS256)
- Refresh token rotation
- Event-driven notifications via Redpanda/Kafka
- Flyway migrations support

## Run locally

```bash
docker-compose up -d # Setup local posgres + redpanda if needed
mvn spring-boot:run
```

## Production Tuning
- Make sure to supply your own PEM keys instead of using `security.jwt.dev-generate-keypair=true`.
- Adjust `initial-size` and `max-size` in `application.yml`'s connection pool depending on target RPS.
