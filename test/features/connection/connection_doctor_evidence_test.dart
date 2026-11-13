import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/connection/model/connection_doctor_evidence.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/proxy/model/connection_health.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';

ConnectionHealth health(ConnectionStatus status, {int? urlDelay}) => ConnectionHealth(
  status: status,
  mainGroup: 'select',
  pickTag: 'selected',
  server: urlDelay == null ? null : OutboundInfo(tag: 'selected', urlTestDelay: urlDelay),
  fastest: null,
  hop: null,
  chain: null,
);

void main() {
  test('no core observation is unknown, not a confirmed VPN disconnect', () {
    final evidence = ConnectionDoctorEvidence.from(null);
    expect(evidence.core, DoctorCoreState.unknown);
    expect(evidence.request, DoctorRequestEvidence.unknown);
    expect(evidence.route, DoctorRouteEvidence.unverified);
    expect(evidence.provesNoDnsLeak, isFalse);
  });

  test('core running with no selected request remains unknown', () {
    final evidence = ConnectionDoctorEvidence.from(health(const Waiting(), urlDelay: 0));
    expect(evidence.core, DoctorCoreState.running);
    expect(evidence.request, DoctorRequestEvidence.unknown);
    expect(evidence.route, DoctorRouteEvidence.unverified);
  });

  test('connected core and successful selected URL check do not prove VPN routing', () {
    final evidence = ConnectionDoctorEvidence.from(health(const Connected(), urlDelay: 73));
    expect(evidence.core, DoctorCoreState.running);
    expect(evidence.request, DoctorRequestEvidence.responded);
    expect(evidence.route, DoctorRouteEvidence.unverified);
    expect(evidence.provesTunnelReady, isFalse);
  });

  test('failed core request does not imply operating system routing failed', () {
    final evidence = ConnectionDoctorEvidence.from(health(const NoAvailableProxies(), urlDelay: 65000));
    expect(evidence.core, DoctorCoreState.running);
    expect(evidence.request, DoctorRequestEvidence.noUsableResult);
    expect(evidence.route, DoctorRouteEvidence.unverified);
  });

  test('disconnecting never inherits stale positive selected-request evidence', () {
    final evidence = ConnectionDoctorEvidence.from(health(const Disconnecting(), urlDelay: 60));
    expect(evidence.core, DoctorCoreState.transitioning);
    expect(evidence.request, DoctorRequestEvidence.unknown);
    expect(evidence.route, DoctorRouteEvidence.unverified);
  });

  test('stopped core never advertises retained positive URL results', () {
    final evidence = ConnectionDoctorEvidence.from(health(const Disconnected(), urlDelay: 45));
    expect(evidence.core, DoctorCoreState.stopped);
    expect(evidence.request, DoctorRequestEvidence.unknown);
  });

  test('connecting without core proof stays transitional', () {
    final evidence = ConnectionDoctorEvidence.from(health(const Connecting(), urlDelay: 75));
    expect(evidence.core, DoctorCoreState.transitioning);
    expect(evidence.request, DoctorRequestEvidence.unknown);
  });

  test('hot reload is a running core but does not change route proof', () {
    final evidence = ConnectionDoctorEvidence.from(health(const HotReloading(), urlDelay: 60));
    expect(evidence.core, DoctorCoreState.running);
    expect(evidence.request, DoctorRequestEvidence.responded);
    expect(evidence.route, DoctorRouteEvidence.unverified);
  });
}
