export const marginCallConfig = {
  resource: 'margin-calls',
  title: 'Margin Calls', metrics: [['Open calls', '3'], ['Collateral deficit', '$4.28M'], ['Due before 16:00', '2'], ['Overdue', '1']], action: 'Issue margin call', footer: 'Margin calls are produced by Lifecycle Service after daily collateral-ratio evaluation.',
  tabs: [['All calls', '6'], ['Open', '3'], ['Overdue', '1'], ['Closed', '2']],
  columns: [['Call ID', 'id'], ['Loan ID', 'loan'], ['Borrower', 'borrower'], ['Security', 'security'], ['Deficit', 'amount'], ['Required by', 'due'], ['Response', 'response'], ['Status', 'status']],
  rows: [
    { id: 'MC-000087', loan: 'LOAN-000057', borrower: 'Morgan Stanley & Co.', security: 'NVDA', amount: '$1.20M', due: '15:00 UTC', response: 'Pledge pending', status: 'Open', detail: [['Valuation trigger', 'Coverage fell to 101.8% versus 102.0% requirement'], ['Required collateral', '$1.20M'], ['Proposed collateral', 'Cash USD · Fedwire'], ['FIX interaction', 'Collateral Assignment (35=CB)']] },
    { id: 'MC-000084', loan: 'LOAN-000071', borrower: 'Atlas Capital LP', security: 'AMZN', amount: '$2.46M', due: '14:30 UTC', response: 'No response', status: 'Overdue', detail: [['Valuation trigger', 'AMZN price moved +3.2%'], ['Required collateral', '$2.46M'], ['Escalation', 'Default workflow not yet triggered'], ['FIX interaction', 'Security Status (35=f)']] },
    { id: 'MC-000081', loan: 'LOAN-000049', borrower: 'Goldman Sachs Intl.', security: 'TSLA', amount: '$0.62M', due: '16:00 UTC', response: 'Receipt confirmed', status: 'Closed', detail: [['Collateral received', '$0.62M cash'], ['Confirmation', 'Lender confirmed collateral receipt'], ['Coverage after', '103.4%'], ['Closed at', '13:10 UTC']] },
  ],
}

export const recallConfig = {
  resource: 'recalls',
  title: 'Recalls', metrics: [['Open recalls', '2'], ['Shares recalled', '80,000'], ['Due today', '50,000'], ['At risk', '1']], action: 'Create recall', footer: 'Recall returns become return instructions once the borrower responds and commits quantity.',
  tabs: [['All recalls', '8'], ['Open', '2'], ['Due today', '2'], ['Completed', '6']],
  columns: [['Recall ID', 'id'], ['Loan ID', 'loan'], ['Borrower', 'borrower'], ['Security', 'security'], ['Quantity', 'quantity'], ['Notice date', 'notice'], ['Return due', 'due'], ['Return ID', 'returnId'], ['Status', 'status']],
  rows: [
    { id: 'REC-000014', loan: 'LOAN-000042', borrower: 'Morgan Stanley & Co.', security: 'TSLA', quantity: '50,000', notice: '28 Jan, 14:15', due: '16:00 UTC', returnId: 'RET-000041', status: 'Acknowledged', detail: [['Reason', 'Internal inventory requirement'], ['Recall quantity', '50,000 TSLA'], ['Borrower response', 'Acknowledged · return committed'], ['Linked return', 'RET-000041 · Settlement scheduled']] },
    { id: 'REC-000009', loan: 'LOAN-000021', borrower: 'Goldman Sachs Intl.', security: 'AMZN', quantity: '30,000', notice: '28 Jan, 10:40', due: '18:00 UTC', returnId: 'RET-000019', status: 'Return scheduled', detail: [['Reason', 'Client delivery obligation'], ['Recall quantity', '30,000 AMZN'], ['Borrower response', 'Return accepted'], ['Linked return', 'RET-000019 · DTC scheduled']] },
    { id: 'REC-000004', loan: 'LOAN-000018', borrower: 'Citigroup Global Markets', security: 'AAPL', quantity: '25,000', notice: '27 Jan, 15:12', due: '27 Jan, 17:00', returnId: 'RET-000011', status: 'Completed', detail: [['Return quantity', '25,000 AAPL'], ['Delivery', 'DTC confirmed'], ['Collateral', 'Released after settlement'], ['Outcome', 'Closed']] },
  ],
}

