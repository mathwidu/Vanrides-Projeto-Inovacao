import unittest
import tempfile
from pathlib import Path
from control import PressureGuard, Runtime, OwnedMemoryGuard
from unittest.mock import patch
import control
from protocol import Blocked


class PressureTest(unittest.TestCase):
    def guard(self):
        self.now=0; self.measure=(11260,0.0)
        return PressureGuard(clock=lambda:self.now,snapshot=lambda:self.measure)

    def sample(self,g,t,pressure=0,available=11260):
        self.now,self.measure=t,(available,pressure)
        return g.sample()

    def test_observed_short_warning_with_headroom_recovers(self):
        g=self.guard()
        for t in range(15):
            self.sample(g,t,1.44 if 4<=t<=7 else 0)
        self.assertEqual(g.summary()['minimumAvailableMiB'],11260)
        self.assertEqual(g.summary()['peakFullAvg10'],1.44)

    def test_sustained_warning_still_interrupts_at_ten_seconds(self):
        g=self.guard()
        for t in range(10):self.sample(g,t,1.1)
        with self.assertRaisesRegex(Blocked,'SUSTAINED'):self.sample(g,10,1.1)

    def test_low_headroom_and_critical_stalls_are_immediate(self):
        with self.assertRaisesRegex(Blocked,'HEADROOM_LOW'):self.sample(self.guard(),0,0,2047)
        with self.assertRaisesRegex(Blocked,'CRITICAL'):self.sample(self.guard(),0,5.0)
        self.assertTrue(self.sample(self.guard(),0,0,2048))

    def test_missing_samples_do_not_fabricate_continuous_stalls(self):
        g=self.guard();self.sample(g,0,1.1)
        with self.assertRaisesRegex(Blocked,'OBSERVATION_GAP'):self.sample(g,3,1.1)

    def test_recovery_resets_timer(self):
        g=self.guard()
        for t in range(9):self.sample(g,t,1.1)
        self.sample(g,9,0)
        for t in range(10,19):self.sample(g,t,1.1)
        self.assertIsNotNone(g.warning_since)

    def test_startup_waits_for_ten_normal_seconds_and_has_deadline(self):
        def run(normal_after):
            self.now=0
            r=object.__new__(Runtime);r.deadline=1800
            r.owned_memory={}
            r.pressure_guard=PressureGuard(clock=lambda:self.now,
                snapshot=lambda:(11260,1.2 if self.now<normal_after else 0))
            def advance(seconds):self.now+=seconds
            with patch.object(control.time,'monotonic',side_effect=lambda:self.now), patch.object(control.time,'sleep',side_effect=advance):
                r.settle_before_start()
            return self.now
        self.assertEqual(run(12),22)
        with self.assertRaisesRegex(Blocked,'STARTUP_PRESSURE_NOT_SETTLED'):run(100)

    def test_invalid_measurement_does_not_open_the_gate(self):
        for value in (float('nan'),float('inf'),-1,101):
            with self.assertRaisesRegex(Blocked,'MEASUREMENT'):self.sample(self.guard(),0,value)


class CgroupFilesTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.base=Path(self.temp.name).resolve()
        self.proc=self.base/'proc';self.root=self.base/'cgroup';self.root.mkdir();(self.root/'cgroup.controllers').write_text('memory cpu\n')
        self.cid='a'*64;self.pid=123;self.limit=512*1024**2;self.now=0
        self.relative='/system.slice/docker-'+self.cid+'.scope'
        self.group=self.root/self.relative[1:];self.group.mkdir(parents=True)
        (self.proc/str(self.pid)).mkdir(parents=True)
        self.write_proc('cgroup','0::'+self.relative+'\n')
        self.write_proc('stat','123 (postgres) S '+' '.join(str(n) for n in range(30)))
        self.events=dict.fromkeys(OwnedMemoryGuard.EVENTS,0)
        self.write('memory.max',str(self.limit));self.write('memory.current','1048576');self.write('memory.peak','2097152')
        self.write_events();self.write_psi()
        self.row={'Id':self.cid,'State':{'Pid':self.pid,'Running':True,'OOMKilled':False},'RestartCount':0,'HostConfig':{'Memory':self.limit}}

    def tearDown(self):self.temp.cleanup()
    def write(self,name,value):(self.group/name).write_text(value+'\n')
    def write_proc(self,name,value):(self.proc/str(self.pid)/name).write_text(value)
    def write_events(self):self.write('memory.events','\n'.join(key+' '+str(value) for key,value in self.events.items()))
    def write_psi(self,full=0,some=0,full_total=0,some_total=0):
        self.write('memory.pressure',f'some avg10={some} avg60=0.00 avg300=0.00 total={some_total}\nfull avg10={full} avg60=0.00 avg300=0.00 total={full_total}')
    def guard(self):return OwnedMemoryGuard(self.row,self.limit,11260,self.proc,self.root,clock=lambda:self.now)

    def test_host_pressure_isolated_from_owned_and_headroom_remains_immediate(self):
        owned=self.guard();runtime=object.__new__(Runtime);runtime.deadline=1800
        runtime.owned_memory={'postgres':owned};measure=[11260,80.0]
        runtime.pressure_guard=PressureGuard(clock=lambda:self.now,snapshot=lambda:tuple(measure))
        with patch.object(control.time,'monotonic',side_effect=lambda:self.now):
            for t in range(25):self.now=t;self.assertFalse(runtime.guard())
            measure[0]=2047
            with self.assertRaisesRegex(Blocked,'HEADROOM_LOW'):runtime.guard()
        self.assertEqual(runtime.memory_summary()['peakFullAvg10'],80)
        self.assertEqual(runtime.memory_summary()['owned']['postgres']['peakFullAvg10'],0)

    def test_own_critical_and_sustained_pressure_abort(self):
        owned=self.guard();self.write_psi(full=5,some=6)
        with self.assertRaisesRegex(Blocked,'CRITICAL'):owned.sample(11260)
        self.write_psi();owned=self.guard()
        for t in range(1,12):
            self.now=t;self.write_psi(full=1.1,some=2)
            if t==11:
                with self.assertRaisesRegex(Blocked,'SUSTAINED'):owned.sample(11260)
            else:owned.sample(11260)

    def test_gap_after_warning_is_not_replaced_with_zero(self):
        owned=self.guard();self.now=1;self.write_psi(full=1.1,some=2);owned.sample(11260)
        self.now=4;self.write_psi()
        with self.assertRaisesRegex(Blocked,'OBSERVATION_GAP'):owned.sample(11260)

    def test_partial_stalls_and_current_peak_are_observed_without_a_percentage_threshold(self):
        owned=self.guard();self.now=1;self.write_psi(some=17,full_total=10,some_total=250)
        self.write('memory.current',str(self.limit));self.write('memory.peak',str(self.limit))
        owned.sample(11260);summary=owned.summary()
        self.assertEqual(summary['peakFullAvg10'],0);self.assertEqual(summary['peakSomeAvg10'],17)
        self.assertEqual(summary['someTotalDeltaUs'],250);self.assertEqual(summary['fullTotalDeltaUs'],10)
        self.assertEqual(summary['currentBytes'],self.limit)

    def test_fresh_critical_events_and_increments_abort(self):
        for key in OwnedMemoryGuard.FATAL_EVENTS:
            self.events=dict.fromkeys(OwnedMemoryGuard.EVENTS,0);self.write_events();owned=self.guard()
            self.events[key]=1;self.write_events()
            with self.assertRaisesRegex(Blocked,'MEMORY_EVENT'):owned.sample(11260)
            with self.assertRaisesRegex(Blocked,'EVENTS_NOT_FRESH'):self.guard()

    def test_missing_replaced_or_malformed_metric_and_limit_drift_abort(self):
        for kind in ('missing','replaced','invalid','limit'):
            self.write('memory.current','1048576');self.write('memory.max',str(self.limit));owned=self.guard()
            if kind=='missing':(self.group/'memory.current').unlink()
            elif kind=='replaced':
                (self.group/'new').write_text('123');(self.group/'new').replace(self.group/'memory.current')
            elif kind=='invalid':self.write('memory.current','not-a-number')
            else:self.write('memory.max','max')
            with self.assertRaises(Blocked):owned.sample(11260)

    def test_cgroup_path_directory_or_pid_change_aborts(self):
        owned=self.guard();self.write_proc('cgroup','0::/foreign\n')
        with self.assertRaisesRegex(Blocked,'PROCESS_CHANGED'):owned.sample(11260)
        self.write_proc('cgroup','0::'+self.relative+'\n');owned=self.guard()
        self.write_proc('stat','123 (reused) S '+' '.join('999' for _ in range(30)))
        with self.assertRaisesRegex(Blocked,'PROCESS_CHANGED'):owned.sample(11260)
        self.write_proc('stat','123 (postgres) S '+' '.join(str(n) for n in range(30)));owned=self.guard()
        self.group.rename(self.group.with_name('old'));self.group.mkdir()
        with self.assertRaises(Blocked):owned.sample(11260)

    def test_pid_not_running_oom_restart_or_wrong_limit_cannot_arm(self):
        import copy
        for kind in ('pid','running','oom','restart','limit','id'):
            row=copy.deepcopy(self.row)
            if kind=='pid':row['State']['Pid']=0
            elif kind=='running':row['State']['Running']=False
            elif kind=='oom':row['State']['OOMKilled']=True
            elif kind=='restart':row['RestartCount']=1
            elif kind=='limit':row['HostConfig']['Memory']-=1
            else:row['Id']='b'*64
            with self.assertRaises(Blocked):OwnedMemoryGuard(row,self.limit,11260,self.proc,self.root)

    def test_global_gate_waits_for_normal_window_and_times_out_with_owned_guards_active(self):
        runtime=object.__new__(Runtime);runtime.deadline=1800;runtime.owned_memory={'postgres':self.guard()}
        runtime.pressure_guard=PressureGuard(clock=lambda:self.now,snapshot=lambda:(11260,6 if self.now<12 else 0))
        def advance(seconds):self.now+=seconds
        with patch.object(control.time,'monotonic',side_effect=lambda:self.now),patch.object(control.time,'sleep',side_effect=advance):
            runtime.settle_before_start()
        self.assertEqual(self.now,22)
        self.assertGreater(runtime.owned_memory['postgres'].samples,20)
        runtime.pressure_guard.snapshot=lambda:(11260,2)
        with patch.object(control.time,'monotonic',side_effect=lambda:self.now),patch.object(control.time,'sleep',side_effect=advance):
            with self.assertRaisesRegex(Blocked,'STARTUP_PRESSURE_NOT_SETTLED'):runtime.settle_before_start()

if __name__=='__main__':unittest.main()
