"""Offline supervisor regressions. All process identities and signals are substitutes."""
import copy
import json
import os
from pathlib import Path
import signal
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import control


PID = 12345


def member(pid=PID, ticks='10', **extra):
    return {'pid':pid, 'startTicks':ticks, 'sid':PID, 'uid':os.getuid(), **extra}


class SessionGroupTest(unittest.TestCase):
    def test_proc_scan_uses_only_numeric_kernel_identity_fields(self):
        class Directory:
            name = '23456'
            def __truediv__(self, name):
                self.assert_name = name
                fields = ['S','9',str(PID),str(PID)] + ['0']*15 + ['20']
                return SimpleNamespace(read_text=lambda:'23456 (node with spaces)) '+' '.join(fields))
            def stat(self): return SimpleNamespace(st_uid=os.getuid())
        directory = Directory()
        with patch.object(Path,'iterdir',return_value=[directory]):
            self.assertEqual([member(23456,'20')],control.session_members(PID))
        self.assertEqual('stat',directory.assert_name)

    def setup_group(self, rows):
        state = {'rows':copy.deepcopy(rows), 'time':0.0}; signals = []
        child = Mock(pid=PID); child.poll.return_value = 1
        def send(pgid, number):
            signals.append((pgid,number)); state['rows'] = []
        def pause(seconds): state['time'] += seconds
        group = control.OwnedSessionGroup(child, '10', members=lambda _:copy.deepcopy(state['rows']),
            signal_group=send, clock=lambda:state['time'], pause=pause)
        return group,state,signals

    def test_orphan_worker_is_stopped_after_its_witnessed_leader_exits(self):
        group,state,signals = self.setup_group([member(),member(23456,'20')])
        group.observe(); state['rows'] = [member(23456,'20')]
        self.assertTrue(group.stop(deadline=20))
        self.assertEqual([(PID,signal.SIGTERM)],signals)
        self.assertEqual([],state['rows'])

    def test_unwitnessed_or_reused_leader_or_member_is_preserved_without_signal(self):
        for rows in ([member(ticks='99')],[member(23456,'99')],[member(23456,'20',sid=999)],
                     [member(23456,'20',uid=os.getuid()+1)]):
            with self.subTest(rows=rows):
                group,state,signals = self.setup_group([member(),member(23456,'20')])
                if rows != [member(23456,'99')]: group.observe()
                state['rows'] = rows
                with self.assertRaises(control.Blocked): group.stop(deadline=20)
                self.assertEqual([],signals)

    def test_member_pid_reuse_after_leader_exit_cannot_anchor_a_foreign_group(self):
        group,state,signals = self.setup_group([member(),member(23456,'20')])
        group.observe(); state['rows'] = [member(23456,'21')]
        with self.assertRaisesRegex(control.Blocked,'IDENTITY_UNCERTAIN'): group.stop(deadline=20)
        self.assertEqual([],signals)

    def test_escalation_rechecks_identity_and_does_not_kill_a_reused_group(self):
        group,state,signals = self.setup_group([member(),member(23456,'20')]); group.observe()
        state['rows'] = [member(23456,'20')]
        def send(pgid,number):
            signals.append((pgid,number)); state['rows'] = [member(ticks='99')]
        group.signal_group = send
        with self.assertRaisesRegex(control.Blocked,'PID_REUSED'): group.stop(deadline=20)
        self.assertEqual([(PID,signal.SIGTERM)],signals)

    def test_only_the_proven_group_is_escalated_and_must_be_observed_empty(self):
        group,state,signals = self.setup_group([member(),member(23456,'20')]); group.observe()
        state['rows'] = [member(23456,'20')]
        def send(pgid,number):
            signals.append((pgid,number))
            if number == signal.SIGKILL: state['rows'] = []
        group.signal_group = send
        self.assertTrue(group.stop(deadline=20))
        self.assertEqual([(PID,signal.SIGTERM),(PID,signal.SIGKILL)],signals)
        group,state,signals = self.setup_group([member()])
        group.signal_group = lambda pgid,number:signals.append((pgid,number))
        with self.assertRaisesRegex(control.Blocked,'NOT_CLOSED'): group.stop(deadline=20)

    def test_empty_session_requires_no_signal_even_when_original_pid_is_gone(self):
        group,state,signals = self.setup_group([])
        self.assertFalse(group.stop(deadline=20)); self.assertEqual([],signals)

    def test_late_orphan_uses_only_remaining_absolute_deadline(self):
        group,state,signals=self.setup_group([member(),member(23456,'20')]);group.observe()
        state['rows']=[member(23456,'20')];state['time']=294
        group.signal_group=lambda pgid,number:signals.append((state['time'],pgid,number))
        with self.assertRaisesRegex(control.Blocked,'GROUP_BUDGET_EXPIRED'):group.stop(deadline=295)
        self.assertEqual([(294,PID,signal.SIGTERM)],signals)
        self.assertLessEqual(state['time'],295)

    def test_expired_or_nonfinite_group_deadline_never_sends_a_signal(self):
        for deadline in (0,float('inf'),float('nan')):
            group,state,signals=self.setup_group([member(),member(23456,'20')]);group.observe()
            with self.assertRaises(control.Blocked):group.stop(deadline=deadline)
            self.assertEqual([],signals)


