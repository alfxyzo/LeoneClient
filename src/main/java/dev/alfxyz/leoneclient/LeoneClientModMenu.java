package dev.alfxyz.leoneclient;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class LeoneClientModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LeoneClientMainConfigScreen::new;
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

abstract class LcScreen extends Screen {
    protected static final int COL_LEFT  = -82;
    protected static final int COL_RIGHT =  36;
    protected static final int COL_WIDE  = -82;
    protected static final int TOGGLE_W  =  46;

    protected LcScreen(Text title) { super(title); }

    protected ButtonWidget toggle(int cx, int y, boolean val, Runnable action) {
        return this.addDrawableChild(ButtonWidget.builder(
            val ? Text.literal("ON").formatted(Formatting.GREEN)
                : Text.literal("OFF").formatted(Formatting.RED),
            btn -> { action.run(); init(); }
        ).dimensions(cx + COL_RIGHT, y, TOGGLE_W, 16).build());
    }

    protected void row(DrawContext ctx, int cx, int y, String label) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), cx + COL_LEFT, y + 3, 0xFFCCCCCC);
    }

    protected void section(DrawContext ctx, int cx, int y, String name) {
        ctx.fill(cx - 82, y + 6, cx + 82, y + 7, 0x40FFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal(name).formatted(Formatting.GRAY), cx + COL_LEFT, y - 2, 0xFF888888);
    }

    protected void panel(DrawContext ctx, int panelH) {
        ctx.fill(0, 0, this.width, panelH, 0xCC000000);
        ctx.fill(0, panelH, this.width, panelH + 1, 0xFF3A3A3A);
    }

    protected void header(DrawContext ctx) {
        ctx.fill(0, 0, this.width, 20, 0xE0000000);
        ctx.fill(0, 20, this.width, 21, 0xFF555555);
        ctx.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 6, 0xFFFFFFFF);
    }
}

// ── General Settings ──────────────────────────────────────────────────────────

class LeoneClientMainConfigScreen extends LcScreen {
    private final Screen parent;
    private String selectedServer;
    private boolean serverDropdownOpen = false;

    private static final String[] SERVERS = {
        "Remain in hub", "ElytraBox", "Wildkits", "Lifesteal",
        "MoneyDupe", "InsaneKits", "Survival", "KnockbackFFA", "GunCube", "PVP"
    };

    private int yServer, yChatSec, yHideChat, yAntiMute;

    LeoneClientMainConfigScreen(Screen parent) {
        super(Text.literal("LeoneClient Settings"));
        this.parent = parent;
        this.selectedServer = LeoneClientConfig.getAutoJoinServer();
    }