export const feeRebateConfig = {
  resource: 'accruals',
  title: 'Fees & Rebates', metrics: [['Fee accrued today', '$42,891'], ['Cash rebate accrued', '$18,420'], ['Due settlements', '6'], ['Net receivable', '$24,471']], action: 'Run daily accruals', footer: 'Daily accruals are calculated by Lifecycle Service using current loan market value and MSLA rates.',
  tabs: [['All accruals', '684'], ['Fees', '342'], ['Rebates', '342'], ['Due settlement', '6']],
  columns: [['Accrual ID', 'id'], ['Loan ID', 'loan'], ['Borrower', 'borrower'], ['Type', 'type'], ['Rate', 'rate'], ['Accrued today', 'amount'], ['Accrued total', 'total'], ['Settlement rule', 'rule'], ['Next settlement', 'due'], ['Status', 'status']],
  rows: [
    { id: 'FEE-000057-28', loan: 'LOAN-000057', borrower: 'Morgan Stanley & Co.', type: 'Lending fee', rate: '185 bps', amount: '$4,686.14', total: '$18,742.63', rule: 'Monthly', due: '31 Jan 2026', status: 'Accrued', detail: [['Calculation base', '$92.29M on-loan market value'], ['Annual rate', '1.85%'], ['Daily accrual', '$4,686.14'], ['Settlement state', 'Accrued; not yet due']] },
    { id: 'REB-000014-28', loan: 'LOAN-000014', borrower: 'Barclays Capital Inc.', type: 'Cash rebate', rate: 'SOFR - 12 bps', amount: '$1,204.14', total: '$8,104.16', rule: 'Monthly', due: '31 Jan 2026', status: 'Accrued', detail: [['Cash collateral base', '$14.97M'], ['Annual rebate rate', '5.18%'], ['Daily accrual', '$1,204.14'], ['Settlement state', 'Accrued; borrower payable']] },
    { id: 'FEE-000061-28', loan: 'LOAN-000061', borrower: 'BNP Paribas Arbitrage', type: 'Lending fee', rate: '175 bps', amount: '$611.08', total: '$4,888.64', rule: 'At termination', due: 'Loan termination', status: 'Accrued', detail: [['Calculation base', '$12.57M on-loan market value'], ['Settlement rule', 'At loan termination'], ['Daily accrual', '$611.08'], ['Settlement state', 'Accrued']] },
  ],
}

export const ledgerConfig = {
  resource: 'ledger',
  title: 'Settlement Ledger', metrics: [['Entries today', '142'], ['Cash movement', '$64.28M'], ['Pending entries', '5'], ['Balanced', '100%']], action: 'Export ledger', footer: 'Ledger entries are posted when a lifecycle settlement leg is confirmed by its receiving party.',
  tabs: [['All entries', '142'], ['Cash', '86'], ['Securities', '42'], ['Pending', '5']],
  columns: [['Ledger ID', 'id'], ['Posted UTC', 'posted'], ['Reference', 'loan'], ['Counterparty', 'borrower'], ['Entry type', 'type'], ['Debit account', 'debit'], ['Credit account', 'credit'], ['Amount / quantity', 'amount'], ['State', 'status']],
  rows: [
    { id: 'LED-009812', posted: '14:12:44', loan: 'LOAN-000061', borrower: 'BNP Paribas Arbitrage', type: 'Fee payment', debit: 'Borrower cash collateral', credit: 'Lender cash account', amount: '$611.08', status: 'Posted', detail: [['Event source', 'Return / fee settlement'], ['Double-entry balance', '$611.08 debit = $611.08 credit'], ['Reference', 'FEE-000061-28'], ['Posting state', 'Final']] },
    { id: 'LED-009809', posted: '13:59:12', loan: 'LOAN-000031', borrower: 'BNP Paribas Arbitrage', type: 'Collateral release', debit: 'Lender collateral account', credit: 'Borrower cash account', amount: '$3.07M', status: 'Posted', detail: [['Event source', 'RET-000031 settlement'], ['Settlement route', 'Fedwire'], ['Reference', 'COL-96881'], ['Posting state', 'Final']] },
    { id: 'LED-009803', posted: '13:30:18', loan: 'LOAN-000057', borrower: 'Morgan Stanley & Co.', type: 'Margin requirement', debit: 'Borrower margin due', credit: 'Lender receivable', amount: '$1.20M', status: 'Pending', detail: [['Event source', 'MC-000087'], ['Requirement state', 'Open margin call'], ['Settlement route', 'Pending pledge'], ['Posting state', 'Pending']] },
  ],
}

