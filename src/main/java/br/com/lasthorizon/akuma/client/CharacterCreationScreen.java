package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.character.CharacterData;
import br.com.lasthorizon.akuma.character.CharacterDraft;
import br.com.lasthorizon.akuma.character.CharacterNetwork;
import br.com.lasthorizon.akuma.character.CharacterOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import br.com.lasthorizon.akuma.registry.ModSounds;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Tela de criação de personagem (abre sozinha na primeira vez que o jogador entra num mundo).
 * Todo o visual e a lógica ficam em {@link CharacterMap} (o mapa); aqui só há o que depende do jogo: o campo de
 * texto do nome, o boneco 3D do retrato (arrastar gira, a cabeça segue o mouse), a rede e o ajuste de escala para o mapa caber em qualquer janela.
 * Não fecha com ESC: só finalizar (papel de confirmação + selo) sai daqui, e depois disso o personagem fica salvo no mundo.
 */
public final class CharacterCreationScreen extends Screen {
    /** Margem mínima entre o mapa e a borda da janela. */
    private static final int MARGIN = 6;

    private EditBox nameBox, mottoBox, epithetBox;
    /** O boneco 3D do retrato (o giro por arrastar fica no mapa; aqui, o desenho e a cabeça seguindo o mouse). */
    private final PlayerPreview preview = new PlayerPreview();
    private CharacterMap map;
    private boolean closing;
    // mapa -> tela: x_tela = ox + x_mapa * scale
    private float scale = 1.0F;
    private int ox, oy;

