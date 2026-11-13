import 'package:hiddify/features/proxy/model/connection_health.dart';
import 'package:hiddify/features/proxy/model/probe_provenance.dart';

/// Core status and a selected-proxy URL result are not OS-level tunnel proof.
enum DoctorCoreState { unknown, stopped, transitioning, running }
enum DoctorRequestEvidence { unknown, responded, noUsableResult }
enum DoctorRouteEvidence { unverified }

/// Read-only evidence available to the connection doctor on every platform.
///
/// Neither the connection status nor the native URL-test delay contains an
/// authoritative OS VPN route check or a DNS-leak test. Those fields stay
/// UNKNOWN until a separate, consented platform measurement exists.
class ConnectionDoctorEvidence {
  const ConnectionDoctorEvidence({
    required this.core,
    required this.request,
    required this.route,
  });

  factory ConnectionDoctorEvidence.from(ConnectionHealth? health) {
    if (health == null) {
      return const ConnectionDoctorEvidence(
        core: DoctorCoreState.unknown,
        request: DoctorRequestEvidence.unknown,
        route: DoctorRouteEvidence.unverified,
      );
    }

    final status = health.status;
    final core = status.isRunning
        ? DoctorCoreState.running
        : status.isDisconnected
            ? DoctorCoreState.stopped
            : status.isSwitching
                ? DoctorCoreState.transitioning
                : DoctorCoreState.unknown;

    // The native group list may retain a previous URL-test result after stop.
    // Only show a selected-proxy result while the core is currently running.
    final selected = status.isRunning && health.server != null
        ? ProbeProvenance.fromOutbound(health.server!)
        : null;
    final request = switch (selected?.outcome) {
      UrlProbeOutcome.responded => DoctorRequestEvidence.responded,
      UrlProbeOutcome.noReply => DoctorRequestEvidence.noUsableResult,
      _ => DoctorRequestEvidence.unknown,
    };

    return ConnectionDoctorEvidence(
      core: core,
      request: request,
      route: DoctorRouteEvidence.unverified,
    );
  }

  final DoctorCoreState core;
  final DoctorRequestEvidence request;
  final DoctorRouteEvidence route;

  bool get provesTunnelReady => false;
  bool get provesNoDnsLeak => false;
}
