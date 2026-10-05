package com.pkmngen.leaks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import kotlin.jvm.internal.Intrinsics;
import leakcanary.ObjectWatcher;
import org.jetbrains.annotations.NotNull;

public final class JvmLeakTracer implements LeakTracer {
   private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
   private final ObjectWatcher objectWatcher;

   public JvmLeakTracer() {
      // The original Kotlin constructor defaults isEnabled to true and schedules
      // retention checks five seconds after an object is watched.
      objectWatcher = new ObjectWatcher(
         System::currentTimeMillis,
         command -> scheduledExecutor.schedule(command, 5L, TimeUnit.SECONDS),
         () -> true
      );
      objectWatcher.addOnObjectRetainedListener(new JvmHeapAnalyzer(objectWatcher));
   }

   @Override
   public void expectWeaklyReachable(@NotNull Object watchedObject, @NotNull String description) {
      Intrinsics.checkNotNullParameter(watchedObject, "watchedObject");
      Intrinsics.checkNotNullParameter(description, "description");
      objectWatcher.expectWeaklyReachable(watchedObject, description);
   }
}
