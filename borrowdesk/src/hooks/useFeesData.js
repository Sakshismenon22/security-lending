import { useEffect, useState } from 'react';
import { feesRebatesApi } from '../api/feesRebatesApi';
const empty={accruals:[],fees:[],rebates:[],payments:[],feePayments:[],loans:[],agreements:[],lenders:[],securities:[]};
export default function useFeesData() {
  const [state,setState]=useState({data:empty,loading:true,error:''});
  const [revision,setRevision]=useState(0);
  useEffect(()=>{
    const controller=new AbortController();
    let inFlight=false;
    async function load(background=false) {
      if(inFlight)return;
      inFlight=true;
      if(!background)setState(s=>({...s,loading:true,error:''}));
      try {
        const data=await feesRebatesApi.load(controller.signal);
        if(!controller.signal.aborted)setState({data,loading:false,error:''});
      } catch(e) {
        if(!controller.signal.aborted)setState(s=>({...s,loading:false,error:e.message}));
      } finally { inFlight=false; }
    }
    void load();
    const timer=setInterval(()=>{if(!document.hidden)void load(true)},10000);
    const focus=()=>{void load(true)};
    window.addEventListener('focus',focus);
    return()=>{controller.abort();clearInterval(timer);window.removeEventListener('focus',focus)};
  },[revision]);
  return {...state,refresh:()=>setRevision(r=>r+1)};
}
