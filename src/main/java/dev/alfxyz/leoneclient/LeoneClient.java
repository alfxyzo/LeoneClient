package dev.alfxyz.leoneclient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.item.Item;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import dev.alfxyz.leoneclient.utils.CooldownManager;
import dev.alfxyz.leoneclient.utils.ActionBarStore;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;

public class LeoneClient implements ModInitializer {
    public static final Logger log = LoggerFactory.getLogger("leoneclient");
    public static Boolean onLeoneMC = false;
    public static boolean hasAutoJoinedThisSession = false;
    public static String lastServerAddress = null;
    
    private static Integer requestedEnderchestPage = null;
    
    private static long lastInputTime = System.currentTimeMillis();
    private static long lastAfkCheck = 0;
    private static boolean afkTriggered = false;
    public static KeyBinding rapidTradeKey;
    private static List<RapidTradeInfo> rapidTrades = new ArrayList<>();
    private static boolean cooldownCountdownActionBar = true;
    private static boolean cooldownReadyActionBar = true;
    private static boolean cooldownCountdownChat = false;
    private static boolean cooldownReadyChat = true;
    private static CooldownManager.CustomItem pendingCooldownItem = null;
    private static long pendingCooldownTimestamp = 0;
    private static final long PENDING_COOLDOWN_TIMEOUT = 100; 
    private static boolean pendingCooldownIsLeftClick = false;
    private static boolean prevCageReady = false;
    private static boolean prevCobwebReady = false;
    private static boolean prevPullPushRightReady = false;
    private static boolean prevPullPushLeftReady = false;
    private static CooldownManager.CustomItem pendingCancelItem = null;
    private static int pendingCancelTicks = 0;
    private static boolean pullPushDebounce = false;
 
