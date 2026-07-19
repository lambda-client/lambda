
package com.minato.mixin;

import com.minato.Minato;
import com.minato.config.EntryLayer;
import com.minato.module.Module;
import com.minato.module.ModuleRegistry;
import com.minato.util.DynamicExceptionKt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kotlin.Unit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.ReportType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

// Modify the crash report behavior for dynamic remapping and GitHub issue link
@Mixin(CrashReport.class)
public class CrashReportMixin {
    @Mutable
    @Shadow @Final private Throwable cause;

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/Throwable;)V", at = @At("TAIL"))
    void injectConstructor(String message, Throwable cause, CallbackInfo ci) {
        if (!Minato.INSTANCE.isDebug() && MinecraftClient.getInstance() != null) {
            this.cause = DynamicExceptionKt.dynamicException(cause);
        }
    }

    @WrapMethod(method = "asString(Lnet/minecraft/util/crash/ReportType;Ljava/util/List;)Ljava/lang/String;")
    String injectString(ReportType type, List<String> extraInfo, Operation<String> original) {
        var list = new ArrayList<>(extraInfo);
        list.add("If this issue is related to Minato, check if other users have experienced this too, or create a new issue at " + Minato.REPO_URL + "/issues.\n\n");

        if (MinecraftClient.getInstance() != null) {
            list.add("Enabled modules:");

            ModuleRegistry.INSTANCE.getModules()
                    .stream()
                    .filter(Module::isEnabled)
                    .forEach(module -> {
                        list.add(String.format("\t%s", module.getName()));

                        module.getSettingLayers().forEachEntry(
                                true,
                                null,
                                (path, single) -> {
                                    final var setting = single.getEntry();
                                    if (setting.isModified()) {
                                        list.add("\t\t" + String.join(".", path.stream().map(EntryLayer.Multiple::getName).toList()) + "." + setting.getName() + " -> " + setting.getValue());
                                    }
                                    return Unit.INSTANCE;
                                }
                        );
                    });
        }

        list.add("\n" + "-".repeat(43) + "\n");

        return original.call(type, list);
    }
}
