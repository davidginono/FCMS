/* Microfinance view settings; the shared workspace navigation owns report requests. */
(() => {
 'use strict';
 const year=document.querySelector('[data-fs-year]');
 if(year){const from=document.querySelector('input[name="from"]'),through=document.querySelector('input[name="through"]');year.addEventListener('change',()=>{if(!year.value)return;from.value=`${year.value}-01-01`;const end=`${year.value}-12-31`;through.value=end>through.dataset.today?through.dataset.today:end;});for(const field of [from,through])field.addEventListener('change',()=>{year.value='';});}
 const boundControls=new WeakSet();
 function applyViewSettings(root,saveSettings=false){
  const paper=root.querySelector('[data-fs-paper]');
  const comparisonSetting=root.querySelector('[data-fs-comparison-setting]');
  if(comparisonSetting)comparisonSetting.hidden=!paper || !['income-statement','balance-sheet','cash-flow','notes'].includes(paper.dataset.kind);
  const settings=root.querySelector('.fs-view-settings');if(settings)settings.hidden=!paper;
  if(!paper)return;
  const key=`fcms-statement-view:${paper.dataset.kind}`;
  let prefs={};
  try{const stored=JSON.parse(localStorage.getItem(key)||'{}');if(stored&&typeof stored==='object')prefs=stored;}catch(_){}
  const save=()=>{try{localStorage.setItem(key,JSON.stringify(prefs));}catch(_){}};
  for(const [name,selector,allowed] of [['font','[data-fs-font]',['reference','sans','serif','mono']],['density','[data-fs-density]',['compact','comfortable']]]){
   const control=root.querySelector(selector);if(!control)continue;
   if(!saveSettings)control.value=allowed.includes(prefs[name])?prefs[name]:allowed[0];
   paper.dataset[name]=control.value;
   prefs[name]=control.value;
   if(!boundControls.has(control)){boundControls.add(control);control.addEventListener('change',()=>applyViewSettings(root,true));}
  }
  const comparison=root.querySelector('[data-fs-comparison]');
  if(comparison){
   if(!saveSettings)comparison.checked=prefs.comparison!==false;
   paper.dataset.comparison=comparison.checked?'shown':'hidden';prefs.comparison=comparison.checked;
   if(!boundControls.has(comparison)){boundControls.add(comparison);comparison.addEventListener('change',()=>applyViewSettings(root,true));}
  }
  if(saveSettings)save();
 }
 applyViewSettings(document);
 document.addEventListener('workspace:report-navigated',()=>{
  applyViewSettings(document);
  window.refreshEnhancedSelects?.(document.querySelector('.fs-toolbar'));
  window.ResourceTable?.init(document.querySelector('[data-fs-report-section]'));
 });

})();
