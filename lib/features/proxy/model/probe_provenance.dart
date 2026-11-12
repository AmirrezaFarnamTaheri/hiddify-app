import 'dart:io';

import 'package:hiddify/core/model/constants.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';

/// The core reports a URL-test delay, not a device-wide route or DNS leak test.
enum UrlProbeOutcome { unknown, responded, noReply }

/// The core's IP metadata can outlive a network handover when cached.
enum IpProbeSource { unavailable, cached, nonCached }
enum ObservedIpFamily { unknown, ipv4, ipv6 }

/// Read-only interpretation of existing core observations.
///
/// No raw host, URL, credential, IP address or network identifier is retained.
/// The core message contains no observation time, so freshness is always
/// unknown rather than fabricating a "just now" timestamp.
class ProbeProvenance {
  const ProbeProvenance({
    required this.outcome,
    required this.latencyMs,
    required this.ipSource,
    required this.family,
  });

  factory ProbeProvenance.fromOutbound(OutboundInfo outbound) {
    final delay = outbound.urlTestDelay;
    final outcome = switch (delay) {
      0 => UrlProbeOutcome.unknown,
      _ when ConnectionConst.isValidDelay(delay) => UrlProbeOutcome.responded,
      _ => UrlProbeOutcome.noReply,
    };
    final observedIp = InternetAddress.tryParse(outbound.ipinfo.ip);
    final cached = outbound.ipinfo.fromCache;
    final ipSource = observedIp == null
        ? IpProbeSource.unavailable
        : cached
            ? IpProbeSource.cached
            : IpProbeSource.nonCached;
    final family = ipSource != IpProbeSource.nonCached
        ? ObservedIpFamily.unknown
        : observedIp!.type == InternetAddressType.IPv6
            ? ObservedIpFamily.ipv6
            : ObservedIpFamily.ipv4;

    return ProbeProvenance(
      outcome: outcome,
      latencyMs: outcome == UrlProbeOutcome.responded ? delay : null,
      ipSource: ipSource,
      family: family,
    );
  }

  final UrlProbeOutcome outcome;
  final int? latencyMs;
  final IpProbeSource ipSource;
  final ObservedIpFamily family;

  /// No observed-at timestamp is provided with OutboundInfo.urlTestDelay.
  DateTime? get observedAt => null;

  /// URL test success does not establish system-wide routing or DNS privacy.
  bool get provesDeviceWideRouting => false;
}