    @Override
    protected void init() {
        this.clearChildren();
        int cx = this.width / 2;
        int y = 22;

        ButtonWidget genTab = ButtonWidget.builder(
            Text.literal("General").formatted(Formatting.BOLD), btn -> {}
        ).dimensions(cx - 105, y, 100, 18).build();
        genTab.active = false;
        this.addDrawableChild(genTab);
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Action Bar →"),
            btn -> { serverDropdownOpen = false; client.setScreen(new LeoneClientActionBarConfigScreen(parent)); }
        ).dimensions(cx + 5, y, 100, 18).build());
        y += 26;

        yServer = y; y += 14;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(selectedServer + "  ▼"),
            btn -> { serverDropdownOpen = !serverDropdownOpen; init(); }
        ).dimensions(cx - 80, y, 160, 18)
         .tooltip(Tooltip.of(Text.literal("Server to auto-join when connecting to LeoneMC"))).build());
        y += 22;

        if (serverDropdownOpen) {
            for (String opt : SERVERS) {
                final String o = opt;
                boolean sel = opt.equals(selectedServer);
                ButtonWidget row = this.addDrawableChild(ButtonWidget.builder(
                    sel ? Text.literal("▶  " + opt).formatted(Formatting.GREEN) : Text.literal("    " + opt),
                    btn -> { selectedServer = o; serverDropdownOpen = false; init(); }
                ).dimensions(cx - 80, y, 160, 16).build());
                if (sel) row.active = false;
                y += 18;
            }
        }

        y += 8;
        yChatSec = y; y += 16;

        yHideChat = y;
        toggle(cx, y, LeoneClientConfig.isHideChatAlerts(),
            () -> LeoneClientConfig.setHideChatAlerts(!LeoneClientConfig.isHideChatAlerts()));
        y += 22;

        yAntiMute = y;
        toggle(cx, y, LeoneClientConfig.isAntiMuteEnabled(),
            () -> LeoneClientConfig.setAntiMuteEnabled(!LeoneClientConfig.isAntiMuteEnabled()));
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("⚙").formatted(Formatting.YELLOW),
            btn -> client.setScreen(new AntiMuteConfigScreen(parent))
        ).dimensions(cx + 84, y, 16, 16)
         .tooltip(Tooltip.of(Text.literal("Configure Anti-Mute filters and replacement text"))).build());

        int bot = this.height - 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),
            btn -> client.setScreen(parent)
        ).dimensions(cx - 105, bot, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save").formatted(Formatting.GREEN),
            btn -> { LeoneClientConfig.setAutoJoinServer(selectedServer); client.setScreen(parent); }
        ).dimensions(cx + 5, bot, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int cx = this.width / 2;
        ctx.fill(0, 0, this.width, this.height, 0xB0000000);
        header(ctx);
        section(ctx, cx, yServer, "AUTO-JOIN SERVER");
        section(ctx, cx, yChatSec, "CHAT & FILTERING");
        row(ctx, cx, yHideChat, "Hide Chat Alerts");
        row(ctx, cx, yAntiMute, "Anti-Mute");
        super.render(ctx, mx, my, delta);
    }
}

// ── Action Bar Settings ───────────────────────────────────────────────────────

class LeoneClientActionBarConfigScreen extends LcScreen {
    final Screen parent;

    private int yFeature, yMerge, yCombatSec, yTimerHUD, yHideBar;

