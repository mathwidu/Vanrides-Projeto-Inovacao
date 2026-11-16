"""Fixtures exclusivamente sintéticas e cópias de controle do ensaio de recuperação."""
import copy
import importlib.util
import json
import secrets
import uuid
from pathlib import Path

from rehearse_migration import Blocked, encode, read, require, sha, write_new

ROOT = Path(__file__).resolve().parents[2]
N8N = 'docker.n8n.io/n8nio/n8n@sha256:80e23ced901f0e22597d4f3464e3aaf9d835372262ea6f08ef760d8ca35df25c'
NODE = 'node:22.23.2-bookworm-slim@sha256:48e4b67d85f87bd551df43704e24d252f56cc5f8e9718841aace50f19948f0f9'
POSTGRES = 'postgres@sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28'
REDIS = 'redis@sha256:976ab0f0939b78c06d802bcd3bea44c5142ea9a756a57b861e08819817f768c8'
DATABASES = ('vanrides_wa070', 'n8n_recovery')
ADDRESS = '000000000000000@s.whatsapp.net'
ENVIRONMENT = 'wa070-local'
FLOWS = tuple('W0' + str(n) for n in range(1, 9))
SCHEDULES = ('W01', 'W04', 'W06', 'W08')

# No external client exists in this server. Only the owned worker's localhost is proxied.
CHANNEL = r"""
const http=require('node:http'),fs=require('node:fs'),crypto=require('node:crypto');
const cfg=JSON.parse(fs.readFileSync('/fixture/config.json','utf8'));
const file='/fixture/state.json';
const stats=fs.existsSync(file)?JSON.parse(fs.readFileSync(file)):
 {control:{process:0,dispatch:0,attendance:0,reconcile:0},accepted:0,duplicates:0,invalid:0,hashes:[]};
const save=()=>{fs.writeFileSync(file+'.tmp',JSON.stringify(stats),{mode:0o600});fs.renameSync(file+'.tmp',file);};save();
const routes={'/v1/process-inbox':'process','/v1/dispatch-one':'dispatch',
 '/v1/tick-attendance':'attendance','/v1/reconcile-delivery':'reconcile'};
http.createServer({requestTimeout:30000,headersTimeout:3000,maxHeaderSize:4096},(req,res)=>{
 const reply=(status,body)=>{save();res.writeHead(status,{'content-type':'application/json'});res.end(JSON.stringify(body));};
 if(req.method==='GET'&&req.url==='/healthz')return reply(200,{status:'UP'});
 if(req.method==='GET'&&req.url==='/instance/connectionState/'+cfg.instance&&req.headers.apikey===cfg.apiKey)
  return reply(200,{instance:{instanceName:cfg.instance,state:'open'}});
 let raw='';req.on('data',part=>{raw+=part;if(raw.length>4096)req.destroy();});
 req.on('end',()=>{
  if(req.method==='POST'&&routes[req.url]&&req.headers.authorization==='Bearer '+cfg.control&&raw==='{}'){
   stats.control[routes[req.url]]++;save();
   const proxy=http.request({hostname:'127.0.0.1',port:7080,path:req.url,method:'POST',timeout:29000,
    headers:{authorization:'Bearer '+cfg.control,'content-type':'application/json'}},up=>{
     let response='';up.on('data',c=>{response+=c;if(response.length>4096)up.destroy();});
     up.on('end',()=>{res.writeHead(up.statusCode,{'content-type':'application/json'});res.end(response);});});
   proxy.on('error',()=>reply(503,{status:'UNAVAILABLE'}));proxy.on('timeout',()=>proxy.destroy());proxy.end('{}');return;
  }
  try{
   const body=JSON.parse(raw);
   if(req.method!=='POST'||req.url!=='/message/sendText/'+cfg.instance||req.headers.apikey!==cfg.apiKey||
    Object.keys(body).sort().join(',')!=='linkPreview,number,text'||body.number!==cfg.address||body.linkPreview!==false||
    typeof body.text!=='string'||body.text.length<1||body.text.length>2000)throw Error();
   const hash=crypto.createHash('sha256').update(body.text).digest('hex');
   if(stats.hashes.includes(hash)){stats.duplicates++;return reply(409,{status:'DUPLICATE_EFFECT'});}
   stats.hashes.push(hash);stats.accepted++;return reply(201,{key:{id:'recovery_synthetic_'+stats.accepted}});
  }catch{stats.invalid++;reply(400,{status:'REJECTED'});}
 });
}).listen(7081,'0.0.0.0');
"""
HTTP = r"""
let raw='';process.stdin.on('data',c=>raw+=c);process.stdin.on('end',async()=>{
 try{const r=JSON.parse(raw);const response=await fetch(r.url,{method:r.method??'GET',headers:r.headers??{},
 body:r.body===undefined?undefined:JSON.stringify(r.body),redirect:'error',signal:AbortSignal.timeout(r.timeoutMs)});
 const body=await response.text();if(body.length>16384)throw Error();
 process.stdout.write(JSON.stringify({status:response.status,body:JSON.parse(body)}));
 }catch{process.exitCode=1;}
});
"""
FINGERPRINT = r"""
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL TIME ZONE 'UTC'; SET LOCAL DateStyle='ISO, YMD'; SET LOCAL extra_float_digits=3;
SELECT format('SELECT %L||''|''||count(*)||''|''||md5(COALESCE(string_agg(md5(row_to_json(t)::text),'','' ORDER BY md5(row_to_json(t)::text)),'''')) FROM %I.%I t;',
 schemaname||'.'||tablename,schemaname,tablename) FROM pg_tables
 WHERE schemaname IN ('public','wa') ORDER BY schemaname,tablename
\gexec
SELECT format('SELECT %L||''|1|''||md5(last_value::text||''|''||is_called::text) FROM %I.%I;',
 schemaname||'.'||sequencename,schemaname,sequencename) FROM pg_sequences
 WHERE schemaname IN ('public','wa') ORDER BY schemaname,sequencename
\gexec
COMMIT;
"""


