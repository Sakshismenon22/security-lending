import { useEffect, useState } from 'react';
import { feesRebatesApi } from '../api/feesRebatesApi';
const empty={accruals:[],fees:[],rebates:[],payments:[],feePayments:[],loans:[],agreements:[],lenders:[],securities:[]};
export default function useFeesData() {
  const [state,setState]=useState({data:empty,loading:true,error:''});
  const [revision,setRevision]=useState(0);
  useEffect(()=>{const controller=new AbortController();setState(s=>({...s,loading:true,error:''}));
    feesRebatesApi.load(controller.signal).then(data=>{if(!controller.signal.aborted)setState({data,loading:false,error:''});}).catch(e=>{if(!controller.signal.aborted)setState(s=>({...s,loading:false,error:e.message}));});
    return()=>controller.abort();
  },[revision]);
  return {...state,refresh:()=>setRevision(r=>r+1)};
}
