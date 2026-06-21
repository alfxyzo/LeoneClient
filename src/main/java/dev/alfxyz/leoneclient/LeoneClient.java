package dev.alfxyz.leoneclient;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.alfxyz.leoneclient.utils.ActionBarStore;

public class LeoneClient implements ModInitializer {
    public static final Logger log = LoggerFactory.getLogger("leoneclient");
    public static Boolean onLeoneMC = false;

    public static final RegistryEntry<StatusEffect> COMBAT_TAG_EFFECT = Registry.registerReference(
        Registries.STATUS_EFFECT,
        Identifier.of("leoneclient", "combat_tag"),
        new CombatTagEffect()
    );
    public static boolean hasAutoJoinedThisSession = false;
    public static String lastServerAddress = null;

    private static Integer requestedEnderchestPage = null;
    private static String pendingAutoJoinServer = null;
    private static int pendingAutoJoinTicks = 0;

    public static KeyBinding openConfigKey;

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer("leoneclient")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
        log.info("LeoneClient v{} by Alfxyz has successfully loaded!", version);

        ClientPlayConnectionEvents.JOIN.register((ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) -> {
            String prevIp = LeoneClient.lastServerAddress;
            if (!MinecraftClient.getInstance().isInSingleplayer()) {
                String ip = client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : null;
                LeoneClient.lastServerAddress = ip;
                boolean isLeoneMC = ip != null && (ip.contains("minehut") ||
                    ip.equalsIgnoreCase("play.leonemc.net") ||
                    ip.equalsIgnoreCase("leonemc.net") ||
                    ip.equalsIgnoreCase("leonemc.minehut.gg") ||
                    ip.equals("104.234.169.135"));
                LeoneClient.onLeoneMC = isLeoneMC;

                if (isLeoneMC && (prevIp == null || !(prevIp.contains("minehut") ||
                    prevIp.equalsIgnoreCase("play.leonemc.net") ||
                    prevIp.equalsIgnoreCase("leonemc.net") ||
                    prevIp.equalsIgnoreCase("leonemc.minehut.gg") ||
                    prevIp.equals("104.234.169.135"))) && !LeoneClient.hasAutoJoinedThisSession) {
                    String server = LeoneClientConfig.getAutoJoinServer();
                    if (server != null && !server.equals("Remain in hub")) {
                        LeoneClient.hasAutoJoinedThisSession = true;
                        pendingAutoJoinServer = server;
                        pendingAutoJoinTicks = 5;
                    }
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LeoneClient.hasAutoJoinedThisSession = false;
            LeoneClient.onLeoneMC = false;
            LeoneClient.lastServerAddress = null;
            pendingAutoJoinServer = null;
            pendingAutoJoinTicks = 0;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ec")
                .then(ClientCommandManager.argument("page", IntegerArgumentType.integer(1, 9))
                    .executes(ctx -> {
                        int page = IntegerArgumentType.getInteger(ctx, "page");
                        requestedEnderchestPage = page;
                        MinecraftClient.getInstance().player.networkHandler.sendChatCommand("enderchest");
                        return 1;
                    })
                )
            );
            dispatcher.register(ClientCommandManager.literal("enderchest")
                .then(ClientCommandManager.argument("page", IntegerArgumentType.integer(1, 9))
                    .executes(ctx -> {
                        int page = IntegerArgumentType.getInteger(ctx, "page");
                        requestedEnderchestPage = page;
                        MinecraftClient.getInstance().player.networkHandler.sendChatCommand("enderchest");
                        return 1;
                    })
                )
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingAutoJoinServer != null && client.player != null) {
                if (pendingAutoJoinTicks > 0) {
                    pendingAutoJoinTicks--;
                } else {
                    client.player.networkHandler.sendChatCommand("server " + pendingAutoJoinServer);
                    pendingAutoJoinServer = null;
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (requestedEnderchestPage != null && client.currentScreen instanceof GenericContainerScreen screen) {
                if (screen.getScreenHandler() instanceof GenericContainerScreenHandler handler
                        && screen.getTitle().getString().toLowerCase().contains("ender chest")) {
                    int page = requestedEnderchestPage;
                    requestedEnderchestPage = null;
                    int slotIndex = page - 1;
                    if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
                        client.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, client.player);
                    }
                }
            }
        });

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.leoneclient.open_config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KeyBinding.Category.create(Identifier.of("leoneclient", "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openConfigKey.wasPressed() && client.currentScreen == null) {
                client.setScreen(new LeoneClientMainConfigScreen(null));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (!LeoneClientConfig.isCombatTimerWidget() || !ActionBarStore.isCombatTagActive()) {
                client.player.removeStatusEffect(COMBAT_TAG_EFFECT);
                return;
            }
            int remainingTicks = Math.max(1, Math.round(ActionBarStore.getCombatTagRemainingSeconds() * 20));
            StatusEffectInstance current = client.player.getStatusEffect(COMBAT_TAG_EFFECT);
            if (current == null || Math.abs(current.getDuration() - remainingTicks) > 10) {
                client.player.addStatusEffect(new StatusEffectInstance(
                    COMBAT_TAG_EFFECT,
                    remainingTicks,
                    0,
                    false,
                    false,
                    true
                ));
            }
        });

        HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, old -> (context, tickCounter) -> {
            if (MinecraftClient.getInstance().currentScreen != null) return;
            if (!LeoneClientConfig.isActionBarEnabled()) {
                old.render(context, tickCounter);
                return;
            }

            int screenW = context.getScaledWindowWidth();
            int screenH = context.getScaledWindowHeight();
            int cx = screenW / 2;
            net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;

            context.createNewRootLayer();

            Text regular = ActionBarStore.getRegularActionBar();
            Text combat  = ActionBarStore.getCombatActionBar();

            boolean hideCombat = LeoneClientConfig.isCombatTimerWidget()
                && LeoneClientConfig.isHideCombatBarWhenEffect()
                && ActionBarStore.getCombatActionBar() != null;

            if (LeoneClientConfig.getMergeActionBars()) {
                Text effectiveCombat = hideCombat ? null : combat;
                Text combined = null;
                if (regular != null && effectiveCombat != null) {
                    combined = regular.copy().append(Text.literal("  §7|  ")).append(effectiveCombat);
                } else if (regular != null) {
                    combined = regular;
                } else if (effectiveCombat != null) {
                    combined = effectiveCombat;
                }
                if (combined != null) {
                    context.drawCenteredTextWithShadow(tr, combined, cx, screenH - LeoneClientConfig.getRegularActionBarY(), 0xFFFFFFFF);
                }
            } else {
                if (regular != null) {
                    context.drawCenteredTextWithShadow(tr, regular, cx, screenH - LeoneClientConfig.getRegularActionBarY(), 0xFFFFAA00);
                }
                if (combat != null && !hideCombat) {
                    context.drawCenteredTextWithShadow(tr, combat, cx, screenH - LeoneClientConfig.getCombatActionBarY(), 0xFFFF5555);
                }
            }
        });
    }
}
