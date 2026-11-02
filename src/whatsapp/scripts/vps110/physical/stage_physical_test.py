import ast
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import stage_physical as local
import stage_physical_remote as remote


def sha(value):return hashlib.sha256(value).hexdigest()
def encoded(value):return (json.dumps(value,sort_keys=True,separators=(',',':'))+'\n').encode()


def fixture_files():
    files={name:('fixture:'+name).encode() for name in remote.FIXED_FILES}
    files['layout.py']=('FIXED_FILES = '+repr(remote.FIXED_FILES)+'\n').encode()
    effective=set(remote.FIXED_FILES)-{'contract.json','source-manifest.json','inputs/backend.jar','inputs/public-ca.der','inputs/verified-ci-receipt.json'}
    files['source-manifest.json']=encoded({'profile':remote.PROFILE,'files':{name:sha(files[name]) for name in effective}})
    module=sha(files['source-manifest.json'])
    files['contract.json']=encoded({'profile':remote.PROFILE,'moduleSha256':module,
        'backend':{'commit':remote.PIN_SOURCE,'jarSha256':sha(files['inputs/backend.jar']),
                   'verifiedReceiptSha256':sha(files['inputs/verified-ci-receipt.json'])},
        'apk':{'publicCaSha256':sha(files['inputs/public-ca.der'])}})
    files['SHA256SUMS.json']=encoded({name:sha(value) for name,value in files.items()})
    return files,module


def tar_bytes(files,extras=()):
    output=io.BytesIO()
    with tarfile.open(fileobj=output,mode='w') as archive:
        for name,body in files.items():
            item=tarfile.TarInfo(name);item.size=len(body);archive.addfile(item,io.BytesIO(body))
        for item in extras:archive.addfile(item)
    return output.getvalue()


class PhysicalStageTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name).resolve();self.root.chmod(0o700)
        self.files,self.module=fixture_files();self.expected={name:sha(body) for name,body in self.files.items()};self.raw=tar_bytes(self.files)
        for name,key in [('PIN_JAR','inputs/backend.jar'),('PIN_CA','inputs/public-ca.der'),('PIN_RECEIPT','inputs/verified-ci-receipt.json')]:
            p=patch.object(remote,name,sha(self.files[key]));p.start();self.addCleanup(p.stop)

    def transfer(self,raw=None,files=None,module=None):
        raw=self.raw if raw is None else raw
        return remote.stage(self.root,'physical-fixture-a1',sha(raw),module or self.module,len(raw),files or self.expected,io.BytesIO(raw))

    def unstage(self,receipt):
        return remote.unstage_unused(self.root,'physical-fixture-a1',sha(self.raw),self.module,self.expected,receipt)

    def test_literal_selection_matches_layout_and_has_fifty_files_with_no_execution(self):
        layout=ast.parse(Path(remote.__file__).with_name('layout.py').read_text())
        selected=next(node.value for node in layout.body if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='FIXED_FILES' for t in node.targets))
        self.assertEqual(set(ast.literal_eval(selected)),set(remote.FIXED_FILES));self.assertEqual(len(remote.ALLOW),50)
        with patch.object(local.subprocess,'run',side_effect=AssertionError('no network/process')):
            self.assertEqual(local.inspect_bundle(io.BytesIO(self.raw),sha(self.raw),self.module),self.expected)

    def test_nested_stage_records_each_private_inode_and_never_starts_services(self):
        mark=self.transfer();dest=self.root/'physical-fixture-a1'
        self.assertFalse(mark['servicesStarted']);self.assertEqual(mark['files'],50)
        self.assertEqual(set(mark['directories']),remote.parents_for(remote.ALLOW)|{''})
        for name,record in mark['directories'].items():
            info=(dest/name).lstat();self.assertEqual((info.st_ino,info.st_dev,info.st_uid,stat.S_IMODE(info.st_mode)),
                (record['inode'],record['device'],record['uid'],0o700))
        self.assertFalse((dest/'.transfer').exists())
        self.assertEqual((dest/'source/integrations/whatsapp/worker/main.mjs').read_bytes(),self.files['source/integrations/whatsapp/worker/main.mjs'])

    def test_digest_module_contract_or_matrix_changes_block_before_transport(self):
        with self.assertRaisesRegex(ValueError,'BUNDLE_DIGEST_CHANGED'):local.inspect_bundle(io.BytesIO(self.raw),'0'*64,self.module)
        with self.assertRaisesRegex(ValueError,'MODULE_PIN_CHANGED'):local.inspect_bundle(io.BytesIO(self.raw),sha(self.raw),'0'*64)
        changed={**self.files,'contract.json':encoded({'profile':'PRODUCTION'})}
        raw=tar_bytes(changed)
        with self.assertRaises(ValueError):local.inspect_bundle(io.BytesIO(raw),sha(raw),self.module)
        matrix=json.loads(self.files['source-manifest.json']);matrix['files']['control.py']='0'*64
        changed={**self.files,'source-manifest.json':encoded(matrix)};raw=tar_bytes(changed)
        with self.assertRaises(ValueError):local.inspect_bundle(io.BytesIO(raw),sha(raw),sha(changed['source-manifest.json']))

    def test_existing_destination_and_started_tree_are_preserved(self):
        mark=self.transfer();dest=self.root/'physical-fixture-a1';foreign=dest/'runtime';foreign.mkdir()
        with self.assertRaises(FileExistsError):self.transfer()
        with self.assertRaises(ValueError):self.unstage(mark)
        self.assertTrue(foreign.exists());self.assertTrue((dest/'control.py').exists())

    def test_whitelist_links_traversal_and_duplicate_members_are_rejected_with_rollback(self):
        for name,kind in [('../escape',tarfile.REGTYPE),('/tmp/escape',tarfile.REGTYPE),
                          ('foreign',tarfile.SYMTYPE),('control.py',tarfile.LNKTYPE),('inputs/backend.jar',tarfile.REGTYPE)]:
            item=tarfile.TarInfo(name);item.type=kind;item.linkname='/not-readable'
            raw=tar_bytes(self.files,[item])
            with self.assertRaises(ValueError):self.transfer(raw)
            self.assertEqual(list(self.root.iterdir()),[])

    def test_unknown_file_or_incomplete_expected_selection_cannot_relax_the_receiver(self):
        with self.assertRaises(ValueError):self.transfer(files={key:value for key,value in self.expected.items() if key!='layout.py'})
        raw=tar_bytes({**self.files,'runtime.log':b'not transferable'})
        with self.assertRaises(ValueError):self.transfer(raw)
        self.assertEqual(list(self.root.iterdir()),[])

    def test_limits_count_and_truncated_stream_are_bounded_and_cleaned(self):
        dirs=[]
        for _ in range(15):item=tarfile.TarInfo('inputs');item.type=tarfile.DIRTYPE;dirs.append(item)
        raw=tar_bytes(self.files,dirs)
        with self.assertRaisesRegex(ValueError,'ENTRY_LIMIT'):self.transfer(raw)
        with self.assertRaises(ValueError):remote.stage(self.root,'physical-fixture-a1',sha(self.raw),self.module,remote.MAX_BYTES+1,self.expected,io.BytesIO(self.raw))
        with self.assertRaises(ValueError):remote.stage(self.root,'physical-fixture-a1',sha(self.raw),self.module,len(self.raw),self.expected,io.BytesIO(self.raw[:-1]))
        self.assertEqual(list(self.root.iterdir()),[])

    def test_transferred_file_tampering_is_detected_and_partial_tree_is_removed(self):
        raw=tar_bytes({**self.files,'inputs/backend.jar':b'rebuilt'})
        with self.assertRaisesRegex(ValueError,'FILE_DIGEST_CHANGED'):self.transfer(raw)
        self.assertEqual(list(self.root.iterdir()),[])

    def test_rollback_preserves_replaced_foreign_transfer_file(self):
        def foreign_then_fail(*_args):
            transfer=self.root/'physical-fixture-a1/.transfer'
            replacement=self.root/'replacement';replacement.write_bytes(b'foreign');replacement.chmod(0o600);replacement.replace(transfer)
            raise OSError('synthetic interruption')
        with patch.object(tarfile.TarFile,'extractfile',side_effect=foreign_then_fail):
            with self.assertRaises(OSError):self.transfer()
        dest=self.root/'physical-fixture-a1';self.assertEqual((dest/'.transfer').read_bytes(),b'foreign')
        self.assertFalse((dest/'control.py').exists())

    def test_rollback_does_not_touch_a_replaced_parent_or_its_foreign_contents(self):
        actual=tarfile.TarFile.extractfile
        def replace_parent(archive,item):
            if item.name.startswith('source/integrations/whatsapp/agent/'):
                parent=self.root/'physical-fixture-a1/source/integrations/whatsapp/agent'
                parent.rename(self.root/'moved-owned-parent');parent.mkdir(mode=0o700)
                (parent/'foreign-marker').write_bytes(b'preserve');raise OSError('synthetic interruption')
            return actual(archive,item)
        with patch.object(tarfile.TarFile,'extractfile',replace_parent):
            with self.assertRaises(OSError):self.transfer()
        self.assertEqual((self.root/'physical-fixture-a1/source/integrations/whatsapp/agent/foreign-marker').read_bytes(),b'preserve')

    def test_unstage_requires_original_inode_owner_mode_hash_and_exact_unstarted_tree(self):
        mark=self.transfer();dest=self.root/'physical-fixture-a1'
        changed=json.loads(json.dumps(mark));changed['directories']['']['inode']=-1
        with self.assertRaises(ValueError):self.unstage(changed)
        changed=json.loads(json.dumps(mark));changed['directories']['source']['uid']=-1
        with self.assertRaises(ValueError):self.unstage(changed)
        file=dest/'control.py';original=file.read_bytes();file.write_bytes(b'changed')
        with self.assertRaises(ValueError):self.unstage(mark)
        file.write_bytes(original);self.assertTrue(self.unstage(mark)['unusedPackageRemoved']);self.assertEqual(list(self.root.iterdir()),[])

    def test_identical_bytes_in_a_replacement_inode_are_preserved(self):
        mark=self.transfer();dest=self.root/'physical-fixture-a1';target=dest/'control.py'
        replacement=self.root/'replacement';replacement.write_bytes(target.read_bytes());replacement.chmod(0o600);replacement.replace(target)
        with self.assertRaisesRegex(ValueError,'FILE_CHANGED'):self.unstage(mark)
        self.assertTrue(target.exists())

    def test_external_parent_creation_is_private_recorded_and_rollback_preserves_foreign(self):
        home=self.root/'home';home.mkdir(mode=0o700)
        root,created=remote.private_root(home)
        self.assertEqual(set(created),set(remote.ROOT_PARENTS))
        for name,record in created.items():self.assertTrue(remote.matches(home/name,record,True))
        (root/'foreign').write_bytes(b'preserve')
        self.assertEqual(remote.remove_created_parents(home,created),4)
        self.assertEqual((root/'foreign').read_bytes(),b'preserve')
        (root/'foreign').unlink();self.assertEqual(remote.remove_created_parents(home,created),0)
        self.assertEqual(list(home.iterdir()),[])

    def test_symlinked_external_parent_is_rejected_without_touching_target(self):
        home=self.root/'home';home.mkdir(mode=0o700);foreign=self.root/'foreign';foreign.mkdir(mode=0o700)
        (home/'.local').symlink_to(foreign,target_is_directory=True)
        with self.assertRaises(ValueError):remote.private_root(home)
        self.assertEqual(list(foreign.iterdir()),[])


if __name__=='__main__':unittest.main()
