package com.pkmngen.leaks;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import shark.SharkLog.Logger;

@Metadata(
   mv = {1, 9, 0},
   k = 1,
   xi = 48,
   d1 = "\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0003\n\u0000\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\u0005\u001a\u00020\u0006H\u0016¨\u0006\t",
   d2 = {"Lcom/pkmngen/leaks/SharkLoggerImpl;", "Lshark/SharkLog$Logger;", "()V", "d", "", "message", "", "throwable", "", "jvm-leaks"}
)
public final class SharkLoggerImpl implements Logger {
   @Override
   public void d(@NotNull String message) {
      Intrinsics.checkNotNullParameter(message, "message");
      System.out.println((Object)message);
   }

   @Override
   public void d(@NotNull Throwable throwable, @NotNull String message) {
      Intrinsics.checkNotNullParameter(throwable, "throwable");
      Intrinsics.checkNotNullParameter(message, "message");
      System.out.println((Object)message);
      throwable.printStackTrace(System.out);
   }
}
