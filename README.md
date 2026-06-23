# Quberratrix Identity Service

A production-intended Spring Boot WebFlux microservice for handling authentication, authorization, and user management.

## Current Status (Phase 1 Completed)
This repository has finalized the foundation architectural components and structural bounds. Real production usage still requires security hardening, detailed JWT validation algorithms, full multi-node load testing, full functional integration tests suites, and an extensive security review.

## Features
- Fully reactive stack using Spring WebFlux and R2DBC PostgreSQL.
- DTO isolated responses across User, Admin, Organization, and Client Endpoints mitigating schema leakages.
- Safe transactional Event Outbox architecture preventing message loss against Redpanda/Kafka instances.
- JWT issuing mapped to isolated `access` and `service` specific scopes correctly supporting configuration-based overrides without requiring blocking DB calls on typical API hits.
- Support for `ACTIVE`, `DISABLED`, `LOCKED`, and `DELETED` state lifecycles dynamically controlling logins explicitly utilizing DB constraints over standard booleans.

## Run locally

```bash
docker-compose up -d # Setup local postgres + redpanda
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Production Tuning
- Always populate configured ENV properties or `.env` files bound to `application.yml` parameters.
- Adjust `initial-size` and `max-size` in `application.yml`'s connection pool depending on target RPS.