    LeoneClientActionBarConfigScreen(Screen parent) {
        super(Text.literal("Action Bar Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearChildren();
        int cx = this.width / 2;
        int y = 22;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("← General"),
            btn -> client.setScreen(new LeoneClientMainConfigScreen(parent))
        ).dimensions(cx - 105, y, 100, 18).build());
        ButtonWidget abTab = ButtonWidget.builder(
            Text.literal("Action Bar").formatted(Formatting.BOLD), btn -> {}
        ).dimensions(cx + 5, y, 100, 18).build();
        abTab.active = false;
        this.addDrawableChild(abTab);
        y += 26;

        y += 10;
        yFeature = y;
        toggle(cx, y, LeoneClientConfig.isActionBarEnabled(),
            () -> LeoneClientConfig.setActionBarEnabled(!LeoneClientConfig.isActionBarEnabled()));
        y += 22;

        yMerge = y;
        toggle(cx, y, LeoneClientConfig.getMergeActionBars(),
            () -> LeoneClientConfig.setMergeActionBars(!LeoneClientConfig.getMergeActionBars()));
        y += 30;

        yCombatSec = y; y += 16;

        yTimerHUD = y;
        boolean timerOn = LeoneClientConfig.isCombatTimerWidget();
        toggle(cx, y, timerOn,
            () -> LeoneClientConfig.setCombatTimerWidget(!LeoneClientConfig.isCombatTimerWidget()));
        y += 22;

        yHideBar = y;
        ButtonWidget hideBtn = toggle(cx, y, LeoneClientConfig.isHideCombatBarWhenEffect(),
            () -> LeoneClientConfig.setHideCombatBarWhenEffect(!LeoneClientConfig.isHideCombatBarWhenEffect()));
        hideBtn.active = timerOn;
        y += 28;

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("↕  Position Bars...").formatted(Formatting.AQUA),
            btn -> client.setScreen(new LeoneClientBarPositionScreen(parent,
                LeoneClientConfig.getRegularActionBarY(),
                LeoneClientConfig.getCombatActionBarY(),
                LeoneClientConfig.getMergeActionBars()))
        ).dimensions(cx - 80, y, 160, 18).build());

        int bot = this.height - 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"),
            btn -> client.setScreen(parent)
        ).dimensions(cx - 105, bot, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done").formatted(Formatting.GREEN),
            btn -> client.setScreen(parent)
        ).dimensions(cx + 5, bot, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int cx = this.width / 2;
        int panelH = this.height - 60;
        ctx.fill(0, 0, this.width, this.height, 0x60000000);
        panel(ctx, panelH);
        header(ctx);
        row(ctx, cx, yFeature, "Feature");
        row(ctx, cx, yMerge, "Merge Bars");
        section(ctx, cx, yCombatSec, "COMBAT TIMER");
        row(ctx, cx, yTimerHUD, "Combat Timer HUD");
        row(ctx, cx, yHideBar, "Hide Combat Bar");
        super.render(ctx, mx, my, delta);
        boolean merged = LeoneClientConfig.getMergeActionBars();
        if (merged) {
            drawIndicator(ctx, cx, this.height - LeoneClientConfig.getRegularActionBarY(),
                0xFFFFAA00, "2m 30s  §7|  §cCombat Tag | 14.5");
        } else {
            drawIndicator(ctx, cx, this.height - LeoneClientConfig.getRegularActionBarY(),
                0xFFFFAA00, "2m 30s");
            drawIndicator(ctx, cx, this.height - LeoneClientConfig.getCombatActionBarY(),
                0xFFFF5555, "Combat Tag | 14.5");
        }
    }

    private void drawIndicator(DrawContext ctx, int cx, int sy, int color, String sample) {
        if (sy < this.height - 55 || sy > this.height - 4) return;
        int w = textRenderer.getWidth(sample) + 16;
        ctx.fill(cx - w / 2, sy - 4, cx + w / 2, sy + 12, 0x80000000);
        ctx.fill(cx - w / 2, sy - 4, cx + w / 2, sy - 3, (color & 0x00FFFFFF) | 0xFF000000);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(sample), cx, sy, color);
    }
}

// ── Bar Position Editor ───────────────────────────────────────────────────────

class LeoneClientBarPositionScreen extends LcScreen {
    private final Screen returnParent;
    private int regularActionBarY;
    private int combatActionBarY;
    private final boolean mergeActionBars;
    private boolean draggingRegular = false;
    private boolean draggingCombat  = false;
    private int dragOffset = 0;

