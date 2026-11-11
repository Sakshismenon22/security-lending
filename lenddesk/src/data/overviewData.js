export const exceptions = [
  ['High','Margin shortfall','Everest Capital','LOAN-000006','NVDA','1,250,000','$769,087,500','Jan 28, 18:00','Open','risk'],
  ['High','Settlement fail (return)','Bridgewater Securities','LOAN-000002','NVDA','800,000','$492,216,000','Jan 28, 16:00','Open','risk'],
  ['Medium','Collateral ratio below target','Atlas Capital LP','LOAN-000001','AAPL','1,200,000','$232,104,000','Jan 28, 20:00','In progress','warn'],
  ['Medium','Recall at risk (T+1)','Summit Global','LOAN-000004','AMZN','1,500,000','$267,465,000','Jan 28, 19:00','In progress','warn'],
  ['Low','Fee payment overdue','Orion Hedge Fund','LOAN-000005','AAPL','750,000','$145,065,000','Jan 28, 21:00','Acknowledged','neutral'],
  ['Low','Rebate adjustment','Rivermere Advisors','LOAN-000007','MSFT','600,000','$252,900,000','Jan 29, 00:00','Acknowledged','neutral'],
]

export const settlements = [
  ['Return','LOAN-000002','NVDA','800,000','$492,216,000','16:00','Settling','good'],['Return','LOAN-000004','AMZN','500,000','$89,155,000','16:30','Settling','good'],
  ['Collateral release','LOAN-000001','AAPL','1,200,000','$232,104,000','17:00','Queued','neutral'],['Fee payment','LOAN-000005','AAPL','—','$78,900','17:30','Queued','neutral'],
  ['Rebate payment','LOAN-000003','MSFT','—','$12,450','18:00','Queued','neutral'],['Return','LOAN-000006','NVDA','450,000','$276,871,500','18:30','At risk','warn'],
]

export const timeline = [
  ['14:30','Return','Return settlement due','LOAN-000002 · NVDA · 800,000','Settling','good'],['16:00','Collateral release','Collateral release','LOAN-000001 · AAPL · 1,200,000','Queued','neutral'],
  ['17:30','Fee payment','Fee payment due','LOAN-000005 · AAPL · $78,900','Queued','neutral'],['18:00','Rebate payment','Rebate payment due','LOAN-000003 · MSFT · $12,450','Queued','neutral'],
  ['19:00','Return','Return settlement due','LOAN-000006 · NVDA · 450,000','At risk','warn'],
]

export const fixRows = [
  ['14:26:58','← In','Execution report (8)','LOAN-000002','NVDA','Return fill 800,000 @ 615.27'],['14:24:11','→ Out','Order single (D)','CLM-006-002','AAPL','Collateral release 1,200,000'],
  ['14:21:37','← In','Execution report (8)','LOAN-000004','AMZN','Return fill 500,000 @ 178.31'],['14:18:52','→ Out','Order single (D)','FEE-000005','AAPL','Fee payment $78,900'],
]
