import axios from "axios";
import https from "https";
const httpsAgent = new https.Agent({
  rejectUnauthorized: false,
});

async function getLatestNews() {
  const url = "https://query1.finance.yahoo.com/v1/finance/search"

  try {
    const response = await axios.get(url, {
      httpsAgent,
      params: {
        q: "AAPL",
        quotesCount: 1,
        newsCount: 10,
        enableFuzzyQuery: false,
      },
      headers: {
        "User-Agent": "Mozilla/5.0",
        Accept: "application/json",
      },
      timeout: 15000,
    });

    const articles = response.data.news || [];

    if (articles.length === 0) {
      console.log("No news found for INFY.");
      return;
    }

    articles.forEach((article, index) => {
      console.log(`\n${index + 1}. ${article.title}`);
      console.log(`Publisher: ${article.publisher || "Unknown"}`);
      console.log(`Published: ${
        article.providerPublishTime
          ? new Date(article.providerPublishTime * 1000).toISOString()
          : "Unknown"
      }`);
      console.log(`URL: ${article.link}`);
    });
  } catch (error) {
    console.error(
      "Request failed:",
      error.response?.status,
      error.response?.data || error.message
    );
  }
}

getLatestNews();