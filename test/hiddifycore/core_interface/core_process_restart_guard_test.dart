import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/hiddifycore/core_interface/core_process_restart_guard.dart';

void main() {
  test('a delayed restart does not spawn after shutdown', () async {
    final guard = CoreProcessRestartGuard();
    final oldGeneration = guard.currentGeneration;
    guard.invalidate();

    var starts = 0;
    var adopts = 0;
    final result = await guard.launch<int>(
      generation: oldGeneration,
      start: () async {
        starts++;
        return 1;
      },
      discard: (_) async {},
      adopt: (_) => adopts++,
    );

    expect(result, isNull);
    expect(starts, 0);
    expect(adopts, 0);
  });

  test('shutdown during process startup discards instead of adopting it', () async {
    final guard = CoreProcessRestartGuard();
    final pendingStart = Completer<int>();
    final discarded = <int>[];
    final adopted = <int>[];

    final restart = guard.launch<int>(
      generation: guard.currentGeneration,
      start: () => pendingStart.future,
      discard: (process) async => discarded.add(process),
      adopt: adopted.add,
    );
    guard.invalidate();
    pendingStart.complete(42);

    expect(await restart, isNull);
    expect(discarded, [42]);
    expect(adopted, isEmpty);
  });

  test('current generation adopts exactly one running process', () async {
    final guard = CoreProcessRestartGuard();
    final adopted = <int>[];
    final discarded = <int>[];

    final result = await guard.launch<int>(
      generation: guard.currentGeneration,
      start: () async => 99,
      discard: (process) async => discarded.add(process),
      adopt: adopted.add,
    );

    expect(result, 99);
    expect(adopted, [99]);
    expect(discarded, isEmpty);
  });

  test('an adopt failure also cleans up the newly started process', () async {
    final guard = CoreProcessRestartGuard();
    final discarded = <int>[];

    await expectLater(
      guard.launch<int>(
        generation: guard.currentGeneration,
        start: () async => 7,
        discard: (process) async => discarded.add(process),
        adopt: (_) => throw StateError('adopt failed'),
      ),
      throwsStateError,
    );
    expect(discarded, [7]);
  });
  test('failed startup validation rolls back its newly owned resource', () async {
    final guard = CoreProcessRestartGuard();
    var rollbacks = 0;
    final result = await guard.finishSetup(
      generation: guard.currentGeneration,
      initialize: () async => 'gRPC hello failed',
      rollback: () async { rollbacks++; },
    );
    expect(result, 'gRPC hello failed');
    expect(rollbacks, 1);
  });

  test('shutdown during startup validation cancels and rolls back', () async {
    final guard = CoreProcessRestartGuard();
    final pending = Completer<String>();
    var rollbacks = 0;
    final setup = guard.finishSetup(
      generation: guard.currentGeneration,
      initialize: () => pending.future,
      rollback: () async { rollbacks++; },
    );
    guard.invalidate();
    pending.complete('');
    expect(await setup, 'Core setup cancelled');
    expect(rollbacks, 1);
  });

  test('healthy startup does not destroy the adopted process', () async {
    final guard = CoreProcessRestartGuard();
    var rollbacks = 0;
    final result = await guard.finishSetup(
      generation: guard.currentGeneration,
      initialize: () async => '',
      rollback: () async { rollbacks++; },
    );
    expect(result, '');
    expect(rollbacks, 0);
  });

  test('unexpected startup exception still triggers cleanup', () async {
    final guard = CoreProcessRestartGuard();
    var rollbacks = 0;
    await expectLater(
      guard.finishSetup(
        generation: guard.currentGeneration,
        initialize: () async => throw StateError('bad channel'),
        rollback: () async { rollbacks++; },
      ),
      throwsStateError,
    );
    expect(rollbacks, 1);
  });

}