def assets(root=ROOT):
    paths = []
    for directory in ('worker', 'agent', 'transport'):
        base = root / 'src/whatsapp' / directory
        require(base.resolve() == base, 'REC_SOURCE_SYMLINK')
        paths += list(base.rglob('*.mjs'))
    paths += [root / 'src/whatsapp/scripts/070-n8n-smoke.py']
    paths += [root / 'deploy/homologacao' / name for name in ('rehearse_recovery.py', 'recovery_fixture.py', 'rehearse_migration.py')]
    require(10 <= len(paths) <= 300, 'REC_SOURCE_INVENTORY_INVALID')
    return {str(path.relative_to(root)): sha(read(path, 2 * 1024 * 1024)) for path in sorted(paths)}


def bundle(root=ROOT):
    path = root / 'src/whatsapp/scripts/070-n8n-smoke.py'
    spec = importlib.util.spec_from_file_location('recovery_checked_bundle', path)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    try: return module.load_bundle(root / 'src/whatsapp/workflows')
    except module.SmokeError: raise Blocked('REC_WORKFLOW_BUNDLE_INVALID') from None


def workflows(original, nonce):
    """Only copied IDs/references/URL/credential change; schedules/content stay identical."""
    require(isinstance(nonce, str) and len(nonce) == 32 and all(c in '0123456789abcdef' for c in nonce), 'REC_WORKFLOW_NONCE_INVALID')
    result = copy.deepcopy(original)
    namespace = 'rec' + nonce[:24]  # n8n 2.45.4 PostgreSQL workflow/credential IDs are varchar(36).
    mapping = {w['id']: namespace + key for key, w in result.items()}
    credential = namespace + 'Control'
    for key, workflow in result.items():
        workflow['id'] = mapping[workflow['id']]
        workflow['settings']['callerIds'] = ','.join(mapping[x] for x in workflow['settings']['callerIds'].split(','))
        if 'errorWorkflow' in workflow['settings']:
            workflow['settings']['errorWorkflow'] = mapping[workflow['settings']['errorWorkflow']]
        for node in workflow['nodes']:
            if node['type'] == 'n8n-nodes-base.httpRequest':
                old = 'http://vanrides-wa070-worker:7080'
                require(node['parameters']['url'].startswith(old + '/v1/'), 'REC_WORKFLOW_URL_INVALID')
                node['parameters']['url'] = 'http://worker:7081' + node['parameters']['url'][len(old):]
                node['credentials']['httpHeaderAuth'] = {'id': credential, 'name': 'Synthetic Recovery Control'}
    return result, credential


def api_env(pg, keys):
    values = {'SPRING_PROFILES_ACTIVE': 'local', 'SPRING_DATASOURCE_URL': 'jdbc:postgresql://' + pg + ':5432/' + DATABASES[0],
              'SPRING_DATASOURCE_USERNAME': 'postgres', 'SPRING_DATASOURCE_PASSWORD': keys['postgres'],
              'SPRING_JPA_HIBERNATE_DDL_AUTO': 'validate', 'SPRING_JPA_SHOW_SQL': 'false',
              'WA_MVP_ENABLED': 'true', 'WA_ENVIRONMENT': ENVIRONMENT, 'WA_TEST_COHORT_ONLY': 'true',
              'WA_MVP_ONLINE': 'false', 'WA_TEAM_API_ONLY': 'false', 'WA_NEW_STARTS_ENABLED': 'true',
              'WA_APPROACH_ENABLED': 'false', 'WA_AUTO_FINISH_ENABLED': 'false', 'WA_MVP_REHEARSAL_ROUTINES': 'false',
              'WA_PUBLIC_BASE_URL': 'https://recovery.invalid', 'WA_START_LINK_BASE_URL': 'https://recovery.invalid',
              'WA_SECRET_HASH_KEY': keys['hash'], 'WA_RESPONSE_ENCRYPTION_KEY': keys['response'],
              'WA_OPERATOR_CREDENTIAL': keys['operator'], 'WA_OPERATOR_EXPIRES_AT': '2099-01-01T00:00:00Z',
              'VANRIDES_EMAIL_PROVIDER': 'disabled', 'VANRIDES_AUTH_BASIC_ENABLED': 'false',
              'VANRIDES_WA_TEAM_WEB_ENABLED': 'false', 'VANRIDES_RATE_LIMIT_ENABLED': 'false',
              'VANRIDES_JWT_SECRET': keys['jwt'], 'VANRIDES_ROUTING_PROVIDER': 'static',
              'VANRIDES_PUSH_PROVIDER': 'none', 'VANRIDES_MAPS_PLATFORM_ENABLED': 'false'}
    return values


