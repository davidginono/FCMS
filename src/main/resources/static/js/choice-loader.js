/* Remote choices share the maintained Microfinance selector and bounded endpoints. */
(()=>{'use strict';
 function init(root=document){root.querySelectorAll('select[data-choice-url]').forEach(select=>{
  if(select.dataset.fcmsChoiceReady)return;const ui=window.NeoSelect?.search(select);if(!ui)return;select.dataset.fcmsChoiceReady='true';
  const blankLabel=select.querySelector('option[value=""]')?.textContent||'';
  let controller,version=0,page=0,opened=false,hasNext=false,retry=false;
  const invalidate=()=>{version++;controller?.abort();};
  async function fetchChoices(reset){
   if(reset)page=0;const serial=++version;controller?.abort();const request=new AbortController();controller=request;let timedOut=false;
   const selected=select.selectedOptions[0],saved=selected?{value:selected.value,label:selected.textContent,data:{...selected.dataset}}:null;
   const query=ui.input.value.trim(),url=new URL(select.dataset.choiceUrl,location.origin);url.searchParams.set('search',query);url.searchParams.set('page',String(page));
   ui.setError?.('');ui.setLoading(true);ui.more.hidden=true;const timeout=setTimeout(()=>{timedOut=true;request.abort();},10000);
   try{
    const response=await fetch(url,{credentials:'same-origin',headers:{Accept:'application/json'},signal:request.signal});if(!response.ok||response.redirected)throw Error('lookup');const result=await response.json();if(serial!==version||!select.isConnected)return;
    const prior=reset?[]:[...select.options].filter(o=>o.value&&!o.hidden).map(o=>({...o.dataset,id:o.value,name:o.textContent})),items=[...prior,...result.items].slice(-100),fragment=document.createDocumentFragment();fragment.append(new Option(blankLabel,'',false,!saved?.value));
    if(saved?.value&&!items.some(item=>String(item.id)===saved.value)){const retained=new Option(saved.label,saved.value,true,true);retained.hidden=true;Object.assign(retained.dataset,saved.data);fragment.append(retained);}
    const seen=new Set();for(const item of items){const id=String(item.id);if(seen.has(id))continue;seen.add(id);const option=new Option(item.name,id,false,id===saved?.value);option.dataset.searchQuery=query.toLowerCase();for(const key of ['depth','classification','normalBalance'])if(item[key]!=null)option.dataset[key]=String(item[key]);fragment.append(option);}select.replaceChildren(fragment);window.refreshEnhancedSelects?.();retry=false;hasNext=Boolean(result.hasNext);ui.more.textContent=document.body.dataset.selectLoadMore;ui.more.hidden=!hasNext;
   }catch(error){if(serial===version&&(error.name!=='AbortError'||timedOut)){retry=true;hasNext=true;ui.more.hidden=false;ui.more.textContent=document.body.dataset.selectRetry;}}
   finally{clearTimeout(timeout);if(serial===version)ui.setLoading(false);}
  }
  window.AppSearch.bind(ui.input,()=>fetchChoices(true),invalidate);ui.more.addEventListener('click',()=>{if(hasNext){if(!retry)page++;void fetchChoices(false);}});
  select.addEventListener('neo-select:open',()=>{if(!opened){opened=true;void fetchChoices(true);}});select.addEventListener('neo-select:close',()=>{invalidate();ui.setLoading(false);opened=false;});window.addEventListener('pagehide',invalidate,{once:true});
 });}
 window.FcmsChoices={init};document.addEventListener('DOMContentLoaded',()=>{
  init();document.querySelectorAll('[data-team-picker]').forEach(picker=>{const choice=picker.querySelector('select'),members=picker.querySelector('[data-team-members]'),type=picker.closest('form').querySelector('[name=workType]'),add=picker.querySelector('[data-team-add]');const sync=()=>{const visible=type.value==='GROUP';picker.hidden=!visible;choice.disabled=!visible;members.querySelectorAll('input[type=checkbox]').forEach(box=>box.disabled=!visible);const others=[...members.querySelectorAll('input:checked')].filter(box=>box.value!==picker.dataset.teamOwner).length;add.disabled=!visible||(others>=19&&choice.value!==picker.dataset.teamOwner);};type.addEventListener('change',sync);picker.addEventListener('change',sync);sync();add.addEventListener('click',()=>{if(!choice.value)return;const existing=[...members.querySelectorAll('input')].find(c=>c.value===choice.value);if(existing){existing.checked=true;existing.dispatchEvent(new Event('change',{bubbles:true}));return;}if(members.querySelectorAll('input:checked').length>=20)return;const label=document.createElement('label'),box=document.createElement('input');box.type='checkbox';box.name='participants';box.value=choice.value;box.checked=true;label.append(box,document.createTextNode(choice.selectedOptions[0].textContent));members.append(label);box.dispatchEvent(new Event('change',{bubbles:true}));box.focus();});});
 });
})();
