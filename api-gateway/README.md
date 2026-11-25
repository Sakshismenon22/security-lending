# Security Lending API Gateway

A standalone Spring Boot gateway for routing browser and external API traffic to the existing Security Lending backend services.

## Technology

- Java 17
- Spring Boot 4.1.2
- Spring Cloud 2025.1.3
- Spring Cloud Gateway Server WebFlux
- Spring Boot Actuator

Spring Cloud 2025.1.3 supports the Spring Boot 4.1.x line. Keep the Spring Cloud BOM in place rather than assigning individual Spring Cloud dependency versions.

## Local service routes

The gateway listens on **http://localhost:8090** by default.

| Gateway path | Downstream service | Default port | Forwarded path |
|---|---|---:|---|
| `/borrower/**` | Borrower service | 8081 | Prefix `/borrower` removed |
| `/lender/**` | Lender service | 8084 | Prefix `/lender` removed |
| `/lifecycle/**` | Lifecycle service | 8085 | Prefix `/lifecycle` removed |
| `/market-data/**` | Market data service | 8080 | Prefix `/market-data` removed |
| `/lendora/**` | Lendora service | 8086 | Prefix `/lendora` removed |
| `/fix/borrower/**` | Borrower FIX adapter | 8082 | First two path segments removed |
| `/fix/lender/**` | Lender FIX adapter | 8083 | First two path segments removed |
| `/api/v1/auth/**` | Lendora service | 8086 | Path unchanged |
| Existing borrower API paths | Borrower service | 8081 | Path unchanged |

The borrower and lender services expose overlapping URL patterns under `/api/v1/organizations/**`. To avoid ambiguous routing, lender requests use the explicit `/lender/**` prefix. The existing borrower API paths remain available for gradual frontend migration.

## Frontend integration

The three Vite applications are configured to use the gateway by default:

- BorrowDesk (`borrowdesk`): `http://localhost:8090` for borrower APIs and `http://localhost:8090/market-data` for market-data requests.
- LendDesk (`lenddesk`): `http://localhost:8090/lender` for lender APIs.
- Lendora (`lendora`): `http://localhost:8090/api/v1` for authentication and organization registration.

The borrower and lender workspace session clients also use the gateway for `/api/v1/auth/**`. The gateway has a dedicated registration route for `/api/v1/organizations/register` before the borrower organization route, so registration reaches Lendora rather than Borrower service. Copy the updated `.env.example` values into any existing local `.env` files; explicit Vite environment values override the code defaults. Restart all Vite dev servers after changing environment variables.

## Run locally

Start PostgreSQL, Kafka, and the backend services using the existing project startup process. Then run:

```bash
cd api-gateway
./mvnw spring-boot:run
```

On Windows, run `.\mvnw.cmd spring-boot:run` from `api-gateway`. The demo launcher uses the same wrapper and runs the current checkout's application classes and resources.

Check gateway health:

```text
http://localhost:8090/actuator/health
```

Example requests:

```text
GET http://localhost:8090/api/v1/organizations/1/loans
GET http://localhost:8090/lender/api/v1/organizations/1/loans
GET http://localhost:8090/market-data/api/v1/securities-current-price
```

Use the exact endpoint paths already exposed by the relevant service after the gateway prefix.

## Configuration

Service destinations can be changed without editing the source:

- `SERVER_PORT`
- `BORROWER_SERVICE_URL`
- `LENDER_SERVICE_URL`
- `LIFECYCLE_SERVICE_URL`
- `MARKET_DATA_SERVICE_URL`
- `LENDORA_SERVICE_URL`
- `BORROWER_FIX_ADAPTER_URL`
- `LENDER_FIX_ADAPTER_URL`
- `GATEWAY_ALLOWED_ORIGINS`

The defaults match the ports in the repository's current local demo launcher.

## Cross-cutting behavior

- A valid incoming `X-Correlation-ID` is forwarded. If it is absent or malformed, the gateway generates a UUID.
- The correlation ID is returned in the response header.
- Gateway request metrics and health information are exposed through Actuator.
- CORS is configured for the local BorrowDesk, LendDesk, and Lendora frontends.

## Security boundary

This gateway does **not** replace Lendora authentication or the authorization and organization-isolation checks in borrower/lender services. Browser session cookies are forwarded to downstream services. Keep authentication and authorization enabled in those services, and do not expose their ports publicly in production. The gateway currently uses explicit local service URLs; service discovery/Eureka and distributed rate limiting can be added once the corresponding infrastructure is enabled in this branch.

## Build

```bash
mvn clean test
mvn clean package
```