def n8n_env(pg, redis, keys):
    return {'DB_TYPE': 'postgresdb', 'DB_POSTGRESDB_HOST': pg, 'DB_POSTGRESDB_PORT': '5432',
            'DB_POSTGRESDB_DATABASE': DATABASES[1], 'DB_POSTGRESDB_USER': 'postgres', 'DB_POSTGRESDB_PASSWORD': keys['postgres'],
            'EXECUTIONS_MODE': 'queue', 'QUEUE_BULL_REDIS_HOST': redis, 'QUEUE_BULL_REDIS_PORT': '6379',
            'N8N_ENCRYPTION_KEY': keys['n8n'], 'N8N_DIAGNOSTICS_ENABLED': 'false',
            'N8N_VERSION_NOTIFICATIONS_ENABLED': 'false', 'N8N_TEMPLATES_ENABLED': 'false',
            'N8N_PERSONALIZATION_ENABLED': 'false', 'N8N_COMMUNITY_PACKAGES_ENABLED': 'false',
            'N8N_BLOCK_ENV_ACCESS_IN_NODE': 'true', 'N8N_USE_WORKFLOW_PUBLICATION_SERVICE': 'true',
            'N8N_DEFAULT_BINARY_DATA_MODE': 'default', 'GENERIC_TIMEZONE': 'UTC', 'TZ': 'UTC',
            'EXECUTIONS_DATA_SAVE_ON_ERROR': 'none', 'EXECUTIONS_DATA_SAVE_ON_SUCCESS': 'none',
            'EXECUTIONS_DATA_SAVE_MANUAL_EXECUTIONS': 'false', 'EXECUTIONS_DATA_SAVE_ON_PROGRESS': 'false',
            'EXECUTIONS_DATA_PRUNE': 'true', 'N8N_LOG_LEVEL': 'warn', 'N8N_SECURE_COOKIE': 'false',
            'N8N_GRACEFUL_SHUTDOWN_TIMEOUT': '20', 'QUEUE_HEALTH_CHECK_ACTIVE': 'true'}


def env_file(path, values):
    require(all('\n' not in k + v and '\r' not in k + v for k, v in values.items()), 'REC_ENV_INVALID')
    write_new(path, ''.join(k + '=' + v + '\n' for k, v in sorted(values.items())).encode())


def seed(nonce, tokens):
    cohort, identity = str(uuid.uuid4()), str(uuid.uuid4())
    instance = 'wa070_synthetic_' + nonce
    rows = ["INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES ('%s','wa070-local',true);" % cohort,
            "INSERT INTO wa.identity(id,test_cohort_id) VALUES ('%s','%s');" % (identity, cohort),
            "INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) "
            "VALUES ('%s','%s','EVOLUTION','%s','PN','%s');" % (uuid.uuid4(), identity, instance, ADDRESS)]
    for principal in ('CHANNEL', 'CONVERSATION_WORKER', 'DELIVERY_WORKER', 'SYSTEM_JOB'):
        rows.append("INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,expires_at) "
                    "VALUES ('%s','%s','wa070-local','%s','%s',CURRENT_TIMESTAMP+interval '1 hour');" %
                    (uuid.uuid4(), principal, sha(tokens[principal].encode()), instance))
    return 'BEGIN;\n' + '\n'.join(rows) + '\nCOMMIT;', identity, instance


def event(instance, text):
    from datetime import datetime, timezone
    now = datetime.now(timezone.utc)
    return {'event': 'messages.upsert', 'instance': instance, 'date_time': now.isoformat().replace('+00:00', 'Z'),
            'data': {'key': {'id': 'recovery_' + uuid.uuid4().hex, 'fromMe': False, 'remoteJid': ADDRESS},
                     'messageTimestamp': int(now.timestamp()), 'message': {'conversation': text}}}


def secrets_for_run():
    return {key: secrets.token_urlsafe(32) for key in ('postgres', 'hash', 'response', 'operator', 'jwt', 'n8n', 'control', 'provider',
                                                     'CHANNEL', 'CONVERSATION_WORKER', 'DELIVERY_WORKER', 'SYSTEM_JOB')}