class SupervisorFinallyTest(unittest.TestCase):
    def setup_value(self):
        value = Mock(unsafe=True)
        value.args = SimpleNamespace(attempt='fixture',nonce='A'*43)
        value.contract = {'backend':{'commit':'a'*40,'jarSha256':'b'*64},
                          'apk':{'sha256':'c'*64,'publicCaSha256':'d'*64},'moduleSha256':'e'*64}
        value.binding = {'sourceSha':'a'*40}; value.owner_path.exists.return_value = True
        return value

    def test_lost_leader_reconciles_owned_worker_and_still_destroys_owned_infra(self):
        value = self.setup_value(); child = Mock(pid=PID,returncode=1); child.poll.return_value = 1
        witness = Mock(); witness.observe.return_value = [member(23456,'20')]
        with patch.object(control,'extract_node',return_value=Path('/private/fixture/node')), \
             patch.object(control,'process_ticks',return_value='10'), patch.object(control,'OwnedSessionGroup',return_value=witness), \
             patch.object(control.infra,'private_json'), patch.object(control,'session_cleanup') as clean, \
             patch.object(control.signal,'signal'), patch.object(control.subprocess,'Popen',return_value=child), \
             patch.object(control.selectors,'DefaultSelector'):
            with self.assertRaisesRegex(control.Blocked,'CHILD_NOT_CONFIRMED'): control.serve(value)
        witness.stop.assert_called_once(); value.stop.assert_called_once(); clean.assert_called_once()

    def test_unknown_group_does_not_skip_infra_stop_or_delete_private_witnesses(self):
        value = self.setup_value(); child = Mock(pid=PID,returncode=1); child.poll.return_value = 1
        witness = Mock(); witness.stop.side_effect = control.Blocked('SESSION_GROUP_IDENTITY_UNCERTAIN')
        with patch.object(control,'extract_node',return_value=Path('/private/fixture/node')), \
             patch.object(control,'process_ticks',return_value='10'), patch.object(control,'OwnedSessionGroup',return_value=witness), \
             patch.object(control.infra,'private_json'), patch.object(control,'session_cleanup') as clean, \
             patch.object(control.signal,'signal'), patch.object(control.subprocess,'Popen',return_value=child), \
             patch.object(control.selectors,'DefaultSelector'):
            with self.assertRaisesRegex(control.Blocked,'IDENTITY_UNCERTAIN'): control.serve(value)
        value.stop.assert_called_once(); clean.assert_not_called(); value.clean_private_files.assert_not_called()
        child.stdin.close.assert_called_once(); child.stdout.close.assert_called_once()

    def test_claimed_cleanup_cannot_publish_success_if_group_needs_emergency_stop(self):
        value = self.setup_value(); child = Mock(pid=PID,returncode=0)
        child.poll.side_effect = [None,None,0,0,0]
        result = {'cleanupConfirmed':True,'functionalComplete':True}
        scope = {'attemptId':'fixture','sourceSha':'a'*40,'nonce':'A'*43}
        payload = (json.dumps({'event':'BOUND','binding':scope})+'\n'
                   +json.dumps({'event':'CLEANUP_BEGIN','binding':scope,'budgetSeconds':280})+'\n'
                   +json.dumps({'event':'CLEANUP_READY','result':result})+'\n').encode()
        selector = Mock(); selector.select.return_value = [(SimpleNamespace(fileobj=Mock()),None)]
        witness = Mock(); witness.stop.return_value = True
        with patch.object(control,'extract_node',return_value=Path('/private/fixture/node')), \
             patch.object(control,'process_ticks',return_value='10'), patch.object(control,'OwnedSessionGroup',return_value=witness), \
             patch.object(control.infra,'private_json') as write, patch.object(control,'atomic_json'), \
             patch.object(control,'hash_file',return_value='f'*64), patch.object(control,'validate_result',return_value=result), \
             patch.object(control,'session_cleanup'), patch.object(control.signal,'signal'), \
             patch.object(control.subprocess,'Popen',return_value=child), patch.object(control.os,'read',return_value=payload), \
             patch.object(control.selectors,'DefaultSelector',return_value=selector):
            with self.assertRaisesRegex(control.Blocked,'REQUIRED_FORCED_RECOVERY'): control.serve(value)
        self.assertFalse(any(call.args[0].name=='final-state.json' for call in write.call_args_list))
        self.assertEqual(2,value.stop.call_count)

    def test_gate_failure_before_journey_never_spawns_node_and_still_attempts_infra_stop(self):
        value=self.setup_value();value.settle_before_start.side_effect=control.Blocked('VPS_STARTUP_PRESSURE_NOT_SETTLED')
        with patch.object(control,'extract_node',return_value=Path('/private/fixture/node')), \
             patch.object(control,'session_cleanup'),patch.object(control.signal,'signal'), \
             patch.object(control.subprocess,'Popen') as spawn,patch.object(control.selectors,'DefaultSelector'):
            with self.assertRaisesRegex(control.Blocked,'STARTUP_PRESSURE_NOT_SETTLED'):control.serve(value)
        spawn.assert_not_called();value.stop.assert_called_once()

    def test_shutdown_begin_must_match_scope_and_cannot_be_omitted_or_repeated(self):
        scope={'attemptId':'fixture','sourceSha':'a'*40,'nonce':'A'*43}
        bad_sequences=[
            [{'event':'CLEANUP_READY','result':{}}],
            [{'event':'CLEANUP_BEGIN','binding':{**scope,'nonce':'foreign'},'budgetSeconds':280}],
            [{'event':'CLEANUP_BEGIN','binding':scope,'budgetSeconds':280}]*2,
        ]
        for events in bad_sequences:
            value=self.setup_value();child=Mock(pid=PID,returncode=0);child.poll.return_value=None
            payload=('\n'.join(json.dumps(e) for e in [{'event':'BOUND','binding':scope},*events])+'\n').encode()
            selector=Mock();selector.select.return_value=[(SimpleNamespace(fileobj=Mock()),None)]
            witness=Mock();witness.stop.return_value=False
            with patch.object(control,'extract_node',return_value=Path('/private/fixture/node')), \
                 patch.object(control,'process_ticks',return_value='10'),patch.object(control,'OwnedSessionGroup',return_value=witness), \
                 patch.object(control.infra,'private_json') as write,patch.object(control,'atomic_json'), \
                 patch.object(control,'session_cleanup'),patch.object(control.signal,'signal'), \
                 patch.object(control.subprocess,'Popen',return_value=child),patch.object(control.os,'read',return_value=payload), \
                 patch.object(control.selectors,'DefaultSelector',return_value=selector):
                with self.assertRaises(control.Blocked):control.serve(value)
            value.stop.assert_called_once();child.stdin.write.assert_not_called()
            self.assertFalse(any(call.args[0].name=='final-state.json' for call in write.call_args_list))

    def test_expired_shared_deadline_cannot_ack_or_publish_state_but_teardown_is_attempted(self):
        value=self.setup_value();child=Mock(pid=PID,returncode=0);child.poll.return_value=None
        scope={'attemptId':'fixture','sourceSha':'a'*40,'nonce':'A'*43}
        payload=('\n'.join(json.dumps(event) for event in [
            {'event':'BOUND','binding':scope},{'event':'CLEANUP_BEGIN','binding':scope,'budgetSeconds':280},
            {'event':'CLEANUP_READY','result':{}}])+'\n').encode()
        selector=Mock();selector.select.return_value=[(SimpleNamespace(fileobj=Mock()),None)]
        witness=Mock();witness.stop.return_value=False
        budget=Mock(deadline=280);budget.check.side_effect=control.Blocked('SHUTDOWN_BUDGET_EXPIRED')
        with patch.object(control,'extract_node',return_value=Path('/private/fixture/node')), \
             patch.object(control,'process_ticks',return_value='10'),patch.object(control,'OwnedSessionGroup',return_value=witness), \
             patch.object(control.infra,'private_json') as write,patch.object(control,'atomic_json'), \
             patch.object(control,'session_cleanup'),patch.object(control.signal,'signal'), \
             patch.object(control.subprocess,'Popen',return_value=child),patch.object(control.os,'read',return_value=payload), \
             patch.object(control.selectors,'DefaultSelector',return_value=selector),patch.object(control,'ShutdownBudget',return_value=budget):
            with self.assertRaisesRegex(control.Blocked,'SHUTDOWN_BUDGET_EXPIRED'):control.serve(value)
        value.stop.assert_called_once();child.stdin.write.assert_not_called()
        self.assertFalse(any(call.args[0].name=='final-state.json' for call in write.call_args_list))

    def test_status_rejects_surviving_group_even_when_the_original_leader_is_absent(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary); (base/'runtime').mkdir(); (base/'runtime/session-child-private.json').touch()
            value = self.setup_value(); value.resources.return_value = ([],[])
            scope = {'attemptId':'fixture','sourceSha':'a'*40,'nonce':'A'*43}
            def load(path):
                return {'pid':PID,'startTicks':'10'} if path.name=='session-child-private.json' else {'binding':scope}
            with patch.object(control,'HERE',base), patch.object(control,'bound_scope',return_value=scope), \
                 patch.object(control.infra,'load',side_effect=load), patch.object(control.infra,'open_port',return_value=False), \
                 patch.object(control,'process_ticks',return_value=None), patch.object(control,'session_members',return_value=[member(23456,'20')]):
                with self.assertRaisesRegex(control.Blocked,'GROUP_STILL_PRESENT'): control.status(value,'A'*43)

    def test_late_down_and_witnessed_orphan_share_one_absolute_recovery_deadline(self):
        value=self.setup_value();child=Mock(pid=PID,returncode=1);child.poll.return_value=None
        state={'time':0.0,'rows':[member(),member(23456,'20')]};signals=[]
        group=control.OwnedSessionGroup(child,'10',members=lambda _:copy.deepcopy(state['rows']),
            signal_group=lambda pgid,number:signals.append((state['time'],pgid,number)),clock=lambda:state['time'],
            pause=lambda seconds:state.__setitem__('time',state['time']+seconds))
        budget=control.ShutdownBudget(clock=lambda:state['time'])
        def late_down():
            state['time']=max(state['time'],294);state['rows']=[member(23456,'20')];child.poll.return_value=1
        value.stop.side_effect=late_down
        scope={'attemptId':'fixture','sourceSha':'a'*40,'nonce':'A'*43}
        result={'cleanupConfirmed':True,'functionalComplete':True}
        payload=('\n'.join(json.dumps(event) for event in [
            {'event':'BOUND','binding':scope},{'event':'CLEANUP_BEGIN','binding':scope,'budgetSeconds':280},
            {'event':'CLEANUP_READY','result':result}])+'\n').encode()
        selector=Mock();selector.select.return_value=[(SimpleNamespace(fileobj=Mock()),None)]
        with patch.object(control,'extract_node',return_value=Path('/private/fixture/node')), \
             patch.object(control,'process_ticks',return_value='10'),patch.object(control,'OwnedSessionGroup',return_value=group), \
             patch.object(control.infra,'private_json') as write,patch.object(control,'atomic_json'), \
             patch.object(control,'session_cleanup'),patch.object(control.signal,'signal'), \
             patch.object(control.subprocess,'Popen',return_value=child),patch.object(control.os,'read',return_value=payload), \
             patch.object(control.selectors,'DefaultSelector',return_value=selector), \
             patch.object(control,'ShutdownBudget',return_value=budget),patch.object(control,'validate_result',return_value=result):
            with self.assertRaisesRegex(control.Blocked,'GROUP_BUDGET_EXPIRED'):control.serve(value)
        self.assertEqual([(294,PID,signal.SIGTERM)],signals);self.assertLessEqual(state['time'],295)
        self.assertEqual(value.shutdown_budget,budget);self.assertEqual(value.stop.call_count,2)
        child.stdin.write.assert_not_called()
        self.assertFalse(any(call.args[0].name=='final-state.json' for call in write.call_args_list))


if __name__=='__main__': unittest.main()
