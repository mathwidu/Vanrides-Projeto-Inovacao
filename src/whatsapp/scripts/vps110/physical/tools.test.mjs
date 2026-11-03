import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { createTools, watchMemory } from './tools.mjs';

const sha=body=>crypto.createHash('sha256').update(body).digest('hex');
function fixture() {
  const base=fs.mkdtempSync(path.join(os.tmpdir(),'wa110-unit-'));
  for(const name of ['runtime','session','inputs'])fs.mkdirSync(path.join(base,name),{mode:0o700});
  const write=(name,body)=>fs.writeFileSync(path.join(base,name),body,{mode:0o600});
  const context={attemptId:'fixture',batch:'11111111-1111-4111-8111-111111111111',actor:'22222222-2222-4222-8222-222222222222',instance:'wa070_synthetic_'+'1'.repeat(32)};
  const project='wa110-physical-fixture',nonce='A'.repeat(43),sourceSha='5ddb7c3db6c38385c018028741470ab6a509604f';
  const manifest=JSON.stringify({files:{}}),contract={moduleSha256:sha(manifest),backend:{commit:sourceSha,jarSha256:sha('synthetic jar')},
    images:{java:'java-pinned',postgres:'postgres-pinned'},destination:{projectPrefix:'wa110-physical-'}};
  write('source-manifest.json',manifest);write('contract.json',JSON.stringify(contract));
  write('SHA256SUMS.json',JSON.stringify({'contract.json':sha(JSON.stringify(contract))}));
  write('inputs/backend.jar','synthetic jar');write('runtime/application.properties','synthetic=true\n');
  write('runtime/operator-private.json',JSON.stringify({environment:'wa070-local',operatorCredential:nonce}));
  write('runtime/owner.json',JSON.stringify({attemptId:'fixture',sourceSha,nonce}));
  const rows=['api','postgres'].map(service=>({Config:{Labels:{'com.docker.compose.service':service,'com.docker.compose.project':project,
    'wa110.owner':nonce,'wa110.backend':sourceSha,'traefik.enable':'false'},Image:contract.images[service==='api'?'java':'postgres'],
    User:service==='api'?process.getuid()+':'+process.getgid():'0'},State:{Running:true,OOMKilled:false},RestartCount:0,
    NetworkSettings:{Networks:{[project+'_private']:{},...(service==='api'?{[project+'_edge']:{}}:{})},
      Ports:service==='api'?{'18083/tcp':[{HostIp:'127.0.0.1',HostPort:'28083'}]}:{}},
    HostConfig:{LogConfig:{Type:'none'},PortBindings:service==='api'?{'18083/tcp':[{HostIp:'127.0.0.1',HostPort:'28083'}]}:{}},
    Mounts:Object.entries(service==='api'?{'/opt/wa110/app.jar':['inputs/backend.jar',false],'/run/wa110/application.properties':['runtime/application.properties',false],
      '/run/wa110/session':['session',true]}:{'/run/secrets/db-password':['runtime/db-password',false],'/run/secrets/pgpass':['runtime/pgpass',false]})
      .map(([Destination,[source,RW]])=>({Type:'bind',Destination,Source:path.join(base,source),RW}))}));
  const calls=[];let responseBatch=context.batch;
  const run=(command,args,options)=>{
    assert.equal(command,'docker');calls.push({args,options});
    let stdout='';
    if(args[0]==='ps')stdout='a'.repeat(12)+'\n'+'b'.repeat(12);
    else if(args[0]==='inspect')stdout=JSON.stringify(rows);
    else if(args.includes('psql'))stdout='synthetic-private-result';
    else if(args.includes('java')) {
      const req=args.find(arg=>arg.startsWith('--vanrides.wa.operator.request-file=')).split('=').at(-1);
      const out=args.find(arg=>arg.startsWith('--vanrides.wa.operator.output-file=')).split('=').at(-1);
      const request=JSON.parse(fs.readFileSync(path.join(base,'session',path.basename(req))));
      write('session/'+path.basename(out),JSON.stringify({status:'COMMITTED',operation:request.operation,batchId:responseBatch,environment:'wa070-local'}));
    }
    return {status:0,stdout};
  };
  const tools=createTools(base,context,()=>{},{run});
  return {base,context,tools,calls,rows,run,setForeignBatch:()=>{responseBatch='foreign';},dispose:()=>fs.rmSync(base,{recursive:true})};
}
test('SQL stays in memory even when a legacy caller requests output recording',()=>{
  const f=fixture();try {
    assert.equal(f.tools.sql('SELECT 1;', {recordOutput:true}),'synthetic-private-result');
    assert.deepEqual(fs.readdirSync(path.join(f.base,'session')),[]);
    const call=f.calls.find(row=>row.args.includes('psql'));assert.equal(call.options.stdio[2],'ignore');
    assert.equal(call.args[call.args.indexOf('--user')+1],'0');assert.equal(call.args[call.args.indexOf('-h')+1],'/var/run/postgresql');
  }finally{f.dispose();}
});
test('official CLI uses only the owned container/private files and suppresses process logs',()=>{
  const f=fixture();try {
    f.tools.operate('create-test-cohort',{synthetic:true});
    const call=f.calls.find(row=>row.args.includes('java'));assert.equal(call.options.stdio,'ignore');
    assert(call.args.includes('--spring.main.web-application-type=none'));
    assert(call.args.some(arg=>arg.startsWith('--vanrides.wa.operator.request-file=/run/wa110/session/')));
    assert(!call.args.some(arg=>arg.includes('operatorCredential')));
    assert(!Object.keys(call.options.env).some(key=>key.includes('TOKEN')||key.includes('PASSWORD')));
    assert.throws(()=>f.tools.operate('driver-bootstrap',{identityId:'foreign'}),/DRIVER_SCOPE/);
    assert.throws(()=>f.tools.operate('seed-test-data',{}),/OPERATION_DENIED/);
  }finally{f.dispose();}
});
test('foreign CLI receipt, mutated mount and changed JAR abort before trusting results',()=>{
  const f=fixture();try {
    f.setForeignBatch();assert.throws(()=>f.tools.operate('create-test-cohort',{synthetic:true}),/RESULT_NOT_COMMITTED/);
    f.rows[0].Mounts[0].Source='/foreign.jar';assert.throws(()=>f.tools.sql('SELECT 1;'),/MOUNT_CHANGED/);
    fs.appendFileSync(path.join(f.base,'inputs/backend.jar'),'changed');assert.throws(()=>f.tools.frozen.assertArtifacts(),/JAR_CHANGED/);
  }finally{f.dispose();}
});
test('only API may join the owned edge and ignored/public port publishing is rejected',()=>{
  const f=fixture();try {
    f.tools.frozen.assertContainer();
    f.rows[1].NetworkSettings.Networks['wa110-physical-fixture_edge']={};
    assert.throws(()=>f.tools.frozen.assertContainer(),/NETWORK_CHANGED/);
    delete f.rows[1].NetworkSettings.Networks['wa110-physical-fixture_edge'];
    f.rows[0].NetworkSettings.Ports={};
    assert.throws(()=>f.tools.frozen.assertContainer(),/RUNTIME_EXPOSURE_CHANGED/);
  }finally{f.dispose();}
});
test('Node checks headroom and TTL without reading host-global PSI',()=>{
  const original=fs.readFileSync;
  fs.readFileSync=(file,...args)=>{
    assert.equal(file,'/proc/meminfo');return 'MemAvailable: 11530240 kB\n';
  };
  try {watchMemory();assert.throws(()=>watchMemory(Date.now()-1),/TTL_EXPIRED/);}
  finally{fs.readFileSync=original;}
  fs.readFileSync=()=> 'MemAvailable: 2096128 kB\n';
  try {assert.throws(()=>watchMemory(),/HEADROOM_LOW/);}finally{fs.readFileSync=original;}
});
test('Docker and official CLI consume the same remaining shutdown budget',()=>{
  const f=fixture();try {
    const tools=createTools(f.base,f.context,()=>{},{run:f.run,remainingMs:()=>13.9});
    tools.operate('create-test-cohort',{synthetic:true});
    assert(f.calls.every(call=>call.options.timeout===13));
    const expired=createTools(f.base,f.context,()=>{},{run:f.run,remainingMs:()=>0});
    const before=f.calls.length;
    assert.throws(()=>expired.sql('SELECT 1;'),/SHUTDOWN_BUDGET_EXPIRED/);assert.equal(f.calls.length,before);
  }finally{f.dispose();}
});
