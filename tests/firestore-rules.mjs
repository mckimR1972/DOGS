// Local emulators only. Uses real signed-in client tokens; admin access only seeds member fixtures.
import assert from 'node:assert/strict';
const project = 'demo-dogs-onboarding';
const root = `projects/${project}/databases/(default)/documents`;
const url = `http://127.0.0.1:8080/v1/${root}`;
const value = v => v === null ? {nullValue:null} : typeof v === 'string' ? {stringValue:v} :
  typeof v === 'boolean' ? {booleanValue:v} : typeof v === 'number' ? (Number.isInteger(v) ? {integerValue:String(v)} : {doubleValue:v}) :
  Array.isArray(v) ? {arrayValue:{values:v.map(value)}} : {mapValue:{fields:fields(v)}};
const fields = obj => Object.fromEntries(Object.entries(obj).map(([k,v])=>[k,value(v)]));
const write = (path, data, times=[]) => ({update:{name:`${root}/${path}`,fields:fields(data)},
  ...(times.length ? {updateTransforms:times.map(fieldPath=>({fieldPath,setToServerValue:'REQUEST_TIME'}))}: {})});
async function req(path, token, method='GET', body) {
  const res=await fetch(url+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined});
  const data=await res.json(); return {status:res.status,data};
}
const commit = (token,writes) => req(':commit',token,'POST',{writes});
async function user(){const r=await fetch(`http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({returnSecureToken:true})});return r.json();}
let n=0;
function ok(r,label){assert.equal(r.status,200,`${label}: ${JSON.stringify(r.data)}`);console.log(`PASS ${++n}: ${label}`);return r.data;}
function denied(r,label){assert.equal(r.status,403,`${label}: ${JSON.stringify(r.data)}`);console.log(`PASS ${++n}: ${label}`);}
const a=await user(), b=await user(), c=await user();
assert(a.idToken && b.idToken && c.idToken);
const gid=`g-${a.localId}`, base=`groups/${gid}`;
const dog={id:'dog-a',group_id:gid,name:'초코',breed:'',birth:null,weight:null,photo:''};
const todo={id:'todo-a',group_id:gid,dog_id:dog.id,title:'급여',repeat_rule:{frequency:'daily',weekdays:[]},due_at:'07:30',created_by:a.localId,note:''};
const profile={id:a.localId,email:'',name:'보호자',profile_img:'',active_group_id:gid,schema_version:2};
ok(await commit(a.idToken,[write(`users/${a.localId}`,profile),write(base,{id:gid,name:'우리집',leader_id:a.localId,invite_code:null,timezone:'Asia/Seoul'},['created_at']),write(`${base}/members/${a.localId}`,{group_id:gid,user_id:a.localId,role:'leader'},['joined_at']),write(`${base}/dogs/${dog.id}`,dog),write(`${base}/todos/${todo.id}`,todo)]),'atomic owner/group/dog/todo bootstrap');
ok(await req('/'+base,a.idToken),'owner reads group');
denied(await req('/'+base,c.idToken),'outsider cannot read group');
denied(await req('/'+base,null),'signed-out cannot read group');
denied(await commit(c.idToken,[write(`${base}/members/${c.localId}`,{group_id:gid,user_id:c.localId,role:'leader'},['joined_at'])]),'outsider cannot self-enroll/escalate');
ok(await commit('owner',[write(`${base}/members/${b.localId}`,{group_id:gid,user_id:b.localId,role:'member'},['joined_at'])]),'seed legitimate member (emulator admin only)');
ok(await req('/'+base,b.idToken),'member reads group');
ok(await commit(b.idToken,[write(`${base}/dogs/${dog.id}`,{...dog,breed:'푸들',weight:4.2})]),'member updates dog profile');
denied(await commit(b.idToken,[write(`${base}/dogs/${dog.id}`,{...dog,group_id:'other'})]),'cross-group dog field rejected');
denied(await commit(b.idToken,[write(`${base}/todos/${todo.id}`,{...todo,title:'unauthorized'})]),'member cannot edit todo');
denied(await commit(b.idToken,[{delete:`${root}/${base}/todos/${todo.id}`}]),'member cannot delete todo');
ok(await commit(a.idToken,[write(`${base}/todos/${todo.id}`,{...todo,title:'저녁 급여'})]),'leader edits todo');
denied(await commit(a.idToken,[write(`${base}/todos/${todo.id}`,{...todo,dog_id:'outside-dog'})]),'invalid/cross-group dog reference rejected');
denied(await commit(a.idToken,[write(`${base}/todos/${todo.id}`,{...todo,repeat_rule:{frequency:'weekly',weekdays:[0,8]}})]),'invalid repeat days rejected');
const log={id:'todo-a_2026-11-06',todo_id:'todo-a',done_by:b.localId,occurrence_day:'2026-11-06'};
ok(await commit(b.idToken,[write(`${base}/todoLogs/${log.id}`,log,['done_at'])]),'member completes occurrence with server time');
denied(await commit(a.idToken,[write(`${base}/todoLogs/${log.id}`,{...log,done_by:a.localId},['done_at'])]),'duplicate completion cannot overwrite original actor');
denied(await commit(c.idToken,[write(`${base}/todoLogs/forged`,{...log,id:'forged'},['done_at'])]),'outsider completion rejected');
ok(await commit(a.idToken,[{delete:`${root}/${base}/todoLogs/${log.id}`}]),'leader can undo completion');
const proposal={id:'proposal-a',todo_id:todo.id,proposer_id:b.localId,payload:{title:'산책'},status:'pending'};
ok(await commit(b.idToken,[write(`${base}/changeRequests/${proposal.id}`,proposal)]),'member submits pending change proposal');
denied(await commit(b.idToken,[write(`${base}/changeRequests/${proposal.id}`,{...proposal,status:'approved'})]),'member cannot approve own proposal');
const request={id:'request-a',group_id:gid,from_user_id:a.localId,to_user_id:b.localId,content:'산책 부탁해',status:'pending'};
ok(await commit(a.idToken,[write(`${base}/requests/${request.id}`,request)]),'group member sends request');
denied(await commit(a.idToken,[write(`${base}/requests/${request.id}`,{...request,status:'accepted'})]),'sender cannot forge recipient acceptance');
ok(await commit(b.idToken,[write(`${base}/requests/${request.id}`,{...request,status:'accepted'})]),'recipient accepts request');
denied(await commit(b.idToken,[write(`${base}/requests/${request.id}`,{...request,status:'rejected'})]),'terminal request cannot be changed again');
denied(await commit(a.idToken,[write(`users/${a.localId}`,{...profile,active_group_id:'foreign'})]),'cannot select non-member group');
const notif={id:'n-a',user_id:a.localId,type:'request',ref_id:'request-a',is_read:false};
ok(await commit('owner',[write(`users/${a.localId}/notifications/n-a`,notif,['created_at'])]),'seed trusted notification');
const nd=ok(await req(`/users/${a.localId}/notifications/n-a`,a.idToken),'own notification readable');
ok(await commit(a.idToken,[{update:{...nd,fields:{...nd.fields,is_read:{booleanValue:true}}},updateMask:{fieldPaths:['is_read']}}]),'own notification may be marked read');
denied(await req(`/users/${a.localId}/notifications/n-a`,b.idToken),'others cannot read private notifications');
denied(await commit(a.idToken,[write(`${base}/photos/photo-a`,{id:'photo-a',group_id:gid,dog_id:dog.id,uploader_id:a.localId,url:'https://example.com',storage_path:'x',status:'ready'},['created_at'])]),'photo publishing disabled until storage workflow exists');
ok(await commit('owner',[{delete:`${root}/${base}/members/${b.localId}`}]),'remove fixture member');
denied(await req('/'+base,b.idToken),'departed member immediately loses group access');
denied(await req(`/users/${a.localId}/settings/onboarding`,a.idToken),'unlisted settings path cannot be read');
denied(await commit(a.idToken,[write(`users/${a.localId}/settings/onboarding`,{nickname:'x'})]),'unlisted settings path cannot be written');
console.log(`Passed ${n} rules assertions; local demo project only.`);
