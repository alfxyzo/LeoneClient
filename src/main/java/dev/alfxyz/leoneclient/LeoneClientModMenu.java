package dev.alfxyz.leoneclient;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import dev.alfxyz.leoneclient.LeoneClientConfig;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TabButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ScrollableWidget;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class LeoneClientModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new LeoneClientMainConfigScreen(parent);
    }
}

class LeoneClientMainConfigScreen extends Screen {
    private final Screen parent;
    private CyclingButtonWidget<String> serverSelectButton;
    private ButtonWidget actionBarButton;
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    private String selectedServer;
    private ButtonWidget afkToggleButton;
    private TextFieldWidget afkTimeoutField;
    private boolean afkEnabled;
    private int afkTimeoutMinutes;
    private ButtonWidget cooldownActionBarToggleButton;
    private ButtonWidget cooldownChatToggleButton;
    private boolean cooldownNotifyActionBar;
    private boolean cooldownNotifyChat;
    private ButtonWidget cooldownCountdownActionBarButton;
    private ButtonWidget cooldownReadyActionBarButton;
    private ButtonWidget cooldownCountdownChatButton;
    private ButtonWidget cooldownReadyChatButton;
    private boolean cooldownCountdownActionBar;
    private boolean cooldownReadyActionBar;
    private boolean cooldownCountdownChat;
    private boolean cooldownReadyChat;
    private boolean mergeActionBars;
    private TextFieldWidget regularYField;
    private TextFieldWidget combatYField;
    private int selectedTab = 0; // 0: General, 1: Cooldowns, 2: Action Bar
    private ButtonWidget generalTab, cooldownsTab, actionBarTab;
    private static final String[] SERVER_OPTIONS = {
        "Remain in hub",
        "ElytraBox",
        "Wildkits",
        "Lifesteal",
        "MoneyDupe",
        "InsaneKits",
        "Survival",
        "KnockbackFFA",
        "GunCube",
        "PVP"
    };

    public LeoneClientMainConfigScreen(Screen parent, int selectedTab) {
        super(Text.literal("LeoneClient Settings"));
        this.parent = parent;
        this.selectedTab = selectedTab;
        this.selectedServer = LeoneClientConfig.getAutoJoinServer();
        this.afkEnabled = LeoneClientConfig.isAfkEnabled();
        this.afkTimeoutMinutes = LeoneClientConfig.getAfkTimeoutMinutes();
        this.cooldownNotifyActionBar = LeoneClientConfig.isCooldownNotifyActionBar();
        this.cooldownNotifyChat = LeoneClientConfig.isCooldownNotifyChat();
        this.cooldownCountdownActionBar = LeoneClientConfig.isCooldownCountdownActionBar();
        this.cooldownReadyActionBar = LeoneClientConfig.isCooldownReadyActionBar();
        this.cooldownCountdownChat = LeoneClientConfig.isCooldownCountdownChat();
        this.cooldownReadyChat = LeoneClientConfig.isCooldownReadyChat();
        this.mergeActionBars = LeoneClientConfig.getMergeActionBars();
    }

    protected LeoneClientMainConfigScreen(Screen parent) {
        this(parent, 0);
    }

