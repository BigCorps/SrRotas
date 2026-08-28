type Name = "home" | "now" | "history" | "ai" | "mcp" | "profile" | "settings" | "user" | "plan" | "message" | "external";
export default function NavIcon({name}:{name:Name}){const c={width:22,height:22,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:1.9,strokeLinecap:"round" as const,strokeLinejoin:"round" as const,"aria-hidden":true};
 if(name==="home")return <svg {...c}><path d="M3 10.7 12 3l9 7.7"/><path d="M5.5 9.4V21h13V9.4"/><path d="M9.4 21v-6.6h5.2V21"/></svg>;
 if(name==="now")return <svg {...c}><path d="M21 3 9.7 14.3"/><path d="m21 3-7.1 18-4.2-6.7L3 10.1 21 3Z"/></svg>;
 if(name==="history")return <svg {...c}><path d="M3.4 12a8.6 8.6 0 1 0 2.5-6.1"/><path d="M3.4 4.5v5h5"/><path d="M12 7.5V12l3.1 2"/></svg>;
 if(name==="ai")return <svg {...c}><path d="M12 2.8 13.7 8l5.2 1.7-5.2 1.7L12 16.6l-1.7-5.2-5.2-1.7L10.3 8 12 2.8Z"/><path d="m18.5 15 .8 2.4 2.4.8-2.4.8-.8 2.4-.8-2.4-2.4-.8 2.4-.8.8-2.4Z"/></svg>;
 if(name==="mcp")return <svg {...c}><path d="M8 7.5 4.5 11 8 14.5"/><path d="m16 7.5 3.5 3.5-3.5 3.5"/><path d="m13.5 5.5-3 13"/></svg>;
 if(name==="settings")return <svg {...c}><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.03 1.56V21H10v-.09A1.7 1.7 0 0 0 8.94 19.35a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.57 15 1.7 1.7 0 0 0 3 14H3v-4h.09A1.7 1.7 0 0 0 4.65 8.94a1.7 1.7 0 0 0-.34-1.88L4.25 7l2.83-2.83.06.06a1.7 1.7 0 0 0 1.88.34A1.7 1.7 0 0 0 10.05 3H14v.09a1.7 1.7 0 0 0 1.06 1.56 1.7 1.7 0 0 0 1.88-.34l.06-.06L19.83 7l-.06.06a1.7 1.7 0 0 0-.34 1.88A1.7 1.7 0 0 0 21 9.97V14h-.09A1.7 1.7 0 0 0 19.4 15Z"/></svg>;
 if(name==="profile"||name==="user")return <svg {...c}><circle cx="12" cy="8" r="3.4"/><path d="M5 21c.4-4.1 2.8-6.2 7-6.2s6.6 2.1 7 6.2"/></svg>;
 if(name==="plan")return <svg {...c}><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M3 10h18"/><path d="M7 15h3"/></svg>;
 if(name==="message")return <svg {...c}><path d="M4 5.5h16v11H8l-4 3v-14Z"/><path d="M8 9h8"/><path d="M8 13h5"/></svg>;
 return <svg {...c}><path d="M14 5h5v5"/><path d="m19 5-8 8"/><path d="M12 6H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2v-5"/></svg>;
}
