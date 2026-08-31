"use client";

import {useEffect,useRef,useState} from "react";
import {applySrTheme,type SrTheme} from "./ThemeController";

const options:Array<{value:SrTheme;label:string}>=[
  {value:"auto",label:"Automático"},
  {value:"light",label:"Claro"},
  {value:"dark",label:"Escuro"},
];

function ThemeGlyph({theme}:{theme:SrTheme}){
  if(theme==="light"){
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="4"/>
        <path d="M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.65 17.65l1.42 1.42M2 12h2M20 12h2M4.93 19.07l1.42-1.42M17.65 6.35l1.42-1.42"/>
      </svg>
    );
  }
  if(theme==="dark"){
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M20.5 14.7A8.2 8.2 0 0 1 9.3 3.5 8.4 8.4 0 1 0 20.5 14.7Z"/>
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="4" width="18" height="13" rx="2"/>
      <path d="M8 21h8M12 17v4"/>
      <path d="M7.5 10.5a4.5 4.5 0 0 1 9 0" className="srThemeAutoArc024"/>
    </svg>
  );
}

export default function ThemeSelector024(){
  const [open,setOpen]=useState(false);
  const [theme,setTheme]=useState<SrTheme>("auto");
  const rootRef=useRef<HTMLDivElement|null>(null);

  useEffect(()=>{
    const read=()=>{
      const current=(
        document.documentElement.dataset.srThemeChoice||
        localStorage.getItem("sr_theme")||
        "auto"
      ) as SrTheme;
      if(["auto","light","dark"].includes(current)) setTheme(current);
    };
    read();

    const observer=new MutationObserver(read);
    observer.observe(document.documentElement,{
      attributes:true,
      attributeFilter:["data-sr-theme-choice"],
    });

    const onPointer=(event:PointerEvent)=>{
      if(!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey=(event:KeyboardEvent)=>{
      if(event.key==="Escape") setOpen(false);
    };
    document.addEventListener("pointerdown",onPointer);
    document.addEventListener("keydown",onKey);
    return()=>{
      observer.disconnect();
      document.removeEventListener("pointerdown",onPointer);
      document.removeEventListener("keydown",onKey);
    };
  },[]);

  function choose(next:SrTheme){
    applySrTheme(next);
    setTheme(next);
    setOpen(false);
    window.dispatchEvent(new Event("sr-theme-changed"));

    // Mesma preferência usada pelo APK. O POST preserva os demais campos
    // porque updatePreferences usa os valores atuais como fallback.
    fetch("/api/v1/preferences",{
      method:"POST",
      headers:{"Content-Type":"application/json"},
      body:JSON.stringify({app_theme:next}),
    }).catch(()=>undefined);
  }

  const label=options.find(option=>option.value===theme)?.label??"Automático";

  return (
    <div className="srThemeSelector024" ref={rootRef}>
      <button
        type="button"
        className="srThemeTrigger024"
        aria-label={`Tema: ${label}`}
        title={`Tema: ${label}`}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={()=>setOpen(value=>!value)}
      >
        <ThemeGlyph theme={theme}/>
      </button>

      {open?(
        <div className="srThemeMenu024" role="menu" aria-label="Selecionar tema">
          <strong>Tema</strong>
          {options.map(option=>(
            <button
              type="button"
              key={option.value}
              role="menuitemradio"
              aria-checked={theme===option.value}
              className={theme===option.value?"active":""}
              onClick={()=>choose(option.value)}
            >
              <span className="srThemeOptionIcon024">
                <ThemeGlyph theme={option.value}/>
              </span>
              <span>{option.label}</span>
              <i aria-hidden="true">{theme===option.value?"✓":""}</i>
            </button>
          ))}
        </div>
      ):null}
    </div>
  );
}