    /** Onde fica o rascunho da criação (no computador do jogador, na pasta config; apagado ao selar). */
    private static Path draftFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("lasthorizon-character-draft.properties");
    }

    public CharacterCreationScreen() {
        super(Component.translatable("character.lasthorizon.title"));
        map = new CharacterMap(new CharacterMap.Hooks() {
            @Override
            public void clipPortrait(GuiGraphics g, int x, int y, int w, int h, boolean on) {
                // o recorte do jogo (1.20.1) usa coordenadas de tela, sem a escala e o deslocamento do mapa: converte aqui
                g.flush();                         // o lote pendente sai com o recorte de antes
                if (!on) {
                    g.disableScissor();
                    return;
                }
                g.enableScissor(Math.round(ox + x * scale), Math.round(oy + y * scale),
                        Math.round(ox + (x + w) * scale), Math.round(oy + (y + h) * scale));
            }

            @Override
            public void setNameText(String s) {
                if (nameBox != null) nameBox.setValue(s);
            }

            @Override
            public void setMottoText(String s) {
                if (mottoBox != null) mottoBox.setValue(s);
            }

            @Override
            public void setEpithetText(String s) {
                if (epithetBox != null) epithetBox.setValue(s);
            }

            @Override
            public void drawEpithetBox(GuiGraphics g, int mx, int my) {
                g.flush();
                epithetBox.render(g, mx, my, 0.0F);
                g.flush();
            }

            @Override
            public void epithetBoxVisible(boolean visible) {
                epithetBox.visible = visible;
                if (!visible && getFocused() == epithetBox) setFocused(null);
            }

            @Override
            public boolean epithetBoxFocused() {
                return epithetBox != null && epithetBox.isFocused();
            }

            @Override
            public boolean worldReady() {
                return CharacterClient.revealReady();
            }

            @Override
            public void sound(int kind, float pitch) {
                SoundEvent ev = switch (kind) {
                    case CharacterMap.SND_TICK -> ModSounds.UI_TICK.get();
                    case CharacterMap.SND_PAGE -> ModSounds.UI_PAGE.get();
                    case CharacterMap.SND_ROLL -> ModSounds.UI_ROLL.get();
                    case CharacterMap.SND_CHIME -> ModSounds.UI_CHIME.get();
                    case CharacterMap.SND_SEAL -> ModSounds.UI_SEAL.get();
                    case CharacterMap.SND_DENY -> ModSounds.UI_DENY.get();
                    case CharacterMap.SND_GEM -> ModSounds.UI_GEM.get();
                    case CharacterMap.SND_OPEN -> ModSounds.UI_OPEN.get();
                    case CharacterMap.SND_FLAP -> ModSounds.UI_FLAP.get();
                    case CharacterMap.SND_HOVER -> ModSounds.UI_HOVER.get();
                    default -> ModSounds.UI_PICK.get();
                };
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch));
            }

            @Override
            public boolean shiftDown() {
                return Screen.hasShiftDown();
            }

            @Override
            public void saveDraft(CharacterDraft d) {
                try {
                    CharacterDraft.save(draftFile(), d);
                } catch (IOException | RuntimeException ignored) {
                    // sem permissão ou disco cheio: o rascunho é só uma ajuda, a criação continua normal
                }
            }

            @Override
            public void changed(CharacterData d) {
                refresh(d);
            }

            @Override
            public void drawPlayer(GuiGraphics g, int cx, int feetY, int s, int mx, int my, float spinDeg) {
                if (minecraft == null || minecraft.player == null) return;
                g.flush();                         // o boneco 3D desenha na hora: o que veio antes tem que sair primeiro
                preview.render(g, minecraft.player, cx, feetY, s, mx, my, spinDeg);
            }

            @Override
            public void drawNameBox(GuiGraphics g, int mx, int my) {
                g.flush();
                nameBox.render(g, mx, my, 0.0F);
                g.flush();
            }

            @Override
            public void mottoBoxVisible(boolean visible) {
                mottoBox.visible = visible;
                if (!visible && getFocused() == mottoBox) setFocused(null);
            }

            @Override
            public void focusMottoBox() {
                setFocused(mottoBox);
                int end = mottoBox.getValue().length();
                mottoBox.setCursorPosition(end);
                mottoBox.setHighlightPos(end);
            }

            @Override
            public String mottoText() {
                return mottoBox == null ? "" : mottoBox.getValue();
            }

            @Override
            public int mottoCursor() {
                return mottoBox == null ? 0 : mottoBox.getCursorPosition();
            }

            @Override
            public int[] mottoSelection() {
                if (mottoBox == null) return null;
                String sel = mottoBox.getHighlighted();
                if (sel == null || sel.isEmpty()) return null;
                // o EditBox não conta onde a seleção começa: acha pelo cursor (que fica numa das pontas)
                String v = mottoBox.getValue();
                int c = mottoBox.getCursorPosition(), n = sel.length();
                if (c - n >= 0 && v.startsWith(sel, c - n)) return new int[]{c - n, c};
                if (c + n <= v.length() && v.startsWith(sel, c)) return new int[]{c, c + n};
                return null;
            }

            @Override
            public void moveMottoCursor(int pos) {
                if (mottoBox == null) return;
                mottoBox.setCursorPosition(pos);
                mottoBox.setHighlightPos(mottoBox.getCursorPosition());
            }

            @Override
            public void nameBoxVisible(boolean visible) {
                nameBox.visible = visible;
                if (!visible && getFocused() == nameBox) setFocused(null);
            }

            @Override
            public void focusNameBox() {
                setFocused(nameBox);
            }

            @Override
            public boolean nameBoxFocused() {
                return nameBox != null && nameBox.isFocused();
            }

            @Override
            public void finalizeNow(CharacterData d) {
                if (!d.isValid()) return;
                CharacterDraft.delete(draftFile());
                CharacterClient.markFinishing();
                CharacterNetwork.CHANNEL.sendToServer(new CharacterNetwork.Finalize(d));
            }
        });
        map.restore(CharacterDraft.load(draftFile()));      // volta de onde parou, se o jogo fechou no meio da criação
    }

    @Override
    protected void init() {
        // o campo fica dentro da placa escura do mapa (coordenadas do mapa; o desenho já está escalado)
        nameBox = new EditBox(this.font, CharacterMap.NAME_X + 6, CharacterMap.NAME_Y + 5, CharacterMap.NAME_W - 12, 10,
                Component.translatable("character.lasthorizon.name"));
        nameBox.setBordered(false);
        nameBox.setTextColor(0xFFF6E9C4);
        nameBox.setMaxLength(CharacterOptions.NAME_MAX);
        nameBox.setResponder(map::setName);
        nameBox.setValue(map.data().name);   // o que já foi digitado não se perde ao redimensionar a janela
        this.addWidget(nameBox);
        this.setInitialFocus(nameBox);

        // o epíteto (sub-aba da Identidade): opcional
        epithetBox = new EditBox(this.font, CharacterMap.EPI_X + 6, CharacterMap.EPI_Y + 5, CharacterMap.EPI_W - 12, 10,
                Component.translatable("character.lasthorizon.epithet"));
        epithetBox.setBordered(false);
        epithetBox.setTextColor(0xFFF6E9C4);
        epithetBox.setMaxLength(CharacterOptions.EPITHET_MAX);
        epithetBox.setFilter(str -> str.indexOf((char) 167) < 0);
        epithetBox.setResponder(map::setEpithet);
        epithetBox.setValue(map.data().epithet);
        this.addWidget(epithetBox);

        // o lema (aba Passado): opcional, sem o § dos códigos de cor
        mottoBox = new EditBox(this.font, CharacterMap.MOTTO_X + 6, CharacterMap.MOTTO_Y + 5, CharacterMap.MOTTO_W - 12, 10,
                Component.translatable("character.lasthorizon.motto"));
        mottoBox.setBordered(false);
        mottoBox.setTextColor(0xFFF6E9C4);
        mottoBox.setMaxLength(CharacterOptions.MOTTO_MAX);
        mottoBox.setFilter(str -> str.indexOf((char) 167) < 0);
        mottoBox.setResponder(map::setMotto);
        mottoBox.setValue(map.data().motto);
        this.addWidget(mottoBox);

        layout();
        refresh(map.data());
    }

    private void layout() {
        scale = Math.min(1.0F, Math.min((this.width - 2 * MARGIN) / (float) MapArt.BW, (this.height - 2 * MARGIN) / (float) MapArt.BH));
        ox = Math.round((this.width - MapArt.BW * scale) / 2.0F);
        oy = Math.round((this.height - MapArt.BH * scale) / 2.0F);
    }

    /** Atualiza a skin do jogador com as escolhas de agora (a prévia é o próprio jogador, via mixin). */
    private void refresh(CharacterData d) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        // o nome pode estar vazio/inválido durante a edição: a skin não depende dele
        CharacterClient.setPreview(this.minecraft.player.getUUID(), d.withName(d.name.isEmpty() ? "x" : d.name));
    }

    @Override
    public void tick() {
        if (!closing && map != null && map.isDone()) {
            closing = true;
            CharacterData done = map.data();
            this.minecraft.setScreen(null);
            CharacterClient.beginHandReveal();         // a tela já sumiu: agora o braço e a UI aparecem
            CharacterClient.sayWelcome(done);          // boas-vindas no chat, só para quem criou
        }
    }

    // ------------------------------------------------------------------------------------------ entrada

    private double lx(double screenX) {
        return (screenX - ox) / scale;
    }

    private double ly(double screenY) {
        return (screenY - oy) / scale;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = lx(mx), y = ly(my);
        if (map.mottoEditorOpen()) {                          // editor do lema aberto: só ele reage, e o foco do campo fica onde está
            if (button == 0) map.click(x, y);
            return true;
        }
        if (button == 1) return map.rightClick(x, y);        // botão direito numa aba: limpa a aba
        if (button != 0) return false;
        if (!map.beginTurn(x, y)) map.click(x, y);       // apertar no retrato pega o boneco para girar
        if (map.mottoEditorOpen()) return true;          // o clique acabou de abrir o editor do lema: ele cuida do foco
        super.mouseClicked(x, y, button);        // foco do campo do nome (clicar fora tira o foco)
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && map.isDraggingSize()) {
            map.dragSize(lx(mx));                        // arrastando a barra de altura ou de busto
            return true;
        }
        if (button == 0 && map.isDraggingAge()) {
            map.dragAge(lx(mx));                         // arrastando a plaquinha da idade
            return true;
        }
        if (button == 0 && map.isTurning()) {
            map.turnBy(dx / scale);                      // pixels da tela -> pixels do mapa
            return true;
        }
        return super.mouseDragged(lx(mx), ly(my), button, dx / scale, dy / scale);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && map.isDraggingSize()) {
            map.endSizeDrag();
            return true;
        }
        if (button == 0 && map.isDraggingAge()) {
            map.endAgeDrag();
            return true;
        }
        if (button == 0 && map.isTurning()) {
            map.endTurn();
            return true;
        }
        return super.mouseReleased(lx(mx), ly(my), button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (map.scroll(lx(mx), ly(my), delta)) return true;          // roda sobre o retrato: zoom
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (map.stage() == CharacterMap.CONFIRM) {
            if (key == 256) return map.back();                       // ESC: volta a editar
            if (key == 257 || key == 335) return map.confirm();      // ENTER: sela
            return true;
        }
        if (map.stage() == CharacterMap.SEALING) return true;
        if (map.mottoEditorOpen()) {
            if (key == 256 || key == 257 || key == 335) return map.closeMottoEditor();   // ESC / ENTER: fecha o editor do lema
            if (key == 258 || key == 266 || key == 267) return true;                     // Tab, Page Up/Down: nada de trocar de campo ou de aba
            if (hasControlDown() && key >= 49 && key <= 55) return true;                 // nem Ctrl + 1..7
            return super.keyPressed(key, scan, mods);                                    // o resto é do campo (digitar, apagar, Ctrl+A/C/V...)
        }
        if (key == 266) return map.moveTab(-1);                      // Page Up / Page Down: aba anterior e seguinte
        if (key == 267) return map.moveTab(1);
        if (hasControlDown() && key >= 49 && key <= 55 && map.selectTab(key - 49)) return true;   // Ctrl + 1..7: vai direto à aba
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return map.stage() == CharacterMap.EDIT && super.charTyped(c, mods);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;      // jogo solo: o mundo fica parado até o personagem ser criado (em servidor quem congela é CreationFreeze)
    }

    // ------------------------------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        float fa = map.exitAlpha();                           // no fim do selo a tela inteira some aos poucos e revela o jogo
        MapArt.setBaseAlpha(fa);                              // o mapa, o texto, os ícones e o boneco 3D seguem este mesmo alfa, juntos
        if (fa < 1.0F) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fa);
        }
        final float dim = map.exitDim();
        g.drawManaged(() -> MapArt.backdrop(g, this.width, this.height, System.currentTimeMillis(), dim));      // ~100 retângulos em 1 desenho
        g.pose().pushPose();
        g.pose().translate((float) ox, (float) oy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        map.render(g, this.font, (int) Math.floor(lx(mx)), (int) Math.floor(ly(my)));
        g.pose().popPose();
        MapArt.setBaseAlpha(1.0F);
        if (fa < 1.0F) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void removed() {
        MapArt.setBaseAlpha(1.0F);                            // nada do fade vaza para o resto do jogo
        super.removed();
    }
}
