import { runEndpoint } from "./_shared.js";

await runEndpoint({
  "file": "read-lenders",
  "name": "List lenders",
  "service": "lender",
  "method": "GET",
  "path": "/lenders",
  "orgScoped": true,
  "paged": false
});
