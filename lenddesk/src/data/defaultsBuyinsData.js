const borrowers = [
  "Everest Capital",
  "Bridgewater Securities",
  "Atlas Capital",
  "Summit Global",
];
const securities = ["TCS", "HDFCBANK", "INFY", "RELIANCE", "SBIN"];
const quantities = [
  2000, 1500, 1000, 2400, 4000, 1000, 500, 3000, 800, 1200, 600, 900,
];
const prices = {
  TCS: 3520,
  HDFCBANK: 1685,
  INFY: 1480,
  RELIANCE: 2420,
  SBIN: 699,
};
const states = [
  "Pending review",
  "Pending review",
  "In default",
  "Buy-in pending",
  "Pending review",
  "Pending review",
  "In default",
  "Resolved",
  "In default",
  "Pending review",
  "Resolved",
  "In default",
];
export const defaultCases = quantities.map((quantity, i) => {
  const security = securities[i % securities.length];
  const resolved = states[i] === "Resolved";
  return {
    id: `DEF-${String(i + 16).padStart(6, "0")}`,
    loanId: `LOAN-${String(i + 10).padStart(6, "0")}`,
    borrower: borrowers[i % borrowers.length],
    security,
    quantity,
    price: prices[security],
    exposure: quantity * prices[security],
    trigger: [
      "Margin shortfall",
      "Recall overdue",
      "Recall overdue",
      "Settlement failure",
    ][i % 4],
    deadline: `2026-11-${i < 4 ? "18" : "19"}T${i === 2 ? "12" : "15"}:00`,
    status: states[i],
    recovery: resolved
      ? "Recovered"
      : [2, 3, 6].includes(i)
        ? "In progress"
        : "Not started",
    reference:
      i === 2
        ? "REC-000122"
        : `${i % 4 === 0 ? "MC" : "REC"}-${String(i + 120).padStart(6, "0")}`,
    notices: [2, 3, 6].includes(i)
      ? [
          {
            id: `NTC-${i + 21942}`,
            type: "Default notice",
            recipient: borrowers[i % borrowers.length],
            sent: "18 Nov 12:05",
            status: "Sent",
          },
          {
            id: `NTC-${i + 21943}`,
            type: "Buy-in intent",
            recipient: borrowers[i % borrowers.length],
            sent: "18 Nov 12:10",
            status: "Sent",
          },
        ]
      : [],
    order: [2, 3, 6].includes(i)
      ? {
          id: `ORD-${String(i + 9881).padStart(6, "0")}`,
          buyinId: `BI-${String(i + 730).padStart(6, "0")}`,
          quantity,
          filled: i === 2 ? 600 : 0,
          limitPrice: prices[security] + 15,
          averagePrice: i === 2 ? 1492 : 0,
          status: i === 2 ? "Partially filled" : "Pending execution",
        }
      : null,
    activities: resolved
      ? [
          {
            time: "19 Nov 10:30",
            event: "Case resolved",
            reference: "Settlement confirmed",
          },
        ]
      : [
          {
            time: "18 Nov 12:00",
            event: "Obligation deadline reached",
            reference: "Return outstanding",
          },
          {
            time: "18 Nov 12:05",
            event: "Case recorded",
            reference: "Default review",
          },
          ...([2, 3, 6].includes(i)
            ? [
                {
                  time: "18 Nov 12:10",
                  event: "Notices sent",
                  reference: "Default notice / buy-in intent",
                },
              ]
            : []),
          ...(i === 2
            ? [
                {
                  time: "18 Nov 14:18",
                  event: "Partial fill",
                  reference: "600 INFY @ ₹1,492.00",
                },
              ]
            : []),
        ],
  };
});
export const previewLoans = [
  {
    id: "LOAN-000040",
    borrower: "Everest Capital",
    security: "INFY",
    quantity: 1800,
    price: 1480,
  },
  {
    id: "LOAN-000041",
    borrower: "Atlas Capital",
    security: "TCS",
    quantity: 600,
    price: 3520,
  },
  {
    id: "LOAN-000042",
    borrower: "Summit Global",
    security: "SBIN",
    quantity: 2500,
    price: 699,
  },
];
export const number = (value) =>
  new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(value);
export const rupees = (value) => `₹${number(value)}`;
export const caseDate = (value) =>
  new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Kolkata",
  }).format(new Date(`${value}:00+05:30`));