    LeoneClientBarPositionScreen(Screen returnParent, int regularY, int combatY, boolean merged) {
        super(Text.literal("Position Action Bars"));
        this.returnParent = returnParent;
        this.regularActionBarY = regularY;
        this.combatActionBarY  = combatY;
        this.mergeActionBars   = merged;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int bot = this.height - 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),
            btn -> client.setScreen(new LeoneClientActionBarConfigScreen(returnParent))
        ).dimensions(cx - 105, bot, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done — Save").formatted(Formatting.GREEN),
            btn -> {
                LeoneClientConfig.setRegularActionBarY(regularActionBarY);
                LeoneClientConfig.setCombatActionBarY(combatActionBarY);
                client.setScreen(new LeoneClientActionBarConfigScreen(returnParent));
            }
        ).dimensions(cx + 5, bot, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x50000000);
        ctx.fill(0, 0, this.width, 22, 0xD0000000);
        ctx.fill(0, 22, this.width, 23, 0xFF444444);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Drag bars to reposition  —  Done to save"),
            this.width / 2, 7, 0xFFAAAAAA);
        super.render(ctx, mx, my, delta);
        int cx = this.width / 2;
        if (mergeActionBars) {
            drawHandle(ctx, cx, this.height - regularActionBarY,
                0xFFFFAA00, "2m 30s  §7|  §cCombat Tag | 14.5",
                "§7y = " + regularActionBarY + "px");
        } else {
            drawHandle(ctx, cx, this.height - regularActionBarY,
                0xFFFFAA00, "2m 30s", "§7y = " + regularActionBarY + "px");
            drawHandle(ctx, cx, this.height - combatActionBarY,
                0xFFFF5555, "Combat Tag | 14.5", "§7y = " + combatActionBarY + "px");
        }
    }

    private void drawHandle(DrawContext ctx, int cx, int sy, int color, String sample, String sub) {
        int w = textRenderer.getWidth(sample) + 24;
        ctx.fill(cx - w / 2 - 1, sy - 6, cx + w / 2 + 1, sy + 20, 0x30FFFFFF);
        ctx.fill(cx - w / 2, sy - 5, cx + w / 2, sy + 19, 0x90000000);
        ctx.fill(cx - w / 2, sy - 5, cx + w / 2, sy - 4, (color & 0x00FFFFFF) | 0xFF000000);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(sample + " §7↕"), cx, sy, color);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(sub), cx, sy + 10, 0xFF777777);
    }

    private boolean overBar(double mx, double my, int cx, int barY) {
        int sy = this.height - barY;
        return Math.abs(mx - cx) <= 120 && my >= sy - 6 && my <= sy + 19;
    }

    @Override
    public boolean mouseClicked(Click c, boolean doubled) {
        int cx = this.width / 2;
        if (overBar(c.x(), c.y(), cx, regularActionBarY)) {
            draggingRegular = true;
            dragOffset = (int) c.y() - (this.height - regularActionBarY);
            return true;
        }
        if (!mergeActionBars && overBar(c.x(), c.y(), cx, combatActionBarY)) {
            draggingCombat = true;
            dragOffset = (int) c.y() - (this.height - combatActionBarY);
            return true;
        }
        return super.mouseClicked(c, doubled);
    }

    @Override
    public boolean mouseReleased(Click c) {
        draggingRegular = draggingCombat = false;
        return super.mouseReleased(c);
    }

    @Override
    public boolean mouseDragged(Click c, double dx, double dy) {
        int mouseY = (int) c.y();
        int safeTop = 30;
        int safeBot = this.height - 36;
        if (draggingRegular) {
            regularActionBarY = this.height - Math.max(safeTop, Math.min(safeBot, mouseY - dragOffset));
            return true;
        }
        if (!mergeActionBars && draggingCombat) {
            combatActionBarY = this.height - Math.max(safeTop, Math.min(safeBot, mouseY - dragOffset));
            return true;
        }
        return super.mouseDragged(c, dx, dy);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}

// ── Anti-Mute Settings ────────────────────────────────────────────────────────

class AntiMuteConfigScreen extends LcScreen {
    private final Screen parent;

    private int yAntiMute, yReplacement, yFiltersSec,
                yDiscrimination, yDeathWishes, ySwears, yAds;

