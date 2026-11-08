import { Client } from "@stomp/stompjs";
import WebSocket from "ws";

const WEBSOCKET_URL =
  process.env.WEBSOCKET_URL ??
  "ws://localhost:8080/ws";

const symbol = process.argv[2] ?? "INFY";

const destination = `/topic/price/${symbol}`;

const client = new Client({
  webSocketFactory: () => {
    return new WebSocket(WEBSOCKET_URL);
  },

  reconnectDelay: 5000,

  heartbeatIncoming: 10_000,
  heartbeatOutgoing: 10_000,

  connectHeaders: {
    // Add authorization when required:
    // Authorization: `Bearer ${process.env.ACCESS_TOKEN}`
  },

  debug: (message) => {
    if (process.env.STOMP_DEBUG === "true") {
      console.log("[STOMP]", message);
    }
  },

  onConnect: (frame) => {
    console.log("Connected to market-data WebSocket");
    console.log("Session:", frame.headers.session);
    console.log("Subscribed topic:", destination);

    client.subscribe(destination, (message) => {
      try {
        const currentPrice = JSON.parse(message.body);

        console.log("\nCurrent-price update received");
        console.table([
          {
            securityId: currentPrice.securityId,
            symbol: currentPrice.symbol,
            exchange: currentPrice.exchange,
            timestamp:
              currentPrice.tradeDateTimestamp,
            open: currentPrice.open,
            high: currentPrice.high,
            low: currentPrice.low,
            close: currentPrice.close,
            volume: currentPrice.volume
          }
        ]);
      } catch (error) {
        console.error(
          "Could not parse WebSocket message:",
          error.message
        );

        console.log("Raw message:", message.body);
      }
    });
  },

  onStompError: (frame) => {
    console.error("STOMP broker error");
    console.error("Message:", frame.headers.message);
    console.error("Details:", frame.body);
  },

  onWebSocketError: (error) => {
    console.error(
      "WebSocket connection error:",
      error.message ?? error
    );
  },

  onWebSocketClose: (event) => {
    console.log(
      `WebSocket closed: code=${event.code}, reason=${event.reason}`
    );
  },

  onDisconnect: () => {
    console.log("Disconnected from WebSocket");
  }
});

console.log("Connecting to:", WEBSOCKET_URL);
console.log("Waiting for updates on:", destination);

client.activate();

async function shutdown(signal) {
  console.log(`\nReceived ${signal}. Disconnecting...`);

  try {
    await client.deactivate();
  } finally {
    process.exit(0);
  }
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));