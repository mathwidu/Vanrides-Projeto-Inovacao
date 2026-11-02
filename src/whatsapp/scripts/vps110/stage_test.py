import hashlib
import io
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import stage
import stage_remote


def sha(b): return hashlib.sha256(b).hexdigest()


def make_tar(files, extra=None):
    stream=io.BytesIO()
    with tarfile.open(fileobj=stream,mode='w') as arc:
        for name,value in files.items():
            i=tarfile.TarInfo(name); i.size=len(value); arc.addfile(i,io.BytesIO(value))
        if extra: arc.addfile(extra)
    return stream.getvalue()


class StageTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name).resolve(); self.root.chmod(0o700)
        self.files={'control.py':b'fixture', 'inputs/backend.jar':b'candidate'}
        self.expected={k:sha(v) for k,v in self.files.items()}
        self.raw=make_tar(self.files)

    def transfer(self, data=None, expected=None, label='redmi-vps-a1'):
        data=self.raw if data is None else data
        return stage_remote.stage(self.root,label,sha(data),len(data),expected or self.expected,io.BytesIO(data))

    def test_existing_destination_is_preserved(self):
        self.transfer()
        marker=self.root/'redmi-vps-a1'/'user-file'; marker.write_bytes(b'preserve me')
        with self.assertRaises(FileExistsError): self.transfer()
        self.assertEqual(marker.read_bytes(),b'preserve me')

    def test_modified_file_is_rejected_after_transport_and_rolled_back(self):
        with self.assertRaises(ValueError):
            self.transfer(make_tar({**self.files,'inputs/backend.jar':b'rebuilt'}))
        self.assertEqual(list(self.root.iterdir()),[])

    def test_rejects_archive_link_and_traversal_without_writing_outside(self):
        for pathname,kind in [('../secret',tarfile.REGTYPE),('readme',tarfile.SYMTYPE),('/tmp/path',tarfile.REGTYPE)]:
            item=tarfile.TarInfo(pathname); item.type=kind; item.linkname='/not-readable'
            with self.assertRaises(ValueError): self.transfer(make_tar(self.files,item))
            self.assertEqual(list(self.root.iterdir()),[])

    def test_extraction_is_bounded_and_does_not_run_the_package(self):
        result=self.transfer()
        self.assertFalse(result['servicesStarted'])
        self.assertEqual((self.root/'redmi-vps-a1/inputs/backend.jar').read_bytes(),b'candidate')
        self.assertFalse((self.root/'redmi-vps-a1/.transfer').exists())

    def test_stream_open_failure_rolls_back_partial_file(self):
        with patch.object(tarfile.TarFile,'extractfile',side_effect=OSError('fixture')):
            with self.assertRaises(OSError): self.transfer()
        self.assertEqual(list(self.root.iterdir()),[])

    def test_abort_preserves_replacement_file(self):
        def replace_then_fail(*args):
            f=self.root/'redmi-vps-a1/.transfer'
            temporary=self.root/'replacement'; temporary.write_bytes(b'other owner')
            os.replace(temporary,f)
            raise OSError('fixture')
        with patch.object(tarfile.TarFile,'extractfile',side_effect=replace_then_fail):
            with self.assertRaises(OSError): self.transfer()
        self.assertEqual((self.root/'redmi-vps-a1/.transfer').read_bytes(),b'other owner')
        self.assertFalse((self.root/'redmi-vps-a1/control.py').exists())

    def test_unstage_requires_original_directory_and_exact_unstarted_contents(self):
        mark=self.transfer()
        args=(self.root,'redmi-vps-a1',sha(self.raw),self.expected)
        with self.assertRaises(ValueError): stage_remote.unstage_unused(*args,{**mark,'inode':-1})
        runtime=self.root/'redmi-vps-a1/runtime';runtime.mkdir()
        with self.assertRaises(ValueError): stage_remote.unstage_unused(*args,mark)
        runtime.rmdir()
        self.assertTrue(stage_remote.unstage_unused(*args,mark)['unusedPackageRemoved'])
        self.assertEqual(list(self.root.iterdir()),[])

    def test_local_allowlist_rejects_unreviewed_file_or_hash(self):
        valid={n:n.encode() for n in stage.ALLOW}; raw=make_tar(valid)
        with patch.object(stage,'PIN_JAR',sha(valid['inputs/backend.jar'])), patch.object(stage,'PIN_CA',sha(valid['inputs/public-ca.der'])):
            self.assertEqual(set(stage.inspect_bundle(io.BytesIO(raw),sha(raw))),set(valid))
            with self.assertRaisesRegex(ValueError,'DIGEST_CHANGED'): stage.inspect_bundle(io.BytesIO(raw),'f'*64)
            unreviewed=make_tar({**valid,'runtime.log':b'not transferable'})
            with self.assertRaisesRegex(ValueError,'UNEXPECTED_ENTRY'):
                stage.inspect_bundle(io.BytesIO(unreviewed),sha(unreviewed))


if __name__=='__main__': unittest.main()
