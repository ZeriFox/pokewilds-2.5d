package com.pkmngen.leaks;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

public final class HotSpotHeapDumper {
   @NotNull
   public static final HotSpotHeapDumper INSTANCE = new HotSpotHeapDumper();

   private static final Lazy<HotSpotDiagnosticMXBean> mBean$delegate = LazyKt.lazy(() -> {
      try {
         return ManagementFactory.newPlatformMXBeanProxy(
            ManagementFactory.getPlatformMBeanServer(),
            "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean.class
         );
      } catch (IOException exception) {
         throw HotSpotHeapDumper.<RuntimeException>propagate(exception);
      }
   });

   private HotSpotHeapDumper() {
   }

   public final void dumpHeap(@NotNull String fileName) {
      Intrinsics.checkNotNullParameter(fileName, "fileName");
      try {
         mBean$delegate.getValue().dumpHeap(fileName, true);
      } catch (IOException exception) {
         throw HotSpotHeapDumper.<RuntimeException>propagate(exception);
      }
   }

   // Kotlin propagates checked exceptions without a throws declaration. Keep the
   // original public Java signature and original exception type on failure.
   @SuppressWarnings("unchecked")
   private static <E extends Throwable> RuntimeException propagate(Throwable exception) throws E {
      throw (E) exception;
   }
}
