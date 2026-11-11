package net.kpkh.anybodyparts.client;

import java.util.EnumMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kpkh.anybodyparts.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AdminBodyScreen extends Screen {
    private AdminStatePayload state;
    private EditBox name;
    private final EnumMap<BodyPart, Button> buttons = new EnumMap<>(BodyPart.class);
    private Button reset;
    private boolean found;
    public AdminBodyScreen(AdminStatePayload state) {
        super(Component.translatable("screen.anybodyparts.admin"));
        this.state = state;
        found = true;
    }
    @Override protected void init() {
        int x = width / 2, top = Math.max(8, (height - 228) / 2);
        name = addRenderableWidget(new EditBox(font, x - 148, top + 26, 200, 20, Component.translatable("screen.anybodyparts.player")));
        name.setMaxLength(16);
        name.setHint(Component.translatable("screen.anybodyparts.player"));
        name.setValue(state.name());
        name.setResponder(value -> { found = value.equalsIgnoreCase(state.name()); labels(); });
        addRenderableWidget(Button.builder(Component.translatable("screen.anybodyparts.find"), b -> request(-1, false))
            .bounds(x + 58, top + 26, 90, 20).build());
        int index = 0;
        for (BodyPart part : BodyPart.values()) {
            buttons.put(part, addRenderableWidget(Button.builder(Component.empty(), b -> request(part.ordinal(), (state.mask() & part.bit()) == 0))
                .bounds(x - 148, top + 70 + index++ * 25, 296, 21).build()));
        }
        reset = addRenderableWidget(Button.builder(Component.translatable("screen.anybodyparts.restore"), b -> request(-2, true))
            .bounds(x - 148, top + 200, 190, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 48, top + 200, 100, 20).build());
        labels();
    }
    private void request(int part, boolean enabled) {
        String target = name.getValue().trim();
        if (target.matches("[A-Za-z0-9_]{1,16}")) ClientPlayNetworking.send(new AdminRequestPayload(target, part, enabled));
    }
    public void update(AdminStatePayload state) {
        if (!state.name().equalsIgnoreCase(name.getValue().trim())) return;
        this.state = state; found = true; labels();
    }
    private void labels() {
        buttons.forEach((part, button) -> {
            boolean present = (state.effectiveMask() & part.bit()) != 0;
            button.setMessage(Component.translatable(part.key()).append(" · ")
                .append(Component.translatable(present ? "screen.anybodyparts.on" : "screen.anybodyparts.off")));
            button.active = found && (state.editableMask() & part.bit()) != 0;
        });
        if (reset != null) reset.active = found && state.canReset();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        int top = Math.max(8, (height - 228) / 2);
        g.centeredText(font, title, width / 2, top + 6, 0xFFFFFFFF);
        g.centeredText(font, Component.translatable(found ? "screen.anybodyparts.admin_hint" : "screen.anybodyparts.find_hint"), width / 2, top + 54, 0xFFBBBBBB);
    }
    @Override public boolean isPauseScreen() { return false; }
}
