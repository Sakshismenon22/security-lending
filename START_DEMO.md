# Start the local lending demo

All Java service settings live in their `src/main/resources/application*.properties` files. No environment variable setup is required. Database credentials are `postgres` / `postgres`, and Kafka is enabled at `localhost:9092`.

Start PostgreSQL first. The configured databases are `market_data`, `borrower_db`, `lender_db`, `borrower_fix_db`, and `lender_fix_db`; create them once if missing.

Run each service in a separate PowerShell terminal:

| Folder under D:\Project\SecurityLending | Command | Port |
| --- | --- | --- |
| kafka-ui-portal\kafka-ui-portal | `npm start` | 3001 |
| market-data-service | `.\mvnw.cmd spring-boot:run` | 8080 |
| fix-adapter-service | `.\run-lender.cmd` | 8083 |
| fix-adapter-service (second terminal) | `.\run-borrower.cmd` | 8082 |
| borrower-service | `.\mvnw.cmd spring-boot:run` | 8081 |
| lender-service | `.\mvnw.cmd spring-boot:run` | 8084 |
| security-lending-test-demo | `npm run dev` | 5176 |

Before starting the Spring services, open http://127.0.0.1:3001, start Kafka, then click **Set up lending topics** near the top of **Overview**. The same action is available in Settings and Topics. It creates the 16 configured topics and preserves existing ones. It is disabled until the broker is ready. Spring does not automatically provision those topics in this project.

The FIX launchers select profiles in the same codebase; all connection and identity details remain in the properties files. By default the adapters allow borrower organization ID 1. If using another borrower organization ID, update `adapter.routes[0].organization-ids` in both adapter profile files and restart both adapters. Match lender external ID `LENDER_DEMO` and borrower connection external ID `BORROWER_DEMO` to the configured institutions.

Open http://127.0.0.1:5176 and select Check connections, then Guided workflow. Lifecycle/payment execution and authentication remain separate, unimplemented services/features; creating Kafka topics does not implement those workflows.