    @Override
    protected void init() {
        this.clearChildren();
        int centerX = this.width / 2;
        int y = 40;
        // Tabs (use ButtonWidget instead of TabButtonWidget)
        ButtonWidget generalTab = ButtonWidget.builder(Text.literal("General"), btn -> { this.client.setScreen(new LeoneClientMainConfigScreen(parent, 0)); }).dimensions(centerX - 150, y, 100, 20).build();
        ButtonWidget cooldownsTab = ButtonWidget.builder(Text.literal("Cooldowns"), btn -> { this.client.setScreen(new LeoneClientMainConfigScreen(parent, 1)); }).dimensions(centerX - 50, y, 100, 20).build();
        ButtonWidget actionBarTab = ButtonWidget.builder(Text.literal("Action Bar"), btn -> { this.client.setScreen(new LeoneClientActionBarConfigScreen(parent)); }).dimensions(centerX + 50, y, 100, 20).build();
        this.addDrawableChild(generalTab);
        this.addDrawableChild(cooldownsTab);
        this.addDrawableChild(actionBarTab);
        y += 30;
        if (selectedTab == 0) {
            // General tab content (server, afk)
            // Title
            this.addDrawableChild(ButtonWidget.builder(Text.literal("LeoneClient Settings").formatted(Formatting.BOLD), btn -> {}).dimensions(centerX - 100, y, 200, 20).build()).active = false;
            y += 30;
            // Group: Server
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Server Options").formatted(Formatting.UNDERLINE), btn -> {}).dimensions(centerX - 100, y, 200, 20).build()).active = false;
            y += 25;
            this.serverSelectButton = CyclingButtonWidget.builder((String s) -> Text.literal(s))
                .values(SERVER_OPTIONS)
                .initially(selectedServer)
                .build(centerX - 100, y, 200, 20, Text.literal("Auto Join"), (btn, value) -> {
                    selectedServer = value;
                    btn.setMessage(Text.literal("Auto Join: " + value));
                });
            this.addDrawableChild(this.serverSelectButton);
            y += 35;
            // Group: AFK
            this.addDrawableChild(ButtonWidget.builder(Text.literal("AFK Options").formatted(Formatting.UNDERLINE), btn -> {}).dimensions(centerX - 100, y, 200, 20).build()).active = false;
            y += 25;
            this.afkToggleButton = ButtonWidget.builder(Text.literal("Auto AFK: " + (afkEnabled ? "ON" : "OFF")), btn -> {
                afkEnabled = !afkEnabled;
                btn.setMessage(Text.literal("Auto AFK: " + (afkEnabled ? "ON" : "OFF")));
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Automatically run /afk after being idle for a set time"))).build();
            this.addDrawableChild(this.afkToggleButton);
            y += 25;
            this.afkTimeoutField = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal("AFK Timeout (min)"));
            this.afkTimeoutField.setText(String.valueOf(afkTimeoutMinutes));
            this.afkTimeoutField.setChangedListener(s -> {
                try { afkTimeoutMinutes = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            });
            this.afkTimeoutField.setTooltip(Tooltip.of(Text.literal("Minutes of inactivity before auto AFK")));
            this.addDrawableChild(this.afkTimeoutField);
            y += 35;
        } else if (selectedTab == 1) {
            // Cooldowns tab content (cooldown notification toggles)
            // Group: Cooldown Notifications
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Cooldown Notifications").formatted(Formatting.UNDERLINE), btn -> {}).dimensions(centerX - 100, y, 200, 20).build()).active = false;
            y += 25;
            this.cooldownActionBarToggleButton = ButtonWidget.builder(Text.literal("Cooldown Action Bar: " + (cooldownNotifyActionBar ? "ON" : "OFF")), btn -> {
                cooldownNotifyActionBar = !cooldownNotifyActionBar;
                btn.setMessage(Text.literal("Cooldown Action Bar: " + (cooldownNotifyActionBar ? "ON" : "OFF")));
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Show cooldowns in the action bar")))
            .build();
            this.addDrawableChild(this.cooldownActionBarToggleButton);
            y += 25;
            this.cooldownChatToggleButton = ButtonWidget.builder(Text.literal("Cooldown Chat: " + (cooldownNotifyChat ? "ON" : "OFF")), btn -> {
                cooldownNotifyChat = !cooldownNotifyChat;
                btn.setMessage(Text.literal("Cooldown Chat: " + (cooldownNotifyChat ? "ON" : "OFF")));
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Show cooldowns in chat")))
            .build();
            this.addDrawableChild(this.cooldownChatToggleButton);
            y += 25;
            this.cooldownCountdownActionBarButton = ButtonWidget.builder(Text.literal("Action Bar Countdown: " + (cooldownCountdownActionBar ? "ON" : "OFF")), btn -> {
                cooldownCountdownActionBar = !cooldownCountdownActionBar;
                btn.setMessage(Text.literal("Action Bar Countdown: " + (cooldownCountdownActionBar ? "ON" : "OFF")));
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Show countdown in action bar")))
            .build();
            this.addDrawableChild(this.cooldownCountdownActionBarButton);
            y += 25;
            this.cooldownReadyActionBarButton = ButtonWidget.builder(Text.literal("Action Bar Ready: " + (cooldownReadyActionBar ? "ON" : "OFF")), btn -> {
                cooldownReadyActionBar = !cooldownReadyActionBar;
                btn.setMessage(Text.literal("Action Bar Ready: " + (cooldownReadyActionBar ? "ON" : "OFF")));
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Show ready message in action bar")))
            .build();
            this.addDrawableChild(this.cooldownReadyActionBarButton);
            y += 25;
            this.cooldownCountdownChatButton = ButtonWidget.builder(Text.literal("Chat Countdown: " + (cooldownCountdownChat ? "ON" : "OFF")), btn -> {
                cooldownCountdownChat = !cooldownCountdownChat;
                btn.setMessage(Text.literal("Chat Countdown: " + (cooldownCountdownChat ? "ON" : "OFF")));
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Show countdown in chat")))
            .build();
            this.addDrawableChild(this.cooldownCountdownChatButton);
            y += 25;
            this.cooldownReadyChatButton = ButtonWidget.builder(Text.literal("Chat Ready: " + (cooldownReadyChat ? "ON" : "OFF")), btn -> {
                cooldownReadyChat = !cooldownReadyChat;
                btn.setMessage(Text.literal("Chat Ready: " + (cooldownReadyChat ? "ON" : "OFF")));
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Show ready message in chat")))
            .build();
            this.addDrawableChild(this.cooldownReadyChatButton);
            y += 35;
        } else if (selectedTab == 2) {
            int y0 = y;
            ButtonWidget actionBarFeatureToggle = ButtonWidget.builder(Text.literal("Action Bar Feature: " + (LeoneClientConfig.isActionBarEnabled() ? "ON" : "OFF")), btn -> {
                LeoneClientConfig.setActionBarEnabled(!LeoneClientConfig.isActionBarEnabled());
                btn.setMessage(Text.literal("Action Bar Feature: " + (LeoneClientConfig.isActionBarEnabled() ? "ON" : "OFF")));
                this.init();
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Enable or disable all LeoneClient action bar features"))).build();
            this.addDrawableChild(actionBarFeatureToggle);
            y += 30;
            ButtonWidget mergeToggleButton = ButtonWidget.builder(Text.literal("Merge Action Bars: " + (mergeActionBars ? "ON" : "OFF")), btn -> {
                mergeActionBars = !mergeActionBars;
                btn.setMessage(Text.literal("Merge Action Bars: " + (mergeActionBars ? "ON" : "OFF")));
                if (mergeActionBars) {
                    combatYField = null; // Hide combat Y field if merging
                    this.init(); // Re-init to update tab content
                }
            }).dimensions(centerX - 100, y, 200, 20).build();
            this.addDrawableChild(mergeToggleButton);
            y += 30;
            this.regularYField = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal(mergeActionBars ? "Action Bar Y" : "Regular Action Bar Y"));
            this.regularYField.setText(String.valueOf(LeoneClientConfig.getRegularActionBarY()));
            this.regularYField.setChangedListener(s -> {
                try {
                    LeoneClientConfig.setRegularActionBarY(Integer.parseInt(s));
                } catch (NumberFormatException ignored) {}
            });
            this.addDrawableChild(this.regularYField);
            y += 30;
            if (!mergeActionBars) {
                this.combatYField = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal("Combat Action Bar Y"));
                this.combatYField.setText(String.valueOf(LeoneClientConfig.getCombatActionBarY()));
                this.combatYField.setChangedListener(s -> {
                    try {
                        LeoneClientConfig.setCombatActionBarY(Integer.parseInt(s));
                    } catch (NumberFormatException ignored) {}
                });
                this.addDrawableChild(this.combatYField);
                y += 30;
            }
            this.actionBarButton = ButtonWidget.builder(Text.literal("Edit Action Bars"), btn -> {
                this.client.setScreen(new LeoneClientActionBarConfigScreen(this));
            }).dimensions(centerX - 100, y, 200, 20).tooltip(Tooltip.of(Text.literal("Configure action bar positions")))
            .build();
            this.addDrawableChild(this.actionBarButton);
            y += 40;
        }
        // Save/Cancel always at bottom
        int bottomY = this.height - 60;
        this.saveButton = ButtonWidget.builder(Text.literal("Save"), btn -> {
            LeoneClientConfig.setAutoJoinServer(selectedServer);
            LeoneClientConfig.setAfkEnabled(afkEnabled);
            LeoneClientConfig.setAfkTimeoutMinutes(afkTimeoutMinutes);
            LeoneClientConfig.setCooldownNotifyActionBar(cooldownNotifyActionBar);
            LeoneClientConfig.setCooldownNotifyChat(cooldownNotifyChat);
            LeoneClientConfig.setCooldownCountdownActionBar(cooldownCountdownActionBar);
            LeoneClientConfig.setCooldownReadyActionBar(cooldownReadyActionBar);
            LeoneClientConfig.setCooldownCountdownChat(cooldownCountdownChat);
            LeoneClientConfig.setCooldownReadyChat(cooldownReadyChat);
            LeoneClientConfig.setMergeActionBars(mergeActionBars);
            LeoneClientConfig.setRegularActionBarY(LeoneClientConfig.getRegularActionBarY());
            LeoneClientConfig.setCombatActionBarY(LeoneClientConfig.getCombatActionBarY());
            this.client.setScreen(this.parent);
        }).dimensions(this.width - 120, bottomY, 100, 20).build();
        this.addDrawableChild(this.saveButton);
        this.cancelButton = ButtonWidget.builder(Text.literal("Cancel"), btn -> this.client.setScreen(this.parent)).dimensions(20, bottomY, 100, 20).build();
        this.addDrawableChild(this.cancelButton);
    }
}

class LeoneClientActionBarConfigScreen extends Screen {
    private final Screen parent;
    private int regularActionBarY;
    private int combatActionBarY;
    private boolean mergeActionBars;
    private TextFieldWidget regularYField;
    private TextFieldWidget combatYField;
    private ButtonWidget mergeToggleButton;
    private ButtonWidget saveButton;
    private ButtonWidget backButton;
    private boolean draggingRegular = false;
    private boolean draggingCombat = false;
    private int dragOffset = 0;
    private ButtonWidget generalTab, cooldownsTab, actionBarTab;

    protected LeoneClientActionBarConfigScreen(Screen parent) {
        super(Text.literal("Edit Action Bars"));
        this.parent = parent;
        this.regularActionBarY = LeoneClientConfig.getRegularActionBarY();
        this.combatActionBarY = LeoneClientConfig.getCombatActionBarY();
        this.mergeActionBars = LeoneClientConfig.getMergeActionBars();
    }

    @Override
    protected void init() {
        this.clearChildren();
        int centerX = this.width / 2;
        int y = 40;
        // Tabs at the top
        generalTab = ButtonWidget.builder(Text.literal("General"), btn -> { this.client.setScreen(new LeoneClientMainConfigScreen(parent, 0)); }).dimensions(centerX - 150, y, 100, 20).build();
        cooldownsTab = ButtonWidget.builder(Text.literal("Cooldowns"), btn -> { this.client.setScreen(new LeoneClientMainConfigScreen(parent, 1)); }).dimensions(centerX - 50, y, 100, 20).build();
        actionBarTab = ButtonWidget.builder(Text.literal("Action Bar"), btn -> {}).dimensions(centerX + 50, y, 100, 20).build();
        this.addDrawableChild(generalTab);
        this.addDrawableChild(cooldownsTab);
        this.addDrawableChild(actionBarTab);
        y += 30;
        this.mergeToggleButton = ButtonWidget.builder(Text.literal("Merge Action Bars: " + (mergeActionBars ? "ON" : "OFF")), btn -> {
            mergeActionBars = !mergeActionBars;
            btn.setMessage(Text.literal("Merge Action Bars: " + (mergeActionBars ? "ON" : "OFF")));
            if (mergeActionBars) draggingCombat = false;
            this.init(); // Refresh UI and fields to match new merge state
        }).dimensions(centerX - 100, y, 200, 20).build();
        this.addDrawableChild(this.mergeToggleButton);
        y += 30;
        this.regularYField = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal(mergeActionBars ? "Action Bar Y" : "Regular Action Bar Y"));
        this.regularYField.setText(String.valueOf(regularActionBarY));
        this.regularYField.setChangedListener(s -> {
            try {
                regularActionBarY = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(this.regularYField);
        y += 30;
        if (!mergeActionBars) {
            this.combatYField = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal("Combat Action Bar Y"));
            this.combatYField.setText(String.valueOf(combatActionBarY));
            this.combatYField.setChangedListener(s -> {
                try {
                    combatActionBarY = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });
            this.addDrawableChild(this.combatYField);
            y += 30;
        }
        this.saveButton = ButtonWidget.builder(Text.literal("Save"), btn -> {
            LeoneClientConfig.setRegularActionBarY(regularActionBarY);
            LeoneClientConfig.setCombatActionBarY(combatActionBarY);
            LeoneClientConfig.setMergeActionBars(mergeActionBars);
            this.client.setScreen(this.parent);
        }).dimensions(this.width - 120, this.height - 60, 100, 20).build();
        this.addDrawableChild(this.saveButton);
        this.backButton = ButtonWidget.builder(Text.literal("Back"), btn -> this.client.setScreen(this.parent)).dimensions(20, this.height - 60, 100, 20).build();
        this.addDrawableChild(this.backButton);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        if (isOverBar(mouseX, mouseY, centerX, regularActionBarY)) {
            draggingRegular = true;
            dragOffset = (int) (mouseY - regularActionBarY);
            return true;
        }
        if (!mergeActionBars && isOverBar(mouseX, mouseY, centerX, combatActionBarY)) {
            draggingCombat = true;
            dragOffset = (int) (mouseY - combatActionBarY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingRegular = false;
        draggingCombat = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingRegular) {
            regularActionBarY = Math.max(20, Math.min(this.height - 20, (int) mouseY - dragOffset));
            this.regularYField.setText(String.valueOf(regularActionBarY));
            return true;
        }
        if (!mergeActionBars && draggingCombat && this.combatYField != null) {
            combatActionBarY = Math.max(20, Math.min(this.height - 20, (int) mouseY - dragOffset));
            this.combatYField.setText(String.valueOf(combatActionBarY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private boolean isOverBar(double mouseX, double mouseY, int centerX, int barY) {
        int barWidth = 200;
        int barHeight = 16;
        return mouseX >= centerX - barWidth / 2 && mouseX <= centerX + barWidth / 2 && mouseY >= barY && mouseY <= barY + barHeight;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, 0, 0, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        if (mergeActionBars) {
            drawBarPreview(context, centerX, regularActionBarY, 0x00AAAA, "Merged Action Bar"); // Teal color
        } else {
            drawBarPreview(context, centerX, regularActionBarY, 0xFFAA00, "Regular Action Bar");
            drawBarPreview(context, centerX, combatActionBarY, 0xFF5555, "Combat Action Bar");
        }
    }

    private void drawBarPreview(DrawContext context, int centerX, int y, int color, String label) {
        int barWidth = 200;
        int barHeight = 16;
        int x = centerX - barWidth / 2;
        context.fill(x, y, x + barWidth, y + barHeight, color | 0x80000000); 
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label), centerX, y + 4, 0xFFFFFF);
    }
} 