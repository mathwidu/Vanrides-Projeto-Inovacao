#!/usr/bin/env node
/** Pipe exactly the transient code through stdin. Never pass it as an argument. */
import { socketRequest } from './060-pairing-socket.mjs';
import { demand, safeCode } from './070-worker-common.mjs';
try {
  const [socketPath, action, ...extra] = process.argv.slice(2);
  demand(extra.length === 0 && ['confirm', 'complete'].includes(action), 'LAB_BRIDGE_ARGUMENTS_INVALID');
  let body = {};
  if (action === 'confirm') {
    let length = 0; const chunks = [];
    for await (const chunk of process.stdin) { length += chunk.length; demand(length <= 16, 'LAB_BRIDGE_INPUT_INVALID'); chunks.push(chunk); }
    const code = new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, length)).replace(/\r?\n$/, '');
    demand(/^[BCDFGHJKMNPQRSTVWXYZ]{8}$/.test(code), 'LAB_BRIDGE_INPUT_INVALID'); body.userCode = code;
  }
  const result = await socketRequest(socketPath, '/' + action, body); delete body.userCode;
  console.log(JSON.stringify(result));
} catch (error) { console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'LAB_BRIDGE_FAILED') })); process.exitCode = 1; }
