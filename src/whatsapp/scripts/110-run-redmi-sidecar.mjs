// IPC for the physical lab only. No listener on TCP, no response bodies on disk.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { demand, exact, SmokeFailure } from './070-worker-common.mjs';

export async function openRunSocket(directory, handlers) {
  const st=fs.lstatSync(directory), socket=path.join(directory,'run.sock');
  demand(st.isDirectory()&&st.uid===process.getuid()&&(st.mode&0o777)===0o700&&fs.realpathSync(directory)===directory
    &&Buffer.byteLength(socket)<100&&!fs.existsSync(socket),'RUN_PRIVATE_SOCKET_REQUIRED');
  const routes=['/pair','/intent','/started','/stopped'];
  let step=0,busy=false,closed=false,ongoing;
  const server=http.createServer({maxHeaderSize:2048,requestTimeout:10000,headersTimeout:5000},(req,res)=>{
    async function handle() {
      const fail=()=>{res.writeHead(409,{'content-type':'application/json'});res.end('{"status":"UNAVAILABLE"}');};
      if(closed||busy||req.method!=='POST'||req.url!==routes[step]||req.headers['content-type']!=='application/json') {req.resume();fail();return;}
      busy=true;
      try {
        let bytes=0;const data=[];
        for await(const chunk of req){bytes+=chunk.length;demand(bytes<=192,'RUN_IPC_INPUT');data.push(chunk);}
        const b=JSON.parse(Buffer.concat(data).toString('utf8')),route=routes[step];
        demand(route==='/pair'?exact(b,['code'])&&/^[BCDFGHJKMNPQRSTVWXYZ]{8}$/u.test(b.code):exact(b,[]),'RUN_IPC_INPUT');
        const result=await handlers[route](b);
        demand(!closed,'RUN_IPC_CLOSED');step++;
        res.writeHead(200,{'content-type':'application/json','cache-control':'no-store'});res.end(JSON.stringify(result??{status:'OK'}));
      }catch{fail();}finally{busy=false;}
    }
    const pending=handle();if(busy)ongoing=pending;pending.catch(()=>res.destroy());
  });
  server.on('clientError',(_e,s)=>s.destroy());
  await new Promise((ok,no)=>{server.once('error',no);server.listen(socket,ok);});fs.chmodSync(socket,0o600);
  return {socket,complete:()=>step===4,stop:async()=>{
    closed=true;await new Promise(resolve=>{server.close(resolve);server.closeAllConnections();});await ongoing;
    if(fs.existsSync(socket))fs.unlinkSync(socket);
  }};
}
