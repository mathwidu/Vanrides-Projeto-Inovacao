import ast
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import subprocess
import unittest
from unittest.mock import Mock,patch

import control
import infra
import prepare
from protocol import Blocked


def reviewed_source_fingerprints(source, tree=None):
    # Python 3.12 adds type_params to AST nodes; bind reviewed source bytes, not ast.dump's schema.
    tree = ast.parse(source) if tree is None else tree
    values = {}
    def add(name, node):
        segment = ast.get_source_segment(source, node)
        assert segment is not None
        values[name] = hashlib.sha256(segment.encode('utf8')).hexdigest()
    for node in tree.body:
        if isinstance(node, ast.FunctionDef) and node.name in ('process_ticks', 'session_members'):
            add(node.name, node)
        if isinstance(node, ast.ClassDef) and node.name == 'OwnedSessionGroup':
            for method in node.body:
                if isinstance(method, ast.FunctionDef) and method.name in ('__init__', 'observe'):
                    add('OwnedSessionGroup.' + method.name, method)
        if isinstance(node, ast.FunctionDef) and node.name == 'serve':
            outer = next(item for item in node.body if isinstance(item, ast.Try))
            first, last = outer.finalbody[0], outer.finalbody[-1]
            add('serve.finally', ast.Pass(lineno=first.lineno, col_offset=first.col_offset,
                                       end_lineno=last.end_lineno, end_col_offset=last.end_col_offset))
    return values


def result():
    return {'cleanupConfirmed':True,'cohortReset':1,'issuedCredentials':4,'revokedCredentials':4,
            'graph':dict.fromkeys(control.TABLES,0),'totals':{'uniqueEvents':70,'duplicateEvents':0,'commandReceipts':70,
             'submittedAttempts':76,'providerRequests':76,'crossActorDeliveries':4,'workerStarts':1},
            'checks':[{'check':name,'status':'PASS'} for name in control.CHECKS],'codes':[],
            'originalNineCheckProfile':False,'hardwareVerified':False,
            'sensorEvidence':'BACKEND_ACCEPTED_NATIVE_CHANNEL_ONLY_MAC_EVIDENCE_REQUIRED',
            'externalBusinessSubmissions':0,'functionalComplete':True,'functionalCode':None}