export const fixMonitorConfig = {
  resource: 'audit',
  title: 'FIX Monitor', metrics: [['Messages today', '142,890'], ['Inbound', '68,441'], ['Outbound', '74,449'], ['Gateway latency', '4.2 ms']], action: 'Open FIX settings', footer: 'Raw FIX is displayed with | delimiters for readability; the transport uses SOH separators.',
  tabs: [['All messages', '142,890'], ['Inbound', '68,441'], ['Outbound', '74,449'], ['Exceptions', '3']],
  columns: [['Sequence', 'id'], ['UTC time', 'posted'], ['Direction', 'type'], ['Message type', 'loan'], ['Sender', 'borrower'], ['Target', 'debit'], ['Business reference', 'credit'], ['Latency', 'amount'], ['State', 'status']],
  rows: [
    { id: '1048291', posted: '14:31:02.148', type: 'IN', loan: 'Trade Capture Report (35=AE)', borrower: 'BARCLAYS_SF', debit: 'LENDER_APEX', credit: 'LOAN-000006', amount: '3 ms', status: 'Accepted', detail: [['Raw FIX', '8=FIX.4.4|9=248|35=AE|49=BARCLAYS_SF|56=LENDER_APEX|34=1048291|52=20261112-14:31:02.148|55=NVDA|48=US67066G1040|38=120000|891=185.00|880=MSLA-00124|890=LOAN-000006|10=184|'], ['Business meaning', 'Trade Capture Report confirms the loan contract'], ['Kafka correlation', 'fix.inbound.trade-capture'], ['Processing state', 'Accepted and persisted']] },
    { id: '1048289', posted: '14:29:41.810', type: 'OUT', loan: 'Collateral Report (35=BA)', borrower: 'LENDER_APEX', debit: 'CITADEL_EXEC', credit: 'MC-000087', amount: '4 ms', status: 'Processed', detail: [['Raw FIX', '8=FIX.4.4|9=192|35=BA|49=LENDER_APEX|56=CITADEL_EXEC|34=1048289|55=NVDA|891=185.00|902=1200000|10=091|'], ['Business meaning', 'Collateral Report conveys margin requirement'], ['Kafka correlation', 'fix.outbound.collateral-report'], ['Processing state', 'Delivered']] },
    { id: '1048287', posted: '14:26:05.109', type: 'IN', loan: 'Order Cancel Reject (35=9)', borrower: 'MS_PB_GLOBAL', debit: 'LENDER_APEX', credit: 'RET-000039', amount: '7 ms', status: 'Exception', detail: [['Raw FIX', '8=FIX.4.4|9=156|35=9|49=MS_PB_GLOBAL|56=LENDER_APEX|34=1048287|11=RET-000039|58=Settlement instruction rejected|10=203|'], ['Business meaning', 'Return-settlement instruction was rejected'], ['Kafka correlation', 'fix.inbound.reject'], ['Processing state', 'Exception queued']] },
  ],
}

