package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynresources.DynamicModelSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Mixin(ClientItemInfoLoader.class)
@ClientOnlyMixin
public abstract class MixinClientItemInfoLoader {
    @Unique
    private static final Method MFIX$LOAD_PENDING;

    @Unique
    private static final Method MFIX$GET_CLIENT_ITEM_INFO;

    @Unique
    private static volatile boolean MFIX$DYNAMIC_CLIENT_ITEMS_ENABLED = true;

    @Unique
    private static volatile boolean MFIX$DYNAMIC_CLIENT_ITEMS_FAILURE_LOGGED = false;

    static {
        Method loadPending = null;
        Method getClientInfo = null;
        try {
            for (Method method : ClientItemInfoLoader.class.getDeclaredMethods()) {
                if (method.getName().equals("lambda$scheduleLoad$3")
                        && Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 3) {
                    method.setAccessible(true);
                    loadPending = method;
                    Class<?> pendingType = method.getReturnType();
                    Method clientItemAccessor = pendingType.getDeclaredMethod("clientItemInfo");
                    clientItemAccessor.setAccessible(true);
                    getClientInfo = clientItemAccessor;
                    break;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        MFIX$LOAD_PENDING = loadPending;
        MFIX$GET_CLIENT_ITEM_INFO = getClientInfo;
    }

    /**
     * @author embeddedt
     * @reason Load client item infos dynamically instead of all at once.
     */
    @ModifyArg(method = "scheduleLoad", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;"))
    private static Function<Map<Identifier, Resource>, ? extends CompletionStage<ClientItemInfoLoader.LoadedClientInfos>> skipAOTClientItemLoad(
            Function<Map<Identifier, Resource>, ? extends CompletionStage<ClientItemInfoLoader.LoadedClientInfos>> original,
            @Local(ordinal = 0) RegistryAccess.Frozen staticRegistries) {
        if (!MFIX$DYNAMIC_CLIENT_ITEMS_ENABLED || MFIX$LOAD_PENDING == null || MFIX$GET_CLIENT_ITEM_INFO == null) {
            return original;
        }
        return resourceMap -> CompletableFuture.completedFuture(DynamicModelSystem.createDynamicClientInfos(resourceMap, (resourceFileId, resource) -> {
            if (!MFIX$DYNAMIC_CLIENT_ITEMS_ENABLED) {
                return null;
            }
            try {
                Object pendingLoad = MFIX$LOAD_PENDING.invoke(null, resourceFileId, resource, staticRegistries);
                if (pendingLoad == null) {
                    return null;
                }
                return (ClientItem)MFIX$GET_CLIENT_ITEM_INFO.invoke(pendingLoad);
            } catch (ReflectiveOperationException e) {
                MFIX$DYNAMIC_CLIENT_ITEMS_ENABLED = false;
                if (!MFIX$DYNAMIC_CLIENT_ITEMS_FAILURE_LOGGED) {
                    MFIX$DYNAMIC_CLIENT_ITEMS_FAILURE_LOGGED = true;
                    ModernFix.LOGGER.warn("Disabling dynamic client item info loading due to reflection failure", e);
                }
                return null;
            } catch (RuntimeException e) {
                MFIX$DYNAMIC_CLIENT_ITEMS_ENABLED = false;
                if (!MFIX$DYNAMIC_CLIENT_ITEMS_FAILURE_LOGGED) {
                    MFIX$DYNAMIC_CLIENT_ITEMS_FAILURE_LOGGED = true;
                    ModernFix.LOGGER.warn("Disabling dynamic client item info loading due to runtime failure", e);
                }
                return null;
            }
        }));
    }
}