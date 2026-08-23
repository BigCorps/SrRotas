"use client";
import { useEffect } from "react";
export type SrTheme="auto"|"light"|"dark";
export function applySrTheme(theme:SrTheme){
  const media=window.matchMedia("(prefers-color-scheme: dark)");
  const resolved=theme==="auto"?(media.matches?"dark":"light"):theme;
  document.documentElement.dataset.srTheme=resolved;
  document.documentElement.dataset.srThemeChoice=theme;
  localStorage.setItem("sr_theme",theme);
}
export default function ThemeController(){
 useEffect(()=>{
  let active=true;
  const local=(localStorage.getItem("sr_theme") as SrTheme|null)||"auto";
  applySrTheme(local);
  fetch("/api/v1/preferences",{cache:"no-store"}).then(r=>r.ok?r.json():null).then(data=>{
    if(!active)return; const remote=data?.preferences?.app_theme as SrTheme|undefined;
    if(remote&&["auto","light","dark"].includes(remote))applySrTheme(remote);
  }).catch(()=>undefined);
  const media=window.matchMedia("(prefers-color-scheme: dark)");
  const listener=()=>{if((localStorage.getItem("sr_theme")||"auto")==="auto")applySrTheme("auto");};
  media.addEventListener?.("change",listener);
  const event=()=>applySrTheme((localStorage.getItem("sr_theme") as SrTheme)||"auto");
  window.addEventListener("sr-theme-changed",event);
  return()=>{active=false;media.removeEventListener?.("change",listener);window.removeEventListener("sr-theme-changed",event);};
 },[]);
 return null;
}
