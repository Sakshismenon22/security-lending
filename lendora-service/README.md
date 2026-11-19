# Lendora account service

Signup and cookie-session login for lender and borrower organizations. Uses Spring
MVC/JPA/Flyway and JDK PBKDF2; **no Spring Security dependency**.

See [LENDORA_AUTH.md](../LENDORA_AUTH.md) for the full contract, setup and workspace
integration. Create `lendora_db`, configure the shared `LENDORA_PROVISIONING_KEY`
on this service and both business services, then run `./mvnw spring-boot:run`.
The default port is 8086. A full JDK 17+ is required.

Run `./mvnw test` for account API, cookie, password, rollback and session tests.
The test profile uses an isolated H2 database and a mocked workspace provisioner.
