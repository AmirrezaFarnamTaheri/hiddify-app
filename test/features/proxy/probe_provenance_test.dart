import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/proxy/model/probe_provenance.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';

OutboundInfo proxy(int delay, {String ip = '', bool cached = false}) => OutboundInfo(
  tag: 'local-test',
  urlTestDelay: delay,
  ipinfo: IpInfo(ip: ip, fromCache: cached),
);

void main() {
  test('zero native delay is ambiguous between no measurement and failure', () {
    final evidence = ProbeProvenance.fromOutbound(proxy(0));
    expect(evidence.outcome, UrlProbeOutcome.unknown);
    expect(evidence.latencyMs, isNull);
    expect(evidence.observedAt, isNull);
    expect(evidence.provesDeviceWideRouting, isFalse);
  });

  test('successful URL test has core provenance but no invented timestamp', () {
    final evidence = ProbeProvenance.fromOutbound(proxy(83));
    expect(evidence.outcome, UrlProbeOutcome.responded);
    expect(evidence.latencyMs, 83);
    expect(evidence.observedAt, isNull);
    expect(evidence.provesDeviceWideRouting, isFalse);
  });

  test('timed out URL test is a failed probe, not proof server is invalid', () {
    final evidence = ProbeProvenance.fromOutbound(proxy(65000));
    expect(evidence.outcome, UrlProbeOutcome.noReply);
    expect(evidence.latencyMs, isNull);
  });

  test('non-cached IPv4 lookup exposes only its family', () {
    final evidence = ProbeProvenance.fromOutbound(proxy(40, ip: '198.51.100.10'));
    expect(evidence.ipSource, IpProbeSource.nonCached);
    expect(evidence.family, ObservedIpFamily.ipv4);
  });

  test('non-cached IPv6 lookup is clearly distinguished', () {
    final evidence = ProbeProvenance.fromOutbound(proxy(40, ip: '2001:db8::1'));
    expect(evidence.ipSource, IpProbeSource.nonCached);
    expect(evidence.family, ObservedIpFamily.ipv6);
  });

  test('cached address cannot claim a current network family', () {
    final evidence = ProbeProvenance.fromOutbound(proxy(40, ip: '2001:db8::1', cached: true));
    expect(evidence.ipSource, IpProbeSource.cached);
    expect(evidence.family, ObservedIpFamily.unknown);
  });

  test('missing and invalid IP values are never guessed as IPv4', () {
    for (final ip in ['', 'not-an-address', 'some-host.example']) {
      final evidence = ProbeProvenance.fromOutbound(proxy(40, ip: ip));
      expect(evidence.family, ObservedIpFamily.unknown);
    }
  });
}
