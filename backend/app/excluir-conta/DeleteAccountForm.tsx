"use client";

import { FormEvent, useState } from "react";

export default function DeleteAccountForm() {
  const [email,setEmail]=useState("");
  const [password,setPassword]=useState("");
  const [confirmation,setConfirmation]=useState("");
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState("");
  const [done,setDone]=useState(false);

  async function submit(event:FormEvent){
    event.preventDefault(); setBusy(true); setMessage("Validando e excluindo...");
    try{
      const response=await fetch("/api/v1/account/delete-web",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email,password,confirmation})});
      const data=await response.json().catch(()=>({}));
      if(!response.ok)throw new Error(data?.message||data?.error||"Não foi possível excluir a conta.");
      setDone(true); setPassword(""); setConfirmation(""); setMessage("Conta e dados associados foram excluídos.");
    }catch(error){setMessage(error instanceof Error?error.message:"Não foi possível excluir a conta.")}
    finally{setBusy(false)}
  }

  if(done)return <div style={{padding:18,border:"1px solid #b9ddd7",borderRadius:16,background:"#effaf7"}}><strong>Exclusão concluída.</strong><p>{message}</p></div>;
  return <form onSubmit={submit} style={{display:"grid",gap:12,padding:18,border:"1px solid #d8e5ea",borderRadius:18,background:"#fff",color:"#102a3b"}}>
    <label style={{display:"grid",gap:5}}>E-mail da conta<input type="email" required autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} style={{padding:12,borderRadius:10,border:"1px solid #c8d8df"}}/></label>
    <label style={{display:"grid",gap:5}}>Senha<input type="password" required autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} style={{padding:12,borderRadius:10,border:"1px solid #c8d8df"}}/></label>
    <label style={{display:"grid",gap:5}}>Digite <strong>EXCLUIR</strong> para confirmar<input required value={confirmation} onChange={e=>setConfirmation(e.target.value)} style={{padding:12,borderRadius:10,border:"1px solid #c8d8df"}}/></label>
    <button disabled={busy||confirmation.trim().toUpperCase()!=="EXCLUIR"} style={{padding:13,border:0,borderRadius:12,background:"#b42318",color:"#fff",fontWeight:800,cursor:"pointer",opacity:busy?0.6:1}}>{busy?"Excluindo...":"Excluir minha conta e dados"}</button>
    {message&&<p role="status" style={{margin:0}}>{message}</p>}
  </form>;
}
