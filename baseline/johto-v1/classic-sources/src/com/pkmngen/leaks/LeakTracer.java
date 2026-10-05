package com.pkmngen.leaks;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(
   mv = {1, 9, 0},
   k = 1,
   xi = 48,
   d1 = "\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001:\u0001\u0007J\u0018\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00012\u0006\u0010\u0005\u001a\u00020\u0006H&¨\u0006\b",
   d2 = {"Lcom/pkmngen/leaks/LeakTracer;", "", "expectWeaklyReachable", "", "watchedObject", "description", "", "NoOp", "core-leaks"}
)
public interface LeakTracer {
   void expectWeaklyReachable(@NotNull Object var1, @NotNull String var2);

   @Metadata(
      mv = {1, 9, 0},
      k = 1,
      xi = 48,
      d1 = "\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016¨\u0006\t",
      d2 = {
            "Lcom/pkmngen/leaks/LeakTracer$NoOp;",
            "Lcom/pkmngen/leaks/LeakTracer;",
            "()V",
            "expectWeaklyReachable",
            "",
            "watchedObject",
            "",
            "description",
            "",
            "core-leaks"
      }
   )
   final class NoOp implements LeakTracer {
      @NotNull
      public static final LeakTracer.NoOp INSTANCE = new LeakTracer.NoOp();

      private NoOp() {
      }

      @Override
      public void expectWeaklyReachable(@NotNull Object watchedObject, @NotNull String description) {
         Intrinsics.checkNotNullParameter(watchedObject, "watchedObject");
         Intrinsics.checkNotNullParameter(description, "description");
      }
   }
}