    AntiMuteConfigScreen(Screen parent) {
        super(Text.literal("Anti-Mute Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearChildren();
        int cx = this.width / 2;
        int y = 22;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("← General"),
            btn -> client.setScreen(new LeoneClientMainConfigScreen(parent))
        ).dimensions(cx - 105, y, 100, 18).build());
        ButtonWidget tab = ButtonWidget.builder(
            Text.literal("Anti-Mute").formatted(Formatting.BOLD), btn -> {}
        ).dimensions(cx + 5, y, 100, 18).build();
        tab.active = false;
        this.addDrawableChild(tab);
        y += 30;

        yAntiMute = y;
        toggle(cx, y, LeoneClientConfig.isAntiMuteEnabled(),
            () -> LeoneClientConfig.setAntiMuteEnabled(!LeoneClientConfig.isAntiMuteEnabled()));
        y += 22;

        yReplacement = y;
        String rep = LeoneClientConfig.getAntiMuteReplacement();
        String repDisplay = rep.length() > 12 ? rep.substring(0, 12) + "…" : rep;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(repDisplay).formatted(Formatting.WHITE),
            btn -> client.setScreen(new EditReplacementScreen(parent))
        ).dimensions(cx + COL_RIGHT, y, TOGGLE_W, 16)
         .tooltip(Tooltip.of(Text.literal("Click to change replacement text"))).build());
        y += 30;

        yFiltersSec = y; y += 16;

        boolean on = LeoneClientConfig.isAntiMuteEnabled();

        yDiscrimination = y;
        ButtonWidget b1 = toggle(cx, y, LeoneClientConfig.isFilterDiscrimination(),
            () -> LeoneClientConfig.setFilterDiscrimination(!LeoneClientConfig.isFilterDiscrimination()));
        b1.active = on;
        y += 20;

        yDeathWishes = y;
        ButtonWidget b2 = toggle(cx, y, LeoneClientConfig.isFilterDeathWishes(),
            () -> LeoneClientConfig.setFilterDeathWishes(!LeoneClientConfig.isFilterDeathWishes()));
        b2.active = on;
        y += 20;

        ySwears = y;
        ButtonWidget b3 = toggle(cx, y, LeoneClientConfig.isFilterSwears(),
            () -> LeoneClientConfig.setFilterSwears(!LeoneClientConfig.isFilterSwears()));
        b3.active = on;
        y += 20;

        yAds = y;
        ButtonWidget b4 = toggle(cx, y, LeoneClientConfig.isFilterAdvertisements(),
            () -> LeoneClientConfig.setFilterAdvertisements(!LeoneClientConfig.isFilterAdvertisements()));
        b4.active = on;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"),
            btn -> client.setScreen(new LeoneClientMainConfigScreen(parent))
        ).dimensions(cx - 50, this.height - 26, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int cx = this.width / 2;
        ctx.fill(0, 0, this.width, this.height, 0xB0000000);
        header(ctx);
        row(ctx, cx, yAntiMute, "Enabled");
        row(ctx, cx, yReplacement, "Replacement word");
        section(ctx, cx, yFiltersSec, "FILTERS");
        boolean on = LeoneClientConfig.isAntiMuteEnabled();
        int dim = on ? 0xFFCCCCCC : 0xFF666666;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Discrimination"), cx + COL_LEFT, yDiscrimination + 3, dim);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Death Wishes"),   cx + COL_LEFT, yDeathWishes + 3,    dim);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Swears"),         cx + COL_LEFT, ySwears + 3,         dim);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Advertisements"), cx + COL_LEFT, yAds + 3,            dim);
        super.render(ctx, mx, my, delta);
    }
}

// ── Edit Replacement Text ─────────────────────────────────────────────────────

class EditReplacementScreen extends LcScreen {
    private final Screen parent;
    private TextFieldWidget field;

    EditReplacementScreen(Screen parent) {
        super(Text.literal("Edit Replacement Text"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        field = new TextFieldWidget(textRenderer, cx - 80, cy - 12, 160, 20, Text.literal(""));
        field.setMaxLength(32);
        field.setText(LeoneClientConfig.getAntiMuteReplacement());
        this.addDrawableChild(field);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done").formatted(Formatting.GREEN),
            btn -> save()
        ).dimensions(cx - 80, cy + 14, 77, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),
            btn -> client.setScreen(new AntiMuteConfigScreen(parent))
        ).dimensions(cx + 3, cy + 14, 77, 20).build());
    }

    private void save() {
        LeoneClientConfig.setAntiMuteReplacement(field.getText());
        client.setScreen(new AntiMuteConfigScreen(parent));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xC8000000);
        header(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Text that replaces blocked words"),
            this.width / 2, this.height / 2 - 28, 0xFF888888);
        super.render(ctx, mx, my, delta);
    }
}