export const auditConfig = {
  resource: 'audit',
  title: 'Audit & Notifications', metrics: [['Events today', '1,284'], ['Unread alerts', '4'], ['Acknowledged', '18'], ['Retention', '7 years']], action: 'Export audit trail', footer: 'All lender actions and system events are retained with actor, timestamp, source and business reference.',
  tabs: [['All events', '1,284'], ['Notifications', '24'], ['User actions', '186'], ['System events', '1,074']],
  columns: [['Event ID', 'id'], ['UTC time', 'posted'], ['Category', 'type'], ['Actor / source', 'borrower'], ['Action', 'loan'], ['Reference', 'credit'], ['Severity', 'amount'], ['Notification state', 'status']],
  rows: [
    { id: 'AUD-008821', posted: '14:18:16', type: 'System event', borrower: 'Lifecycle Service', loan: 'Margin call created', credit: 'MC-000087 · LOAN-000057', amount: 'High', status: 'Unread', detail: [['Event payload', 'Coverage ratio 101.8% below required 102.0%'], ['Source topic', 'lifecycle.margin-call.created'], ['Recipients', 'Lender operations · borrower operations'], ['Retention class', 'Regulatory audit']] },
    { id: 'AUD-008817', posted: '14:15:42', type: 'User action', borrower: 'JD · Lender Operations', loan: 'Recall approved', credit: 'REC-000014 · LOAN-000042', amount: 'Normal', status: 'Acknowledged', detail: [['Action', 'Recall request approved and published'], ['Authentication', 'Organization administrator'], ['FIX message', 'Security Status (35=f)'], ['Outcome', 'Borrower notified']] },
    { id: 'AUD-008804', posted: '13:59:15', type: 'System event', borrower: 'Lifecycle Service', loan: 'Fee payment posted', credit: 'LED-009812 · LOAN-000061', amount: 'Normal', status: 'Read', detail: [['Event payload', '$611.08 fee payment posted'], ['Source topic', 'lifecycle.fee-payment.received'], ['Ledger entry', 'LED-009812'], ['Outcome', 'Settled']] },
  ],
}

export const administrationConfig = {
  resource: 'users',
  title: 'Administration', metrics: [['Organizations', '1'], ['Active users', '14'], ['FIX sessions', '2'], ['Services healthy', '7/7']], action: 'Add user', footer: 'Administration controls access, organizations, operational calendars, configuration and service connectivity.',
  tabs: [['Users', '14'], ['Organizations', '1'], ['Service connectivity', '7'], ['Configurations', '18']],
  columns: [['User / configuration', 'id'], ['Organization', 'loan'], ['Role / type', 'type'], ['Email / endpoint', 'borrower'], ['Scope', 'credit'], ['Last activity', 'posted'], ['State', 'status']],
  rows: [
    { id: 'Jordan Davis', loan: 'Apex Prime Fund', type: 'Lender administrator', borrower: 'jordan.davis@apex.example', credit: 'Organization-wide', posted: '14:22 UTC', status: 'Active', detail: [['User ID', 'USR-00001'], ['Permissions', 'Organization administration, agreements, lifecycle operations'], ['MFA', 'Enabled'], ['Last sign-in', '28 Jan 2026 14:22 UTC']] },
    { id: 'Lender FIX Gateway', loan: 'Apex Prime Fund', type: 'FIX session', borrower: 'LENDER_APEX → BORROWER_FIX', credit: 'FIX 4.4', posted: '14:31 UTC', status: 'Online', detail: [['Session ID', 'FIX-LEND-01'], ['Transport', 'QuickFIX adapter'], ['Kafka bridge', 'Connected'], ['Heartbeat', '30 seconds']] },
    { id: 'Lifecycle Service', loan: 'Apex Prime Fund', type: 'Service', borrower: 'http://localhost:8085', credit: 'Daily risk & settlement', posted: '14:18 UTC', status: 'Healthy', detail: [['Responsibilities', 'Fees, rebates, margin calls, recalls, defaults'], ['Kafka consumer group', 'lifecycle-service'], ['Database', 'PostgreSQL lifecycle schema'], ['Health check', 'UP']] },
  ],
}
