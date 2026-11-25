 "use client";
import { useEffect, useMemo, useState } from "react";
import "./globals.css";

type Status = "approved" | "rejected" | "pending";
type Ad = { id: string; title: string; description: string; price: number; category: string; images: string[]; status: Status; reason?: string; createdAt: string };
type Form = { title: string; description: string; price: string; category: string; image: string };
const CATEGORIES = ["eletronicos","moveis","veiculos","imoveis","esportes","colecionaveis"];
const STORAGE_KEY = "olx-enterprise-demo-ads-v1";
const SEED: Ad[] = [
 {id:"demo-101",title:"Notebook para trabalho e estudos",description:"Notebook em ótimo estado, ideal para programação, estudos e produtividade.",price:2850,category:"eletronicos",images:[],status:"approved",createdAt:"2026-11-17T12:00:00Z"},
 {id:"demo-102",title:"Bicicleta urbana aro 29",description:"Bicicleta revisada, pronta para pedalar na cidade.",price:1250,category:"esportes",images:[],status:"approved",createdAt:"2026-11-16T12:00:00Z"},
 {id:"demo-103",title:"Sofá retrátil de três lugares",description:"Sofá confortável com tecido cinza e pouco uso.",price:1890,category:"moveis",images:[],status:"pending",createdAt:"2026-11-15T12:00:00Z"},
 {id:"demo-104",title:"Coleção de moedas antigas",description:"Coleção para entusiastas de numismática.",price:850,category:"colecionaveis",images:[],status:"rejected",reason:"prohibited_item",createdAt:"2026-11-14T12:00:00Z"},
 {id:"demo-105",title:"Apartamento com varanda e garagem",description:"Apartamento com dois quartos e localização central.",price:340000,category:"imoveis",images:[],status:"approved",createdAt:"2026-11-13T12:00:00Z"},
 {id:"demo-106",title:"Carro hatch automático completo",description:"Veículo com revisões em dia e documentação regular.",price:58500,category:"veiculos",images:[],status:"approved",createdAt:"2026-11-12T12:00:00Z"}
];
const money = (n:number) => new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL",maximumFractionDigits:2}).format(n);
const statusLabel:Record<Status,string> = {approved:"Aprovado",pending:"Pendente",rejected:"Rejeitado"};
const titleCase = (s:string) => s.charAt(0).toUpperCase()+s.slice(1);
const API = (process.env.NEXT_PUBLIC_API_BASE_URL || "").replace(/\/$/,"");
const isAd = (v:unknown):v is Ad => typeof v==="object" && v!==null && typeof (v as Ad).id==="string" && typeof (v as Ad).title==="string";
function normalizeList(v:unknown):Ad[] {
 if(Array.isArray(v)) return v.filter(isAd);
 if(v && typeof v==="object" && "content" in v && Array.isArray(v.content)) return v.content.filter(isAd);
 if(v && typeof v==="object" && "items" in v && Array.isArray(v.items)) return v.items.filter(isAd);
 throw new Error("Formato de listagem não reconhecido. Ajustaremos o contrato quando a API Java for implementada.");
}
const blank:Form = {title:"",description:"",price:"",category:"eletronicos",image:""};
export default function Home() {
 const [ads,setAds] = useState<Ad[]>(SEED);
 const [ready,setReady] = useState(false);
 const [filter,setFilter] = useState("todos");
 const [query,setQuery] = useState("");
 const [page,setPage] = useState(1);
 const [selected,setSelected] = useState<Ad|null>(null);
 const [showForm,setShowForm] = useState(false);
 const [form,setForm] = useState<Form>(blank);
 const [error,setError] = useState("");
 const [notice,setNotice] = useState("");
 const [busy,setBusy] = useState(false);
 const [apiError,setApiError] = useState("");
 useEffect(()=>{
   if(!API) {
     try { const stored=localStorage.getItem(STORAGE_KEY); if(stored){const parsed:unknown=JSON.parse(stored);if(Array.isArray(parsed))setAds(parsed.filter(isAd));} }
     catch { setNotice("Os dados locais foram reiniciados porque não puderam ser lidos."); }
     setReady(true);return;
   }
   fetch(`${API}/api/ads`,{cache:"no-store"}).then(async r=>{if(!r.ok)throw new Error(`HTTP ${r.status}`);return normalizeList(await r.json());}).then(setAds).catch(e=>setApiError(`Não foi possível consultar a API: ${e instanceof Error?e.message:String(e)}`)).finally(()=>setReady(true));
 },[]);
 useEffect(()=>{if(ready&&!API)localStorage.setItem(STORAGE_KEY,JSON.stringify(ads));},[ads,ready]);
 const filtered=useMemo(()=>ads.filter(a=>(filter==="todos"||a.category===filter)&&`${a.title} ${a.description}`.toLowerCase().includes(query.toLowerCase())),[ads,filter,query]);
 const perPage=6;
 const totalPages=Math.max(1,Math.ceil(filtered.length/perPage));
 const visible=filtered.slice((page-1)*perPage,page*perPage);
 const counts={all:ads.length,approved:ads.filter(a=>a.status==="approved").length,pending:ads.filter(a=>a.status==="pending").length,rejected:ads.filter(a=>a.status==="rejected").length};
 function changeFilter(v:string){setFilter(v);setPage(1);}
 function validate():string {
   const title=form.title.trim();
   if(title.length<10||title.length>100)return "O título deve ter entre 10 e 100 caracteres.";
   if(!form.description.trim())return "Informe uma descrição.";
   if(!Number.isFinite(Number(form.price))||Number(form.price)<=0)return "O preço deve ser maior que zero.";
   if(!CATEGORIES.includes(form.category))return "Selecione uma categoria válida.";
   if(!form.image.trim())return "Informe ao menos uma URL de imagem.";
   try { const u=new URL(form.image);if(!["https:","http:"].includes(u.protocol))throw new Error(); } catch {return "Informe uma URL de imagem HTTP ou HTTPS válida.";}
   return "";
 }
 async function submit(e:React.FormEvent<HTMLFormElement>) {
   e.preventDefault();setError("");setNotice("");
   const problem=validate();if(problem){setError(problem);return;}
   setBusy(true);
   try {
     const payload={title:form.title.trim(),description:form.description.trim(),price:Number(form.price),category:form.category,images:[form.image.trim()]};
     if(API) {
       const response=await fetch(`${API}/api/ads`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)});
       if(!response.ok){const body=await response.text();throw new Error(`HTTP ${response.status}: ${body.slice(0,180)}`);}
       const created:unknown=await response.json();
       if(!isAd(created))throw new Error("Resposta da API incompatível com o contrato esperado.");
       setAds(old=>[created,...old]);
       setNotice("Anúncio enviado à API. Consulte o status retornado pelo backend.");
     } else {
       const created:Ad={...payload,id:`demo-${Date.now()}`,status:"pending",createdAt:new Date().toISOString()};
       setAds(old=>[created,...old]);
       setNotice("Anúncio criado no modo demonstração. Nenhuma moderação real foi executada.");
     }
     setForm(blank);setShowForm(false);setPage(1);setFilter("todos");setQuery("");
   } catch(e){setError(e instanceof Error?e.message:"Falha ao cadastrar anúncio.");}
   finally{setBusy(false);}
 }
 function resetDemo(){if(!API){setAds(SEED);setSelected(null);setNotice("Dados de demonstração restaurados.");setPage(1);}}
 return <div className="shell">
   <aside className="sidebar">
     <div className="brand"><div className="brandmark">O<span>·</span></div><div><strong>OLX Enterprise</strong><small>Engineering playground</small></div></div>
     <div className="sidegroup"><div className="sidetitle">WORKSPACE</div><div className="navitem active">▦ <span>Anúncios</span><b>{counts.all}</b></div><div className="navitem muted">◈ <span>Moderação</span></div><div className="navitem muted">⌁ <span>Integrações</span></div></div>
     <div className="sidebar-bottom"><div className="system-dot"/><div><strong>{API?"API configurada":"Modo demonstração"}</strong><small>{API?"Verifique a conectividade":"Dados salvos neste navegador"}</small></div></div>
   </aside>
   <main className="main">
     <header className="topbar"><div className="crumb">Workspace <span>/</span> Marketplace <span>/</span> Anúncios</div><div className="topright"><span className={`mode ${API?"live":"demo"}`}>{API?"● API real":"● DEMO"}</span><span className="avatar">DEV</span></div></header>
     <div className="content">
       <div className="intro"><div><div className="eyebrow">PAINEL DE CONTROLE</div><h1>Gerenciar anúncios<span className="dot">.</span></h1><p>Acompanhe o ciclo de vida dos anúncios e visualize as mudanças do backend.</p></div><button className="primary" onClick={()=>{setError("");setShowForm(true);}}>＋ Novo anúncio</button></div>
       {!API&&<div className="info"><span>◉</span><div><strong>Ambiente de demonstração</strong><p>Este frontend está pronto, mas a API Java ainda será desenvolvida durante as aulas. As ações usam armazenamento local; status e motivos dos exemplos são ilustrativos.</p></div><button className="textbutton" onClick={resetDemo}>Restaurar exemplos</button></div>}
       {apiError&&<div className="alert">{apiError} <button onClick={()=>location.reload()}>Tentar novamente</button></div>}
       {notice&&<div className="success">{notice}<button onClick={()=>setNotice("")}>×</button></div>}
       <div className="stats">
         <div className="stat"><div className="stat-label">Total de anúncios <span>▤</span></div><div className="stat-value">{counts.all.toString().padStart(2,"0")}</div><div className="stat-foot">Anúncios cadastrados</div></div>
         <div className="stat"><div className="stat-label">Aprovados <span className="green">●</span></div><div className="stat-value">{counts.approved.toString().padStart(2,"0")}</div><div className="stat-foot">Visíveis no marketplace</div></div>
         <div className="stat"><div className="stat-label">Em análise <span className="amber">●</span></div><div className="stat-value">{counts.pending.toString().padStart(2,"0")}</div><div className="stat-foot">Aguardando moderação</div></div>
         <div className="stat"><div className="stat-label">Rejeitados <span className="red">●</span></div><div className="stat-value">{counts.rejected.toString().padStart(2,"0")}</div><div className="stat-foot">Com motivo registrado</div></div>
       </div>
       <section className="panel">
         <div className="panelhead"><div><h2>Todos os anúncios</h2><p>Consulte, filtre e inspecione o status de cada anúncio.</p></div><span className="count">{filtered.length} resultados</span></div>
         <div className="filters"><div className="search"><span>⌕</span><input aria-label="Pesquisar anúncios" placeholder="Buscar por título ou descrição..." value={query} onChange={e=>{setQuery(e.target.value);setPage(1);}}/></div><select aria-label="Filtrar por categoria" value={filter} onChange={e=>changeFilter(e.target.value)}><option value="todos">Todas as categorias</option>{CATEGORIES.map(c=><option key={c} value={c}>{titleCase(c)}</option>)}</select></div>
         <div className="tablewrap"><table><thead><tr><th>ANÚNCIO</th><th>CATEGORIA</th><th>PREÇO</th><th>STATUS</th><th>CRIADO EM</th><th></th></tr></thead><tbody>{visible.map((a,i)=><tr key={a.id}><td><div className="adcell"><div className={`thumbnail thumb-${i%6}`}>{a.images?.[0]?<img src={a.images[0]} alt="" onError={e=>{e.currentTarget.style.display="none";}}/>:"▧"}</div><div><strong>{a.title}</strong><small>#{a.id}</small></div></div></td><td><span className="category">{titleCase(a.category)}</span></td><td className="price">{money(a.price)}</td><td><span className={`status ${a.status}`}>● {statusLabel[a.status]??a.status}</span></td><td className="date">{new Date(a.createdAt).toLocaleDateString("pt-BR")}</td><td><button className="inspect" onClick={()=>setSelected(a)}>Ver detalhes →</button></td></tr>)}</tbody></table>{visible.length===0&&<div className="empty">Nenhum anúncio encontrado para este filtro.</div>}</div>
         <div className="panelbottom"><span>Exibindo {filtered.length===0?0:(page-1)*perPage+1}–{Math.min(page*perPage,filtered.length)} de {filtered.length}</span><div className="pager"><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>← Anterior</button><span>{page} / {totalPages}</span><button disabled={page>=totalPages} onClick={()=>setPage(p=>p+1)}>Próximo →</button></div></div>
       </section>
       <footer>OLX ENTERPRISE LAB <span>•</span> Projeto educacional independente <span>•</span> Java 21 + Spring Boot + Next.js</footer>
     </div>
   </main>
   {selected&&<div className="overlay" onClick={()=>setSelected(null)}><div className="drawer" role="dialog" aria-modal="true" aria-label="Detalhes do anúncio" onClick={e=>e.stopPropagation()}><div className="drawerhead"><span>DETALHES DO ANÚNCIO</span><button onClick={()=>setSelected(null)}>✕</button></div><div className="drawerbody"><div className="detailhero">▧</div><span className={`status ${selected.status}`}>● {statusLabel[selected.status]??selected.status}</span><h2>{selected.title}</h2><p>{selected.description}</p><div className="detailprice">{money(selected.price)}</div><dl><dt>Identificador</dt><dd>{selected.id}</dd><dt>Categoria</dt><dd>{titleCase(selected.category)}</dd><dt>Data de criação</dt><dd>{new Date(selected.createdAt).toLocaleString("pt-BR")}</dd><dt>Imagem</dt><dd>{selected.images?.[0]?<a href={selected.images[0]} target="_blank" rel="noopener noreferrer">Abrir imagem ↗</a>:"Não informada"}</dd></dl>{selected.status==="rejected"&&<div className="rejection"><strong>Motivo da rejeição</strong><p>{selected.reason||"Motivo não informado"}</p></div>}{selected.status==="pending"&&<div className="pendingnote">Aguardando moderação. No modo demo, o status não muda automaticamente.</div>}</div></div></div>}
   {showForm&&<div className="overlay" onClick={()=>setShowForm(false)}><div className="drawer" role="dialog" aria-modal="true" aria-label="Novo anúncio" onClick={e=>e.stopPropagation()}><div className="drawerhead"><span>NOVO ANÚNCIO</span><button onClick={()=>setShowForm(false)}>✕</button></div><form className="drawerbody form" onSubmit={submit}><h2>Publicar um anúncio</h2><p>Preencha os dados para iniciar o fluxo de cadastro.</p><label>Título <small>10 a 100 caracteres</small><input required minLength={10} maxLength={100} value={form.title} onChange={e=>setForm({...form,title:e.target.value})} placeholder="Ex.: Notebook para trabalho e estudos"/></label><label>Descrição<textarea required rows={4} value={form.description} onChange={e=>setForm({...form,description:e.target.value})} placeholder="Descreva seu anúncio..."/></label><div className="formrow"><label>Preço (R$)<input required type="number" min="0.01" step="0.01" value={form.price} onChange={e=>setForm({...form,price:e.target.value})} placeholder="0,00"/></label><label>Categoria<select value={form.category} onChange={e=>setForm({...form,category:e.target.value})}>{CATEGORIES.map(c=><option key={c} value={c}>{titleCase(c)}</option>)}</select></label></div><label>URL da imagem<input required type="url" value={form.image} onChange={e=>setForm({...form,image:e.target.value})} placeholder="https://exemplo.com/imagem.jpg"/></label>{error&&<div className="alert">{error}</div>}<button disabled={busy} className="primary full" type="submit">{busy?"Enviando...":"Criar anúncio"}</button><p className="formnote">{API?"Envia uma requisição para a API configurada.":"No modo demonstração, o anúncio será salvo apenas neste navegador."}</p></form></div></div>}
 </div>;
}
