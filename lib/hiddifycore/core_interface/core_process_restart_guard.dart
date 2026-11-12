import 'dart:async';

/// Prevents a delayed or in-flight process start from outliving shutdown or setup.
class CoreProcessRestartGuard {
  int _generation = 0;

  int get currentGeneration => _generation;

  int invalidate() => ++_generation;

  bool isCurrent(int generation) => generation == _generation;

  /// Adopts only while [generation] still owns this process. If ownership
  /// changes during [start], the newly-created process is immediately stopped.
  Future<T?> launch<T extends Object>({
    required int generation,
    required Future<T> Function() start,
    required Future<void> Function(T) discard,
    required void Function(T) adopt,
  }) async {
    if (!isCurrent(generation)) return null;
    final resource = await start();
    if (!isCurrent(generation)) {
      await discard(resource);
      return null;
    }
    try {
      adopt(resource);
    } catch (_) {
      await discard(resource);
      rethrow;
    }
    return resource;
  }
  /// Finalize a started core only if its setup succeeds and the lifecycle is
  /// still current. Failed and stale startups must release newly-owned resources.
  Future<String> finishSetup({
    required int generation,
    required Future<String> Function() initialize,
    required Future<void> Function() rollback,
  }) async {
    String result;
    try {
      result = await initialize();
    } catch (_) {
      await rollback();
      rethrow;
    }
    if (!isCurrent(generation)) {
      await rollback();
      return 'Core setup cancelled';
    }
    if (result.isNotEmpty) {
      await rollback();
    }
    return result;
  }

}
