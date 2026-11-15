/* Journal amounts stay decimal strings; the server owns posting and balances. */
(()=>{'use strict';
 const form=document.querySelector('.fcms-form:has([data-journal-lines])');if(!form)return;
 const lines=form.querySelector('[data-journal-lines]'),template=document.getElementById('journal-line-template'),add=form.querySelector('[data-line-add]');let identifier=50;
 const cents=value=>{if(!/^\d{1,12}(?:\.\d{0,2})?$/.test(value))return null;const [whole,fraction='']=value.split('.');return BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0'));};
 const display=value=>{const text=value.toString().padStart(3,'0');return text.slice(0,-2)+'.'+text.slice(-2);};
 function update(){
  const rows=[...lines.children];rows.forEach((row,index)=>{row.querySelector('[data-line-number]').textContent=String(index+1);row.querySelector('[data-line-remove]').disabled=rows.length<=2;row.querySelectorAll('[name]').forEach(input=>{input.name=input.name.replace(/lines\[\d+\]/,'lines['+index+']');});});add.disabled=rows.length>=50;
  for(const type of ['debit','credit']){let sum=0n,valid=true;for(const input of lines.querySelectorAll('[data-journal-'+type+']')){const parsed=cents(input.value||'0');if(parsed===null){valid=false;break;}sum+=parsed;}form.querySelector('[data-'+type+'-total]').textContent=valid?display(sum):'—';}
 }
 add.addEventListener('click',()=>{if(lines.children.length>=50)return;const fragment=template.content.cloneNode(true),index=identifier++;fragment.querySelectorAll('*').forEach(element=>{for(const attr of ['id','for','name'])if(element.hasAttribute(attr))element.setAttribute(attr,element.getAttribute(attr).replaceAll('__index__',String(index)));});const row=fragment.firstElementChild;lines.append(fragment);window.refreshEnhancedSelects?.(row);window.FcmsChoices?.init(row);update();row.querySelector('button')?.focus();});
 lines.addEventListener('click',event=>{const button=event.target.closest('[data-line-remove]');if(!button||lines.children.length<=2)return;button.closest('[data-journal-line]').remove();update();add.focus();});
 lines.addEventListener('input',update);update();
})();
