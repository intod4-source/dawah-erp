(function(){
'use strict';

const V8_WORKSPACES={
 command:{title:'Executive Command Center',short:'Command Center',modules:['dashboard']},
 strategy:{title:'Strategy & Performance',short:'Strategy & Performance',modules:['countryPlanning','targets','monthlyPerformance','performance','reports','projects','thinktank']},
 network:{title:'Institutions & Academic Network',short:'Academic Network',modules:['institutions','universities','researchInstitutes']},
 people:{title:'Students, Teachers & Academic Talent',short:'People & Talent',modules:['students','internationalStudents','postgraduate','teachers','faculty','researchers']},
 engagement:{title:'Outreach & Engagement',short:'Outreach & Engagement',modules:['activities','visits','trainings','studentAssociations','mosques','professionals','campaigns']},
 operations:{title:'Operations, Tasks & Follow-up',short:'Tasks & Follow-up',modules:['departmentWork','actionOrders','taskManagement','whatsappFollowup','meetingFollowups']},
 governance:{title:'Governance, Approvals & Records',short:'Governance & Records',modules:['approvals','documents','evidence','invitations']},
 leadership:{title:'Leadership & Responsible Network',short:'Leadership Network',modules:['volunteers','offices','countryMapping','shura','nigrani','emailDirectory']},
 digital:{title:'Digital & Communications',short:'Digital & Communications',modules:['social','socialCalendar','links']},
 finance:{title:'Finance & Support',short:'Finance & Support',modules:['donations','budgetExpenses']},
 system:{title:'System Administration',short:'Administration',modules:['admin','about']}
};
const V8_GROUPS=[
 {title:'Executive Management',workspaces:['command','strategy']},
 {title:'Field Network',workspaces:['network','people','engagement']},
 {title:'Operations & Governance',workspaces:['operations','governance','leadership']},
 {title:'Resources & System',workspaces:['digital','finance','system']}
];

function v8Module(key){return (state.modules||[]).find(m=>m.key===key);}
function v8VisibleModule(key){let m=v8Module(key);return !!(m&&canSee(m));}
function v8WorkspaceFor(key){for(const [wk,w] of Object.entries(V8_WORKSPACES)){if(w.modules.includes(key))return {key:wk,...w};}return null;}
function v8WorkspaceVisible(w){return w.modules.some(v8VisibleModule);}
function v8StatusClosed(v){return /^(completed|closed|done|inactive|cancelled|canceled|approved)$/i.test(String(v||'').trim());}
function v8Num(v){if(typeof v==='number')return Number.isFinite(v)?v:0;let n=Number(String(v??'').replace(/,/g,'').replace(/[^0-9.\-]/g,''));return Number.isFinite(n)?n:0;}
function v8FmtNum(n){n=v8Num(n);return Math.abs(n)>=1000000?(n/1000000).toFixed(n>=10000000?1:2).replace(/\.0+$/,'')+'M':Math.abs(n)>=1000?(n/1000).toFixed(n>=10000?1:2).replace(/\.0+$/,'')+'K':Math.round(n).toLocaleString();}
function v8Pct(v){return Math.max(0,Math.min(100,Number(v)||0));}
function v8Schema(key){if(key==='trainings')return trainingSchema;if(key==='donations')return donationSchema;if(key==='links')return linkSchema;if(key==='social')return socialSchema;return schemaOr(key);}
function v8Col(key,names){let cols=v8Schema(key);for(const name of names){let i=cols.indexOf(name);if(i>=0)return i;}return -1;}
function v8CountrySet(){let c=state.filters?.country||'All',cont=state.filters?.continent||'All';if(c!=='All')return new Set([c]);if(cont!=='All'){let ci=countrySchema.indexOf('Continent'),ni=countrySchema.indexOf('Country');return new Set((state.data.masterCountries||[]).filter(r=>String(r[ci])===cont).map(r=>String(r[ni])).filter(Boolean));}return null;}
function v8Rows(key){let rows=(state.data[key]||[]);let set=v8CountrySet();if(!set)return rows;let cols=v8Schema(key);let candidates=['Country','Country / Group','Country / Scope','Countries','Country / Region'];let i=-1;for(const c of candidates){i=cols.indexOf(c);if(i>=0)break;}if(i<0)return rows;return rows.filter(r=>{let value=String(r[i]||'');for(const c of set){if(value===c||value.split(/[,;/|]/).map(x=>x.trim()).includes(c))return true;}return false;});}
function v8MasterRows(){let rows=state.data.masterCountries||[],set=v8CountrySet();if(!set)return rows;let i=countrySchema.indexOf('Country');return rows.filter(r=>set.has(String(r[i])));}
function v8Date(v){if(!v)return null;let d=new Date(String(v).length<=10?String(v)+'T00:00:00':v);return isNaN(d)?null:d;}
function v8Days(date){let d=v8Date(date);if(!d)return null;let now=new Date();now.setHours(0,0,0,0);return Math.ceil((d-now)/86400000);}
function v8IsOverdue(date,status){let days=v8Days(date);return days!==null&&days<0&&!v8StatusClosed(status);}
function v8IsDueSoon(date,status,days=14){let d=v8Days(date);return d!==null&&d>=0&&d<=days&&!v8StatusClosed(status);}
function v8NonEmpty(v){let s=String(v??'').trim();return !!s&&!/^(0|#n\/a|n\/a|null|undefined)$/i.test(s);}
function v8Sum(rows,key,col){let i=v8Col(key,[col]);return i<0?0:rows.reduce((a,r)=>a+v8Num(r[i]),0);}
function v8CountOpen(rows,key,statusNames=['Status']){let i=v8Col(key,statusNames);return i<0?rows.length:rows.filter(r=>!v8StatusClosed(r[i])).length;}
function v8ScopeLabel(){let c=state.filters?.country||'All',cont=state.filters?.continent||'All';if(c!=='All')return c;if(cont!=='All')return cont;return 'Worldwide';}
function v8Today(){return new Date().toLocaleDateString(undefined,{day:'2-digit',month:'short',year:'numeric'});}
function v8Escape(s){return String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));}

function v8ShowWorkspace(key){let w=V8_WORKSPACES[key];if(!w)return;let target=w.modules.find(v8VisibleModule);if(!target)return;current=target;renderSections();show(target);}
function v8OpenModule(key){if(!v8VisibleModule(key))return;current=key;renderSections();show(key);}
window.v8ShowWorkspace=v8ShowWorkspace;
window.v8OpenModule=v8OpenModule;

renderNav=function(){
 let html='';
 for(const group of V8_GROUPS){
   let buttons='';
   for(const wk of group.workspaces){
     let w=V8_WORKSPACES[wk];if(!v8WorkspaceVisible(w))continue;
     let active=w.modules.includes(current);
     buttons+=`<button class="workspaceBtn ${active?'active':''}" onclick="v8ShowWorkspace('${wk}')"><span class="workspaceMark"></span><span>${v8Escape(w.short)}</span></button>`;
   }
   if(buttons)html+=`<div class="execNavGroup"><div class="execNavLabel">${v8Escape(group.title)}</div>${buttons}</div>`;
 }
 document.getElementById('nav').innerHTML=html;
};

function v8WorkspaceTabs(id){
 let w=v8WorkspaceFor(id);if(!w||w.modules.length<2)return '';
 let tabs=w.modules.filter(v8VisibleModule).map(k=>{let m=v8Module(k);return `<button class="workspaceTab ${k===id?'active':''}" onclick="v8OpenModule('${k}')">${v8Escape(m?.title||k)}</button>`;}).join('');
 return `<div class="workspaceHeader"><div><div class="workspaceEyebrow">WORKSPACE</div><h3>${v8Escape(w.title)}</h3></div><div class="workspaceTabs">${tabs}</div></div>`;
}
const v8BasePage=page;
page=function(id){let body=v8BasePage(id);if(id==='dashboard')return body;return v8WorkspaceTabs(id)+body;};

function v8Kpi(label,value,meta,tone='green'){
 return `<div class="execKpi ${tone}"><div class="execKpiTop"><span>${v8Escape(label)}</span><span class="kpiDot"></span></div><div class="execKpiValue">${v8Escape(value)}</div><div class="execKpiMeta">${v8Escape(meta||'')}</div></div>`;
}
function v8MiniMetric(label,value,sub){return `<div class="miniMetric"><div class="miniMetricLabel">${v8Escape(label)}</div><div class="miniMetricValue">${v8Escape(value)}</div><div class="miniMetricSub">${v8Escape(sub||'')}</div></div>`;}
function v8Progress(label,value,meta){let p=v8Pct(value);return `<div class="execProgressRow"><div class="execProgressHead"><span>${v8Escape(label)}</span><strong>${Math.round(p)}%</strong></div><div class="execProgressTrack"><div class="execProgressFill" style="width:${p}%"></div></div><div class="execProgressMeta">${v8Escape(meta||'')}</div></div>`;}
function v8FilterPanel(){
 let cs=countries().map(c=>`<option ${state.filters.country===c?'selected':''}>${v8Escape(c)}</option>`).join('');
 let cont=continents().map(c=>`<option ${state.filters.continent===c?'selected':''}>${v8Escape(c)}</option>`).join('');
 let months=['All','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
 return `<div class="execFilterBar"><div class="execFilterTitle"><span>Management Scope</span><small>All dashboard figures update to this scope.</small></div><div class="execFilter"><label>Country</label><select onchange="state.filters.country=this.value;save();renderSections();show('dashboard')">${cs}</select></div><div class="execFilter"><label>Continent</label><select onchange="state.filters.continent=this.value;save();renderSections();show('dashboard')">${cont}</select></div><div class="execFilter"><label>Month</label><select onchange="state.filters.month=this.value;save();renderSections();show('dashboard')">${months.map(x=>`<option ${state.filters.month===x?'selected':''}>${x}</option>`).join('')}</select></div><div class="execFilter year"><label>Year</label><input value="${v8Escape(state.filters.year||new Date().getFullYear())}" onchange="state.filters.year=this.value;save();renderSections();show('dashboard')"></div></div>`;
}
function v8AttentionItems(){
 let out=[];
 const add=(key,titleCol,dueCol,statusCol,type,ownerCol,priorityCol)=>{
   let rows=v8Rows(key),ti=v8Col(key,[titleCol]),di=v8Col(key,[dueCol]),si=v8Col(key,[statusCol]),oi=ownerCol?v8Col(key,[ownerCol]):-1,pi=priorityCol?v8Col(key,[priorityCol]):-1;
   rows.forEach(r=>{let due=di>=0?r[di]:'',status=si>=0?r[si]:'';if(!v8IsOverdue(due,status)&&!v8IsDueSoon(due,status,14))return;let days=v8Days(due);out.push({type,title:ti>=0?r[ti]:type,due:String(due||''),owner:oi>=0?r[oi]:'',priority:pi>=0?r[pi]:'',days,overdue:days<0});});
 };
 add('actionOrders','Order / Instruction','Deadline','Status','Action Order','Responsible Person','Priority');
 add('taskManagement','Task Title','Due Date','Status','Task','Assigned To','Priority');
 add('projects','Title','Deadline','Status','Project','Responsible Person','');
 add('countryPlanning','Strategic Objective','Deadline','Status','Country Plan','Responsible Person','');
 add('visits','Institution','Next Follow-up','Status','Visit Follow-up','Assigned Person','');
 add('whatsappFollowup','Contact / Group','Next Follow-up Date','Status','WhatsApp Follow-up','Assigned Person','');
 add('meetingFollowups','Meeting Title','Next Reminder Date','Status','Meeting Reminder','','');
 out.sort((a,b)=>{if(a.overdue!==b.overdue)return a.overdue?-1:1;return (a.days??999)-(b.days??999);});return out;
}
function v8AttentionTable(items){
 if(!items.length)return `<div class="emptyExecutive"><b>No critical deadlines in the next 14 days.</b><span>Operational follow-ups will appear here automatically.</span></div>`;
 return `<div class="execTableWrap"><table class="execTable"><thead><tr><th>Priority Item</th><th>Type</th><th>Owner</th><th>Due</th><th>Attention</th></tr></thead><tbody>${items.slice(0,8).map(x=>`<tr><td><b>${v8Escape(x.title||'Untitled')}</b>${x.priority?`<span class="subline">${v8Escape(x.priority)} priority</span>`:''}</td><td>${v8Escape(x.type)}</td><td>${v8Escape(x.owner||'Not assigned')}</td><td>${v8Escape(x.due||'—')}</td><td><span class="attentionBadge ${x.overdue?'critical':'soon'}">${x.overdue?Math.abs(x.days)+'d overdue':x.days===0?'Due today':x.days+'d left'}</span></td></tr>`).join('')}</tbody></table></div>`;
}
function v8MonthlyPerformance(){
 let rows=v8Rows('monthlyPerformance');let cols=v8Schema('monthlyPerformance');let mi=cols.indexOf('Month'),yi=cols.indexOf('Year');
 let y=String(state.filters.year||'').trim(),m=String(state.filters.month||'All');
 if(y)rows=rows.filter(r=>!r[yi]||String(r[yi])===y);if(m!=='All')rows=rows.filter(r=>String(r[mi])===m);
 const fields=['Institutions Visited','Universities Connected','Students Connected','Teachers Connected','Associations Connected','Mosques / Centers Connected','Trainings Held','Volunteers Active','New Contacts','Pending Follow-ups'];
 let totals={};for(const f of fields){let i=cols.indexOf(f);totals[f]=i<0?0:rows.reduce((a,r)=>a+v8Num(r[i]),0);}
 return {rows,totals};
}
function v8FinanceSummary(){
 let rows=v8Rows('budgetExpenses'),cols=v8Schema('budgetExpenses'),bi=cols.indexOf('Budget Amount'),ei=cols.indexOf('Expense Amount'),ci=cols.indexOf('Currency');let groups={};
 rows.forEach(r=>{let c=String(r[ci]||'Unspecified');if(!groups[c])groups[c]={budget:0,expense:0,count:0};groups[c].budget+=v8Num(r[bi]);groups[c].expense+=v8Num(r[ei]);groups[c].count++;});
 return Object.entries(groups).map(([currency,x])=>({currency,...x,util:x.budget?x.expense/x.budget*100:0}));
}
function v8CountryBrief(row){
 if(!row)return '';
 let get=n=>row[countrySchema.indexOf(n)];
 let stats=[['Population',get('Total Population')],['Muslim Population',get('Total Muslim Population')],['Universities',get('Total Universities')],['Students',get('Total Students')],['Muslim Students',get('Total Muslim Students')],['Professionals',get('Total Muslim Professionals')],['Faculty',get('Estimated University Faculty')],['Researchers',get('Estimated Researchers')]];
 return `<div class="execSection countryBrief"><div class="execSectionHead"><div><span class="sectionKicker">COUNTRY BRIEF</span><h3>${v8Escape(get('Country'))} Strategic Snapshot</h3></div><span class="statusChip">${v8Escape(get('DI Level')||'DI Level not set')}</span></div><div class="countryMeta"><span><b>Country Nigran:</b> ${v8Escape(get('Country Nigran')||'Not assigned')}</span><span><b>Region:</b> ${v8Escape(get('DWTI Region')||get('Continent')||'—')}</span><span><b>Desk / Office:</b> ${v8Escape(get('Desk / Office')||'—')}</span><span><b>Travelled:</b> ${v8Escape(get('Travelled')||'—')}</span></div><div class="countryStatGrid">${stats.map(([l,v])=>v8MiniMetric(l,v8FmtNum(v),'Master country data')).join('')}</div>${get('2024 Target')?`<div class="countryObjective"><b>Strategic objective:</b> ${v8Escape(get('2024 Target'))}</div>`:''}</div>`;
}

dashboard=function(){
 let masters=v8MasterRows();
 let miNigran=countrySchema.indexOf('Country Nigran'),miCont=countrySchema.indexOf('Continent');
 let led=masters.filter(r=>v8NonEmpty(r[miNigran])).length;
 let institutions=v8Rows('institutions'),universities=v8Rows('universities'),researchInst=v8Rows('researchInstitutes');
 let students=v8Rows('students'),intl=v8Rows('internationalStudents'),post=v8Rows('postgraduate'),teachers=v8Rows('teachers'),faculty=v8Rows('faculty'),researchers=v8Rows('researchers');
 let volunteers=v8Rows('volunteers'),vi=v8Col('volunteers',['Status']),activeVol=volunteers.filter(r=>vi<0||!v8StatusClosed(r[vi])).length;
 let activities=v8Rows('activities'),visits=v8Rows('visits'),trainings=v8Rows('trainings'),campaigns=v8Rows('campaigns');
 let targets=v8Rows('targets'),assigned=v8Sum(targets,'targets','Assigned'),achieved=v8Sum(targets,'targets','Achieved'),targetRate=assigned?achieved/assigned*100:0;
 let plans=v8Rows('countryPlanning'),progI=v8Col('countryPlanning',['Progress %']),planAvg=plans.length?plans.reduce((a,r)=>a+v8Num(r[progI]),0)/plans.length:0;
 let attention=v8AttentionItems(),overdue=attention.filter(x=>x.overdue).length;
 let approvals=v8Rows('approvals'),pendingApprovals=v8CountOpen(approvals,'approvals');
 let followups=v8CountOpen(v8Rows('whatsappFollowup'),'whatsappFollowup')+v8CountOpen(v8Rows('meetingFollowups'),'meetingFollowups')+v8CountOpen(v8Rows('visits'),'visits');
 let mp=v8MonthlyPerformance(),fin=v8FinanceSummary();
 let participants=v8Sum(activities,'activities','Participants')+v8Sum(trainings,'trainings','Participants');
 let peopleTotal=students.length+intl.length+post.length+teachers.length+faculty.length+researchers.length;
 let academicTotal=institutions.length+universities.length+researchInst.length;
 let leaderPct=masters.length?led/masters.length*100:0;
 let row=currentCountryRow();
 let conts={};masters.forEach(r=>{let c=String(r[miCont]||'Unspecified');if(!conts[c])conts[c]={total:0,led:0};conts[c].total++;if(v8NonEmpty(r[miNigran]))conts[c].led++;});
 let riskI=v8Col('countryPlanning',['Risk / Challenge']),statusI=v8Col('countryPlanning',['Status']);let plansAtRisk=plans.filter(r=>riskI>=0&&v8NonEmpty(r[riskI])&&(statusI<0||!v8StatusClosed(r[statusI]))).length;
 let highAction=v8Rows('actionOrders'),prioI=v8Col('actionOrders',['Priority']),aoStatus=v8Col('actionOrders',['Status']);let highOpen=highAction.filter(r=>/high|urgent|critical/i.test(String(r[prioI]||''))&&(aoStatus<0||!v8StatusClosed(r[aoStatus]))).length;
 let totalBudgetRecords=v8Rows('budgetExpenses').length;
 return `
 <div class="execHero"><div><span class="execEyebrow">EXECUTIVE COMMAND CENTER</span><h3>Students & Teachers Dawah — Management Overview</h3><p>Live operational view of global coverage, academic network, people engagement, performance, follow-up and management attention.</p></div><div class="execHeroRight"><span class="livePill ${sharedReady?'online':'offline'}"><i></i>${sharedReady?'Shared data connected':'Offline backup mode'}</span><div class="heroScope">${v8Escape(v8ScopeLabel())}<small>${v8Escape(v8Today())}</small></div></div></div>
 ${v8FilterPanel()}
 <div class="execKpiGrid">
 ${v8Kpi('Countries in Scope',v8FmtNum(masters.length),led+' with country leadership','green')}
 ${v8Kpi('Academic Network',v8FmtNum(academicTotal),institutions.length+' institutions • '+universities.length+' universities','blue')}
 ${v8Kpi('People Records',v8FmtNum(peopleTotal),students.length+' students • '+teachers.length+' teachers','teal')}
 ${v8Kpi('Active Representatives',v8FmtNum(activeVol),volunteers.length+' total representative records','purple')}
 ${v8Kpi('Outreach Engagements',v8FmtNum(activities.length+visits.length+trainings.length+campaigns.length),v8FmtNum(participants)+' recorded participants','blue')}
 ${v8Kpi('Target Achievement',Math.round(targetRate)+'%',v8FmtNum(achieved)+' achieved of '+v8FmtNum(assigned),'green')}
 ${v8Kpi('Open Follow-ups',v8FmtNum(followups),pendingApprovals+' approvals pending','amber')}
 ${v8Kpi('Critical Overdues',v8FmtNum(overdue),attention.length+' due / upcoming items',overdue?'red':'green')}
 </div>
 <div class="execGridTwo">
   <div class="execSection"><div class="execSectionHead"><div><span class="sectionKicker">MANAGEMENT ATTENTION</span><h3>Priority actions & deadlines</h3></div><span class="sectionCount">${attention.length}</span></div>${v8AttentionTable(attention)}</div>
   <div class="execSection"><div class="execSectionHead"><div><span class="sectionKicker">STRATEGIC HEALTH</span><h3>Coverage & execution</h3></div></div>
      ${v8Progress('Country leadership coverage',leaderPct,led+' of '+masters.length+' countries have a Country Nigran')}
      ${v8Progress('Strategic plan progress',planAvg,plans.length+' country planning records in scope')}
      ${v8Progress('Target achievement',targetRate,v8FmtNum(achieved)+' achieved / '+v8FmtNum(assigned)+' assigned')}
      <div class="attentionTiles">${v8MiniMetric('Plans at Risk',plansAtRisk,'Open plans with recorded risk')}${v8MiniMetric('High Priority Orders',highOpen,'Open high/urgent action orders')}${v8MiniMetric('Pending Approvals',pendingApprovals,'Awaiting management decision')}</div>
   </div>
 </div>
 <div class="execGridThree">
  <div class="execSection"><div class="execSectionHead"><div><span class="sectionKicker">MONTHLY PERFORMANCE</span><h3>${v8Escape(state.filters.month==='All'?'Selected period':state.filters.month)} ${v8Escape(state.filters.year||'')}</h3></div><span class="sectionCount">${mp.rows.length} submissions</span></div><div class="performanceGrid">${v8MiniMetric('Institutions Visited',v8FmtNum(mp.totals['Institutions Visited']),'reported')}${v8MiniMetric('Students Connected',v8FmtNum(mp.totals['Students Connected']),'reported')}${v8MiniMetric('Teachers Connected',v8FmtNum(mp.totals['Teachers Connected']),'reported')}${v8MiniMetric('New Contacts',v8FmtNum(mp.totals['New Contacts']),'reported')}${v8MiniMetric('Trainings Held',v8FmtNum(mp.totals['Trainings Held']),'reported')}${v8MiniMetric('Pending Follow-ups',v8FmtNum(mp.totals['Pending Follow-ups']),'reported')}</div></div>
  <div class="execSection"><div class="execSectionHead"><div><span class="sectionKicker">FIELD REACH</span><h3>Engagement footprint</h3></div></div><div class="performanceGrid">${v8MiniMetric('Activities',v8FmtNum(activities.length),v8FmtNum(v8Sum(activities,'activities','Participants'))+' participants')}${v8MiniMetric('Visits',v8FmtNum(visits.length),'institution follow-ups')}${v8MiniMetric('Trainings',v8FmtNum(trainings.length),v8FmtNum(v8Sum(trainings,'trainings','Participants'))+' participants')}${v8MiniMetric('Associations',v8FmtNum(v8Rows('studentAssociations').length),'student societies')}${v8MiniMetric('Mosques / Centers',v8FmtNum(v8Rows('mosques').length),'community links')}${v8MiniMetric('Professional Contacts',v8FmtNum(v8Rows('professionals').length),'outreach records')}</div></div>
  <div class="execSection"><div class="execSectionHead"><div><span class="sectionKicker">CONTROL PANEL</span><h3>Operational pipeline</h3></div></div><div class="pipelineList"><div><span>Open Action Orders</span><b>${v8CountOpen(v8Rows('actionOrders'),'actionOrders')}</b></div><div><span>Open Tasks</span><b>${v8CountOpen(v8Rows('taskManagement'),'taskManagement')}</b></div><div><span>Active Projects</span><b>${v8CountOpen(v8Rows('projects'),'projects')}</b></div><div><span>Documents / Evidence</span><b>${v8Rows('documents').length+v8Rows('evidence').length}</b></div><div><span>Budget / Expense Records</span><b>${totalBudgetRecords}</b></div><div><span>Email / Nigran Contacts</span><b>${v8Rows('emailDirectory').length}</b></div></div></div>
 </div>
 <div class="execGridTwo">
  <div class="execSection"><div class="execSectionHead"><div><span class="sectionKicker">GLOBAL COVERAGE</span><h3>Leadership coverage by continent</h3></div></div><div class="continentList">${Object.entries(conts).sort((a,b)=>b[1].total-a[1].total).map(([c,x])=>v8Progress(c,x.total?x.led/x.total*100:0,x.led+' of '+x.total+' countries assigned')).join('')||'<div class="emptyExecutive">No country master data in this scope.</div>'}</div></div>
  <div class="execSection"><div class="execSectionHead"><div><span class="sectionKicker">FINANCE & SUPPORT</span><h3>Budget utilization by currency</h3></div></div>${fin.length?`<div class="financeList">${fin.map(x=>`<div class="financeRow"><div><b>${v8Escape(x.currency)}</b><span>${x.count} records</span></div><div><strong>${v8FmtNum(x.expense)} / ${v8FmtNum(x.budget)}</strong><span>${Math.round(x.util)}% utilized</span></div></div>`).join('')}</div>`:'<div class="emptyExecutive"><b>No budget records in this scope.</b><span>Add budget and expense entries to see utilization here.</span></div>'}</div>
 </div>
 ${v8CountryBrief(row)}
 `;
};

const v8OldShow=show;
show=function(id){v8OldShow(id);let w=v8WorkspaceFor(current);let t=document.getElementById('pageTitle');if(t)t.textContent=current==='dashboard'?'Executive Dashboard':(w?.title||v8Module(current)?.title||'Dawah ERP');renderNav();v8DecorateTopbar();};
const v8OldRender=render;
render=function(){v8OldRender();v8DecorateTopbar();};
function v8DecorateTopbar(){let actions=document.querySelector('.top-actions');if(!actions)return;actions.querySelectorAll('.v8TopMeta').forEach(x=>x.remove());let u=state.currentUser;let meta=document.createElement('div');meta.className='v8TopMeta';meta.innerHTML=`<span class="syncDot ${sharedReady?'ok':'off'}"></span><span><b>${v8Escape(u?.[1]||'User')}</b><small>${sharedReady?'Live shared workspace':'Offline backup'}</small></span>`;actions.insertBefore(meta,actions.firstChild);}

document.querySelectorAll('.top-actions .btn.secondary,.top-actions label.btn.secondary').forEach(el=>el.classList.add('v8HiddenTopAction'));

const v8OldAdminContent=adminContent;
adminContent=function(){if(adminTab==='designMode')adminTab='overview';return v8OldAdminContent();};
adminPage=function(){let tabs=['overview','masterUpload','easyUpload','templateCenter','emailSetup','modules','fields','categories','users','permissions','formats','settings','audit'];return `<div class="card"><h3>Administration & Data Control</h3><div class="notice"><b>Shared ERP control center:</b> manage data structures, users, imports, templates, email contacts, backups and audit logs. Visual mode buttons have been removed from the operational navigation.</div><div class="tabs">${tabs.map(t=>`<button class="tab ${adminTab===t?'active':''}" onclick="adminTab='${t}';renderSections();show('admin')">${title(t)}</button>`).join('')}</div>${adminContent()}</div>`;};

state.settings=state.settings||{};state.settings.uiMode='modern';state.settings.navSearch='';
setTimeout(()=>{try{render();}catch(e){console.warn('Executive UI refresh deferred',e);}},50);
})();