class PhysicalControlTest(unittest.TestCase):
    def test_receipt_accepts_only_aggregate_known_fields_and_same_cleanup_totals(self):
        self.assertIs(control.validate_result(value:=result()),value)
        for mutate in [lambda v:v.update(gps={'latitude':1}),lambda v:v.update(hardwareVerified=True),
                       lambda v:v.update(originalNineCheckProfile=True),lambda v:v.update(externalBusinessSubmissions=1),
                       lambda v:v.update(revokedCredentials=3),lambda v:v['graph'].update(identity=1),
                       lambda v:v['checks'].append({'check':'foreign','status':'PASS'}),
                       lambda v:v['totals'].update(providerRequests=75),lambda v:v.update(codes=['unexpected payload']),
                       lambda v:v.update(functionalCode='payload,not-code')]:
            candidate=result();mutate(candidate)
            with self.assertRaises(Blocked):control.validate_result(candidate)

    def test_functional_failure_does_not_invent_cleanup_failure_or_hardware_pass(self):
        value=result();value.update(functionalComplete=False,functionalCode='RUN_REAL_GPS_NOT_RECEIVED')
        value['checks']=value['checks'][:2]
        self.assertTrue(control.validate_result(value)['cleanupConfirmed'])
        self.assertFalse(value['hardwareVerified'])

    def test_unrecognized_graph_table_cannot_replace_a_required_zero(self):
        value=result();value['graph'].pop('tracking_session');value['graph']['unrelated']=0
        with self.assertRaisesRegex(Blocked,'GRAPH_INVALID'):control.validate_result(value)

    def test_private_artifacts_are_removed_without_logs_and_missing_files_are_okay(self):
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder);(base/'session').mkdir(mode=0o700)
            for name in ['cohort-scope-private.json','001-operator-request.json','002-operator-result.json','control_token.secret']:
                path=base/'session'/name;path.write_text('synthetic');path.chmod(0o600)
            control.session_cleanup(base);control.session_cleanup(base)
            self.assertEqual(list((base/'session').iterdir()),[])

    def test_unknown_file_directory_or_symlink_prevents_any_artifact_deletion(self):
        for kind in ('file','directory','symlink'):
            with tempfile.TemporaryDirectory() as folder:
                base=Path(folder);session=base/'session';session.mkdir(mode=0o700)
                known=session/'001-operator-request.json';known.write_text('synthetic');known.chmod(0o600)
                target=session/'database.log'
                if kind=='file':target.write_text('never export');target.chmod(0o600)
                elif kind=='directory':target.mkdir()
                else:target.symlink_to(known)
                with self.assertRaises(Blocked):control.session_cleanup(base)
                self.assertTrue(known.exists())

    def test_signal_or_process_loss_never_makes_a_still_live_pid_absent(self):
        with patch.object(Path,'read_text',return_value='11 (node with spaces) S '+' '.join(str(n) for n in range(30))):
            self.assertEqual(control.process_ticks(11),'18')
        with patch.object(Path,'read_text',side_effect=FileNotFoundError):
            self.assertIsNone(control.process_ticks(11))

    def test_pressure_class_and_health_methods_are_exact_reviewed_origin(self):
        raw,compose_raw=prepare.canonical_infrastructure(prepare.default_repository())
        raw=raw.decode()
        canonical=ast.parse(raw);actual=ast.parse((control.HERE/'infra.py').read_text())
        def selected(tree):
            values={}
            for node in tree.body:
                if isinstance(node,ast.ClassDef) and node.name in ('PressureGuard','OwnedMemoryGuard'):values[node.name]=ast.dump(node)
                if isinstance(node,ast.ClassDef) and node.name=='Runtime':
                    for method in node.body:
                        if isinstance(method,ast.FunctionDef) and method.name in ('guard','bind_owned_memory','memory_summary','settle_before_start','assert_live_health'):
                            values[method.name]=ast.dump(method)
            return values
        self.assertEqual(selected(canonical),selected(actual))
        self.assertEqual(len(selected(actual)),7)
        expected=raw.replace("FIXED_FILES = ('control.py', 'protocol.py', 'compose.yaml', 'contract.json',\n               'inputs/backend.jar', 'inputs/verified-ci-receipt.json', 'inputs/public-ca.der')",'from layout import FIXED_FILES')
        expected=expected.replace("'/run/wa110/application.properties': ('runtime/application.properties', False)}",
                                  "'/run/wa110/application.properties': ('runtime/application.properties', False),\n                                '/run/wa110/session': ('session', True)}")
        self.assertEqual(expected,(control.HERE/'infra.py').read_text())
        compose=compose_raw.decode()
        compose=compose.replace('      - ./runtime/application.properties:/run/wa110/application.properties:ro\n',
                               '      - ./runtime/application.properties:/run/wa110/application.properties:ro\n      - ./session:/run/wa110/session\n')
        self.assertEqual(compose,(control.HERE/'compose.yaml').read_text())

    def test_orphan_group_helpers_and_finally_preserve_the_reviewed_ast(self):
        expected={'process_ticks':'74f479feb2b5e49c2002951e847b4bdb8b69e421d67e1501fbc565fd620c21e1',
                  'session_members':'6114465e2066a7ee54bee776df76f9bacc0d94d9ddfb8eaebd8beccbf0c26266',
                  'OwnedSessionGroup.__init__':'2363ac304c5976bba0aff442a55aa8895cba38264ad4aa260b3832778a4691dc',
                  'OwnedSessionGroup.observe':'84d3d18e80bd88be009749de1373fa860c1137dd249228def98547499dc5c7c8',
                  'serve.finally':'627ecaf4c78b3e08dda4be2dd07fcf7f76cadda046d9e2ae2d99a8e407bacdbe'}
        raw=(control.HERE/'control.py').read_bytes();source=raw.decode('utf8');tree=ast.parse(source)
        self.assertEqual(hashlib.sha256(raw).hexdigest(),'4f4da26d6b35644ce7723080d2cbe09ed8f0de47b5dfc8717221f9b0a76ec639')
        diagnostic=next(node for node in tree.body if isinstance(node,ast.FunctionDef) and node.name=='failure_diagnostic')
        lines=source.splitlines(keepends=True)
        self.assertEqual(lines[diagnostic.end_lineno:diagnostic.end_lineno+2],['\n','\n'])
        original=''.join(lines[:diagnostic.lineno-1]+lines[diagnostic.end_lineno+2:])
        current="result={'status':'BLOCKED','code':code,'diagnostic':failure_diagnostic(error,value)}"
        self.assertEqual(original.count(current),1)
        original=original.replace(current,"result={'status':'BLOCKED','code':code}")
        self.assertEqual(hashlib.sha256(original.encode()).hexdigest(),'13be9713b4b52e5ba15d66cf2543bb8003a399075e66b91163d4f6f802ed1c1d')
        self.assertEqual(expected,reviewed_source_fingerprints(source,tree))
        class DeadlineOnly(ast.NodeVisitor):
            calls=0
            def visit_Call(self,node):
                if isinstance(node.func,ast.Attribute) and isinstance(node.func.value,ast.Name) and node.func.value.id=='session_group' and node.func.attr=='stop':
                    self.calls+=1;self_test.assertEqual(node.args,[])
                    self_test.assertEqual(len(node.keywords),1);self_test.assertEqual(node.keywords[0].arg,'deadline')
                    self_test.assertEqual(ast.dump(node.keywords[0].value),ast.dump(ast.Call(func=ast.Name(id='group_deadline',ctx=ast.Load()),args=[],keywords=[])))
                self.generic_visit(node)
        self_test=self
        for node in tree.body:
            if isinstance(node,ast.FunctionDef) and node.name=='serve':
                outer=next(item for item in node.body if isinstance(item,ast.Try))
                visitor=DeadlineOnly();visitor.visit(ast.Module(body=outer.finalbody,type_ignores=[]))
                self.assertEqual(visitor.calls,1)

    def test_source_fingerprints_survive_python312_type_params_but_detect_code_changes(self):
        source=(control.HERE/'control.py').read_text();old=ast.parse(source)
        kinds=(ast.FunctionDef,ast.AsyncFunctionDef,ast.ClassDef)
        for node in ast.walk(old):
            if isinstance(node,kinds):
                node._fields=tuple(name for name in node._fields if name!='type_params')
                if hasattr(node,'type_params'):del node.type_params
        newer=copy.deepcopy(old)
        for node in ast.walk(newer):
            if isinstance(node,kinds):node._fields=(*node._fields,'type_params');node.type_params=[]
        self.assertNotEqual(ast.dump(old),ast.dump(newer))
        self.assertEqual(reviewed_source_fingerprints(source,old),reviewed_source_fingerprints(source,newer))
        changed=source.replace('self.known = {}',"self.known = {'changed': True}",1)
        self.assertNotEqual(reviewed_source_fingerprints(source)['OwnedSessionGroup.__init__'],reviewed_source_fingerprints(changed)['OwnedSessionGroup.__init__'])
        head,separator,tail=source.rpartition('session_group.stop(deadline=group_deadline())');self.assertTrue(separator)
        changed=head+'session_group.stop()'+tail
        self.assertNotEqual(reviewed_source_fingerprints(source)['serve.finally'],reviewed_source_fingerprints(changed)['serve.finally'])

    def test_shared_budget_does_not_allocate_full_phase_timeouts_or_renew_after_expiry(self):
        now=[0];budget=control.ShutdownBudget(clock=lambda:now[0]);now[0]=245
        self.assertEqual(budget.timeout(75),35);self.assertEqual(budget.timeout(75,recovery=True),50)
        now[0]=280
        with self.assertRaisesRegex(Blocked,'BUDGET_EXPIRED'):budget.check()
        self.assertEqual(budget.timeout(75,recovery=True),15)
        now[0]=295
        with self.assertRaisesRegex(Blocked,'RECOVERY_BUDGET_EXPIRED'):budget.timeout(75,recovery=True)

    def test_expired_normal_budget_still_attempts_owned_teardown_in_reserved_time(self):
        now=[0];budget=control.ShutdownBudget(clock=lambda:now[0]);now[0]=281
        value=object.__new__(control.Infrastructure);value.shutdown_budget=budget
        value.location=Mock();value.owner=Mock();value.resources=Mock(return_value=([],[]));value.docker=['docker','compose']
        value.command=Mock()
        with patch.object(control.infra,'open_port',return_value=False):value.stop()
        self.assertEqual(value.command.call_args.args[0],['docker','compose','down','--timeout','12'])
        self.assertFalse(value.teardown_active)

    def test_all_infra_commands_are_capped_by_the_same_remaining_budget(self):
        now=[0];value=object.__new__(control.Infrastructure);value.shutdown_budget=control.ShutdownBudget(clock=lambda:now[0]);now[0]=270
        with patch.object(control.infra.Runtime,'command',return_value='fixture') as run:
            value.command(['docker','inspect'],timeout=75);self.assertEqual(run.call_args.args[2],10)
            value.teardown_active=True;value.command(['docker','compose','down'],timeout=75);self.assertEqual(run.call_args.args[2],25)

    def test_builder_accepts_another_checkout_path_without_any_local_private_commit(self):
        raw,compose=prepare.canonical_infrastructure(prepare.default_repository())
        with tempfile.TemporaryDirectory() as temporary:
            repository=Path(temporary).resolve();folder=repository/prepare.INFRA_DIRECTORY;folder.mkdir(parents=True)
            (folder/'control.py').write_bytes(raw);(folder/'compose.yaml').write_bytes(compose)
            with patch.object(prepare,'git_bytes',side_effect=AssertionError('current canonical files need no history')):
                self.assertEqual(prepare.canonical_infrastructure(repository),(raw,compose))

    def test_builder_fallback_uses_only_the_remote_equivalent_infrastructure_commit(self):
        raw,compose=prepare.canonical_infrastructure(prepare.default_repository());calls=[]
        def show(repository,revision,name):
            calls.append(revision);self.assertEqual(revision,'1ea0f1790f94586509ffb2af8fa991e08c5c97bb')
            return raw if name.endswith('control.py') else compose
        with tempfile.TemporaryDirectory() as temporary,patch.object(prepare,'git_bytes',side_effect=show):
            self.assertEqual(prepare.canonical_infrastructure(Path(temporary)),(raw,compose))
        self.assertEqual(len(calls),2)

    def test_builder_rejects_unpinned_external_inputs_before_creating_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            base=Path(temporary).resolve();source=base/'fixture';source.write_bytes(b'synthetic input');output=base/'package'
            with self.assertRaisesRegex(Blocked,'PINNED_INPUT_CHANGED'):
                prepare.package(prepare.default_repository(),output,source,source,source)
            self.assertFalse(output.exists());self.assertFalse(output.with_suffix('.tar').exists())

    def test_builder_rejects_clone_a_output_in_git_b_before_reading_inputs_or_writing(self):
        for marker_type in ('directory','worktree-file'):
            with self.subTest(marker_type=marker_type),tempfile.TemporaryDirectory() as temporary:
                base=Path(temporary).resolve();clone_a=base/'clone-a';clone_b=base/'clone-b'
                clone_a.mkdir();(clone_a/'.git').mkdir();clone_b.mkdir();marker=clone_b/'.git'
                if marker_type=='directory':marker.mkdir()
                else:marker.write_text('gitdir: /fixture/other-gitdir\n')
                output=clone_b/'new-parent'/'candidate';source=Mock()
                source.is_file.side_effect=AssertionError('inputs must not be inspected')
                with patch.object(prepare.shutil,'copyfile') as copy:
                    with self.assertRaisesRegex(Blocked,'OUTSIDE_ANY_GIT_CHECKOUT'):
                        prepare.package(clone_a,output,source,source,source)
                source.is_file.assert_not_called();copy.assert_not_called()
                self.assertEqual(list(clone_b.iterdir()),[marker]);self.assertFalse(output.with_suffix('.tar').exists())

    def test_builder_checks_canonical_tar_target_before_inputs_and_preserves_its_symlink(self):
        with tempfile.TemporaryDirectory() as temporary:
            base=Path(temporary).resolve();clone_a=base/'clone-a';clone_b=base/'clone-b';exports=base/'exports'
            clone_a.mkdir();(clone_a/'.git').mkdir();clone_b.mkdir();(clone_b/'.git').mkdir();exports.mkdir()
            output=exports/'candidate';archive=output.with_suffix('.tar');target=clone_b/'candidate.tar';archive.symlink_to(target)
            source=Mock();source.is_file.side_effect=AssertionError('inputs must not be inspected')
            with self.assertRaisesRegex(Blocked,'OUTSIDE_ANY_GIT_CHECKOUT'):
                prepare.package(clone_a,output,source,source,source)
            source.is_file.assert_not_called();self.assertFalse(output.exists());self.assertFalse(target.exists())
            self.assertTrue(archive.is_symlink());self.assertEqual(archive.resolve(),target)


if __name__=='__main__':unittest.main()
