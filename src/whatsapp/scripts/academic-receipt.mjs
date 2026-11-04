import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { demand } from './070-worker-common.mjs';

const inside = (file, directory) => file === directory || file.startsWith(directory + path.sep);

/** Optional CI artifact, never a log capture. Preparation does not create a file. */
export function prepareAcademicReceipt(outputPath, checkout) {
  if (outputPath === undefined) return () => {};
  let target;
  try {
    demand(typeof outputPath === 'string' && path.isAbsolute(outputPath)
      && path.normalize(outputPath) === outputPath, 'ACADEMIC_RECEIPT_PATH_INVALID');
    const root = fs.realpathSync(checkout), parent = fs.realpathSync(path.dirname(outputPath));
    target = path.join(parent, path.basename(outputPath));
    demand(!inside(target, root) && fs.statSync(parent).isDirectory(), 'ACADEMIC_RECEIPT_PATH_INVALID');
    try { fs.lstatSync(target); demand(false, 'ACADEMIC_RECEIPT_ALREADY_EXISTS'); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  } catch (error) {
    demand(false, error.code === 'ACADEMIC_RECEIPT_ALREADY_EXISTS'
      ? error.code : 'ACADEMIC_RECEIPT_PATH_INVALID');
  }
  return result => {
    demand(result?.status === 'PASS' && result.cleanup?.status === 'OWNED_RESOURCES_REMOVED', 'ACADEMIC_RECEIPT_NOT_SUCCESSFUL');
    const temporary = path.join(path.dirname(target), '.vanrides-receipt-' + randomUUID() + '.tmp');
    let fd, created = false;
    try {
      fd = fs.openSync(temporary, fs.constants.O_WRONLY | fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_NOFOLLOW, 0o600);
      created = true;
      fs.fchmodSync(fd, 0o600);
      fs.writeFileSync(fd, JSON.stringify(result, null, 2) + '\n');
      fs.fsyncSync(fd);
      fs.closeSync(fd); fd = undefined;
      // A hard link publishes the fully written file atomically and refuses an
      // existing target; rename would silently replace someone else's receipt.
      fs.linkSync(temporary, target);
    } catch {
      demand(false, 'ACADEMIC_RECEIPT_WRITE_FAILED');
    } finally {
      if (fd !== undefined) fs.closeSync(fd);
      if (created) fs.unlinkSync(temporary);
    }
  };
}
