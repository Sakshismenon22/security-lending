import { useState } from 'react';
export default function useTablePage(rows, scope) {
  const [state,setState]=useState({scope,page:0,size:10});
  const pages=Math.max(1,Math.ceil(rows.length/state.size));
  const page=state.scope===scope?Math.min(state.page,pages-1):0;
  return {rows:rows.slice(page*state.size,(page+1)*state.size),page,pages,size:state.size,total:rows.length,
    start:rows.length?page*state.size+1:0,end:Math.min((page+1)*state.size,rows.length),
    setPage:value=>setState({...state,scope,page:value}),
    setSize:value=>setState({scope,page:0,size:Number(value)})};
}
