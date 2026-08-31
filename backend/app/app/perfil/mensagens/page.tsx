"use client";

import { useEffect, useMemo, useState } from "react";
import "./mensagens.css";
import AccountPageHeader from "../../_components/AccountPageHeader";

type Shortcut = {
  id: string;
  order: number;
  shortLabel: string;
  accessibilityLabel: string | null;
  text: string;
  colorToken: string;
  enabled: boolean;
};

const colors = [
  "shortcut01",
  "shortcut02",
  "shortcut03",
  "shortcut04",
  "shortcut05",
  "shortcut06",
];
const MIN_SLOTS = 6;
const MAX_SLOTS = 12;

function blankSlot(index:number):Shortcut{
  return {
    id:`slot-${index+1}`,
    order:index,
    shortLabel:String(index+1),
    accessibilityLabel:null,
    text:"",
    colorToken:colors[index%colors.length],
    enabled:true,
  };
}

function minimumSlots(count=MIN_SLOTS){
  return Array.from(
    {length:Math.max(MIN_SLOTS,Math.min(MAX_SLOTS,count))},
    (_,index)=>blankSlot(index),
  );
}

export default function MensagensPage() {
  const [items, setItems] = useState<Shortcut[]>(minimumSlots());
  const [message, setMessage] = useState("Carregando mensagens...");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetch("/api/v1/messages/presets", { cache: "no-store" })
      .then(async (response) => {
        const json = await response.json();
        if (!response.ok) throw new Error(json?.error || "load_failed");

        if (Array.isArray(json.messages) && json.messages.length) {
          const server = json.messages as Shortcut[];
          const highest = Math.max(
            MIN_SLOTS,
            ...server.map(item=>Number(item.order)+1),
          );
          const merged = minimumSlots(highest);
          server.forEach((item) => {
            const index = Number(item.order);
            if(index>=0&&index<MAX_SLOTS){
              while(merged.length<=index&&merged.length<MAX_SLOTS){
                merged.push(blankSlot(merged.length));
              }
              merged[index]={
                ...item,
                colorToken:colors[index%colors.length],
              };
            }
          });
          setItems(merged);
        }

        setMessage(
          "As mensagens são copiadas pelo Android; nunca são enviadas automaticamente.",
        );
      })
      .catch(() => setMessage("Não foi possível carregar as mensagens."));
  }, []);

  const configured = useMemo(
    () => items.filter((item) => item.text.trim()).length,
    [items],
  );

  function patch(index: number, values: Partial<Shortcut>) {
    setItems((current) =>
      current.map((item, i) =>
        i === index ? { ...item, ...values } : item,
      ),
    );
  }

  function addMessage(){
    setItems(current=>{
      if(current.length>=MAX_SLOTS)return current;
      return [...current,blankSlot(current.length)];
    });
  }

  function removeLast(){
    setItems(current=>
      current.length>MIN_SLOTS
        ?current.slice(0,-1)
        :current,
    );
  }

  async function save() {
    setBusy(true);
    setMessage("Salvando...");
    try {
      const payload = items
        .map((item, index) => ({
          ...item,
          order: index,
          shortLabel: String(index + 1),
          accessibilityLabel:
            item.accessibilityLabel?.trim() ||
            `Copiar mensagem ${index + 1}`,
          text: item.text,
          colorToken: colors[index % colors.length],
        }))
        .filter((item) => item.text.trim());

      const response = await fetch("/api/v1/messages/presets", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: payload }),
      });
      const json = await response.json();
      if (!response.ok) throw new Error(json?.error || "save_failed");
      setMessage(
        "Mensagens salvas. O Android verifica atualizações automaticamente durante a jornada.",
      );
    } catch {
      setMessage("Não foi possível salvar as mensagens.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="sr023Page srAccountSubpage">
      <AccountPageHeader
        title="Mensagens"
        subtitle="Configure de 6 a 12 atalhos. As cores se repetem a cada seis mensagens."
      />

      <section className="msg23Panel">
        <div className="msg23Top">
          <div>
            <strong>
              {configured} configurada(s) · {items.length} atalho(s)
            </strong>
            <small>{message}</small>
          </div>
          <button onClick={save} disabled={busy}>
            {busy ? "Salvando..." : "Salvar mensagens"}
          </button>
        </div>

        <div className="msg23AddRow">
          <button
            type="button"
            className="msg23Add"
            onClick={addMessage}
            disabled={items.length>=MAX_SLOTS}
          >
            + Adicionar mensagem
          </button>
          {items.length>MIN_SLOTS?(
            <button
              type="button"
              className="msg23Remove"
              onClick={removeLast}
            >
              Remover último
            </button>
          ):null}
          <small>Máximo: 12 atalhos.</small>
        </div>

        <div className="msg23Grid">
          {items.map((item, index) => (
            <article className="msg23Card" key={item.id || index}>
              <div className="msg23CardHead">
                <span
                  className={`msg23Number ${colors[index % colors.length]}`}
                >
                  {index + 1}
                </span>
                <div>
                  <strong>Atalho {index + 1}</strong>
                  <small>Aparece na aba lateral da janela flutuante</small>
                </div>
                <label className="msg23Toggle">
                  <input
                    type="checkbox"
                    checked={item.enabled}
                    onChange={(e) =>
                      patch(index, { enabled: e.target.checked })
                    }
                  />
                  <span>Ativo</span>
                </label>
              </div>
              <textarea
                value={item.text}
                maxLength={500}
                placeholder="Digite a mensagem que deseja copiar..."
                onChange={(event) =>
                  patch(index, { text: event.target.value })
                }
              />
              <div className="msg23Counter">{item.text.length}/500</div>
            </article>
          ))}
        </div>

        <div className="msg23Note">
          <strong>Privacidade e segurança</strong>
          <span>
            Os atalhos não controlam Uber, 99 ou outro aplicativo,
            não acessam o chat e não enviam mensagens automaticamente.
          </span>
        </div>
      </section>
    </div>
  );
}
