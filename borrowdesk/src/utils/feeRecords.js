const num = v => v == null ? null : Number(v);
const byId = rows => new Map(rows.map(r => [String(r.id), r]));
export const feeRecordPath = r => `/fees-rebates/${r.category}/${r.id}`;
export function buildFeeRecords(data) {
  const loans=byId(data.loans), agreements=byId(data.agreements), lenders=byId(data.lenders), securities=byId(data.securities);
  return [['accruals',data.accruals],['fees',data.fees],['rebates',data.rebates]].flatMap(([category,records])=>records.map(raw=>{
    const loan=loans.get(String(raw.loanId)), agreement=agreements.get(String(loan?.mslaId));
    const kind=category==='fees'?'FEE':category==='rebates'?'REBATE':raw.type;
    const date=raw.accrualDate ?? raw.periodEnd ?? raw.paymentDate ?? '';
    const bill=category==='accruals'?(kind==='FEE'?data.fees:data.rebates).find(b=>String(b.loanId)===String(raw.loanId)&&b.periodStart&&b.periodEnd&&date>=b.periodStart&&date<=b.periodEnd):raw;
    return {...raw,key:`${category}:${raw.id}`,category,kind,loan,agreement,
      label:`${category==='accruals'?'ACC':category==='fees'?'FEE':'REB'}-${String(raw.id).padStart(6,'0')}`,
      lender:lenders.get(String(agreement?.lenderId))?.name ?? '—',symbol:securities.get(String(loan?.securityId))?.symbol ?? '—',
      amount:num(category==='fees'?raw.feeAmount:raw.amount),rate:num(raw.rate??raw.feeRate??raw.rebateRate),baseValue:num(raw.baseValue),
      date,currency:raw.currency??'INR',dayBasis:agreement?.dayBasis??null,bill,
      dueDate:bill?.dueDate??bill?.nextPaymentDate??bill?.paymentDate??null,
      status:category==='accruals'?(bill?.status??'RECORDED'):raw.status,
    };
  })).sort((a,b)=>String(b.date).localeCompare(String(a.date))||Number(b.id)-Number(a.id));
}
export function relatedFeeHistory(record, data) {
  return data.accruals.filter(a=>String(a.loanId)===String(record.loanId)&&a.type===record.kind&&
    (!record.periodStart||a.accrualDate>=record.periodStart)&&(!record.periodEnd||a.accrualDate<=record.periodEnd))
    .sort((a,b)=>String(b.accrualDate).localeCompare(String(a.accrualDate)));
}
