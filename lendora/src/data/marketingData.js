export const lenderRows = [
  {
    id: "LD-104582",
    counterparty: "Alpha Capital",
    security: "RELIANCE",
    quantity: "1,200,000",
    collateral: "INR 1,51,20,00,000",
    status: "Active",
  },
  {
    id: "LD-104583",
    counterparty: "Beta Securities",
    security: "INFY",
    quantity: "500,000",
    collateral: "INR 842,50,00,000",
    status: "Active",
  },
  {
    id: "LD-104584",
    counterparty: "Gamma Markets",
    security: "TCS",
    quantity: "300,000",
    collateral: "INR 1,10,25,00,000",
    status: "Active",
  },
];
export const borrowerRows = [
  {
    id: "LC-778201",
    security: "RELIANCE",
    quantity: "1,200,000",
    status: "Located",
  },
  { id: "LC-778202", security: "INFY", quantity: "500,000", status: "Located" },
  { id: "LC-778203", security: "TCS", quantity: "300,000", status: "Pending" },
];
export const desks = [
  {
    id: "for-lenders",
    title: "LendDesk",
    audience: "for institutional lenders",
    description:
      "A dedicated workspace to lend securities, manage inventory, and oversee the full lifecycle with your counterparties.",
    points: [
      "Manage lending agreements",
      "Publish and control available inventory",
      "Respond to locate requests",
      "Monitor loans, collateral and returns",
    ],
  },
  {
    id: "for-borrowers",
    title: "BorrowDesk",
    audience: "for institutional borrowers",
    description:
      "A dedicated workspace to source borrow, request locates, manage loans and collateral, and coordinate returns across counterparties.",
    points: [
      "Request and manage locates",
      "Initiate and track borrow loans",
      "Maintain collateral and margin",
      "Oversee returns and closeouts",
    ],
  },
];
