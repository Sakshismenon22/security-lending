import axios from "axios";

async function getHistoricalCandles() {
  try {
    const response = await axios.get(
      "http://localhost:8080/api/v1/security-data/history",
      {
        params: {
          symbol: "INFY",
          from: "2026-11-05T03:45:00Z",
          to: "2026-11-05T10:00:00Z",
          exchange: "NSE"
        }
      }
    );

    console.log(response.data);
  } catch (error) {
    console.error(
      error.response?.data ?? error.message
    );
  }
}

await getHistoricalCandles();