     @Override
     public void onInitialize() {
         log.info("LeoneClient v{} by Alfxyz has successfully loaded!", Config.version);
 
         Config.create();
        loadRapidTrades();

        ClientPlayConnectionEvents.JOIN.register((ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) -> {
            String prevIp = LeoneClient.lastServerAddress;
            String ip = null;
            if (!MinecraftClient.getInstance().isInSingleplayer()) {
                ip = client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : null;
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
                    String server = dev.alfxyz.leoneclient.LeoneClientConfig.getAutoJoinServer();
                    if (server != null && !server.equals("Remain in hub")) {
                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.networkHandler.sendChatCommand("server " + server);
                                LeoneClient.hasAutoJoinedThisSession = true;
                            }
                        });
                    }
                }
            }
        });

        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LeoneClient.hasAutoJoinedThisSession = false;
            LeoneClient.onLeoneMC = false;
            LeoneClient.lastServerAddress = null;
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
            dispatcher.register(ClientCommandManager.literal("checkcooldown")
                .then(ClientCommandManager.argument("item", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("cage");
                        builder.suggest("cobweb_circle");
                        builder.suggest("pullpush_rod");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        String item = StringArgumentType.getString(ctx, "item");
                        CooldownManager.CustomItem ci = null;
                        if (item.equalsIgnoreCase("cage")) ci = CooldownManager.CustomItem.CAGE;
                        else if (item.equalsIgnoreCase("cobweb_circle")) ci = CooldownManager.CustomItem.COBWEB_CIRCLE;
                        else if (item.equalsIgnoreCase("pullpush_rod")) {
                            long right = CooldownManager.getRemaining(CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT);
                            long left = CooldownManager.getRemaining(CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT);
                            String pullStr = CooldownManager.isReady(CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT) ? "§aReady" : "§e" + CooldownManager.formatTime(right);
                            String pushStr = CooldownManager.isReady(CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT) ? "§aReady" : "§e" + CooldownManager.formatTime(left);
                            String msg = "§b[LeoneClient] §fPull: " + pullStr + " §7| §fPush: " + pushStr;
                            MinecraftClient.getInstance().player.sendMessage(Text.literal(msg));
                            return 1;
                        }
                        if (ci != null) {
                            long rem = CooldownManager.getRemaining(ci);
                            String name = getCustomItemName(ci);
                            if (rem == 0) {
                                MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[LeoneClient] §a" + name + " is ready!"));
                            } else {
                                MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[LeoneClient] §e" + name + " ready in " + CooldownManager.formatTime(rem)));
                            }
                        }
                        return 1;
                    })
                )
            );
            dispatcher.register(ClientCommandManager.literal("checkpullcooldown")
                .executes(ctx -> {
                    long pull = CooldownManager.getRemaining(CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT);
                    String msg = CooldownManager.isReady(CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT)
                        ? "§b[LeoneClient] §aPull ability is ready!"
                        : "§b[LeoneClient] §ePull ability ready in " + CooldownManager.formatTime(pull);
                    MinecraftClient.getInstance().player.sendMessage(Text.literal(msg));
                    return 1;
                })
            );
            dispatcher.register(ClientCommandManager.literal("checkpushcooldown")
                .executes(ctx -> {
                    long push = CooldownManager.getRemaining(CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT);
                    String msg = CooldownManager.isReady(CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT)
                        ? "§b[LeoneClient] §aPush ability is ready!"
                        : "§b[LeoneClient] §ePush ability ready in " + CooldownManager.formatTime(push);
                    MinecraftClient.getInstance().player.sendMessage(Text.literal(msg));
                    return 1;
                })
            );
        });

        
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (requestedEnderchestPage != null && client.currentScreen instanceof GenericContainerScreen screen) {
                if (screen.getScreenHandler() instanceof GenericContainerScreenHandler handler && screen.getTitle().getString().toLowerCase().contains("ender chest")) {
                    int page = requestedEnderchestPage;
                    requestedEnderchestPage = null;
                    
                    int slotIndex = page - 1;
                    if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
                        client.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, client.player);
                    }
                }
            }
        });

        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            long now = System.currentTimeMillis();
            
            if (now - lastInputTime > 50) {
                boolean inputDetected = false;
                
                for (int key = 32; key <= 348; key++) {
                    if (InputUtil.isKeyPressed(client.getWindow().getHandle(), key)) {
                        inputDetected = true;
                        break;
                    }
                }
                
                for (int button = 0; button <= 7; button++) {
                    if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), button) == GLFW.GLFW_PRESS) {
                        inputDetected = true;
                        break;
                    }
                }
                if (inputDetected) {
                    lastInputTime = now;
                    afkTriggered = false;
                }
            }
            
            if (now - lastAfkCheck < 1000) return;
            lastAfkCheck = now;
            if (!LeoneClientConfig.isAfkEnabled()) return;
            int timeoutMs = LeoneClientConfig.getAfkTimeoutMinutes() * 60 * 1000;
            if (!afkTriggered && now - lastInputTime > timeoutMs) {
                client.player.networkHandler.sendChatCommand("afk");
                afkTriggered = true;
            }
        });

        
        rapidTradeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.leoneclient.rapid_trade",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "category.leoneclient"
        ));

        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (rapidTradeKey != null && rapidTradeKey.wasPressed()) {
                System.out.println("Rapid Trade key pressed! Current screen: " + (client.currentScreen == null ? "null" : client.currentScreen.getClass().getName()));
                if (client.currentScreen instanceof GenericContainerScreen screen) {
                    System.out.println("Screen is a GenericContainerScreen, running performRapidTrade...");
                    performRapidTrade(screen);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            ItemStack held = client.player.getMainHandStack();
            if (held == null || held.isEmpty()) return;
            String displayName = held.getName().getString();
            boolean isCage = held.getItem() == Items.RED_STAINED_GLASS && displayName.equalsIgnoreCase("Cage");
            boolean isCobweb = held.getItem() == Items.BONE_MEAL && displayName.equalsIgnoreCase("Cobweb Circle");
            boolean isPullPush = held.getItem() == Items.CARROT_ON_A_STICK && displayName.equalsIgnoreCase("Pull/Push Weapon");
            CooldownManager.CustomItem customItem = null;
            if (isCage) {
                customItem = CooldownManager.CustomItem.CAGE;
            } else if (isCobweb) {
                customItem = CooldownManager.CustomItem.COBWEB_CIRCLE;
            } else if (isPullPush) {
            }
            if (customItem != null) {
                long rem = CooldownManager.getRemaining(customItem);
                boolean ready = CooldownManager.isReady(customItem);
                boolean prevReady = false;
                if (customItem == CooldownManager.CustomItem.CAGE) prevReady = prevCageReady;
                if (customItem == CooldownManager.CustomItem.COBWEB_CIRCLE) prevReady = prevCobwebReady;
                if (!ready) {
                    if (LeoneClientConfig.isCooldownNotifyActionBar() && LeoneClientConfig.isCooldownCountdownActionBar()) {
                        ActionBarStore.setRegularActionBar(Text.literal(displayName + " ready in " + CooldownManager.formatTime(rem)));
                    }
                    if (LeoneClientConfig.isCooldownNotifyChat() && LeoneClientConfig.isCooldownCountdownChat()) {
                        client.player.sendMessage(Text.literal(displayName + " ready in " + CooldownManager.formatTime(rem)));
                    }
                } else if (!prevReady) {
                    if (LeoneClientConfig.isCooldownNotifyActionBar() && LeoneClientConfig.isCooldownReadyActionBar()) {
                        ActionBarStore.setRegularActionBar(Text.literal(displayName + " is ready!"));
                    }
                    if (LeoneClientConfig.isCooldownNotifyChat() && LeoneClientConfig.isCooldownReadyChat()) {
                        client.player.sendMessage(Text.literal(displayName + " is ready!"));
                    }
                }

                if (customItem == CooldownManager.CustomItem.CAGE) prevCageReady = ready;
                if (customItem == CooldownManager.CustomItem.COBWEB_CIRCLE) prevCobwebReady = ready;
            }

            if (isPullPush) {
                long pull = CooldownManager.getRemaining(CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT);
                long push = CooldownManager.getRemaining(CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT);
                boolean pullReady = CooldownManager.isReady(CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT);
                boolean pushReady = CooldownManager.isReady(CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT);
                String pullStr = pullReady ? "§aReady" : "§e" + CooldownManager.formatTime(pull);
                String pushStr = pushReady ? "§aReady" : "§e" + CooldownManager.formatTime(push);
                String msg = "§b[LeoneClient] §fPull: " + pullStr + " §7| §fPush: " + pushStr;
                if (!pullReady || !pushReady) {
                    if (LeoneClientConfig.isCooldownNotifyActionBar() && LeoneClientConfig.isCooldownCountdownActionBar()) {
                        ActionBarStore.setRegularActionBar(Text.literal(msg));
                    }
                    if (LeoneClientConfig.isCooldownNotifyChat() && LeoneClientConfig.isCooldownCountdownChat()) {
                        client.player.sendMessage(Text.literal(msg));
                    }
                } else if ((!prevPullPushRightReady || !prevPullPushLeftReady) && pullReady && pushReady) {
                    String readyMsg = "§b[LeoneClient] §aPull/Push Weapon is ready!";
                    if (LeoneClientConfig.isCooldownNotifyActionBar() && LeoneClientConfig.isCooldownReadyActionBar()) {
                        ActionBarStore.setRegularActionBar(Text.literal(readyMsg));
                    }
                    if (LeoneClientConfig.isCooldownNotifyChat() && LeoneClientConfig.isCooldownReadyChat()) {
                        client.player.sendMessage(Text.literal(readyMsg));
                    }
                }
                prevPullPushRightReady = pullReady;
                prevPullPushLeftReady = pushReady;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            ItemStack held = client.player.getMainHandStack();
            if (held == null || held.isEmpty()) return;
            String displayName = held.getName().getString();
            boolean isCage = held.getItem() == Items.RED_STAINED_GLASS && displayName.equalsIgnoreCase("Cage");
            boolean isCobweb = held.getItem() == Items.BONE_MEAL && displayName.equalsIgnoreCase("Cobweb Circle");
            boolean isPullPush = held.getItem() == Items.CARROT_ON_A_STICK && displayName.equalsIgnoreCase("Pull/Push Weapon");
            if (isPullPush && pullPushDebounce) return;
            if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) {
                if (isPullPush && CooldownManager.isReady(CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT) && pendingCancelTicks == 0) {
                    CooldownManager.startCooldown(CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT);
                    pendingCancelItem = CooldownManager.CustomItem.PULL_PUSH_WEAPON_RIGHT;
                    pendingCancelTicks = 5;
                    pullPushDebounce = true;
                }
                if (isCage && CooldownManager.isReady(CooldownManager.CustomItem.CAGE) && pendingCancelTicks == 0) {
                    CooldownManager.startCooldown(CooldownManager.CustomItem.CAGE);
                    pendingCancelItem = CooldownManager.CustomItem.CAGE;
                    pendingCancelTicks = 5;
                } else if (isCobweb && CooldownManager.isReady(CooldownManager.CustomItem.COBWEB_CIRCLE) && pendingCancelTicks == 0) {
                    CooldownManager.startCooldown(CooldownManager.CustomItem.COBWEB_CIRCLE);
                    pendingCancelItem = CooldownManager.CustomItem.COBWEB_CIRCLE;
                    pendingCancelTicks = 5;
                }
            }
            if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                if (isPullPush && CooldownManager.isReady(CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT) && pendingCancelTicks == 0) {
                    CooldownManager.startCooldown(CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT);
                    pendingCancelItem = CooldownManager.CustomItem.PULL_PUSH_WEAPON_LEFT;
                    pendingCancelTicks = 5;
                    pullPushDebounce = true;
                }
            }
            if (!isPullPush || (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)) {
                pullPushDebounce = false;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingCancelTicks > 0) {
                pendingCancelTicks--;
                if (pendingCancelTicks == 0) {
                    pendingCancelItem = null;
                }
            }
        });

        cooldownCountdownActionBar = true; 
        cooldownReadyActionBar = true;
        cooldownCountdownChat = false;
        cooldownReadyChat = true;
    }
 
    
    public static void performRapidTrade(GenericContainerScreen screen) {
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        for (RapidTradeInfo trade : rapidTrades) {
            Item item = Registries.ITEM.get(Identifier.of("minecraft", trade.item));
            int invSlot = findItemInInventory(client, item, trade.amount);
            if (invSlot != -1) {
                
                client.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(handler.syncId, trade.slot, 0, SlotActionType.PICKUP, client.player);
                
                client.interactionManager.clickSlot(handler.syncId, trade.slot + 7, 0, SlotActionType.PICKUP, client.player);
                
                client.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.PICKUP, client.player);
            }
        }
    }
 
    public static int findItemInInventory(MinecraftClient client, Item item, int amount) {
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            if (client.player.getInventory().getStack(i).getItem() == item && client.player.getInventory().getStack(i).getCount() >= amount) {
                return i + 36; 
            }
        }
        return -1;
    }
 
    private static void loadRapidTrades() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "leoneclient/rapid_trade.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                Type listType = new TypeToken<ArrayList<RapidTradeInfo>>(){}.getType();
                rapidTrades = new Gson().fromJson(reader, listType);
            } catch (IOException e) {
                log.error("Could not read rapid trade config", e);
            }
        }
    }
 
    private static class RapidTradeInfo {
        int slot;
        String item;
        int amount;
    }

    private static boolean hasCustomItemLore(ItemStack stack) {
        NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComponent == null) return false;
        NbtCompound nbt = nbtComponent.copyNbt();
        if (nbt == null) return false;
        if (!nbt.contains("display", 10)) return false;
        NbtCompound display = nbt.getCompound("display");
        if (!display.contains("Lore", 9)) return false;
        NbtList lore = display.getList("Lore", 8); // 8 = String
        if (lore.isEmpty()) return false;
        String first = lore.getString(0);
        return first.toLowerCase().contains("custom item"); // ignore case
    }

    private static String getCustomItemName(CooldownManager.CustomItem item) {
        return switch (item) {
            case CAGE -> "Cage";
            case COBWEB_CIRCLE -> "Cobweb Circle";
            case PULL_PUSH_WEAPON_RIGHT, PULL_PUSH_WEAPON_LEFT -> "Pull/Push Weapon";
        };
    }

    public static void onCustomItemDenial() {
        if (pendingCancelTicks > 0 && pendingCancelItem != null) {
            CooldownManager.resetCooldown(pendingCancelItem);
            pendingCancelItem = null;
            pendingCancelTicks = 0;
        }
    }
}
