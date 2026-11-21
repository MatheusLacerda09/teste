package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.character.BodyScale;
import br.com.lasthorizon.akuma.character.CharacterData;
import br.com.lasthorizon.akuma.character.CharacterDestiny;
import br.com.lasthorizon.akuma.character.CharacterDraft;
import br.com.lasthorizon.akuma.character.CharacterOptions;
import br.com.lasthorizon.akuma.character.SkinPainter;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;

/**
 * A interface da criação de personagem: um mapa de pergaminho dobrado ({@link MapArt}).
 *
 * <ul>
 *   <li><b>Abas de marcador</b> saindo pela esquerda do mapa (Identidade, Corpo, Cabelo, Roupa, Alma, Passado e
 *       Sonho) e o dado do <b>Aleatório</b>. Cada aba tem ícone, dica ao passar o mouse e uma pedra de estado
 *       (cinza: não visitada; dourado: visitada; vermelho: falta algo obrigatório; verde: concluída). Uma barra de
 *       segmentos no alto do painel da esquerda mostra o progresso de todas de uma vez.</li>
 *   <li><b>Painel da esquerda</b>: o título, o retrato com o próprio jogador (arrastar gira; roda do mouse e botões
 *       aproximam, afastam e viram de costas), a plaquinha com o nome e o botão de finalizar (selo de cera).</li>
 *   <li><b>Painel da direita</b>: a aba escolhida, numa faixa de título (as pontas da faixa passam para a aba anterior
 *       e a seguinte), sobre uma camada de pergaminho que acalma o desenho do mapa. Cada coisa que pode mudar é uma seção
 *       separada com ícone, título, filete e divisória.</li>
 *   <li><b>Resumo da Jornada</b>: um papel preso com prego com o retrato e todas as escolhas; cada linha leva de volta à
 *       aba dela. Ao confirmar, o selo cai no mapa, a tela escurece e o jogo começa.</li>
 * </ul>
 *
 * <p>Modo imediato: {@link #render} desenha e {@link #click} roda os mesmos métodos sem desenhar, só para tratar o clique
 * (por isso todo desenho passa por helpers que ignoram {@code g == null}). Só usa {@code fill}, {@code drawString} e
 * {@code pose()} do GuiGraphics e fala com o jogo pelo {@link Hooks}, então também roda no preview fora do Minecraft.
 * As coordenadas são do "espaço do mapa" ({@link MapArt}): o chamador traduz/escala a tela para o mapa caber.
 */
public final class CharacterMap {

    /** O que o mapa precisa do jogo de verdade (tela, rede, jogador, arquivo do rascunho). */
    public interface Hooks {
        /** As escolhas mudaram: atualizar a skin da prévia. */
        void changed(CharacterData d);

        /**
         * O mundo atrás da tela já pode ser mostrado: o servidor confirmou o personagem e o terreno em volta do jogador já
         * está carregado e desenhado. Enquanto for falso, o fade final espera (senão o mundo "pisca" e aparece tudo de uma vez).
         */
        boolean worldReady();

        /**
         * Desenha o jogador em 3D (pés em {@code feetY}, centro em {@code cx}) girado {@code spinDeg} graus em torno do
         * próprio eixo (0 = de frente, positivo = a frente gira para a direita da tela) e com a cabeça olhando para o mouse.
         */
        void drawPlayer(GuiGraphics g, int cx, int feetY, int scale, int mx, int my, float spinDeg);

        /** Liga ({@code on}) ou desliga o recorte que impede o boneco aproximado de passar da moldura do retrato. */
        void clipPortrait(GuiGraphics g, int x, int y, int w, int h, boolean on);

        /** Desenha o campo de texto do nome (na posição NAME_X/NAME_Y). */
        void drawNameBox(GuiGraphics g, int mx, int my);

        void nameBoxVisible(boolean visible);

        void focusNameBox();

        boolean nameBoxFocused();

        /** Troca o texto do campo do nome (que avisa o mapa pelo {@link #setName}). */
        void setNameText(String s);

        /**
         * Liga ({@code true}) ou desliga o campo de texto do lema. Ele não é mais desenhado pelo jogo: enquanto o editor
         * grande está aberto ele só recebe o teclado (digitar, apagar, colar, Ctrl+A...) e o mapa desenha o texto.
         */
        void mottoBoxVisible(boolean visible);

        /** Dá o foco do teclado ao campo do lema, com o cursor no fim do texto. */
        void focusMottoBox();

        /** O texto do campo do lema como está agora (sem tirar espaços: o cursor é contado sobre ele). */
        String mottoText();

        /** Posição do cursor dentro de {@link #mottoText()}. */
        int mottoCursor();

        /** Trecho selecionado de {@link #mottoText()} como {@code {de, até}}, ou {@code null} se não há seleção. */
        int[] mottoSelection();

        /** Põe o cursor na posição {@code pos} de {@link #mottoText()} (sem seleção). */
        void moveMottoCursor(int pos);

        /** Troca o texto do campo do lema (que avisa o mapa pelo {@link #setMotto}). */
        void setMottoText(String s);

        /** Desenha o campo de texto do epíteto (na posição EPI_X/EPI_Y). */
        void drawEpithetBox(GuiGraphics g, int mx, int my);

        void epithetBoxVisible(boolean visible);

        boolean epithetBoxFocused();

        /** Troca o texto do campo do epíteto (que avisa o mapa pelo {@link #setEpithet}). */
        void setEpithetText(String s);

        /** Toca um som da tela ({@code SND_*}) com o tom indicado (1.0 = normal). */
        void sound(int kind, float pitch);

        /** Shift está apertado? (Aleatório com Shift sorteia só a aparência.) */
        boolean shiftDown();

        /** Grava o rascunho da criação (chamado de tempos em tempos, só se algo mudou). */
        void saveDraft(CharacterDraft d);

        /** Envia o personagem ao servidor (chamado uma vez, quando o selo começa a cair). */
        void finalizeNow(CharacterData d);
    }

    public static final int EDIT = 0, CONFIRM = 1, SEALING = 2;
    /** Sons da tela: tique de traço, escolha, virar página, dados, sino, selo, negado, brilho de aba concluída. */
    public static final int SND_TICK = 0, SND_PICK = 1, SND_PAGE = 2, SND_ROLL = 3, SND_CHIME = 4, SND_SEAL = 5, SND_DENY = 6, SND_GEM = 7,
            SND_OPEN = 8, SND_FLAP = 9, SND_HOVER = 10;
    public static final int TAB_IDENTITY = 0, TAB_BODY = 1, TAB_HAIR = 2, TAB_OUTFIT = 3, TAB_SOUL = 4,
            TAB_PAST = 5, TABS = 6;
    private static final int TIP_RANDOM = -2;

    private static final int BODY = MapFont.BODY, BOLD = MapFont.BOLD, TITLE = MapFont.TITLE, NOTE = MapFont.NOTE;

    // ------------------------------------------------------------------------------------------ geometria
    private static final int LX = MapArt.LPX, LW = MapArt.LPW, RX = MapArt.RPX, RW = MapArt.RPW;
    /** Onde começa o conteúdo das duas páginas (embaixo da faixa de título). */
    private static final int TOP = 34;
    /** Campo do nome (o EditBox do jogo fica dentro dele); à direita dele fica o dado do nome aleatório. */
    public static final int NAME_X = RX, NAME_Y = TOP + 44, NAME_W = RW - 26, NAME_H = 18;      // 20 px abaixo do topo: as sub-abas Nome/Epíteto ficam em cima
    private static final int DICE_X = RX + RW - 22, DICE_W = 22;
    /** Campo do lema, na aba Passado (idade e mar natal ficam acima; o EditBox do jogo fica dentro dele). */
    public static final int MOTTO_X = RX, MOTTO_Y = 187, MOTTO_W = RW, MOTTO_H = 18;
    /** Campo do epíteto (sub-aba Epíteto da Identidade; o EditBox do jogo fica dentro dele). */
    public static final int EPI_X = RX, EPI_Y = TOP + 44, EPI_W = RW - 26, EPI_H = 18;
    /** Página da direita: a camada de pergaminho por baixo do texto. */
    private static final int WASH_Y = 30, WASH_H = 176;
    /** Aba Alma: início, passo e geometria dos seis eixos; o painel de descrição fica embaixo deles. */
    private static final int TILE_GAP = 4, TILE_H = 48, TILE_Y = 32, ROLL_H = 20, SETTLE0 = 650, SETTLE_STEP = 210;
    private static final int ROLL_Y = TILE_Y + 2 * (TILE_H + TILE_GAP), SOUL_PANEL_Y = ROLL_Y + ROLL_H + 4, SOUL_PANEL_H = 46;
    /** Animações: virada de página, pulso de escolha e brilho de aba concluída. */
    private static final long OPEN_FADE_MS = 520L, TAB_FADE_MS = 260L;
    private static final long TRANS_MS = 210L, PULSE_MS = 340L, SPARKLE_MS = 700L;
    /** Bandeiras (abas): x, largura (com o mastro) e altura da bandeira recolhida; a escolhida sai 3 px mais para fora. */
    private static final int FLAG_X = 2, FLAG_W = 31, FLAG_H = 22, FLAG_Y = 20, FLAG_STEP = 25, RANDOM_Y = FLAG_Y + TABS * FLAG_STEP + 2;
    /** Todo balão (dica ao passar o mouse) só aparece depois do mouse ficar 5 segundos sobre a mesma coisa. */
    private static final long TIP_DELAY_MS = 5_000L;
    /** Formato da ponta solta de cada bandeira (varia para cada aba ter a sua cara). */
    private static final int[] FLAG_SHAPE = {MapArt.FLAG_SWALLOW, MapArt.FLAG_POINTED, MapArt.FLAG_TATTERED, MapArt.FLAG_SWALLOW,
            MapArt.FLAG_POINTED, MapArt.FLAG_SWALLOW, MapArt.FLAG_TATTERED};
    /** Placa do nome (embaixo do retrato). */
    private static final int NAMEPLATE_Y = 147;
    private static final int PORT_W = 124, PORT_H = 102, PORT_Y = 39;
    private static final int FLAG_HIT = FLAG_X + FLAG_W;
    /** Barra de progresso (um segmento por aba) e botão de limpar aba, no alto do painel da esquerda. */
    private static final int PROG_X = LX + 7, PROG_Y = 21, PROG_SEG_W = 12, PROG_GAP = 3, PROG_H = 9;
    /** Cavidade do contador "n/6", à direita das etapas dentro da plaquinha. */
    private static final int COUNT_X = LX + 97, COUNT_Y = 20, COUNT_W = 23, COUNT_H = 11;
    private static final long COUNT_POP_MS = 360L, CLEAR_SPIN_MS = 560L;
    /** Plaquinhas do cabeçalho: título (à esquerda do botão de limpar) e barra de progresso com o contador. */
    private static final int HEAD_X = LX + 2, HEAD_Y = 3, HEAD_W = LW - 4, HEAD_H = 14, PLATE_X = LX + 2, PLATE_Y = 18, PLATE_W = LW - 22, PLATE_H = 15;
    /** Botão de limpar reposicionado ao lado da barra, com área de toque confortável. */
    private static final int CLEAR_X = LX + LW - 17, CLEAR_Y = 18, CLEAR_S = 15;
    /** Botões do retrato (coluna na borda direita da moldura). */
    private static final int VIEW_BTN = 16, VIEW_GAP = 3;
    private static final float ZOOM_MIN = 0.7F, ZOOM_MAX = 1.8F, ZOOM_STEP = 0.15F;
    private static final int CARD_W = 336, CARD_H = 216;
    /** Editor do lema: tamanho da janelinha, escala do texto (inteira, para a letra ficar nítida) e altura de cada linha (em unidades do texto). */
    private static final int ME_W = 280, ME_H = 176, ME_PITCH = 11;
    /** Tamanho do texto do editor do lema (1 = normal, 2 = dobro). Estava em 2, grande demais; agora 1 (tamanho normal). */
    private static final float ME_SCALE = 1.0F;
    /** Lápis do campo do lema (8 x 8). */
    private static final String[] PENCIL = {"......XX", ".....XXX", "....XXX.", "...XXX..", "..XXX...", ".XXX....", ".XX.....", "X......."};
    private static final long SEAL_TOTAL = 5400L, SAVE_EVERY = 900L;
    /** Os últimos {@code SEAL_FADE_MS} da tela do selo: ela vai ficando transparente e revela o jogo (nada de cortar para preto). */
    private static final long SEAL_FADE_MS = 2400L;
    /** Quanto o fade final espera, no máximo, pelo mundo ficar pronto (passou disso, segue assim mesmo). */
    private static final long WORLD_WAIT_MAX_MS = 7000L;
    /** Espera do fade pelo mundo: quando começou a segurar, se já foi liberado e quanto tempo ficou parado. */
    private long holdStart = -1L, heldMs = 0L;
    private boolean fadeReleased = false;
    /** Balões do resumo final (idade, mar, alma...) aparecem rápido: ali o mouse está só para ler. */
    private static final long TIP_QUICK_MS = 320L;
    private final float[] rowHover = new float[6];
    private boolean tipQuick;

    private static final int[] SOUL_AXIS_ICON = MapIcons.SOUL_AXIS;
    private static final int[] KIND_COLOR = {0xFF1D5A2A, 0xFF8E1F14, 0xFF834508, 0xFF30496A};   // verde, vermelho, laranja e azul escuros: leem bem sobre o pergaminho
    private static final int[] FLAG_COLOR = {0xA82E22, 0xB4640E, 0x2F7A3A, 0x2E5FA8, 0x7A3F8F, 0x2A7F86, 0x7A5A2E, 0xB8892A};
    private static final int[] FLAG_ICON = {MapIcons.TAB_IDENTITY, MapIcons.TAB_BODY, MapIcons.TAB_HAIR, MapIcons.TAB_OUTFIT,
            MapIcons.TAB_SOUL, MapIcons.TAB_PAST};
    /** Ajuste fino (px) de cada ícone para o desenho ficar no centro da caixa de 16 x 16 (o corpo é mais largo à direita). */
    private static final int[] FLAG_ICON_DX = {0, -2, 0, 0, 0, 0};
    /** Ajuste vertical (px) de cada ícone dentro do pano: os quatro primeiros (mais baixos que a bandeira) sobem 2 px. */
    private static final int[] FLAG_ICON_DY = {-2, -2, -2, -2, 0, 0};
    private static final int MAX_SOUL_ROLLS = 5;
    private static final int WARM = 0xFF8A3A12;      // laranja-queimado dos destaques sobre o pergaminho

    // ------------------------------------------------------------------------------------------ estado
    private final Hooks hooks;
    private String name = "", motto = "", epithet = "";
    private int skin, hair, eyes, eyeStyle, hairStyle, faceStyle, outfit, outfitColor, gender, age, sea, beard, accessory;
    /** Adereços do rosto marcados (máscara de bits, no máximo {@link CharacterOptions#ADORN_MAX}) e a hora em que o jogador tentou passar do limite (pisca o contador). */
    private long adorn;
    /** Metal dos piercings do rosto e o de cada orelha, esquerda e direita (0 prata, 1 dourado) e a classe aberta na página Rosto (0 Faces, 1 Piercings, 2 Cicatrizes, 3 Marcas, 4 Outros). */
    private int metal, earMetalL, earMetalR, itemClass, lip, earSide, paint, faceX;
    private int race;                                                                  // 0 = Humana (a única disponível por enquanto)
    private int height = CharacterOptions.HEIGHT_DEFAULT, bust = CharacterOptions.BUST_DEFAULT;   // porte: altura em centésimos de bloco (a largura acompanha), busto em %
    /** Itens de cada orelha (esquerda e direita do personagem) e qual orelha a página Rosto está editando (0 esquerda, 1 direita). */
    private int earL, earR, earEdit;
    private long adornFullAt = Long.MIN_VALUE / 2;
    private final int[] traits = new int[CharacterOptions.TRAITS];
    private int tab = TAB_IDENTITY;
    private int eyePage, eyeStyleSeen = -1;                       // página da grade de estilos de olhos (8 por página, 4 x 2 como a página Rosto) e o estilo visto no último quadro
    private static final int EYE_PER_PAGE = 8;
    private int identGroup, bodyGroup, hairGroup, outfitGroup;   // sub-abas: Corpo (pele, olhos, rosto), Cabelo (cabelo, barba), Roupa (roupa, acessório)
    private int stage = EDIT;
    private long stageStart;
    private boolean finalizeSent;

    /** Um bit por aba: já foi aberta / o jogador fez ao menos uma escolha nela. */
    private int visited = 1 << TAB_IDENTITY, touched;
    private boolean dirty;
    private long lastSave;
    /** Alma sorteada: o giro anima os seis traços (cada um "assenta" um depois do outro). */
    private long rollAt, doneAt;
    private int soulRolls;
    private boolean rollDone = true;
    /** A alma sorteada é a lendária (todos os traços no máximo): plaquinhas e painel brilham. */
    private boolean legendGlow;
    private final boolean[] settled = new boolean[CharacterOptions.TRAITS];
    private final long[] flashAt = new long[CharacterOptions.TRAITS];
    /** Animações: abas deslizando, brilho de aba concluída, pulso de escolha, virada de página, som do selo. */
    private final float[] flagExt = new float[TABS];
    private final int[] lastState = new int[TABS];
    private final long[] gemAt = new long[TABS];
    private long lastGemSnd, pulseAt, tabAt;
    /** Bandeira sob o mouse (-1 nenhuma, {@link #TIP_RANDOM} o dado) e desde quando. */
    private int hoverFlag = -1;
    private long hoverFlagAt, clearHoverAt;
    private boolean clearHover;
    /** Etapa sob o mouse na barra de progresso e desde quando (o balão só vem depois do atraso). */
    private int segHover = -1;
    private long segHoverAt, clearSpinAt, doneChangeAt;
    private int shownDone = -1, prevDone;
    /** Animação das bandeiras: quando a tela abriu (elas se desenrolam uma depois da outra) e o último clique de cada uma (chacoalham). */
    private long openedAt, diceFlutter;
    /** Cada bandeira já estalou ao se desenrolar? (um som por bandeira, na abertura) */
    private final boolean[] flapped = new boolean[TABS];
    private final long[] flutterAt = new long[TABS];
    private int pulseX, pulseY, pulseW, pulseH, tabDir = 1;
    private boolean sealSnd;
    /** Miniaturas das opções: a skin de cada variante, pintada pelo próprio SkinPainter (o que se vê é o que se leva para o jogo). */
    private final Map<Integer, int[]> thumbCache = new HashMap<>();
    private float dt = 0.016F;
    private long diceAt;
    /** Som pedido durante o tratamento de um clique (tocado só se o clique mexeu em algo). */
    private int pendingSnd = -1;
    private float pendingPitch = 1.0F;
    private boolean dead;
    /** Editor grande do lema (janelinha aberta pelo clique no campo da aba Passado) e desde quando está aberto. */
    private boolean mottoOpen;
    private long mottoOpenAt;
    /** O editor do lema está por cima: o que está embaixo não reage ao mouse (nem brilha ao passar). */
    private boolean underModal;

    // por quadro
    private GuiGraphics g;
    private Font font;
    private int mx, my;
    private boolean click, consumed;
    private long t;
    private String tipTitle, tipDesc, tipExtra, tipKey;
    private long tipSince, tipShownAt;
    private int tipX, tipY, tipExtraColor;

    // giro e zoom do boneco (arrastar o retrato com o mouse, roda do mouse, botões)
    private static final float DEG_PER_PX = 2.4F;
    private float spin, spinVel, spinGoal;
    private float zoom = 1.0F, zoomTarget = 1.0F;
    private boolean turning, recentering, everTurned;
    private long lastDragMs, lastPressMs, lastFrameMs;

    private final Random rnd = new Random();

    public CharacterMap(Hooks hooks) {
        this.hooks = hooks;
        java.util.Arrays.fill(lastState, -1);
        CharacterData d = CharacterData.defaults();
        apply(d);
    }

    private void apply(CharacterData d) {
        skin = d.skin; hair = d.hair; eyes = d.eyes; eyeStyle = d.eyeStyle; hairStyle = d.hairStyle; faceStyle = d.faceStyle;
        outfit = d.outfit; outfitColor = d.outfitColor; gender = d.gender;
        age = d.age; sea = d.sea; beard = d.beard; accessory = d.accessory; adorn = d.adorn; metal = d.metal; earMetalL = d.earMetalL; earMetalR = d.earMetalR; lip = d.lip; earSide = d.earSide; earL = d.earL; earR = d.earR; paint = d.paint; faceX = d.faceX; height = d.height; bust = d.bust; race = d.race;
        for (int i = 0; i < traits.length; i++) traits[i] = d.trait(i);
    }

    // ------------------------------------------------------------------------------------------ API para a tela

    public CharacterData data() {
        int[] v = {skin, hair, eyes, hairStyle, faceStyle, outfit, outfitColor, 0, gender, age, sea, 0,
                traits[0], traits[1], traits[2], traits[3], traits[4], traits[5], beard, accessory, eyeStyle,
                (int) (adorn & 0x7FFFFFFFL), metal, (int) (adorn >>> 31), lip, earSide, earMetalL, (int) (adorn >>> 63), earL, earR, paint, faceX, height, 0, bust, race, earMetalR};          // o 0 é o número reservado (era a largura)
        return CharacterData.fromInts(name, motto, epithet, v);
    }

    /** O rascunho de agora (para gravar em arquivo). */
    public CharacterDraft draft() {
        return new CharacterDraft(data(), visited, touched, tab, soulRolls);
    }

    /** Volta ao ponto de um rascunho salvo (chamado logo ao abrir a tela, antes do primeiro desenho). */
    public void restore(CharacterDraft d) {
        if (d == null) return;
        apply(d.data);
        name = d.data.name;
        motto = d.data.motto;
        epithet = d.data.epithet;
        visited = d.visited | (1 << TAB_IDENTITY);
        touched = d.touched;
        tab = d.tab >= 0 && d.tab < TABS ? d.tab : TAB_IDENTITY;
        soulRolls = Math.max(0, Math.min(MAX_SOUL_ROLLS, d.soulRolls));
        visited |= 1 << tab;
    }

    public void setName(String n) {
        n = n == null ? "" : n;
        if (n.equals(name)) return;
        name = n;
        edit(TAB_IDENTITY);
    }

    /** O lema vai sem espaços nas pontas (o servidor recusa o personagem inteiro se sobrar um). */
    public void setMotto(String m) {
        m = m == null ? "" : m.strip();
        if (m.equals(motto)) return;
        motto = m;
        edit(TAB_PAST);
    }

    /** O epíteto vai sem espaços nas pontas. Opcional: vazio é válido. */
    public void setEpithet(String e) {
        e = e == null ? "" : e.strip();
        if (e.equals(epithet)) return;
        epithet = e;
        edit(TAB_IDENTITY);
    }

    public int stage() {
        return stage;
    }

    public int tab() {
        return tab;
    }

    /** O selo terminou de cair e a tela escureceu: pode fechar a tela. */
    public boolean isDone() {
        return stage == SEALING && fadeClock() >= SEAL_TOTAL;
    }

    /**
     * Relógio do fade final: igual ao tempo do selo até o instante em que o fade começaria; aí PARA até o mundo estar
     * pronto (servidor confirmou, terreno carregado e desenhado) e só então segue. Os brilhos e as brasas do selo continuam
     * no relógio normal, então a tela não congela enquanto espera.
     */
    private long fadeClock() {
        long now = System.currentTimeMillis(), raw = now - stageStart, from = SEAL_TOTAL - SEAL_FADE_MS;
        if (!fadeReleased) {
            if (raw < from) return raw;
            if (holdStart < 0L) holdStart = now;
            if (hooks.worldReady() || now - holdStart > WORLD_WAIT_MAX_MS) {
                fadeReleased = true;
                heldMs = now - holdStart;
            } else {
                return from;
            }
        }
        return raw - heldMs;
    }

    /** ESC no cartão de confirmação volta para a edição. */
    public boolean back() {
        if (stage != CONFIRM) return false;
        stage = EDIT;
        return true;
    }

    /** ENTER no cartão de confirmação sela. */
    public boolean confirm() {
        if (stage != CONFIRM) return false;
        startSealing();
        return true;
    }

    /** O editor grande do lema está aberto? (a tela manda o teclado para o campo e não deixa o clique vazar para o mapa) */
    public boolean mottoEditorOpen() {
        return mottoOpen && stage == EDIT && tab == TAB_PAST;
    }

    /** Esc ou Enter com o editor do lema aberto: fecha (o que foi escrito já está guardado). */
    public boolean closeMottoEditor() {
        if (!mottoEditorOpen()) return false;
        closeMotto();
        return true;
    }

    /** Atalho de teclado: vai para a aba {@code i}. Devolve true se mudou. */
    public boolean selectTab(int i) {
        if (stage != EDIT || mottoEditorOpen() || i < 0 || i >= TABS || i == tab) return false;
        setTab(i);
        return true;
    }

    /** Atalho de teclado: aba seguinte (+1) ou anterior (-1), sem dar a volta. */
    public boolean moveTab(int dir) {
        return selectTab(tab + dir);
    }

    public void render(GuiGraphics g, Font font, int mx, int my) {
        // Todos os retângulos do quadro (milhares) entram num lote só e saem juntos: sem isto cada fill() do jogo vira uma
        // chamada de desenho própria. Imagens, texto e o boneco 3D chamam g.flush() antes (ver MapIcons, MapArt.sheet,
        // MapFont e CharacterCreationScreen), então a ordem das camadas continua a mesma.
        g.drawManaged(() -> renderFrame(g, font, mx, my));
    }

    private void renderFrame(GuiGraphics g, Font font, int mx, int my) {
        this.g = g;
        this.font = font;
        this.mx = mx;
        this.my = my;
        this.click = false;
        this.t = System.currentTimeMillis();
        this.tipTitle = null;
        this.tipQuick = false;
        boolean modal = mottoEditorOpen();
        if (!modal) mottoOpen = false;
        underModal = modal;
        stepView();
        autosave();
        MapArt.sheet(g, 0, 0, t);
        hooks.nameBoxVisible(stage == EDIT && tab == TAB_IDENTITY && identGroup == 0);
        hooks.epithetBoxVisible(stage == EDIT && tab == TAB_IDENTITY && identGroup == 1);
        hooks.mottoBoxVisible(modal);
        flags();
        leftPage();
        rightPage();
        drawPulse();
        if (stage == EDIT) {
            applyTipDelay();
            tooltip();
        }
        if (stage != EDIT) {
            tipTitle = null;                  // os balões das páginas de baixo não vazam para o resumo
            overlay();
        }
        underModal = false;
        fades();
        if (modal) {
            tipTitle = null;
            mottoEditor();
        }
    }

    /**
     * Transições: ao abrir, o mapa sai do escuro (fade); ao trocar de aba, a página nova clareia a partir de uma névoa
     * de pergaminho (junto com o deslize). São só retângulos translúcidos por cima, então valem para texto e desenho.
     */
    private void fades() {
        if (g == null) return;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 190.0F);
        long since = t - openedAt;
        if (openedAt != 0L && since >= 0L && since < OPEN_FADE_MS) {
            double u = since / (double) OPEN_FADE_MS, k = Math.pow(1.0D - u, 2);
            MapArt.rect(g, -3000, -3000, 6400, 6400, MapArt.argb((int) Math.round(235 * k), 0x05030A));
        }
        long ta = t - tabAt;
        if (tabAt != 0L && ta >= 0L && ta < TAB_FADE_MS) {
            double k = Math.pow(1.0D - ta / (double) TAB_FADE_MS, 2);
            MapArt.rect(g, RX - 5, WASH_Y, RW + 8, WASH_H, MapArt.argb((int) Math.round(170 * k), 0xE3C78D));
        }
        g.pose().popPose();
    }

    /** Clique do mouse (coordenadas do mapa). Devolve true se algo reagiu. */
    public boolean click(double x, double y) {
        if (font == null) return false;
        this.g = null;
        this.mx = (int) Math.floor(x);
        this.my = (int) Math.floor(y);
        this.click = true;
        this.consumed = false;
        this.pendingSnd = -1;
        this.dead = false;
        this.underModal = false;
        if (stage == EDIT) {
            if (mottoEditorOpen()) {
                mottoEditor();                 // o editor do lema fica por cima de tudo e engole o clique
            } else {
                flags();
                leftPage();
                rightPage();
            }
        } else if (stage == CONFIRM) {
            overlay();
        }
        this.click = false;
        if (consumed && !dead) hooks.sound(pendingSnd >= 0 ? pendingSnd : SND_PICK, pendingSnd >= 0 ? pendingPitch : 0.95F + rnd.nextFloat() * 0.2F);
        return consumed;
    }

    /** A aba pode ser limpa? A Alma não: sem rolagens sobrando, os traços que saíram ficam (senão limpar e rolar viraria truque). */
    private boolean canClear(int i) {
        return i != TAB_SOUL || soulRolls < MAX_SOUL_ROLLS;
    }

    /** Botão direito do mouse: sobre uma aba, limpa a aba. */
    public boolean rightClick(double x, double y) {
        if (stage != EDIT || mottoEditorOpen()) return false;
        int px = (int) Math.floor(x), py = (int) Math.floor(y);
        for (int i = 0; i < TABS; i++) {
            if (px >= 0 && px < FLAG_HIT && py >= FLAG_Y + i * FLAG_STEP && py < FLAG_Y + i * FLAG_STEP + FLAG_H) {
                if (canClear(i)) clearTab(i); else sfx(SND_DENY, 1.0F);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ estados das abas

    private boolean seen(int i) {
        return (visited & (1 << i)) != 0;
    }

    /** Estado de uma aba: vermelho (falta o obrigatório), verde (concluída), dourado (visitada) ou cinza (ainda não). */
    private int tabState(int i) {
        if (i == TAB_IDENTITY) return CharacterOptions.validName(name) ? MapArt.ST_GREEN : MapArt.ST_RED;   // o nome é a única coisa obrigatória
        if ((touched & (1 << i)) != 0) return MapArt.ST_GREEN;
        return seen(i) ? MapArt.ST_GOLD : MapArt.ST_GRAY;
    }

    private int doneCount() {
        int n = 0;
        for (int i = 0; i < TABS; i++) if (tabState(i) == MapArt.ST_GREEN) n++;
        return n;
    }

    /** Marca que o jogador mexeu na aba {@code i} (concluída) e que há algo novo para gravar no rascunho. */
    private void edit(int i) {
        touched |= 1 << i;
        visited |= 1 << i;
        dirty = true;
    }

    private void autosave() {
        if (!dirty || finalizeSent || stage == SEALING) return;
        if (t - lastSave < SAVE_EVERY) return;
        dirty = false;
        lastSave = t;
        hooks.saveDraft(draft());
    }

    private void setTab(int i) {
        if (i != tab) {                                  // a página nova desliza para o lugar e a folha faz barulho de papel
            tabDir = i > tab ? 1 : -1;
            tabAt = System.currentTimeMillis();
            flutterAt[i] = tabAt;
            sfx(SND_PAGE, 0.92F + 0.06F * (i % 3));
        }
        tab = i;
        visited |= 1 << i;
        dirty = true;
    }

    /** Volta uma aba ao padrão (e ao estado de não mexida). */
    private void clearTab(int i) {
        CharacterData d = CharacterData.defaults();
        switch (i) {
            case TAB_IDENTITY -> {
                gender = d.gender;
                name = "";
                epithet = "";
                hooks.setNameText("");
                hooks.setEpithetText("");
            }
            case TAB_BODY -> { skin = d.skin; eyes = d.eyes; eyeStyle = d.eyeStyle; faceStyle = d.faceStyle; adorn = d.adorn; metal = d.metal; earMetalL = d.earMetalL; earMetalR = d.earMetalR; lip = d.lip; earSide = d.earSide; earL = d.earL; earR = d.earR; paint = d.paint; faceX = d.faceX; height = d.height; bust = d.bust; race = d.race; }
            case TAB_HAIR -> { hair = d.hair; hairStyle = d.hairStyle; beard = d.beard; }
            case TAB_OUTFIT -> { outfit = d.outfit; outfitColor = d.outfitColor; accessory = d.accessory; }
            case TAB_SOUL -> {
                for (int k = 0; k < traits.length; k++) traits[k] = d.trait(k);
                rollDone = true;
                rollAt = 0L;                                     // as rolagens já gastas continuam gastas
            }
            case TAB_PAST -> {
                age = d.age;
                sea = d.sea;
                motto = "";
                hooks.setMottoText("");
            }
        }
        touched &= ~(1 << i);
        dirty = true;
        changed();
    }

    // ------------------------------------------------------------------------------------------ giro e zoom do boneco

    private static int portraitX() {
        return LX + LW / 2 - PORT_W / 2;
    }

    private int viewBtnX() {
        return portraitX() + PORT_W - VIEW_BTN - 9;
    }

    private static int viewBtnY(int k) {
        return PORT_Y + 12 + k * (VIEW_BTN + VIEW_GAP);
    }

    private boolean overViewButtons(double x, double y) {
        return x >= viewBtnX() - 2 && x < viewBtnX() + VIEW_BTN + 2 && y >= viewBtnY(0) - 2 && y < viewBtnY(2) + VIEW_BTN + 2;
    }

    /** O ponto (coordenadas do mapa) está em cima do retrato (fora dos botões dele)? */
    public boolean inPortrait(double x, double y) {
        return x >= portraitX() && x < portraitX() + PORT_W && y >= PORT_Y && y < PORT_Y + PORT_H && !overViewButtons(x, y);
    }

    private static float wrap(float a) {
        a %= 360.0F;
        if (a > 180.0F) a -= 360.0F;
        if (a <= -180.0F) a += 360.0F;
        return a;
    }

    /**
     * Botão do mouse apertado em (x, y). Se for no retrato começa o giro (arrastar) e devolve true; dois cliques
     * seguidos no retrato fazem o boneco voltar de frente.
     */
    public boolean beginTurn(double x, double y) {
        if (stage != EDIT || mottoEditorOpen() || !inPortrait(x, y)) return false;
        long now = System.currentTimeMillis();
        spinVel = 0.0F;
        if (now - lastPressMs < 320L) {
            goTo(0.0F);
            turning = false;
            lastPressMs = 0L;
            return true;
        }
        lastPressMs = now;
        recentering = false;
        turning = true;
        lastDragMs = now;
        return true;
    }

    public boolean isTurning() {
        return turning;
    }

    /** O mouse andou {@code dxMap} pixels (do mapa) com o botão apertado: gira o boneco. */
    public void turnBy(double dxMap) {
        if (!turning) return;
        long now = System.currentTimeMillis();
        float dt = Math.max(1L, now - lastDragMs) / 1000.0F;
        float step = (float) (dxMap * DEG_PER_PX);
        spin = wrap(spin + step);
        spinVel = Math.max(-900.0F, Math.min(900.0F, spinVel * 0.6F + (step / dt) * 0.4F));
        lastDragMs = now;
        everTurned = true;
    }

    /** Soltou o botão: se o mouse ainda estava em movimento o boneco continua girando e vai parando. */
    public void endTurn() {
        if (!turning) return;
        turning = false;
        if (System.currentTimeMillis() - lastDragMs > 90L) spinVel = 0.0F;
    }

    /** Roda do mouse sobre o retrato: aproxima ({@code dir > 0}) ou afasta. Devolve true se o mouse estava no retrato. */
    public boolean scroll(double x, double y, double dir) {
        if (stage != EDIT) return false;
        if (mottoEditorOpen()) return true;                                  // com o editor do lema aberto a roda não mexe no mapa
        if (tab == TAB_PAST && ageRowY >= 0 && x >= RX && x < RX + RW && y >= ageRowY && y < ageRowY + AGE_ROW_H) {
            setAge(age + (dir > 0 ? 1 : -1));                               // roda sobre a barra da idade: ajuste fino
            return true;
        }
        if (tab == TAB_BODY && bodyGroup == 0 && x >= RX && x < RX + RW) {
            for (int k = 0; k < 2; k++) {
                if (sizeRowY[k] >= 0 && (k == 0 || gender == 1) && y >= sizeRowY[k] && y < sizeRowY[k] + SZ_ROW_H) {
                    szSet(k, szGet(k) + (dir > 0 ? 1 : -1));                     // roda sobre a barra: ajuste fino
                    return true;
                }
            }
        }
        if (tab == TAB_BODY && bodyGroup == 1 && x >= RX && x < RX + RW && y >= TOP && y < MapArt.MY + MapArt.MH - 8) {
            int pages = (CharacterOptions.EYE_STYLE_ORDER.length + EYE_PER_PAGE - 1) / EYE_PER_PAGE, np = Math.max(0, Math.min(pages - 1, eyePage + (dir > 0 ? -1 : 1)));
            if (np != eyePage) {                                             // roda sobre a página dos olhos: troca a página da grade de estilos
                eyePage = np;
                sfx(SND_PAGE, 1.2F);
            }
            return true;
        }
        boolean over = x >= portraitX() && x < portraitX() + PORT_W && y >= PORT_Y && y < PORT_Y + PORT_H;
        if (over) zoomBy(dir > 0 ? 1 : -1);
        return over;
    }

    private void zoomBy(int dir) {
        zoomTarget = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Math.round((zoomTarget + dir * ZOOM_STEP) * 100.0F) / 100.0F));
    }

    /** Gira o boneco com suavidade até o ângulo {@code deg} (0 = de frente, 180 = de costas). */
    private void goTo(float deg) {
        spinGoal = deg;
        recentering = true;
        spinVel = 0.0F;
    }

    private boolean viewingBack() {
        return Math.abs(wrap(spinGoal - spin)) < 1.0F ? spinGoal == 180.0F : Math.abs(spin) > 90.0F;
    }

    /** Inércia do giro, o giro até o ângulo pedido e a suavização do zoom (a cada quadro). */
    private void stepView() {
        dt = lastFrameMs == 0L ? 0.016F : Math.min(0.05F, (t - lastFrameMs) / 1000.0F);
        lastFrameMs = t;
        for (int i = 0; i < TABS; i++) {                 // uma aba que acaba de ficar verde solta um brilho
            int st = tabState(i);
            if (lastState[i] != st) {
                if (lastState[i] >= 0 && st == MapArt.ST_GREEN) {
                    gemAt[i] = t;
                    if (t - lastGemSnd > 500L && stage == EDIT) {
                        lastGemSnd = t;
                        hooks.sound(SND_GEM, 1.0F);
                    }
                }
                lastState[i] = st;
            }
        }
        if (rollAt != 0L && !rollDone) {                 // cada traço da alma assenta um depois do outro, com um tique
            long el = t - rollAt;
            for (int k = 0; k < settled.length; k++) {
                if (!settled[k] && el >= SETTLE0 + (long) k * SETTLE_STEP) {
                    settled[k] = true;
                    flashAt[k] = t;
                    if (tab == TAB_SOUL) hooks.sound(SND_TICK, 0.80F + 0.09F * k);
                }
            }
            if (el >= SETTLE0 + (long) (settled.length - 1) * SETTLE_STEP + 140L) {
                rollDone = true;
                doneAt = t;
                if (tab == TAB_SOUL) hooks.sound(SND_CHIME, 1.0F);
            }
        }
        zoom += (zoomTarget - zoom) * (1.0F - (float) Math.exp(-12.0D * dt));
        if (Math.abs(zoomTarget - zoom) < 0.004F) zoom = zoomTarget;
        if (stage != EDIT) {
            turning = false;
            spinVel = 0.0F;
        }
        if (turning) return;
        if (recentering) {
            float d = wrap(spinGoal - spin);
            spin = wrap(spin + d * (1.0F - (float) Math.exp(-9.0D * dt)));
            if (Math.abs(d) < 0.6F) {
                spin = spinGoal == 180.0F ? 180.0F : 0.0F;
                recentering = false;
            }
        } else if (Math.abs(spinVel) > 6.0F) {
            spin = wrap(spin + spinVel * dt);
            spinVel *= (float) Math.exp(-3.2D * dt);
        } else {
            spinVel = 0.0F;
        }
    }

    // ------------------------------------------------------------------------------------------ helpers de entrada e texto

    private boolean in(int x, int y, int w, int h) {
        if (underModal) return false;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** No modo clique: true (uma vez só por clique) se o mouse está no retângulo. */
    private boolean hit(int x, int y, int w, int h) {
        if (click && !consumed && in(x, y, w, h)) {
            consumed = true;
            return true;
        }
        return false;
    }

    private int stateOf(int base, int x, int y, int w, int h) {
        return base == MapArt.NORMAL && in(x, y, w, h) ? MapArt.HOVER : base;
    }

    private static int inkOf(int state) {
        return switch (state) {
            case MapArt.SELECTED -> MapArt.CREAM;
            case MapArt.DISABLED -> 0xFF7A705C;
            default -> MapArt.INK;
        };
    }

    private static String tr(String key, Object... args) {
        return I18n.get("character.lasthorizon." + key, args);
    }

    private int w(String s, int kind) {
        return MapFont.width(font, s, kind);
    }

    private void text(String s, int x, int y, int color, int kind) {
        if (g != null) MapFont.draw(g, font, s, x, y, color, kind);
    }

    /**
     * Texto que tem que caber em {@code maxW}: se for um pouco maior, encolhe até 70% (a letra continua legível);
     * se ainda assim não couber, corta com reticências.
     */
    private void fitText(String s, int x, int y, int maxW, int color, int kind) {
        if (g == null) return;
        int tw = w(s, kind);
        if (tw <= maxW) {
            text(s, x, y, color, kind);
            return;
        }
        float sc = maxW / (float) tw;
        if (sc < 0.70F) {
            text(clip(s, maxW, kind), x, y, color, kind);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - sc) * 3.0F, 0.0F);      // desce um pouco para a linha de base não subir
        g.pose().scale(sc, sc, 1.0F);
        MapFont.draw(g, font, s, 0, 0, color, kind);
        g.pose().popPose();
    }

    private void textC(String s, int cx, int y, int color, int kind) {
        if (g != null) MapFont.draw(g, font, s, cx - w(s, kind) / 2, y, color, kind);
    }

    /** Texto com sombra deslocada 1 px (título entalhado no papel ou letra clara sobre fundo colorido). */
    private void textCS(String s, int cx, int y, int color, int shadow, int kind) {
        textC(s, cx + 1, y + 1, shadow, kind);
        textC(s, cx, y, color, kind);
    }

    /** Quebra {@code s} em linhas de no máximo {@code maxW} pixels. */
    private List<String> wrap(String s, int maxW, int kind) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() == 0 || w(trial, kind) <= maxW) {
                cur.setLength(0);
                cur.append(trial);
            } else {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private int lines(List<String> ls, int x, int y, int color, int kind, int pitch) {
        for (String l : ls) {
            text(l, x, y, color, kind);
            y += pitch;
        }
        return y;
    }

    /**
     * Parágrafo que nunca é cortado: quebra {@code s} em linhas de até {@code maxW} pixels e, se não couber em {@code maxH} de altura,
     * encolhe a letra aos poucos (até 62%) e quebra de novo, em vez de cortar o fim do texto. Desenha só se {@code g != null}, mas a
     * altura devolvida é a mesma nos dois casos (o clique usa o mesmo layout). Devolve a altura usada.
     */
    private int paragraph(String s, int x, int y, int maxW, int maxH, int color, int kind, int pitch) {
        float sc = 1.0F;
        List<String> ls = wrap(s, maxW, kind);
        while (ls.size() * pitch * sc > maxH + 0.5F && sc > 0.62F) {
            sc = Math.max(0.62F, sc - 0.08F);
            ls = wrap(s, Math.round(maxW / sc), kind);
        }
        if (g != null) {
            if (sc >= 0.999F) {
                lines(ls, x, y, color, kind, pitch);
            } else {
                g.pose().pushPose();
                g.pose().translate(x, y + (1.0F - sc) * 3.0F, 0.0F);
                g.pose().scale(sc, sc, 1.0F);
                lines(ls, 0, 0, color, kind, pitch);
                g.pose().popPose();
            }
        }
        return Math.round(ls.size() * pitch * sc);
    }

    private String clip(String s, int maxW, int kind) {
        if (w(s, kind) <= maxW) return s;
        while (s.length() > 1 && w(s + "...", kind) > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    /** Pede um som. No tratamento de clique ele só toca se o clique reagiu a algo; fora disso, toca na hora. */
    private void sfx(int kind, float pitch) {
        if (click) {
            pendingSnd = kind;
            pendingPitch = pitch;
        } else {
            hooks.sound(kind, pitch);
        }
    }

    /** Marca um retângulo para o anel dourado que se abre (e some) quando o jogador escolhe algo. */
    private void pulse(int x, int y, int w, int h) {
        if (!click) return;
        pulseAt = System.currentTimeMillis();
        pulseX = x;
        pulseY = y;
        pulseW = w;
        pulseH = h;
    }

    private void drawPulse() {
        if (g == null || pulseAt == 0L) return;
        long a = t - pulseAt;
        if (a < 0L || a >= PULSE_MS) return;
        double u = a / (double) PULSE_MS;
        int e = 1 + (int) Math.round(5.0D * u), col = MapArt.argb((int) (215 * (1.0D - u)), 0xFFE9A0);
        MapArt.rect(g, pulseX - e, pulseY - e, pulseW + 2 * e, 1, col);
        MapArt.rect(g, pulseX - e, pulseY + pulseH + e - 1, pulseW + 2 * e, 1, col);
        MapArt.rect(g, pulseX - e, pulseY - e, 1, pulseH + 2 * e, col);
        MapArt.rect(g, pulseX + pulseW + e - 1, pulseY - e, 1, pulseH + 2 * e, col);
    }

    private static int hash(int a, int b) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7FFFFFFF;
    }

    private void changed() {
        hooks.changed(data());
    }

    /** O jogador escolheu algo na aba atual que muda a skin: marca a aba e atualiza a prévia. */
    private void picked() {
        edit(tab);
        changed();
    }

    private static int argb(int a, int rgb) {
        return MapArt.argb(a, rgb);
    }

    /** Sorteio. Completo: aparência, personalidade, idade e mar natal (e um nome, se não houver). Com Shift: só a aparência. */
    private void randomize(boolean looksOnly) {
        skin = rnd.nextInt(CharacterOptions.SKIN.length);
        hair = rnd.nextInt(CharacterOptions.HAIR.length);
        eyes = rnd.nextInt(CharacterOptions.EYES.length);
        eyeStyle = CharacterOptions.EYE_STYLE_ORDER[rnd.nextInt(CharacterOptions.EYE_STYLE_ORDER.length)];
        hairStyle = rnd.nextInt(CharacterOptions.HAIR_STYLES);
        faceStyle = 0;
        outfit = rnd.nextInt(CharacterOptions.OUTFITS);
        outfitColor = rnd.nextInt(CharacterOptions.OUTFIT_COLOR.length);
        beard = rnd.nextInt(4) == 0 ? 1 + rnd.nextInt(CharacterOptions.BEARDS - 1) : 0;          // a maioria sem barba
        accessory = rnd.nextInt(3) == 0 ? 1 + rnd.nextInt(CharacterOptions.ACCESSORIES - 1) : 0;
        adorn = 0;                                                                                // a maioria com poucas marcas: de 0 a 2 itens do rosto (faces ou adereços), sem repetir
        for (int n = rnd.nextInt(3); n > 0; n--) adorn |= 1L << rnd.nextInt(CharacterOptions.FACE_ITEMS);
        metal = rnd.nextInt(CharacterOptions.METALS);
        earMetalL = rnd.nextInt(CharacterOptions.METALS);
        earMetalR = rnd.nextInt(4) == 0 ? rnd.nextInt(CharacterOptions.METALS) : earMetalL;      // na maioria dos sorteios os brincos são um par (o mesmo metal nas duas orelhas)
        lip = rnd.nextInt(CharacterOptions.LIPS);
        paint = rnd.nextInt(CharacterOptions.PAINTS);
        faceX = 0;
        earSide = 0;
        earL = rnd.nextInt(3) == 0 ? 1 << rnd.nextInt(CharacterOptions.EAR_ITEMS) : 0;          // a maioria sem brincos; cada orelha pode ter o seu
        earR = rnd.nextInt(3) == 0 ? 1 << rnd.nextInt(CharacterOptions.EAR_ITEMS) : 0;
        touched |= (1 << TAB_BODY) | (1 << TAB_HAIR) | (1 << TAB_OUTFIT);
        if (!looksOnly) {
            sea = rnd.nextInt(CharacterOptions.SEAS);
            age = CharacterOptions.AGE_MIN + 2 + rnd.nextInt(34);       // 18 a 51: a maior parte dos aventureiros
            if (soulRolls < MAX_SOUL_ROLLS && !rolling()) {              // sortear a alma gasta uma rolagem; sem rolagens ela fica como está
                int[] old = traits.clone();
                int[] next;
                do {
                    next = CharacterOptions.rollTraits(rnd);
                } while (java.util.Arrays.equals(old, next));
                System.arraycopy(next, 0, traits, 0, traits.length);
                soulRolls++;
                rollDone = true;
                touched |= 1 << TAB_SOUL;
            }
            touched |= 1 << TAB_PAST;
            if (name.isEmpty()) hooks.setNameText(CharacterOptions.randomName(rnd, name));
        }
        dirty = true;
        changed();
        sfx(SND_ROLL, 1.0F);
    }

    // ------------------------------------------------------------------------------------------ dicas (balão)

    /** Pede um balão com título, descrição e uma linha extra colorida (as linhas extras se separam com \n). */
    private void tip(String title, String desc, String extra, int extraColor, int x, int y) {
        tipTitle = title;
        tipDesc = desc;
        tipExtra = extra;
        tipExtraColor = extraColor;
        tipX = x;
        tipY = y;
    }

    /**
     * Atraso único de todos os balões da criação de personagem: o balão pedido por {@link #tip} só passa a aparecer
     * depois de {@link #TIP_DELAY_MS} com o mouse sobre a mesma coisa (a "mesma coisa" é o título + texto do balão;
     * trocar de botão, cor ou aba zera o relógio, e tirar o mouse também).
     */
    private void applyTipDelay() {
        if (tipTitle == null) {
            tipKey = null;
            return;
        }
        String key = tipTitle + "\u0000" + tipDesc;
        if (!key.equals(tipKey)) {
            tipKey = key;
            tipSince = t;
        }
        long delay = tipQuick ? TIP_QUICK_MS : TIP_DELAY_MS;
        if (t - tipSince < delay) tipTitle = null;
        else tipShownAt = tipSince + delay;      // o balão entra com uma animação curta a partir daqui
    }

    /** Dica de uma cor: só o nome, ao lado do mouse. */
    private void colorTip(String kind, int i) {
        tip(tr("color." + kind + "." + i), null, null, 0, mx + 10, my + 12);
    }

    /** Dica de uma aba: nome, o que ela guarda e o estado dela. */
    private void tabTip(int i, int x, int y) {
        int st = tabState(i);
        String state = st == MapArt.ST_RED ? tr("tab.missing." + i) : tr("tab.state." + st);
        tip(tr("tab." + i), tr("tab." + i + ".desc"), state, MapArt.stateColor(st), x, y);
    }

    /** Quanto tempo o balão leva para entrar (sobe 4 px e aparece). */
    private static final long TIP_IN_MS = 170L;
    /** Largura do texto do balão e recuo dos itens com marcador. */
    private static final int TIP_TEXT_W = 124, TIP_BULLET = 8;

    /**
     * Balão do que está sob o mouse (aba, cor, controle). Placa escura com faixa no título, divisor com losango, itens
     * extras com marcador (o estado de uma aba leva um losango da cor do estado) e um ponteirinho que olha para o mouse.
     */
    private void tooltip() {
        if (g == null || tipTitle == null) return;
        List<String> body = tipDesc == null ? new ArrayList<>() : wrap(tipDesc, TIP_TEXT_W, NOTE);
        List<List<String>> extra = new ArrayList<>();                        // um item por linha do texto extra; cada um com o seu marcador
        if (tipExtra != null) for (String part : tipExtra.split("\n")) extra.add(wrap(part, TIP_TEXT_W - TIP_BULLET, NOTE));
        int extraLines = 0, bw = w(tipTitle, BOLD);
        for (String l : body) bw = Math.max(bw, w(l, NOTE));
        for (List<String> item : extra) {
            extraLines += item.size();
            for (String l : item) bw = Math.max(bw, w(l, NOTE) + TIP_BULLET);
        }
        bw += 16;
        boolean more = !body.isEmpty() || !extra.isEmpty();
        int bh = 6 + 9 + (more ? 5 : 0) + body.size() * 10 + extraLines * 10 + (extra.isEmpty() ? 0 : 1) + 6;
        int x = Math.max(2, Math.min(tipX, MapArt.BW - bw - 2)), y = Math.max(2, Math.min(tipY, MapArt.BH - bh - 2));

        double u = Math.max(0.0D, Math.min(1.0D, (t - tipShownAt) / (double) TIP_IN_MS));
        double e = 1.0D - Math.pow(1.0D - u, 3);                             // entra freando
        y += (int) Math.round((1.0D - e) * 4.0D);

        // o ponteiro sai do lado da caixa mais perto do mouse (se o mouse está dentro da caixa, não há ponteiro)
        int dx = mx < x ? x - mx : mx >= x + bw ? mx - (x + bw - 1) : 0;
        int dy = my < y ? y - my : my >= y + bh ? my - (y + bh - 1) : 0;
        int side = MapArt.TAIL_NONE, pos = 0;
        if (dx > 0 || dy > 0) {
            if (dy >= dx) {
                side = my < y ? MapArt.TAIL_TOP : MapArt.TAIL_BOTTOM;
                pos = mx;
            } else {
                side = mx < x ? MapArt.TAIL_LEFT : MapArt.TAIL_RIGHT;
                pos = my;
            }
        }

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 120.0F);
        MapArt.tipBox(g, x, y, bw, bh, t, e, side, pos, more);
        if (e >= 0.2D) {                                                       // (texto com alfa quase zero o jogo desenha opaco)
            text(tipTitle, x + 8, y + 6, MapArt.fade(MapArt.GOLD_LIGHT, e), BOLD);
            int cy = y + 15;
            if (more) {
                MapArt.tipDivider(g, x + 6, cy + 1, bw - 12, e);
                cy += 5;
            }
            cy = lines(body, x + 8, cy, MapArt.fade(0xFFEBDDB8, e), NOTE, 10);
            if (!extra.isEmpty()) {
                cy += 1;
                int col = MapArt.fade(brighter(tipExtraColor), e);
                for (List<String> item : extra) {
                    MapArt.diamond(g, x + 9, cy + 4, col);                    // marcador do item
                    cy = lines(item, x + 8 + TIP_BULLET, cy, col, NOTE, 10);
                }
            }
        }
        g.pose().popPose();
    }

    /** As cores de estado são escuras para o pergaminho; sobre o balão escuro elas precisam de mais luz. */
    private static int brighter(int argb) {
        return MapArt.lerp(argb, 0xFFFFFFFF, 0.38D);
    }

    // ------------------------------------------------------------------------------------------ abas

    /** As bandeiras (abas) e a do aleatório, hasteadas na borda esquerda do mapa. */
    private void flags() {
        int hovered = -1;
        if (g != null && openedAt == 0L) {
            openedAt = t;
            hooks.sound(SND_OPEN, 1.0F);                                       // o mapa se desenrola na mesa
        }
        for (int i = 0; i < TABS + 1; i++) {
            if (i == TABS) break;
            int y = FLAG_Y + i * FLAG_STEP;
            boolean sel = i == tab;
            boolean over = in(0, y, FLAG_HIT, FLAG_H);
            int st = tabState(i);
            if (over) hovered = i;
            if (g != null) {
                flagExt[i] += ((sel ? 1.0F : over ? 0.5F : 0.0F) - flagExt[i]) * (1.0F - (float) Math.exp(-16.0D * dt));
                int ext = Math.round(flagExt[i] * 3.0F);          // a bandeira se estica para fora do mastro (suave)
                double u = Math.max(0.0D, Math.min(1.0D, (t - openedAt - i * 90L) / 520.0D));      // desenrola da esquerda para a direita, uma por vez
                double e = 1.0D + 2.2D * Math.pow(u - 1.0D, 3) + 1.2D * Math.pow(u - 1.0D, 2);       // sai do mastro e passa um pouco do ponto (mola)
                int grow = (int) Math.round((1.0D - Math.min(e, 1.12D)) * (FLAG_W - 8));
                if (!flapped[i] && u > 0.3D) {                                    // cada bandeira estala ao sair do mastro
                    flapped[i] = true;
                    hooks.sound(SND_FLAP, 0.85F + 0.07F * i);
                }
                double boost = Math.max(0.0D, 1.0D - (t - flutterAt[i]) / 650.0D);
                int x = FLAG_X - ext + grow, wd = FLAG_W + ext - grow;
                int rgb = FLAG_COLOR[i];                              // cada aba tem a sua cor; dentro da bandeira é um tom só
                if (!seen(i) && st != MapArt.ST_RED) rgb = MapArt.lerp(0xFF000000 | rgb, 0xFF2A1A10, 0.38D) & 0xFFFFFF;   // ainda não visitada: a mesma cor, mais escura
                int dy = MapArt.flag(g, x, y, wd, FLAG_H, rgb, FLAG_SHAPE[i], i, sel, over, t, boost);
                if (u > 0.7D) {
                    int ix = MapArt.flagIconX(x, wd) + FLAG_ICON_DX[i] + Math.round((x - FLAG_X) / 2.0F), iy = y + 3 + dy + FLAG_ICON_DY[i];      // o pano cresce para a esquerda (mastro fixo): o ícone anda metade disso e fica no meio do pano
                    MapIcons.drawLive(g, FLAG_ICON[i], ix, iy, t);      // o desenho se mexe por dentro (sem pulinho)
                }
                int gx = FLAG_X + FLAG_W - 2, gy = y + 1;                  // a pedra de estado é o enfeite do alto do mastro
                MapArt.gem(g, gx, gy, st, t);
                if (gemAt[i] != 0L) MapArt.sparkle(g, gx, gy, (t - gemAt[i]) / (double) SPARKLE_MS, 0xFFF3B0);
            }
            if (over && g != null && flagTipReady(i)) tabTip(i, FLAG_X + FLAG_W + 5, y);
            if (hit(0, y, FLAG_HIT, FLAG_H)) setTab(i);
        }
        boolean over = in(0, RANDOM_Y, FLAG_HIT, FLAG_H);
        if (over) hovered = TIP_RANDOM;
        if (g != null) {
            int dy = MapArt.flag(g, FLAG_X, RANDOM_Y, FLAG_W, FLAG_H, 0x5A4630, MapArt.FLAG_CHECKER, 9, false, over, t, Math.max(0.0D, 1.0D - (t - diceFlutter) / 650.0D));
            int ix = MapArt.flagIconX(FLAG_X, FLAG_W), iy = RANDOM_Y + 3 + dy;
            MapArt.notched(g, ix - 1, iy - 1, MapIcons.SIZE + 2, MapIcons.SIZE + 2, 0xFF24140A);      // plaquinha escura atrás do dado
            MapArt.notched(g, ix, iy, MapIcons.SIZE, MapIcons.SIZE, 0xFF6B4A32);
            long sinceRoll = t - diceFlutter;                                            // ao clicar o dado rola; em cima dele, chacoalha
            if (diceFlutter != 0L && sinceRoll < IconMotion.ROLL_MS) MapIcons.drawPose(g, MapIcons.TAB_RANDOM, ix, iy, IconMotion.roll(sinceRoll));
            else MapIcons.drawLive(g, MapIcons.TAB_RANDOM, ix, iy, t);
            if (over && flagTipReady(TIP_RANDOM)) tip(tr("random"), tr("random.desc"), null, 0, FLAG_X + FLAG_W + 5, RANDOM_Y);
            if (hovered != hoverFlag) {                                  // o relógio do balão recomeça ao trocar de bandeira
                hoverFlag = hovered;
                hoverFlagAt = t;
                if (hovered >= 0 && stage == EDIT && !underModal && openedAt != 0L && t - openedAt > 1400L) hooks.sound(SND_HOVER, hovered == TIP_RANDOM ? 1.3F : 0.9F + 0.05F * hovered);
            }
        }
        if (hit(0, RANDOM_Y, FLAG_HIT, FLAG_H)) {
            diceFlutter = System.currentTimeMillis();
            randomize(hooks.shiftDown());
        }
    }

    /** O mouse já ficou parado tempo bastante sobre a bandeira {@code i} para o balão aparecer? */
    private boolean flagTipReady(int i) {
        return true;          // o atraso de 5 s é do {@link #applyTipDelay}; aqui basta o mouse estar sobre a bandeira
    }

    /** O hover do botão lateral só aparece depois do mesmo atraso de 5 segundos dos outros balões. */
    private boolean clearTipReady() {
        return clearHover && t - clearHoverAt >= TIP_DELAY_MS;
    }

    // ------------------------------------------------------------------------------------------ painel da esquerda

    private void leftPage() {
        int cx = LX + LW / 2;
        if (g != null) {
            MapArt.darkPlate(g, HEAD_X, HEAD_Y, HEAD_W, HEAD_H, t);
            String title = tr("title");
            int tk = w(title, TITLE) <= HEAD_W - 10 ? TITLE : BODY;
            MapFont.drawRaw(g, font, title, HEAD_X + (HEAD_W - w(title, tk)) / 2, HEAD_Y + 3, MapArt.GOLD_LIGHT, tk);
        }

        // botão de limpar: agora fica ao lado da barra, sem disputar espaço com o título
        boolean clearOver = in(CLEAR_X, CLEAR_Y, CLEAR_S, CLEAR_S);
        if (clearOver != clearHover) {
            clearHover = clearOver;
            clearHoverAt = t;
        }
        boolean clearOk = canClear(tab);
        int cs = clearOk ? stateOf(MapArt.NORMAL, CLEAR_X, CLEAR_Y, CLEAR_S, CLEAR_S) : MapArt.DISABLED;
        if (g != null) {
            MapArt.clearButton(g, CLEAR_X, CLEAR_Y, CLEAR_S, cs, t, clearTipReady());
            double sp = clearSpinAt == 0L ? -1.0D : (t - clearSpinAt) / (double) CLEAR_SPIN_MS;
            MapArt.clearIcon(g, CLEAR_X, CLEAR_Y, CLEAR_S, cs, t, sp);
        }
        // A dica é deliberadamente tardia: o botão não polui a leitura da barra ao passar rapidamente.
        if (clearOver) tip(tr("clear"), tr(clearOk ? "clear.desc" : "clear.soul.locked"), null, 0, CLEAR_X - 116, CLEAR_Y + CLEAR_S + 4);
        if (hit(CLEAR_X, CLEAR_Y, CLEAR_S, CLEAR_S)) {
            if (clearOk) {
                clearSpinAt = System.currentTimeMillis();
                clearTab(tab);
            } else {
                sfx(SND_DENY, 1.0F);
            }
        }

        progress();

        // retrato
        int px = cx - PORT_W / 2;
        int feetY = PORT_Y + PORT_H - 15;      // os pés ficam dentro da moldura (sem plataforma: só uma sombrinha)
        if (g != null) {
            MapArt.portrait(g, px, PORT_Y, PORT_W, PORT_H, t);
            drawFigure(cx, feetY, px);
            turnHints(px, cx);
            caption(cx);
        }
        viewButtons();
        if (stage == EDIT && inPortrait(mx, my) && !turning && !everTurned) {
            tip(tr("turn.hint"), tr("view.flip.desc"), null, 0, mx + 10, my + 12);
        }

        // placa do nome (nova borda ornamentada, sem setas) e a frase logo abaixo
        if (g != null) {
            boolean empty = name.isEmpty();
            boolean valid = CharacterOptions.validName(name);
            int gemRgb = valid ? 0x3FD16A : empty ? 0xE0453A : 0xE8A33A;
            MapArt.nameplate(g, cx, NAMEPLATE_Y, 128, 20, t, gemRgb);
            String shown = empty ? tr("name.none") : name;
            int kind = empty ? NOTE : MapFont.kindFor(shown, BOLD);
            shown = clip(shown, 88, kind);
            textCS(shown, cx, NAMEPLATE_Y + 6, empty ? 0xFFB09A70 : MapArt.CREAM, 0xFF0E0703, kind);
            caption(cx);
        }

        // finalizar (botão em fita com medalhão de cera). Sem nome ele é pedra cinza; com nome vira fita vermelha, e a
        // passagem de um estado para o outro é animada (cor, medalhão, texto, clarão e um tremidinho ao voltar a cinza)
        int fx = LX + 4, fy = 179, fw = LW - 8, fh = 22;
        boolean ok = CharacterOptions.validName(name);
        boolean over = ok && in(fx, fy, fw, fh);
        if (g != null) {
            if (finishMix < 0.0F) {
                finishMix = ok ? 1.0F : 0.0F;
                finishOk = ok;
            } else {
                if (ok != finishOk) {
                    finishOk = ok;
                    finishFlipAt = t;
                    if (ok && stage == EDIT) hooks.sound(SND_GEM, 1.15F);              // pronto: tim
                    else if (stage == EDIT) hooks.sound(SND_TICK, 0.6F);               // voltou a cinza: toc surdo
                }
                float goal = ok ? 1.0F : 0.0F, step = dt / 0.45F;
                finishMix = finishMix < goal ? Math.min(goal, finishMix + step) : Math.max(goal, finishMix - step);
            }
            double e = finishMix * finishMix * (3.0D - 2.0D * finishMix);              // suaviza o começo e o fim
            long since = t - finishFlipAt;
            boolean flipped = finishFlipAt != 0L;
            double flash = flipped && finishOk && since < 650L ? 1.0D - since / 650.0D : 0.0D;
            int shake = flipped && !finishOk && since < 260L ? (int) Math.round(Math.sin(since / 22.0D) * 2.0D * (1.0D - since / 260.0D)) : 0;
            int ox = fx + shake;
            MapArt.finishButton(g, ox, fy, fw, fh, e, flash, over, t);
            int sx = ox + 15, sy = fy + fh / 2;
            int rim = MapArt.lerp(0xFF8A8270, over ? MapArt.GOLD_LIGHT : MapArt.goldPulse(t), e);
            MapArt.disc(g, sx, sy, 11, 0xFF1A0E06);                           // medalhão: aro de ouro com rebites em volta do selo
            MapArt.disc(g, sx, sy, 10, rim);
            MapArt.disc(g, sx, sy, 8, 0xFF1A0E06);
            MapArt.disc(g, sx, sy, 7, MapArt.lerp(0xFF6E685A, 0xFF5E120C, e));
            int studs = MapArt.lerp(0xFF4A4638, 0xFF3A200A, e);
            for (int k = 0; k < 8; k++) {
                double an = k * Math.PI / 4.0D;
                MapArt.rect(g, sx + (int) Math.round(Math.cos(an) * 9.0D), sy + (int) Math.round(Math.sin(an) * 9.0D), 1, 1, studs);
            }
            if (e < 0.999D) MapArt.disc(g, sx, sy, 6, MapArt.argb((int) (200 * (1.0D - e)), 0xB9B09A));   // o cinza some aos poucos
            if (e > 0.5D) {
                boolean stamp = flipped && finishOk && since < 140L;              // o selo "carimba": começa maior e assenta
                MapArt.seal(g, sx, sy, stamp ? 7 : 6, over ? 0.6D : stamp ? 0.8D : 0.0D);
            }
            if (flipped && finishOk && since < 520L) {                            // anel de luz que se abre do medalhão
                double p = since / 520.0D;
                int rr = (int) Math.round(12 + 13 * p), a = (int) (170 * (1.0D - p));
                for (int k = 0; k < 28; k++) {
                    double an = k * Math.PI / 14.0D;
                    MapArt.rect(g, sx + (int) Math.round(Math.cos(an) * rr), sy + (int) Math.round(Math.sin(an) * rr), 1, 1, MapArt.argb(a, 0xFFE9A0));
                }
            }
            // texto: o antigo some e o novo aparece (a troca acontece no meio da transição)
            boolean showOk = e > 0.5D;
            float fade = (float) Math.min(1.0D, Math.abs(2.0D * e - 1.0D) * 1.6D);
            if (fade > 0.06F) {
                int tx = ox + 30 + (fw - 30) / 2, ty = fy + (fh - 7) / 2;
                String label = showOk ? tr("finish") : tr("finish.blocked");
                int half = w(label, BOLD) / 2;
                int tcol = showOk ? (over ? 0xFFFFF4C8 : MapArt.CREAM) : 0xFF4A463A, scol = showOk ? 0xFF3A0A06 : 0x66FFFFFF;
                textCS(label, tx, ty, fadeArgb(tcol, fade), fadeArgb(scol, fade), BOLD);
                int dcol = fadeArgb(showOk ? MapArt.GOLD_LIGHT : 0xFF7A705C, fade);
                MapArt.diamond(g, tx - half - 7, fy + fh / 2, dcol);                // enfeites ao lado do texto
                MapArt.diamond(g, tx + half + 7, fy + fh / 2, dcol);
            }
        }
        if (in(fx, fy, fw, fh)) {
            if (ok) tip(tr("finish"), tr("finish.ready"), null, 0, fx, fy - 40);
            else tip(tr("finish.pending"), null, tr("tab.missing.0"), MapArt.RED, fx, fy - 30);
        }
        if (hit(fx, fy, fw, fh)) {
            if (ok) {
                stage = CONFIRM;
                stageStart = System.currentTimeMillis();
                sfx(SND_PAGE, 0.78F);
            } else {                                  // falta o nome: leva para o campo
                setTab(TAB_IDENTITY);
                identGroup = 0;
                hooks.focusNameBox();
                sfx(SND_DENY, 1.0F);
            }
        }
    }

    /** Transição do botão de finalizar: 0 = cinza (sem nome), 1 = vermelho (pronto); -1 até o primeiro quadro. */
    private float finishMix = -1.0F;
    private boolean finishOk;
    private long finishFlipAt;

    /** A mesma cor com a opacidade multiplicada por {@code f} (0 a 1). */
    private static int fadeArgb(int argb, float f) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, f)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Barra de progresso: uma etapa por aba (que se enche de cor ao concluir), a luz que varre a barra e o contador. */
    private void progress() {
        int overSeg = -1;
        int done = doneCount();
        boolean all = done == TABS;
        if (shownDone != done) {                                  // o número "rola" quando muda (menos na primeira vez)
            if (shownDone >= 0) {
                prevDone = shownDone;
                doneChangeAt = t;
            }
            shownDone = done;
        }
        int segsW = TABS * PROG_SEG_W + (TABS - 1) * PROG_GAP;
        if (g != null) {
            MapArt.darkPlate(g, PLATE_X, PLATE_Y, PLATE_W, PLATE_H, t);
            MapArt.progressSheen(g, PROG_X - 1, PROG_Y - 1, segsW + 2, PROG_H + 2, t, all, false);   // luz larga por trás das etapas
        }
        for (int i = 0; i < TABS; i++) {
            int sx = PROG_X + i * (PROG_SEG_W + PROG_GAP);
            boolean over = in(sx - 1, PROG_Y - 3, PROG_SEG_W + 2, PROG_H + 6);
            if (over) overSeg = i;
            if (g != null) {
                long sinceDone = gemAt[i] == 0L ? Long.MAX_VALUE / 4 : t - gemAt[i];
                MapArt.segment(g, sx, PROG_Y, PROG_SEG_W, PROG_H, tabState(i), i == tab, over, t, FLAG_COLOR[i], sinceDone);
                if (gemAt[i] != 0L) MapArt.sparkle(g, sx + PROG_SEG_W / 2, PROG_Y + PROG_H / 2, (t - gemAt[i]) / (double) SPARKLE_MS, 0xFFFFFF);
            }
            if (hit(sx - 1, PROG_Y - 3, PROG_SEG_W + 2, PROG_H + 6)) setTab(i);
        }
        if (g != null) {
            MapArt.progressSheen(g, PROG_X - 1, PROG_Y - 1, segsW + 2, PROG_H + 2, t, all, true);    // e o reflexo fino por cima
            counter(done, all);
        }
        if (g != null && overSeg != segHover) {                   // o relógio do balão recomeça ao trocar de etapa
            segHover = overSeg;
            segHoverAt = t;
        }
        // como nas bandeiras e no botão lateral: o balão só aparece depois do mouse ficar parado sobre a etapa
        if (overSeg >= 0) {
            tabTip(overSeg, PROG_X, PROG_Y + 10);
            tipDesc = tr("progress") + ": " + tr("progress.count", done, TABS);
        }
    }

    /** Contador "n/6" no poço: o dígito rola (sai por cima e entra por baixo; ao limpar, ao contrário) quando muda. */
    private void counter(int done, boolean all) {
        MapArt.counterWell(g, COUNT_X, COUNT_Y, COUNT_W, COUNT_H, all, t);
        String cur = String.valueOf(done), tail = "/" + TABS;
        int x0 = COUNT_X + (COUNT_W - (w(cur, BOLD) + w(tail, BOLD) - 1)) / 2, y0 = COUNT_Y + 2;   // dígito de 7 linhas no miolo de 9
        int base = all ? 0xFFB8F0A0 : MapArt.CREAM;
        double since = t - doneChangeAt;
        boolean rolling = doneChangeAt != 0L && since >= 0.0D && since < COUNT_POP_MS;
        hooks.clipPortrait(g, COUNT_X + 1, COUNT_Y + 1, COUNT_W - 2, COUNT_H - 2, true);
        if (rolling) {
            double e = 1.0D - Math.pow(1.0D - since / COUNT_POP_MS, 3);
            int off = (int) Math.round(e * 9.0D), dir = done >= prevDone ? 1 : -1;
            countText(String.valueOf(prevDone), x0, y0 - dir * off, MapArt.lerp(base, 0xFF150C06, 0.5D * e));
            countText(cur, x0, y0 + dir * (9 - off), MapArt.lerp(MapArt.GOLD_LIGHT, base, e));
        } else {
            countText(cur, x0, y0, base);
        }
        countText(tail, x0 + w(cur, BOLD), y0, MapArt.lerp(base, 0xFF150C06, 0.35D));
        hooks.clipPortrait(g, 0, 0, 0, 0, false);
        if (all) MapArt.sparkle(g, COUNT_X + COUNT_W - 3, COUNT_Y + 2, (t % 2400L) / 700.0D, 0xFFFFFF);   // fecha de vez: brilho ocasional
    }

    private void countText(String s, int x, int y, int argb) {
        MapFont.drawRaw(g, font, s, x, y + 1, 0xFF0A0604, BOLD);      // sombrinha de 1 px para o dígito não se perder no poço
        MapFont.drawRaw(g, font, s, x, y, argb, BOLD);
    }

    /** O boneco 3D, com zoom (ancorado na cabeça ao aproximar e no meio do corpo ao afastar) e recorte na moldura. */
    private void drawFigure(int cx, int feetY, int px) {
        int headTop = PORT_Y + 14;
        int h0 = feetY - headTop;
        int scale0 = (int) (h0 / (1.95D * Math.max(1.0D, BodyScale.of(height))));      // personagem mais alto que o padrão: encolhe o quadro para caber inteiro
        float z = zoom;
        int feet;
        if (z >= 1.0F) {
            feet = headTop + Math.round(h0 * z);
        } else {
            float mid = headTop + h0 / 2.0F;
            feet = Math.round(mid + (feetY - mid) * z);
        }
        int scale = Math.max(4, Math.round(scale0 * z));
        if (z <= 1.2F) MapArt.groundShadow(g, cx, feet, Math.round(18 * z), Math.max(2, Math.round(3 * z)));
        boolean clip = z > 1.0F;
        if (clip) hooks.clipPortrait(g, px + 4, PORT_Y + 4, PORT_W - 8, PORT_H - 8, true);
        hooks.drawPlayer(g, cx, feet, scale, mx, my, spin);
        if (clip) hooks.clipPortrait(g, 0, 0, 0, 0, false);
        if (g != null && Math.abs(height - CharacterOptions.HEIGHT_DEFAULT) >= 3) {      // régua: até onde iria o personagem padrão (a altura dele, em blocos)
            int ry = feet - Math.round(1.95F * scale);
            for (int rx = px + 8; rx < px + PORT_W - 8; rx += 4) MapArt.rect(g, rx, ry, 2, 1, MapArt.argb(150, 0x8A6A3A));
            text(String.format(java.util.Locale.ROOT, "%.2f", CharacterOptions.HEIGHT_DEFAULT / 100.0D), px + 9, ry - 9, MapArt.INK_FAINT, NOTE);
        }
    }

    /** Frase embaixo da placa do nome (com epíteto, vira o título do procurado): tinta no pergaminho entre dois enfeites. */
    private void caption(int cx) {
        String s = epithet.isEmpty() ? tr("portrait.caption") : epithet;
        int maxW = 100, y = NAMEPLATE_Y + 20 + 3;
        int full = w(s, NOTE), tw = Math.min(full, maxW);
        if (full <= maxW) {
            textC(s, cx + 1, y + 1, MapArt.argb(110, 0xFFF0C8), NOTE);
            textC(s, cx, y, 0xFF6A4420, NOTE);
        } else {
            fitText(s, cx - tw / 2, y, maxW, 0xFF6A4420, NOTE);
        }
        for (int side = 0; side < 2; side++) {
            int dir = side == 0 ? -1 : 1, bx = cx + dir * (tw / 2 + 5);
            int len = 12, x0 = dir < 0 ? bx - 3 - len : bx + 4;
            MapArt.diamond(g, bx, y + 3, 0xFF8A5A14);
            MapArt.rect(g, x0, y + 3, len, 1, MapArt.argb(150, 0x8A5A14));
            MapArt.rect(g, x0, y + 4, len, 1, MapArt.argb(70, 0xFFF0C8));
        }
    }

    /** Botões do retrato: aproximar, afastar e ver de costas (ou de frente). */
    private void viewButtons() {
        int bx = viewBtnX();
        String[] sprites = null;
        for (int k = 0; k < 3; k++) {
            int by = viewBtnY(k);
            boolean offLimit = (k == 0 && zoomTarget >= ZOOM_MAX - 0.01F) || (k == 1 && zoomTarget <= ZOOM_MIN + 0.01F);
            int st = offLimit ? MapArt.DISABLED : stateOf(k == 2 && viewingBack() ? MapArt.SELECTED : MapArt.NORMAL, bx, by, VIEW_BTN, VIEW_BTN);
            if (g != null) {
                MapArt.button(g, bx, by, VIEW_BTN, VIEW_BTN, st, t);
                String[] sp = k == 0 ? MapArt.PLUS : k == 1 ? MapArt.MINUS : MapArt.TURN;
                int col = st == MapArt.SELECTED ? MapArt.GOLD_LIGHT : st == MapArt.DISABLED ? 0xFF8F8670 : st == MapArt.HOVER ? 0xFF7A4A10 : MapArt.INK;
                MapArt.mono(g, sp, bx + 3, by + 3, col);                       // 10 x 10 no miolo livre do botão (3 px de cada lado)
            }
            if (in(bx, by, VIEW_BTN, VIEW_BTN) && stage == EDIT) {
                if (k == 0) tip(tr("view.zoom_in"), tr("view.zoom_in.desc"), null, 0, bx - 96, by);
                else if (k == 1) tip(tr("view.zoom_out"), tr("view.zoom_out.desc"), null, 0, bx - 96, by);
                else tip(viewingBack() ? tr("view.flip.front") : tr("view.flip"), tr("view.flip.desc"), null, 0, bx - 96, by);
            }
            if (hit(bx, by, VIEW_BTN, VIEW_BTN) && !offLimit) {
                if (k == 0) zoomBy(1);
                else if (k == 1) zoomBy(-1);
                else {
                    goTo(viewingBack() ? 0.0F : 180.0F);
                    everTurned = true;
                }
            }
        }
    }

    /** Setinhas nas laterais do retrato quando o mouse está em cima (somem enquanto arrasta); lembrete de voltar de frente. */
    private void turnHints(int px, int cx) {
        boolean over = stage == EDIT && inPortrait(mx, my);
        if (over && !turning) {
            double pulse = 0.5D + 0.5D * Math.sin(t / 260.0D);
            int col = MapArt.argb(170 + (int) (80 * pulse), 0xA82E22);       // vermelho, como o pontilhado (o fundo agora é claro)
            // mesma geometria do pontilhado do MapArt.idBackdrop: centro do retrato (cx), 30 px de meia-largura, y de 12 a 106
            int ay = PORT_Y + (PORT_H + 2) / 2 - 3;                                           // meio vertical do pontilhado (a seta tem 7 de altura)
            int sh = (int) Math.round(pulse);
            MapArt.arrow(g, cx - 35 - sh, ay, 7, true, col);                    // 27..30: um pixel de folga do pontilhado
            MapArt.arrow(g, cx + 32 + sh, ay, 7, false, col);                   // 94..97: espelho exato da esquerda
        }
    }

    // ------------------------------------------------------------------------------------------ painel da direita

    private void rightPage() {
        int rc = RX + RW / 2;
        if (g != null) {
            MapArt.wash(g, RX - 5, WASH_Y, RW + 8, WASH_H);
            watermark();
            MapArt.banner(g, rc, 9, 118, 17, t, FLAG_COLOR[tab]);               // a faixa de título leva a cor da aba
            // medalhão com o ícone da aba, preso na ponta da faixa (ouro por fora, a cor da aba por dentro)
            int mcx = rc - 59 + 12, mcy = 17;
            MapArt.disc(g, mcx, mcy, 10, 0xFF1E120A);
            MapArt.disc(g, mcx, mcy, 9, MapArt.goldPulse(t));
            MapArt.disc(g, mcx, mcy, 8, 0xFF1E120A);
            MapArt.disc(g, mcx, mcy, 7, MapArt.lerp(0xFF000000 | FLAG_COLOR[tab], 0xFF000000, 0.30D));
            MapArt.rect(g, mcx - 4, mcy - 6, 5, 1, MapArt.argb(120, 0xFFFFFF));
            MapIcons.drawLive(g, FLAG_ICON[tab], mcx - 8, mcy - 8, t);
            // título: letra creme com contorno escuro na cor da aba, no espaço ao lado do medalhão
            String ttl = tr("tab." + tab);
            int tk = w(ttl, TITLE) <= 54 ? TITLE : BODY;      // perolinhas de cada lado: o texto precisa caber entre elas e o medalhão
            int tcx = rc + 6, ty = 14;                         // centro do espaço livre da faixa (o medalhão ocupa a ponta esquerda)
            int tw = w(ttl, tk);
            MapFont.drawRaw(g, font, ttl, tcx - tw / 2, ty, 0xFFFFF6DC, tk);
            int pw = tw / 2 + 6;
            for (int sd = -1; sd <= 1; sd += 2) {
                int dx = tcx + sd * pw;
                MapArt.rect(g, dx - 1, 16, 3, 1, MapArt.GOLD_LIGHT);
                MapArt.rect(g, dx, 15, 1, 3, MapArt.GOLD_LIGHT);
            }
        }
        bannerArrows(rc);
        long ta = System.currentTimeMillis() - tabAt;
        boolean sliding = g != null && tabAt != 0L && ta >= 0L && ta < TRANS_MS;
        if (sliding) {                                                          // a página nova desliza para o lugar (só dentro da página)
            double u = ta / (double) TRANS_MS, ease = 1.0D - Math.pow(1.0D - u, 3);
            hooks.clipPortrait(g, RX - 6, WASH_Y, RW + 12, WASH_H, true);
            g.pose().pushPose();
            g.pose().translate((float) Math.round((1.0D - ease) * 14.0D * tabDir), 0.0F, 0.0F);
        }
        switch (tab) {
            case TAB_IDENTITY -> identity();
            case TAB_BODY -> body();
            case TAB_HAIR -> {
                int y = pills("hair.group.", 3, hairGroup, v -> hairGroup = v);
                switch (hairGroup) {
                    case 0 -> {
                        y = swatchSection(MapIcons.H_DROP, "hair", "hair", y, CharacterOptions.HAIR, hair, v -> hair = v, 20, 4, false);
                        if (g != null) lines(wrap(tr("hair.note"), RW, NOTE), RX, y + 4, MapArt.INK_SOFT, NOTE, 10);
                    }
                    case 1 -> styleSection(MapIcons.H_HAIR, "hair_style", y, hairStyle, CharacterOptions.HAIR_STYLES, 4, v -> hairStyle = v, "hair_style.", V_HAIRSTYLE, TH_HEAD, 34, 3, CharacterOptions.HAIR_STYLE_ORDER);
                    default -> {
                        y = styleSection(MapIcons.H_BEARD, "beard", y, beard, CharacterOptions.BEARDS, 4, v -> beard = v, "beard.", V_BEARD, TH_HEAD, 34, 3);
                        if (g != null) fitText(tr("beard.note"), RX, y - 12, RW, MapArt.INK_FAINT, NOTE);
                    }
                }
            }
            case TAB_OUTFIT -> {
                int y = pills("outfit.group.", 3, outfitGroup, v -> outfitGroup = v);
                switch (outfitGroup) {
                    case 0 -> styleSection(MapIcons.H_OUTFIT, "outfit", y, outfit, CharacterOptions.OUTFITS, 3, v -> outfit = v, "outfit.", V_OUTFIT, TH_TORSO, 34, 2, CharacterOptions.OUTFIT_ORDER);
                    case 1 -> styleSection(MapIcons.H_ACCESSORY, "accessory", y, accessory, CharacterOptions.ACCESSORIES, 3, v -> accessory = v, "accessory.", V_ACC, TH_HEAD, 34, 3);
                    default -> {
                        y = swatchSection(MapIcons.H_PALETTE, "outfit_color", "outfit", y, CharacterOptions.OUTFIT_COLOR, outfitColor, v -> outfitColor = v, 20, 4, false);
                        if (g != null) lines(wrap(tr("outfit_color.note"), RW, NOTE), RX, y + 4, MapArt.INK_SOFT, NOTE, 10);
                    }
                }
            }
            case TAB_SOUL -> soul();
            case TAB_PAST -> past();
        }
        if (sliding) {
            g.pose().popPose();
            hooks.clipPortrait(g, 0, 0, 0, 0, false);
        }
    }

    /** Marca-d'água: o ícone da aba, grande e bem clara, no canto de baixo da página (dá vida ao pergaminho sem atrapalhar o texto). */
    private void watermark() {
        g.flush();                                   // o lote pendente sai com a cor normal, antes de mudar a cor do shader
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.13F * MapArt.baseAlpha());
        g.pose().pushPose();
        g.pose().translate((float) (RX + RW - 50), (float) (WASH_Y + WASH_H - 54), 0.0F);
        g.pose().scale(3.0F, 3.0F, 1.0F);
        MapIcons.draw(g, FLAG_ICON[tab], 0, 0);
        g.pose().popPose();
        g.flush();                                   // a marca-d'água sai com a cor dela, antes de voltar ao alfa da tela
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, MapArt.baseAlpha());      // volta ao alfa da tela (1 fora do fade final), nunca a 1 fixo
    }

    /** As pontas da faixa de título passam para a aba anterior e a seguinte. */
    private void bannerArrows(int rc) {
        int ay = 20;                                    // meio do rabo da faixa (o rabo desce 3 px em relação ao pano)
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int cxA = left ? rc - 62 : rc + 62;           // centro do rabo; a seta (4 x 7) fica centrada nele
            int ax = cxA - 2;
            boolean can = left ? tab > 0 : tab < TABS - 1;
            boolean over = can && in(cxA - 7, 11, 14, 19);
            if (g != null) {
                int col = !can ? MapArt.argb(70, 0xE8B8A0) : over ? 0xFFFFE9A0 : 0xFFF0D08A;
                int sh = over ? (left ? -1 : 1) : 0;      // com o mouse em cima a seta dá um passinho para fora
                if (can) {                                // contorno escuro de 1 px e sombra: a seta lê bem sobre o rabo
                    for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}}) {
                        MapArt.tailArrow(g, ax + sh + d[0], ay - 3 + d[1], left, 0xFF1E120A);
                    }
                }
                MapArt.tailArrow(g, ax + sh, ay - 3, left, col);
            }
            if (over && stage == EDIT) tip(tr("tab." + (tab + (left ? -1 : 1))), null, null, 0, left ? cxA - 6 : cxA - 40, 34);
            if (can && hit(cxA - 7, 11, 14, 19)) setTab(tab + (left ? -1 : 1));
        }
    }

    /** Título de seção: ícone, nome, filete embaixo e, opcionalmente, um texto à direita (contador). Devolve o y do conteúdo. */
    private int heading(int icon, String s, int y, String right, int rightColor) {
        if (g != null) {
            MapIcons.draw(g, icon, RX, y);
            final int tx = RX + MapIcons.SIZE + 4, avail = RW - (MapIcons.SIZE + 4) - 8;
            final int tw = w(s, BOLD), rw = right == null ? 0 : w(right, BODY);
            if (right == null || tw + rw <= avail) {
                text(s, tx, y + 5, MapArt.INK, BOLD);
                if (right != null) text(right, RX + RW - rw, y + 5, rightColor, BODY);
            } else {                                                           // título e texto da direita não cabem juntos: os dois encolhem (até 62%), nada é cortado
                final float k = Math.max(0.62F, avail / (float) (tw + rw));
                final int tw2 = (int) Math.ceil(tw * k), rw2 = (int) Math.ceil(rw * k);
                fitScaled(s, tx, y + 5 + Math.round((1.0F - k) * 3.0F), tw2 + 1, MapArt.INK, BOLD, k);
                fitScaled(right, RX + RW - rw2, y + 5 + Math.round((1.0F - k) * 3.0F), rw2 + 1, rightColor, BODY, k);
            }
            MapArt.rect(g, RX, y + MapIcons.SIZE + 1, RW, 1, MapArt.argb(120, 0x8A6A3A));
            MapArt.rect(g, RX, y + MapIcons.SIZE + 2, RW, 1, MapArt.argb(70, 0xFFF4D0));
        }
        return y + MapIcons.SIZE + 7;
    }

    private int divider(int y) {
        if (g != null) MapArt.divider(g, RX + 10, y + 1, RW - 20);
        return y + 10;
    }

    /** Grade de cores com o nome da cor escolhida à direita do título e o nome de qualquer cor ao passar o mouse. */
    private int swatchSection(int icon, String key, String colorKind, int y, int[] colors, int sel, IntConsumer set,
                              int size, int gap, boolean withDivider) {
        y = heading(icon, tr(key), y, tr("color." + colorKind + "." + sel), MapArt.INK_FAINT);
        return swatchGrid(colorKind, y, colors, sel, set, size, gap, withDivider);
    }

    /** Só a grade de cores (sem título), para quando o título é desenhado por quem chama. */
    private int swatchGrid(String colorKind, int y, int[] colors, int sel, IntConsumer set, int size, int gap, boolean withDivider) {
        return swatchGrid(colorKind, y, colors, sel, set, size, gap, withDivider, 5);
    }

    /** {@code rowGap}: espaço entre uma fileira de amostras e a seguinte (5 é o normal). */
    private int swatchGrid(String colorKind, int y, int[] colors, int sel, IntConsumer set, int size, int gap, boolean withDivider, int rowGap) {
        int n = colors.length, maxPerRow = (RW + gap) / (size + gap);
        int rows = (n + maxPerRow - 1) / maxPerRow, perRow = (n + rows - 1) / rows;
        int width = perRow * (size + gap) - gap, x0 = RX + (RW - width) / 2;
        for (int i = 0; i < n; i++) {
            int sx = x0 + (i % perRow) * (size + gap), sy = y + 2 + (i / perRow) * (size + rowGap);
            boolean over = in(sx - 1, sy - 1, size + 2, size + 2);
            if (g != null) MapArt.swatch(g, sx, sy, size, size, colors[i], i == sel, over, t);
            if (over) colorTip(colorKind, i);
            if (hit(sx - 1, sy - 1, size + 2, size + 2)) {
                set.accept(i);
                picked();
                pulse(sx, sy, size, size);
                sfx(SND_PICK, 1.15F);
            }
        }
        y += 4 + rows * (size + rowGap);
        return withDivider ? divider(y) : y;
    }

    /** Seletor com setas; {@code describe} mostra embaixo a descrição curta da opção escolhida. */
    private int cycleSection(int icon, String key, int y, int idx, int n, IntConsumer set, String optPrefix, boolean withDivider,
                             boolean describe) {
        y = heading(icon, tr(key), y, (idx + 1) + "/" + n, MapArt.INK_FAINT);
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int bx = left ? RX : RX + RW - 20;
            int st = stateOf(MapArt.NORMAL, bx, y, 20, 18);
            if (g != null) MapArt.arrowButton(g, bx, y, 20, 18, left, st, t);
            if (hit(bx, y, 20, 18)) {
                set.accept(((idx + (left ? -1 : 1)) % n + n) % n);
                picked();
                pulse(bx, y, 20, 18);
                sfx(SND_PICK, left ? 0.9F : 1.1F);
            }
        }
        textC(tr(optPrefix + idx), RX + RW / 2, y + 5, MapArt.INK, BOLD);
        y += 23;
        if (describe) {
            paragraph(tr(optPrefix + idx + ".desc"), RX, y, RW, 22, MapArt.INK_SOFT, NOTE, 10);
            y += 22;
        }
        return withDivider ? divider(y) : y;
    }

    // ------------------------------------------------------------------------------------------ abas

    private static final int V_HAIRSTYLE = 3, V_FACE = 4, V_OUTFIT = 5, V_BEARD = 18, V_ACC = 19, V_EYESTYLE = 20, V_ADORN = 21, V_ADORN_HI = 23, V_ADORN_TOP = 27, V_EAR_SIDE = 25, V_EAR_L = 28, V_EAR_R = 29;
    private static final int TH_HEAD = 0, TH_TORSO = 1, TH_EYES = 2;

    /** A skin do personagem de agora, só com a opção {@code slot} trocada por {@code val} (em cache). */
    private int[] variantSkin(int slot, int val) {
        int[] v = data().ints();
        v[slot] = val;
        int key = java.util.Arrays.hashCode(v);
        int[] px = thumbCache.get(key);
        if (px == null) {
            if (thumbCache.size() > 128) thumbCache.clear();
            px = SkinPainter.paint(CharacterData.fromInts("x", "", "", v));
            thumbCache.put(key, px);
        }
        return px;
    }

    /** A skin do personagem de agora, só com o item do rosto {@code mask} (um bit) no lugar dos que estão marcados (em cache). */
    private int[] variantMask(long mask) {
        int[] v = data().ints();
        v[V_ADORN] = (int) (mask & 0x7FFFFFFFL);
        v[V_ADORN_HI] = (int) (mask >>> 31);
        v[V_ADORN_TOP] = (int) (mask >>> 63);
        int key = java.util.Arrays.hashCode(v);
        int[] px = thumbCache.get(key);
        if (px == null) {
            if (thumbCache.size() > 128) thumbCache.clear();
            px = SkinPainter.paint(CharacterData.fromInts("x", "", "", v));
            thumbCache.put(key, px);
        }
        return px;
    }

    /**
     * Igual a {@link #variantMask}, mas para as miniaturas das orelhas: o item de orelha {@code item} (índice de {@link CharacterOptions#EAR_ITEMS})
     * aparece sempre nas DUAS orelhas e sozinho (o que está marcado em cada orelha só vale na skin do personagem), senão a miniatura
     * ficaria vazia quando a orelha mostrada não é a que está sendo editada.
     */
    private int[] variantEarMask(int item) {
        int[] v = data().ints();
        v[V_EAR_L] = item;
        v[V_EAR_R] = item;
        int key = java.util.Arrays.hashCode(v);
        int[] px = thumbCache.get(key);
        if (px == null) {
            if (thumbCache.size() > 128) thumbCache.clear();
            px = SkinPainter.paint(CharacterData.fromInts("x", "", "", v));
            thumbCache.put(key, px);
        }
        return px;
    }

    /**
     * Igual a {@link #variantMask}, mas para as cicatrizes do corpo: com a roupa de baixo padrão (as outras roupas cobririam o tronco e a
     * cicatriz não apareceria na miniatura).
     */
    private int[] variantBodyMask(long mask) {
        int[] v = data().ints();
        v[V_ADORN] = (int) (mask & 0x7FFFFFFFL);
        v[V_ADORN_HI] = (int) (mask >>> 31);
        v[V_ADORN_TOP] = (int) (mask >>> 63);
        v[V_OUTFIT] = CharacterOptions.OUTFIT_DEFAULT;
        int key = java.util.Arrays.hashCode(v);
        int[] px = thumbCache.get(key);
        if (px == null) {
            if (thumbCache.size() > 128) thumbCache.clear();
            px = SkinPainter.paint(CharacterData.fromInts("x", "", "", v));
            thumbCache.put(key, px);
        }
        return px;
    }

    /** Pinta um pedaço da skin (w x h em (sx, sy)) com o canto de cima à esquerda em (dx, dy), cada pixel com {@code sc} de lado. */
    private void skinRegion(int[] px, int sx, int sy, int w, int h, int dx, int dy, int sc) {
        for (int j = 0; j < h; j++) {
            int i = 0;
            while (i < w) {
                int c = px[(sy + j) * SkinPainter.SIZE + sx + i];
                if ((c >>> 24) == 0) {
                    i++;
                    continue;
                }
                int i2 = i;
                while (i2 + 1 < w && px[(sy + j) * SkinPainter.SIZE + sx + i2 + 1] == c) i2++;     // junta pixels iguais da mesma linha
                g.fill(dx + i * sc, dy + j * sc, dx + (i2 + 1) * sc, dy + (j + 1) * sc, c);
                i = i2 + 1;
            }
        }
    }

    private void headThumb(int[] px, int dx, int dy, int sc) {
        skinRegion(px, 8, 8, 8, 8, dx, dy, sc);          // cabeça (frente) e, por cima, a camada externa (cabelo, chapéu)
        skinRegion(px, 40, 8, 8, 8, dx, dy, sc);
    }

    /** Só a faixa dos olhos do rosto (linhas 1 a 5 da cara), para ver bem o desenho de cada estilo de olhos. */
    private void eyeThumb(int[] px, int dx, int dy, int sc) {
        skinRegion(px, 8, 9, 8, 5, dx, dy, sc);
        skinRegion(px, 40, 9, 8, 5, dx, dy, sc);
    }

    private void torsoThumb(int[] px, int aw, int dx, int dy, int sc) {
        skinRegion(px, 44, 20, aw, 12, dx, dy, sc);               // braços, tronco e as camadas externas
        skinRegion(px, 20, 20, 8, 12, dx + aw * sc, dy, sc);
        skinRegion(px, 36, 52, aw, 12, dx + (aw + 8) * sc, dy, sc);
        skinRegion(px, 44, 36, aw, 12, dx, dy, sc);
        skinRegion(px, 20, 36, 8, 12, dx + aw * sc, dy, sc);
        skinRegion(px, 52, 52, aw, 12, dx + (aw + 8) * sc, dy, sc);
    }

    /**
     * Seção de estilo com miniaturas: título com o contador, grade de molduras (cada uma mostra a cabeça ou o corpo
     * com aquela opção, no visual de agora do personagem), e embaixo o nome e a descrição da opção escolhida.
     */
    private int styleSection(int icon, String key, int y, int sel, int n, int cols, IntConsumer set, String prefix, int slot,
                             int kind, int slotH, int scale) {
        return styleSection(icon, key, y, sel, n, cols, set, prefix, slot, kind, slotH, scale, null);
    }

    /**
     * Igual, com {@code order}: a ordem em que as opções aparecem na grade (posição na grade -> valor guardado). Os valores
     * guardados nunca mudam (personagens já criados continuam iguais); só a ordem de exibição. {@code null} = ordem natural.
     */
    private int styleSection(int icon, String key, int y, int sel, int n, int cols, IntConsumer set, String prefix, int slot,
                             int kind, int slotH, int scale, int[] order) {
        return styleSection(icon, key, y, sel, n, cols, set, prefix, slot, kind, slotH, scale, order, false, 0, n);
    }

    /**
     * {@code compact}: sem o título da seção e sem nome/descrição embaixo (só a grade; devolve o y logo abaixo dela); a descrição
     * continua na dica ao passar o mouse. {@code first}/{@code shown}: só as casas {@code first} a {@code first + shown - 1}
     * aparecem (uma página). Usado quando a seção divide a página com outra (cor + estilo dos olhos).
     */
    private int styleSection(int icon, String key, int y, int sel, int n, int cols, IntConsumer set, String prefix, int slot,
                             int kind, int slotH, int scale, int[] order, boolean compact, int first, int shown) {
        int selPos = sel;
        if (order != null) for (int k = 0; k < n; k++) if (order[k] == sel) selPos = k;
        if (!compact) y = heading(icon, tr(key), y, (selPos + 1) + "/" + n, MapArt.INK_FAINT);
        int gap = 3, cw = (RW - gap * (cols - 1)) / cols, x0 = RX + (RW - (cols * cw + gap * (cols - 1))) / 2;
        int aw = gender == 1 ? 3 : 4;
        final int last = Math.min(n, first + shown);                                // só as casas first..last-1 aparecem (página)
        for (int pos = first; pos < last; pos++) {
            int i = order == null ? pos : order[pos];                                // valor guardado desta casa da grade
            int gp = pos - first;
            int x = x0 + (gp % cols) * (cw + gap), yy = y + (gp / cols) * (slotH + gap);
            boolean over = in(x, yy, cw, slotH), on = i == sel;
            if (g != null) {
                MapArt.rect(g, x + 1, yy + 2, cw, slotH, 0x40000000);
                if (over && !on) MapArt.hoverGlow(g, x, yy, cw, slotH, t);
                MapArt.notched(g, x, yy, cw, slotH, 0xFF1E120A);
                MapArt.notched(g, x + 1, yy + 1, cw - 2, slotH - 2, on ? MapArt.goldPulse(t) : over ? MapArt.lerp(MapArt.GOLD, MapArt.GOLD_LIGHT, 0.5D + 0.5D * Math.sin(t / 260.0D)) : MapArt.GOLD_DARK);
                MapArt.notched(g, x + 2, yy + 2, cw - 4, slotH - 4, on ? 0xFF50351A : over ? 0xFF47301A : 0xFF2B1A0E);
                if (over && !on) MapArt.rect(g, x + 2, yy + slotH - 4, cw - 4, 2, MapArt.argb(46, 0xFFC060));      // calor de baixo
                int[] px = variantSkin(slot, i);
                if (kind == TH_HEAD) headThumb(px, x + (cw - 8 * scale) / 2, yy + (slotH - 8 * scale) / 2, scale);
                else if (kind == TH_EYES) eyeThumb(px, x + (cw - 8 * scale) / 2, yy + (slotH - 5 * scale) / 2, scale);
                else torsoThumb(px, aw, x + (cw - (8 + 2 * aw) * scale) / 2, yy + (slotH - 12 * scale) / 2, scale);
                if (on) {                                                         // selo de ouro no canto: esta é a escolhida
                    MapArt.disc(g, x + cw - 6, yy + 6, 3, 0xFF140A04);
                    MapArt.disc(g, x + cw - 6, yy + 6, 2, MapArt.GOLD);
                    MapArt.rect(g, x + cw - 7, yy + 4, 1, 1, MapArt.GOLD_LIGHT);
                }
            }
            if (over && stage == EDIT) tip(tr(prefix + i), tr(prefix + i + ".desc"), null, 0, mx + 10, my + 12);
            if (hit(x, yy, cw, slotH) && sel != i) {
                set.accept(i);
                picked();
                pulse(x, yy, cw, slotH);
                sfx(SND_PICK, 0.95F + 0.04F * pos);
            }
        }
        y += ((Math.min(n, shown) + cols - 1) / cols) * (slotH + gap) + 2;
        if (compact) return y;                       // quem chama desenha a linha de baixo (nome da escolhida e a troca de página)
        if (g != null) {
            textC(tr(prefix + sel), RX + RW / 2, y, MapArt.INK, BOLD);
            paragraph(tr(prefix + sel + ".desc"), RX, y + 13, RW, 21, MapArt.INK_SOFT, NOTE, 10);       // a descrição inteira: se for comprida, a letra encolhe em vez de cortar
        }
        return y + 34;
    }

    /** Fileira de sub-abas no alto da página (uma de cada vez embaixo). Devolve o y onde o conteúdo começa. */
    private int pills(String keyPrefix, int n, int cur, IntConsumer set) {
        int bw = (RW - 4 * (n - 1)) / n;
        for (int k = 0; k < n; k++) {
            int bx = RX + k * (bw + 4);
            int st = stateOf(cur == k ? MapArt.SELECTED : MapArt.NORMAL, bx, TOP - 2, bw, 16);
            if (g != null) {
                MapArt.button(g, bx, TOP - 2, bw, 16, st, t);
                { String pl = tr(keyPrefix + k); int pw0 = Math.min(w(pl, BOLD), bw - 6); fitText(pl, bx + (bw - pw0) / 2, TOP + 2, bw - 6, st == MapArt.SELECTED ? MapArt.GOLD_LIGHT : inkOf(st), BOLD); }
            }
            if (hit(bx, TOP - 2, bw, 16) && cur != k) {
                set.accept(k);
                pulse(bx, TOP - 2, bw, 16);
                sfx(SND_PAGE, 1.25F);
            }
        }
        return TOP + 20;
    }

    /** Corpo: quatro sub-abas (pele, olhos, rosto, corpo). A página Olhos junta a cor da íris e o estilo dos olhos. */
    private void body() {
        int y = pills("body.group.", 4, bodyGroup, v -> bodyGroup = v);
        switch (bodyGroup) {
            case 0 -> {
                y = swatchSection(MapIcons.H_SKIN, "skin", "skin", y, CharacterOptions.SKIN, skin, v -> skin = v, 16, 4, false);
                if (in(RX, TOP + 20, RW, y - TOP - 20)) tip(tr("skin"), tr("skin.note"), null, 0, mx + 10, my + 10);
                y = divider(y - 1);
                y = sizeSlider(0, y);                                                   // altura (a largura acompanha)
                if (gender == 1) sizeSlider(1, y);                                      // busto (só o corpo feminino)
            }
            case 1 -> {
                y = swatchGrid("eyes", y - 2, CharacterOptions.EYES, eyes, v -> eyes = v, 15, 3, false, 3);     // todas as cores, em 2 fileiras de 8
                eyeStyles(y + 1);
            }
            case 2 -> {
                if (itemClass == CharacterOptions.ITEM_CLASS_BODY) itemClass = 0;      // voltando da página Corpo: abre em Faces
                faceItems(y);
            }
            default -> {                                                                // Corpo: só as cicatrizes do corpo (mesma grade e mesmo limite de itens do Rosto)
                itemClass = CharacterOptions.ITEM_CLASS_BODY;
                faceItems(y);
            }
        }
    }

    /** Grade do rosto: 6 colunas de casas com a cabeça a 2x. */
    private static final String[] LOCK = {".XXX.", "X...X", "X...X", "XXXXX", "XXXXX", "XXXXX", "XXXXX"};
    /** Algarismos 3 x 5 (para o número dentro do selo das abas, que a fonte do jogo não deixa legível tão pequeno). */
    private static final String[][] DIGITS = {
            {"XXX", "X.X", "X.X", "X.X", "XXX"}, {".X.", "XX.", ".X.", ".X.", "XXX"}, {"XXX", "..X", "XXX", "X..", "XXX"},
            {"XXX", "..X", "XXX", "..X", "XXX"}, {"X.X", "X.X", "XXX", "..X", "..X"}, {"XXX", "X..", "XXX", "..X", "XXX"},
            {"XXX", "X..", "XXX", "X.X", "XXX"}, {"XXX", "..X", "..X", ".X.", ".X."}, {"XXX", "X.X", "XXX", "X.X", "XXX"},
            {"XXX", "X.X", "XXX", "..X", "XXX"}};
    private static final int ITEM_GAP = 3, ITEM_COLS = 6, ITEM_H = 20, ITEM_CLASS_EARS = 5, ITEM_CLASS_OTHERS = 4;

    /** O item {@code k} da página Rosto está marcado? (k de 0 a 63: máscara principal; a partir de {@link CharacterOptions#FACEX_BASE}: faces novas) */
    private boolean hasItem(int k) {
        return k >= CharacterOptions.FACEX_BASE ? (faceX & (1 << (k - CharacterOptions.FACEX_BASE))) != 0 : (adorn & (1L << k)) != 0;
    }

    /** Igual a {@link #variantMask}, mas para uma face nova: só ela, no lugar dos itens do rosto marcados. */
    private int[] variantFaceX(int mask) {
        int[] v = data().ints();
        v[V_ADORN] = 0;
        v[V_ADORN_HI] = 0;
        v[V_ADORN_TOP] = 0;
        v[31] = mask;
        int key = java.util.Arrays.hashCode(v);
        int[] px = thumbCache.get(key);
        if (px == null) {
            if (thumbCache.size() > 128) thumbCache.clear();
            px = SkinPainter.paint(CharacterData.fromInts("x", "", "", v));
            thumbCache.put(key, px);
        }
        return px;
    }

    /** Nome e descrição de um item do rosto (bit 0 a 11 = adereço, 12 a 18 = face com marca, 19 a 25 = piercing extra, 26 em diante = cicatrizes, marcas e outros novos). */
    private String itemKey(int bit) {
        if (bit >= CharacterOptions.FACEX_BASE) return "facex." + (bit - CharacterOptions.FACEX_BASE);
        if (bit >= CharacterOptions.EXTRA_BIT_BASE) return "extra." + (bit - CharacterOptions.EXTRA_BIT_BASE);
        if (bit >= CharacterOptions.PIERCE_BIT_BASE) return "pierce." + (bit - CharacterOptions.PIERCE_BIT_BASE);
        return bit < CharacterOptions.ADORNMENTS ? "adorn." + bit : "face_style." + (bit - CharacterOptions.FACE_BIT_BASE);
    }

    /**
     * Página Rosto: faces, piercings, orelhas e o resto numa seleção só, em 6 classes (abas pequenas, cada uma com a quantidade marcada).
     * O jogador marca até {@link CharacterOptions#ADORN_MAX} itens ao mesmo tempo, de qualquer classe: clicar de novo tira; com o limite
     * atingido as outras casas ficam apagadas, com um cadeado, e o contador pisca. Piercings e Orelhas têm a escolha do metal (prata ou
     * dourado; nas Orelhas, cada orelha com o seu, escolhido logo abaixo de Esquerda / Direita) e, em Outros, o batom e o brilho labial têm a escolha da cor dos lábios. Embaixo, em duas colunas, ficam os nomes do que
     * está marcado. Nada marcado = rosto padrão. Tudo é pintado na textura da cabeça (as orelhas, nas laterais).
     */
    private void faceItems(int y) {
        y -= 4;                                                                 // mais perto das abas Pele/Olhos/Rosto: sobra espaço para as listas de baixo
        infoTitle = null;
        infoDesc = null;
        final boolean ears = itemClass == ITEM_CLASS_EARS;                      // mesma grade das outras classes (6 por fileira); a miniatura é a lateral da cabeça
        final int earCur = earEdit == 0 ? earL : earR, earOther = earEdit == 0 ? earR : earL;   // itens da orelha que está sendo editada e da outra (cada orelha tem a sua lista)
        final int count = CharacterOptions.itemCount(adorn, earL, earR, faceX), max = CharacterOptions.ADORN_MAX;   // o limite vale para tudo junto: itens do rosto e de orelha (o mesmo item nas duas orelhas conta uma vez)
        final boolean warn = t - adornFullAt < 700L;
        y = heading(MapIcons.H_FACE, tr("item_class." + itemClass), y, tr("item.count", count, max), warn || count >= max ? MapArt.ORANGE : count > 0 ? MapArt.GOLD_DARK : MapArt.INK_FAINT) - 3;
        y = itemClass < CharacterOptions.ITEM_CLASSES ? itemClassTabs(y - 2) : y - 2;                  // a página Corpo não tem as abas pequenas: é uma classe só
        final int[] order = ears ? CharacterOptions.EAR_ORDER : CharacterOptions.ITEM_CLASS_ORDER[itemClass];
        final int cols = ITEM_COLS, gap = ITEM_GAP, ch = ITEM_H;
        final int cw = (RW - gap * (cols - 1)) / cols, x0 = RX + (RW - (cols * cw + gap * (cols - 1))) / 2;
        for (int pos = 0; pos < order.length; pos++) {
            final long bit = 1L << order[pos];
            final int k = order[pos];
            final int x = x0 + (pos % cols) * (cw + gap), yy = y + (pos / cols) * (ch + gap);
            final boolean over = in(x, yy, cw, ch), on = ears ? (earCur & (1 << k)) != 0 : hasItem(k),
                    locked = !on && count >= max && !(ears && (earOther & (1 << k)) != 0);   // o mesmo item já na outra orelha não gasta uma vaga nova
            if (g != null) {
                MapArt.rect(g, x + 1, yy + 2, cw, ch, 0x40000000);
                if (over && !on && !locked) MapArt.hoverGlow(g, x, yy, cw, ch, t);
                MapArt.notched(g, x, yy, cw, ch, 0xFF1E120A);
                MapArt.notched(g, x + 1, yy + 1, cw - 2, ch - 2, on ? MapArt.goldPulse(t) : over && !locked ? MapArt.lerp(MapArt.GOLD, MapArt.GOLD_LIGHT, 0.5D + 0.5D * Math.sin(t / 260.0D)) : MapArt.GOLD_DARK);
                MapArt.notched(g, x + 2, yy + 2, cw - 4, ch - 4, on ? 0xFF5E3F1E : over && !locked ? 0xFF47301A : 0xFF2B1A0E);
                if (ears) earThumb(variantEarMask(1 << k), earEdit == 0, x + (cw - 16) / 2, yy + (ch - 16) / 2, 2);
                else if (CharacterOptions.isBodyScar(k)) bodyThumb(variantBodyMask(bit), k, x + (cw - 16) / 2, yy + (ch - 16) / 2, 2);
                else if (k >= CharacterOptions.FACEX_BASE) headThumb(variantFaceX(1 << (k - CharacterOptions.FACEX_BASE)), x + (cw - 16) / 2, yy + (ch - 16) / 2, 2);
                else headThumb(variantMask(bit), x + (cw - 16) / 2, yy + (ch - 16) / 2, 2);
                if (locked) {
                    MapArt.rect(g, x + 2, yy + 2, cw - 4, ch - 4, MapArt.argb(110, 0x140A04));                // sem vaga: apagadinho, com um cadeado
                    final int lx = x + (cw - 5) / 2, ly = yy + (ch - 7) / 2;                                  // cadeado 5 x 7 centralizado na casa
                    MapArt.mono(g, LOCK, lx, ly, 0xFFD9CBAA);
                    MapArt.rect(g, lx + 2, ly + 4, 1, 2, 0xFF2B1A0E);
                }
                if (on) {                                                                                      // selo dourado com o visto no canto
                    MapArt.disc(g, x + cw - 4, yy + 4, 4, 0xFF140A04);
                    MapArt.disc(g, x + cw - 4, yy + 4, 3, MapArt.GOLD);
                    MapArt.mono(g, MapArt.CHECK, x + cw - 7, yy + 2, 0xFF2B1A0E);
                }
            }
            final String key = ears ? "ear_item." + k : itemKey(k);
            if (over && stage == EDIT) {
                infoTitle = tr(key);                                                                           // a descrição aparece já, embaixo da grade (o balão só vem depois de 5 s)
                infoDesc = tr(key + ".desc");
                final boolean lipItem = k == CharacterOptions.LIP_BIT_STICK || k == CharacterOptions.LIP_BIT_GLOSS;
                tip(tr(key), tr(key + ".desc"), locked ? tr("adorn.full", max) : tr(on ? "adorn.on" : lipItem ? "adorn.off.lip" : "adorn.off"), locked ? MapArt.ORANGE : on ? MapArt.GREEN : MapArt.INK_FAINT, mx + 10, my + 12);
            }
            if (hit(x, yy, cw, ch)) {
                if (on) {
                    if (ears) setEar(earCur & ~(1 << k)); else if (k >= CharacterOptions.FACEX_BASE) faceX &= ~(1 << (k - CharacterOptions.FACEX_BASE)); else adorn &= ~bit;
                } else if (locked) {
                    adornFullAt = t;
                    sfx(SND_DENY, 1.0F);
                    continue;
                } else {
                    if (ears) setEar(earCur | (1 << k)); else if (k >= CharacterOptions.FACEX_BASE) faceX |= 1 << (k - CharacterOptions.FACEX_BASE); else adorn |= bit;
                }
                picked();
                pulse(x, yy, cw, ch);
                sfx(SND_PICK, on ? 0.85F : 1.05F + 0.02F * pos);
            }
        }
        y += ((order.length + cols - 1) / cols) * (ch + gap);
        if (itemClass == 1) y = metalChoice(y, false);                          // piercings do rosto: o metal deles
        if (itemClass == ITEM_CLASS_EARS) {
            y = earSideChoice(y);                                               // orelhas: primeiro a orelha que está sendo editada (esquerda ou direita)...
            y = metalChoice(y, true);                                           // ...e logo abaixo o metal dela: cada orelha tem o seu (a outra e o rosto podem ter outro)
        }
        if (itemClass == ITEM_CLASS_OTHERS && CharacterOptions.usesLip(adorn)) y = lipChoice(y);
        if ((itemClass == 0 || itemClass == 3) && CharacterOptions.usesPaint(adorn, faceX)) y = paintChoice(y);   // faces e marcas: a cor da pintura, quando uma pintura está marcada
        faceInfo(y + 1);
    }

    /** Texto pequeno (80% do tamanho normal) que cabe em {@code maxW}: se ainda for largo, encolhe até 62%; só então vira reticências. */
    private void smallText(String s, int x, int y, int maxW, int color) {
        smallText(s, x, y, maxW, color, 0.8F);
    }

    /** Igual, partindo de {@code base} (em vez de 80%) como tamanho preferido. */
    private void smallText(String s, int x, int y, int maxW, int color, float base) {
        if (g == null) return;
        final float full = w(s, BODY);
        float sc = base;
        String shown = s;
        if (full * sc > maxW) {
            sc = Math.min(base, Math.max(0.62F, maxW / full));
            if (full * sc > maxW + 0.5F) shown = clip(s, Math.round(maxW / sc), BODY);
        }
        g.pose().pushPose();
        g.pose().translate(x, y + (0.8F - sc) * 4.0F + 1.0F, 0.0F);
        g.pose().scale(sc, sc, 1.0F);
        MapFont.draw(g, font, shown, 0, 0, color, BODY);
        g.pose().popPose();
    }

    /** As abas pequenas das classes do rosto (nome curto e, se houver itens marcados nela, um selo com a quantidade), em fileiras de até 3. Devolve o y logo abaixo delas. */
    private int itemClassTabs(int y) {
        final int n = CharacterOptions.ITEM_CLASSES, gap = 1, bh = 11, perRow = 3;
        for (int row = 0; row * perRow < n; row++) {
            final int first = row * perRow, inRow = Math.min(perRow, n - first);
            final int bw = (RW - gap * (inRow - 1)) / inRow;
            final int by = y + row * (bh + gap);
            for (int i = 0; i < inRow; i++) {
                final int k = first + i, bx = RX + i * (bw + gap);
                final int st = stateOf(itemClass == k ? MapArt.SELECTED : MapArt.NORMAL, bx, by, bw, bh);
                int inClass = 0;
                for (int bit : CharacterOptions.ITEM_CLASS_ORDER[k]) if (hasItem(bit)) inClass++;
                if (k == ITEM_CLASS_EARS) inClass = Integer.bitCount(earL | earR);
                if (g != null) {
                    MapArt.button(g, bx, by, bw, bh, st, t);
                    final String label = tr("item_class." + k + ".short");
                    final int room = bw - 4 - (inClass > 0 ? 11 : 0);
                    final int lw = Math.min(Math.round(w(label, BODY) * 0.85F), room);
                    smallText(label, bx + 2 + (room - lw) / 2, by + 1, room, st == MapArt.SELECTED ? MapArt.GOLD_LIGHT : inkOf(st));
                    if (inClass > 0) {                                                                         // selo dourado com a quantidade (algarismo de 3 x 5 pixels)
                        final int bcx = bx + bw - 7, bcy = by + bh / 2;
                        MapArt.disc(g, bcx, bcy, 5, 0xFF140A04);
                        MapArt.disc(g, bcx, bcy, 4, MapArt.GOLD);
                        MapArt.mono(g, DIGITS[Math.min(9, inClass)], bcx - 1, bcy - 2, 0xFF2B1A0E);
                    }
                }
                if (hit(bx, by, bw, bh) && itemClass != k) {
                    itemClass = k;
                    pulse(bx, by, bw, bh);
                    sfx(SND_PAGE, 1.25F);
                }
            }
        }
        final int rows = (n + perRow - 1) / perRow;
        return y + rows * (bh + gap) + 1;
    }

    /**
     * Escolha do metal: dois botões (prata e dourado) com uma amostra da cor. {@code ear} = false escolhe o metal dos piercings do rosto;
     * true, o da orelha que está sendo editada ({@link #earEdit}, escolhida nos botões Esquerda / Direita logo acima): cada orelha tem o seu
     * metal, então uma pode ser dourada e a outra prateada. São escolhas separadas das dos piercings do rosto. Passar o mouse mostra já a
     * explicação, embaixo. Devolve o y logo abaixo.
     */
    private int metalChoice(int y, boolean ear) {
        final int n = CharacterOptions.METALS, gap = 4, bh = 12, cur = ear ? earMetalOf(earEdit) : metal;
        final int bw = (RW - gap * (n - 1)) / n;
        final String desc = tr(ear ? "ear_metal." + earEdit + ".desc" : "metal.desc");
        for (int k = 0; k < n; k++) {
            final int bx = RX + k * (bw + gap);
            final int st = stateOf(cur == k ? MapArt.SELECTED : MapArt.NORMAL, bx, y, bw, bh);
            if (g != null) {
                MapArt.button(g, bx, y, bw, bh, st, t);
                metalSwatch(bx + 5, y + 2, k);
                final String label = tr("metal." + k);
                smallText(label, bx + 15, y + 1, bw - 18, st == MapArt.SELECTED ? MapArt.GOLD_LIGHT : inkOf(st));
            }
            if (in(bx, y, bw, bh) && stage == EDIT) {
                infoTitle = tr(ear ? "ear_metal." + earEdit + ".title" : "metal.title") + ": " + tr("metal." + k);
                infoDesc = desc;
                tip(tr("metal." + k), desc, null, 0, mx + 10, my + 12);
            }
            if (hit(bx, y, bw, bh) && cur != k) {
                if (!ear) metal = k;
                else if (earEdit == 0) earMetalL = k;
                else earMetalR = k;
                picked();
                pulse(bx, y, bw, bh);
                sfx(SND_PICK, 1.1F);
            }
        }
        return y + bh + 2;
    }

    /** Metal (0 prata, 1 dourado) da orelha {@code side} do personagem (0 esquerda, 1 direita). */
    private int earMetalOf(int side) {
        return side == 0 ? earMetalL : earMetalR;
    }

    /** Amostra 7 x 7 de um metal: moldura escura, o tom claro, uma linha do tom escuro embaixo e um brilho em cima (as cores são as mesmas da skin). */
    private void metalSwatch(int x, int y, int metal) {
        MapArt.rect(g, x, y, 7, 7, 0xFF140A04);
        MapArt.rect(g, x + 1, y + 1, 5, 5, SkinPainter.metalLt(metal));
        MapArt.rect(g, x + 1, y + 5, 5, 1, SkinPainter.metalDk(metal));
        MapArt.rect(g, x + 1, y + 1, 2, 1, 0xFFFFFFFF);
    }

    /** Troca os itens da orelha que está sendo editada. */
    private void setEar(int mask) {
        if (earEdit == 0) earL = mask; else earR = mask;
    }

    /**
     * Escolha da orelha editada: Esquerda ou Direita (do personagem; cada uma com a sua lista de itens e o seu metal, e um selo com a quantidade
     * de itens). Cada botão traz uma amostra do metal da orelha dele, então os dois metais aparecem juntos sem precisar trocar de lado; os botões
     * Prata / Dourado logo abaixo ({@link #metalChoice}) mudam o da orelha escolhida aqui. Devolve o y logo abaixo.
     */
    private int earSideChoice(int y) {
        final int n = 2, gap = 3, bh = 12, sw = 11;                                                      // sw: espaço da amostra de metal, à esquerda do nome
        final int bw = (RW - gap * (n - 1)) / n;
        for (int k = 0; k < n; k++) {
            final int bx = RX + k * (bw + gap);
            final int st = stateOf(earEdit == k ? MapArt.SELECTED : MapArt.NORMAL, bx, y, bw, bh);
            final int inEar = Integer.bitCount(k == 0 ? earL : earR);
            if (g != null) {
                MapArt.button(g, bx, y, bw, bh, st, t);
                metalSwatch(bx + 3, y + 2, earMetalOf(k));
                final String label = tr("ear_edit." + k);
                final int room = bw - 4 - sw - (inEar > 0 ? 8 : 0);
                final int lw = Math.min(Math.round(w(label, BODY) * 0.8F), room);
                smallText(label, bx + 2 + sw + (room - lw) / 2, y + 1, room, st == MapArt.SELECTED ? MapArt.GOLD_LIGHT : inkOf(st));
                if (inEar > 0) {                                                                           // selo dourado com a quantidade de itens da orelha
                    final int bcx = bx + bw - 6, bcy = y + bh / 2;
                    MapArt.disc(g, bcx, bcy, 4, 0xFF140A04);
                    MapArt.disc(g, bcx, bcy, 3, MapArt.GOLD);
                    MapArt.mono(g, DIGITS[Math.min(9, inEar)], bcx - 1, bcy - 2, 0xFF2B1A0E);
                }
            }
            if (in(bx, y, bw, bh) && stage == EDIT) {
                infoTitle = tr("ear_edit." + k);
                infoDesc = tr("ear_edit." + k + ".desc");
                tip(tr("ear_edit." + k), tr("ear_edit." + k + ".desc"), null, 0, mx + 10, my + 12);
            }
            if (hit(bx, y, bw, bh) && earEdit != k) {
                earEdit = k;
                picked();
                pulse(bx, y, bw, bh);
                sfx(SND_PICK, 1.1F);
            }
        }
        return y + bh + 2;
    }

    /**
     * Cor dos lábios: linha de amostras logo abaixo da grade, que só aparece com o Batom ou o Brilho labial marcado. O nome da cor
     * escolhida vem na linha de cima e o de qualquer cor aparece ao passar o mouse. A primeira cor é o visual de sempre (clássico).
     */
    private int lipChoice(int y) {
        if (g != null) {
            final String name = tr("color.lip." + lip), label = tr("lip.label");
            final int nw = Math.round(w(name, BODY) * 0.8F) + 1;
            smallText(label, RX + 1, y, RW - nw - 8, MapArt.INK_SOFT);          // o título cede espaço (encolhe) antes do nome da cor ser cortado
            smallText(name, RX + RW - nw - 1, y, nw, MapArt.INK);
        }
        return swatchGrid("lip", y + 9, CharacterOptions.LIP, lip, v -> lip = v, 11, 3, false, 3);
    }

    /** Cor da pintura (Pintura facial, Lágrima pintada e Pintura azul): igual à dos lábios, aparece quando uma delas está marcada. A primeira é a cor original de cada uma. */
    private int paintChoice(int y) {
        if (g != null) {
            final String name = tr("color.paint." + paint), label = tr("paint.label");
            final int nw = Math.round(w(name, BODY) * 0.8F) + 1;
            smallText(label, RX + 1, y, RW - nw - 8, MapArt.INK_SOFT);
            smallText(name, RX + RW - nw - 1, y, nw, MapArt.INK);
        }
        return swatchGrid("paint", y + 9, CharacterOptions.PAINT, paint, v -> paint = v, 11, 3, false, 3);
    }

    /**
     * Miniatura de uma cicatriz do corpo: 8 x 8 pixels da skin (a 2x) na parte do corpo onde ela está: peito, abdômen, os dois braços,
     * as duas pernas, o ombro (tronco + braço esquerdo), o tronco todo (pele cicatrizada e hematomas) ou as costas.
     */
    private void bodyThumb(int[] px, int bit, int dx, int dy, int sc) {
        final int aw = SkinPainter.isSlim(data()) ? 3 : 4;
        switch (bit - CharacterOptions.BODY_SCAR_BASE) {
            case 0, 5, 6 -> skinRegion(px, 20, 20, 8, 8, dx, dy, sc);                  // tronco, frente, linhas 0 a 7
            case 1 -> skinRegion(px, 20, 24, 8, 8, dx, dy, sc);                        // abdômen: linhas 4 a 11
            case 2 -> {                                                                 // os dois braços (frente), linhas 0 a 7
                skinRegion(px, 44, 20, aw, 8, dx, dy, sc);
                skinRegion(px, 36, 52, aw, 8, dx + (8 - aw) * sc, dy, sc);
            }
            case 3 -> {                                                                 // as duas pernas (frente), linhas 3 a 10
                skinRegion(px, 4, 23, 4, 8, dx, dy, sc);
                skinRegion(px, 20, 55, 4, 8, dx + 4 * sc, dy, sc);
            }
            case 4 -> {                                                                 // ombro: o lado esquerdo do tronco e o braço esquerdo
                skinRegion(px, 24, 20, 4, 8, dx, dy, sc);
                skinRegion(px, 36, 52, aw, 8, dx + 4 * sc, dy, sc);
            }
            default -> skinRegion(px, 32, 20, 8, 8, dx, dy, sc);                        // costas
        }
    }

    /**
     * Miniatura das orelhas: a lateral da cabeça inteira da orelha que está sendo editada ({@code left}: a esquerda do personagem; senão a
     * direita), literalmente a skin do personagem de agora (pele, cabelo, camada externa) com o piercing pintado nela, no metal escolhido
     * para aquela orelha, como nas miniaturas das outras classes. Sem contorno nem destaque.
     */
    private void earThumb(int[] px, boolean left, int dx, int dy, int sc) {
        skinRegion(px, left ? 16 : 0, 8, 8, 8, dx, dy, sc);                      // camada de baixo: lateral esquerda (16, 8) ou direita (0, 8) da cabeça
        skinRegion(px, left ? 48 : 32, 8, 8, 8, dx, dy, sc);                     // camada de cima, 32 pixels à direita
    }

    /** Nome curto de um item para a lista dos marcados (usa {@code <chave>.short} do lang quando existe; senão, o nome inteiro). */
    private String shortName(int bit) {
        final String key = itemKey(bit), sh = tr(key + ".short");
        return sh.startsWith("character.lasthorizon.") ? tr(key) : sh;
    }

    /** Último y (espaço do mapa) onde ainda cabe texto dentro da página Rosto, acima da borda de baixo do pergaminho. */
    private static final int FACE_BOTTOM = 198;
    /** O que está sob o mouse na página Rosto (item, metal, orelha): título e descrição mostrados na hora, embaixo. Nulos = nada. */
    private String infoTitle, infoDesc;

    /**
     * Área de baixo da página Rosto, do {@code y} até a borda do pergaminho. Com o mouse sobre um item (ou botão de metal / orelha) mostra o
     * nome e a DESCRIÇÃO INTEIRA dele na hora (a letra encolhe um pouco se for comprida; nada é cortado). Sem nada sob o mouse, mostra os
     * nomes de tudo que está marcado, nomes inteiros (ver {@link #nameList}); sem nada marcado, o rosto padrão.
     */
    private void faceInfo(int y) {
        if (g == null) return;
        final int h = Math.max(12, FACE_BOTTOM - y);
        if (infoTitle != null) {
            MapArt.rect(g, RX + 1, y + 3, 3, 3, 0xFF140A04);
            MapArt.rect(g, RX + 2, y + 4, 1, 1, MapArt.GOLD);
            fitText(infoTitle, RX + 7, y, RW - 8, MapArt.INK, BOLD);
            paragraph(infoDesc == null ? "" : infoDesc, RX + 1, y + 11, RW - 2, h - 11, MapArt.INK_SOFT, NOTE, 10);
            return;
        }
        final java.util.List<String> full = new java.util.ArrayList<>(), brief = new java.util.ArrayList<>();
        for (int[] cls : CharacterOptions.ITEM_CLASS_ORDER)
            for (int bit : cls) if (hasItem(bit)) {
                full.add(tr(itemKey(bit)));
                brief.add(shortName(bit));
            }
        for (int side = 0; side < 2; side++) {                                                              // brincos: o nome do item e a orelha (E = esquerda, D = direita)
            final int m = side == 0 ? earL : earR;
            for (int q = 0; q < CharacterOptions.EAR_ITEMS; q++) if ((m & (1 << q)) != 0) {
                full.add(tr("ear_item." + q) + " (" + tr("ear_edit." + side + ".tag") + ")");
                brief.add(tr("ear_item." + q) + " " + tr("ear_edit." + side + ".tag"));
            }
        }
        if (full.isEmpty()) {
            smallText(tr("item.none"), RX + 1, y, RW - 2, MapArt.INK_FAINT);
            return;
        }
        nameList(full, brief, y, h);
    }

    /**
     * Os nomes do que está marcado, cada um com um marcador dourado, SEM cortar: primeiro tenta os nomes inteiros em duas colunas, depois em
     * linhas corridas (quantos couberem por linha), cada vez com a letra menor (80%, 72%, 64%), até caber na altura {@code h}. Só se nem assim
     * couber é que passa aos nomes curtos. Com os 5 marcados todos aparecem inteiros.
     */
    private void nameList(java.util.List<String> full, java.util.List<String> brief, int y, int h) {
        final float[] scales = {0.8F, 0.72F, 0.64F};
        final int gap = 4;
        for (int pass = 0; pass < 2; pass++) {
            final java.util.List<String> names = pass == 0 ? full : brief;
            for (int si = 0; si < scales.length; si++) {
                final float sc = scales[si];
                final boolean last = pass == 1 && si == scales.length - 1;
                final int pitch = Math.round(11 * sc), n = names.size();
                final int[] nw = new int[n];
                for (int i = 0; i < n; i++) nw[i] = (int) Math.ceil(w(names.get(i), BODY) * sc) + 9;
                int left = 0, right = 0;                                              // duas colunas: a da esquerda tem o tamanho do maior nome dela
                for (int i = 0; i < n; i++) { if (i % 2 == 0) left = Math.max(left, nw[i]); else right = Math.max(right, nw[i]); }
                if (left + right + gap <= RW && ((n + 1) / 2) * pitch <= h) {
                    for (int i = 0; i < n; i++) nameItem(names.get(i), RX + (i % 2 == 0 ? 0 : left + gap), y + (i / 2) * pitch, nw[i] - 9, sc);
                    return;
                }
                final int[] lineOf = new int[n], xOf = new int[n];                    // linhas corridas: um nome depois do outro enquanto couber
                int line = 0, x = 0;
                for (int i = 0; i < n; i++) {
                    if (x > 0 && x + nw[i] > RW) { line++; x = 0; }
                    lineOf[i] = line;
                    xOf[i] = x;
                    x += nw[i] + gap;
                }
                if ((line + 1) * pitch <= h || last) {
                    for (int i = 0; i < n; i++) nameItem(names.get(i), RX + xOf[i], y + lineOf[i] * pitch, Math.min(nw[i] - 9, RW - 9), sc);
                    return;
                }
            }
        }
    }

    /** Um nome da lista dos marcados: marcador dourado e o texto na escala {@code sc}, com a largura {@code tw} (já medida, então não é cortado). */
    private void nameItem(String name, int x, int y, int tw, float sc) {
        final int dy = Math.round(sc * 4.0F);
        MapArt.rect(g, x + 1, y + dy, 3, 3, 0xFF140A04);
        MapArt.rect(g, x + 2, y + dy + 1, 1, 1, MapArt.GOLD);
        smallText(name, x + 7, y, tw + 2, MapArt.INK, sc);
    }

    /**
     * Estilos de olhos: {@value #EYE_PER_PAGE} por página (4 x 2, quadrados do mesmo tamanho dos da página Rosto), com setas para
     * trocar de página; embaixo, o contador e, abaixo dele, o nome do estilo escolhido. A roda do mouse sobre a página também troca.
     * A página acompanha o estilo escolhido quando ele muda por outro caminho (sorteio, limpar).
     */
    private void eyeStyles(int y) {
        final int[] order = CharacterOptions.EYE_STYLE_ORDER;
        final int n = order.length, pages = (n + EYE_PER_PAGE - 1) / EYE_PER_PAGE;
        if (eyeStyle != eyeStyleSeen) {
            eyeStyleSeen = eyeStyle;
            for (int k = 0; k < n; k++) if (order[k] == eyeStyle) eyePage = k / EYE_PER_PAGE;
        }
        eyePage = Math.max(0, Math.min(pages - 1, eyePage));
        y = styleSection(MapIcons.H_EYES, "eye_style", y, eyeStyle, n, 4, v -> eyeStyle = v, "eye_style.", V_EYESTYLE, TH_HEAD, 34, 3, order,
                true, eyePage * EYE_PER_PAGE, EYE_PER_PAGE);
        final int bw = 24, bh = 16;
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int bx = left ? RX : RX + RW - bw;
            boolean can = left ? eyePage > 0 : eyePage < pages - 1;
            int st = can ? stateOf(MapArt.NORMAL, bx, y, bw, bh) : MapArt.DISABLED;
            if (g != null) MapArt.arrowButton(g, bx, y, bw, bh, left, st, t);
            if (can && hit(bx, y, bw, bh)) {
                eyePage += left ? -1 : 1;
                pulse(bx, y, bw, bh);
                sfx(SND_PAGE, left ? 1.15F : 1.3F);
            }
            if (can && in(bx, y, bw, bh) && stage == EDIT) tip(tr("eye_pages.go"), null, tr("eye_pages.of", eyePage + 1 + (left ? -1 : 1), pages), 0, mx + 10, my - 24);
        }
        if (g != null) {
            textC((eyePage + 1) + "/" + pages, RX + RW / 2, y + 4, MapArt.INK, BOLD);               // contador entre as setas
            fitText(tr("eye_style." + eyeStyle), RX + (RW - Math.min(w(tr("eye_style." + eyeStyle), BOLD), RW)) / 2, y + bh + 3, RW, MapArt.INK_SOFT, BOLD);   // nome embaixo
        }
    }

    private void identity() {
        int y0 = pills("ident.group.", 3, identGroup, v -> identGroup = v);
        if (identGroup == 2) {
            racePage(y0);
            return;
        }
        if (identGroup == 1) {
            epithetPage(y0);
            return;
        }
        int prob = CharacterOptions.nameProblem(name);
        int len = name.codePointCount(0, name.length());
        heading(MapIcons.H_NAME, tr("name"), y0, len + "/" + CharacterOptions.NAME_MAX,
                len >= CharacterOptions.NAME_MAX ? MapArt.ORANGE : MapArt.INK_FAINT);

        int border;
        int kind;
        String msg;
        switch (prob) {
            case CharacterOptions.NAME_OK -> {
                border = 0x3F9A52; kind = 0; msg = tr("name.ok");
            }
            case CharacterOptions.NAME_SHORT -> {
                border = 0xD08A2A; kind = 2; msg = tr("name.short", CharacterOptions.NAME_MIN);
            }
            case CharacterOptions.NAME_EDGE_SPACE -> {
                border = 0xD08A2A; kind = 2; msg = tr("name.edge");
            }
            case CharacterOptions.NAME_LONG -> {
                border = 0xC0392B; kind = 1; msg = tr("name.long", CharacterOptions.NAME_MAX);
            }
            case CharacterOptions.NAME_BAD_CHAR -> {
                border = 0xC0392B; kind = 1; msg = tr("name.bad", CharacterOptions.firstBadChar(name));
            }
            default -> {
                border = hooks.nameBoxFocused() ? 0xD9A441 : 0x8A6A3A; kind = 3;
                msg = tr("name.empty", CharacterOptions.NAME_MIN, CharacterOptions.NAME_MAX);
            }
        }
        if (g != null) {
            MapArt.field(g, NAME_X, NAME_Y, NAME_W, NAME_H, border, hooks.nameBoxFocused());
            hooks.drawNameBox(g, mx, my);
            MapArt.status(g, RX, NAME_Y + NAME_H + 6, kind);
            paragraph(msg, RX + 15, NAME_Y + NAME_H + 6, RW - 15, 24, KIND_COLOR[kind], NOTE, 10);
        }

        // dado do nome aleatório
        diceButton(NAME_Y, tr("name.random"), tr("name.random.desc"), () -> hooks.setNameText(CharacterOptions.randomName(rnd, name)));

        int y = divider(NAME_Y + NAME_H + 31);
        y = heading(MapIcons.G_BOTH, tr("gender"), y, null, 0);
        if (in(RX, y - 24, RW, 24 + 22)) tip(tr("gender"), tr("gender.note"), null, 0, mx + 10, my + 12);
        for (int i = 0; i < CharacterOptions.GENDERS; i++) {
            int bx = RX + i * (RW / 2 + 2), bw = RW / 2 - 2;
            int st = stateOf(gender == i ? MapArt.SELECTED : MapArt.NORMAL, bx, y, bw, 22);
            if (g != null) {
                MapArt.button(g, bx, y, bw, 22, st, t);
                String label = tr("gender." + i);
                int lk = w(label, BOLD) + MapIcons.SIZE + 3 > bw - 4 ? NOTE : BOLD;      // o ícone de 18 px come espaço: letra menor se precisar
                int lw = w(label, lk);
                if (genderIcons(bw)) {
                    int total = MapIcons.SIZE + 3 + lw, x0 = bx + (bw - total) / 2;
                    MapIcons.draw(g, i == 0 ? MapIcons.G_MARS : MapIcons.G_VENUS, x0, y + 2);
                    text(label, x0 + MapIcons.SIZE + 3, lk == BOLD ? y + 7 : y + 8, inkOf(st), lk);
                } else {                                    // não cabe nem com a letra pequena: os dois botões ficam só com o texto
                    text(label, bx + (bw - lw) / 2, y + 8, inkOf(st), lk);
                }
            }
            if (hit(bx, y, bw, 22) && gender != i) {
                gender = i;
                picked();
                pulse(bx, y, bw, 22);
            }
        }
    }

    /** Sub-aba Raça da Identidade: Humana é a única disponível; as outras vagas aparecem trancadas ("em breve"). */
    private void racePage(int y0) {
        int y = heading(MapIcons.H_RACES, tr("race"), y0, "1/" + CharacterOptions.RACE_SLOTS, MapArt.INK_FAINT);
        if (in(RX, y0, RW, 22)) tip(tr("race"), tr("race.note"), null, 0, mx + 10, my + 12);
        int cols = 3, gap = 4, cw = (RW - gap * (cols - 1)) / cols, ch = 38;
        for (int i = 0; i < CharacterOptions.RACE_SLOTS; i++) {
            int bx = RX + (i % cols) * (cw + gap), by = y + (i / cols) * (ch + gap);
            boolean open = i < CharacterOptions.RACES;
            int st = stateOf(open && race == i ? MapArt.SELECTED : MapArt.NORMAL, bx, by, cw, ch);
            if (g != null) {
                MapArt.button(g, bx, by, cw, ch, st, t);
                if (open) {
                    MapIcons.draw(g, MapIcons.H_RACE, bx + (cw - MapIcons.SIZE) / 2, by + 3);
                    String label = tr("race." + i);
                    fitText(label, bx + (cw - Math.min(w(label, BOLD), cw - 4)) / 2, by + ch - 12, cw - 4, inkOf(st), BOLD);
                } else {
                    MapArt.mono(g, LOCK, bx + (cw - 5) / 2, by + 8, 0xFF9A8460);
                    String soon = tr("race.soon");
                    fitText(soon, bx + (cw - Math.min(w(soon, NOTE), cw - 4)) / 2, by + ch - 12, cw - 4, 0xFF9A8460, NOTE);
                }
            }
            if (in(bx, by, cw, ch)) {
                if (open) tip(tr("race." + i), tr("race." + i + ".desc"), tr("race.available"), MapArt.GREEN, mx + 10, my + 12);
                else tip(tr("race.soon"), tr("race.locked.desc"), null, 0, mx + 10, my + 12);
            }
            if (open && hit(bx, by, cw, ch)) {
                race = i;
                picked();
                pulse(bx, by, cw, ch);
            }
        }
        int ty = y + 2 * (ch + gap) + 2;
        paragraph(tr("race." + race + ".desc"), RX, ty, RW, 34, MapArt.INK_SOFT, NOTE, 10);
    }

    /** O dado ao lado de um campo de texto (nome, epíteto): sorteia um valor de exemplo. */
    private void diceButton(int fy, String title, String desc, Runnable roll) {
        int dst = stateOf(MapArt.NORMAL, DICE_X, fy, DICE_W, NAME_H);
        long since = System.currentTimeMillis() - diceAt;
        int shake = g != null && since >= 0L && since < 260L ? (int) Math.round(Math.sin(since / 22.0D) * 2.0D * (1.0D - since / 260.0D)) : 0;
        if (g != null) {
            MapArt.button(g, DICE_X, fy, DICE_W, NAME_H, dst, t);
            MapIcons.draw(g, MapIcons.TAB_RANDOM, DICE_X + (DICE_W - MapIcons.SIZE) / 2 + shake, fy + (NAME_H - MapIcons.SIZE) / 2);
        }
        if (in(DICE_X, fy, DICE_W, NAME_H)) tip(title, desc, null, 0, DICE_X - 100, fy + NAME_H + 4);
        if (hit(DICE_X, fy, DICE_W, NAME_H)) {
            roll.run();
            diceAt = System.currentTimeMillis();
            sfx(SND_ROLL, 1.35F);
        }
    }

    /** Sub-aba Epíteto da Identidade: campo opcional, dado e sugestões para clicar. */
    private void epithetPage(int y0) {
        int len = epithet.codePointCount(0, epithet.length());
        heading(MapIcons.H_EPITHET, tr("epithet"), y0, len + "/" + CharacterOptions.EPITHET_MAX,
                len >= CharacterOptions.EPITHET_MAX ? MapArt.ORANGE : MapArt.INK_FAINT);
        boolean foc = hooks.epithetBoxFocused();
        if (g != null) {
            MapArt.field(g, EPI_X, EPI_Y, EPI_W, EPI_H, epithet.isEmpty() ? (foc ? 0xD9A441 : 0x8A6A3A) : 0x3F9A52, foc);
            hooks.drawEpithetBox(g, mx, my);
            if (epithet.isEmpty() && !foc) {
                String ex = tr("epithet.sample." + (int) ((t / 2600L) % 10));
                fitText(ex, EPI_X + 6, EPI_Y + 5, EPI_W - 12, 0xFF9A8460, NOTE);
            }
            MapArt.status(g, RX, EPI_Y + EPI_H + 6, epithet.isEmpty() ? 3 : 0);
            paragraph(epithet.isEmpty() ? tr("epithet.empty") : tr("epithet.ok"), RX + 15, EPI_Y + EPI_H + 6, RW - 15, 24, KIND_COLOR[epithet.isEmpty() ? 3 : 0], NOTE, 10);
        }
        diceButton(EPI_Y, tr("epithet.random"), tr("epithet.random.desc"), () -> {
            int k = rnd.nextInt(10);
            if (tr("epithet.sample." + k).equals(epithet)) k = (k + 1) % 10;
            hooks.setEpithetText(tr("epithet.sample." + k));
        });

        int y = divider(EPI_Y + EPI_H + 29) - 2;
        int cw = (RW - 4) / 2, bh = 19, step = 21;
        int sk = NOTE;
        // as sugestões ficam numa linha só, todas no mesmo tamanho: o da maior frase, encolhido até caber no botão
        float sc = 1.0F;
        for (int k = 0; k < 6; k++) sc = Math.min(sc, (cw - 8) / (float) Math.max(1, w(tr("epithet.sample." + k), sk)));
        sc = Math.max(0.55F, sc);
        for (int k = 0; k < 6; k++) {
            int bx = RX + (k % 2) * (cw + 4), by = y + (k / 2) * step;
            String label = tr("epithet.sample." + k);
            int st = stateOf(label.equals(epithet) ? MapArt.SELECTED : MapArt.NORMAL, bx, by, cw, bh);
            if (g != null) {
                MapArt.button(g, bx, by, cw, bh, st, t);
                int col = st == MapArt.SELECTED ? MapArt.GOLD_LIGHT : inkOf(st);
                int lw = Math.min(Math.round(w(label, sk) * sc), cw - 8);
                fitScaled(label, bx + (cw - lw) / 2, Math.round(by + 9.5F - 3.5F * sc), cw - 8, col, sk, sc);
            }
            if (hit(bx, by, cw, bh)) {
                hooks.setEpithetText(label);
                pulse(bx, by, cw, bh);
            }
        }
    }

    /** Os dois botões de gênero mostram o ícone só se os dois nomes cabem com ele (assim ficam sempre iguais). */
    private boolean genderIcons(int bw) {
        for (int i = 0; i < CharacterOptions.GENDERS; i++) {
            String label = tr("gender." + i);
            int lk = w(label, BOLD) + MapIcons.SIZE + 3 > bw - 4 ? NOTE : BOLD;
            if (MapIcons.SIZE + 3 + w(label, lk) > bw - 4) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ alma e passado

    private boolean soulRolled() {
        return (touched & (1 << TAB_SOUL)) != 0;
    }

    private boolean rolling() {
        return rollAt != 0L && !rollDone;
    }

    // ------------------------------------------------------------------------------------------ controle deslizante da idade

    private static final int AGE_ROW_H = 20, AGE_HANDLE_W = 26;
    private boolean ageDragging;
    private int ageRowY = -1;
    private long ageTickAt;

    private static int ageTrackX0() {
        return RX + AGE_HANDLE_W / 2;
    }

    private static int ageTrackX1() {
        return RX + RW - AGE_HANDLE_W / 2;
    }

    private int ageHandleX() {
        float f = (age - CharacterOptions.AGE_MIN) / (float) (CharacterOptions.AGE_MAX - CharacterOptions.AGE_MIN);
        return ageTrackX0() + Math.round(f * (ageTrackX1() - ageTrackX0()));
    }

    /** Põe a idade na posição {@code x} (coordenada do mapa) da barra. */
    private void setAgeFromX(double x) {
        double f = (x - ageTrackX0()) / (double) (ageTrackX1() - ageTrackX0());
        setAge(CharacterOptions.AGE_MIN + (int) Math.round(Math.max(0.0D, Math.min(1.0D, f)) * (CharacterOptions.AGE_MAX - CharacterOptions.AGE_MIN)));
    }

    private void setAge(int v) {
        v = Math.max(CharacterOptions.AGE_MIN, Math.min(CharacterOptions.AGE_MAX, v));
        if (v == age) return;
        age = v;
        edit(TAB_PAST);
        long now = System.currentTimeMillis();
        if (now - ageTickAt > 45L) {                                         // um tique por ano que passa (sem metralhar o som)
            ageTickAt = now;
            hooks.sound(SND_TICK, 0.75F + 0.5F * (age - CharacterOptions.AGE_MIN) / (float) (CharacterOptions.AGE_MAX - CharacterOptions.AGE_MIN));
        }
    }

    /** O jogador está arrastando a plaquinha da idade. */
    public boolean isDraggingAge() {
        return ageDragging;
    }

    /** O mouse andou com o botão apertado: a idade acompanha (x em coordenadas do mapa). */
    public void dragAge(double x) {
        if (ageDragging) setAgeFromX(x);
    }

    public void endAgeDrag() {
        ageDragging = false;
    }

    /** Sorteia os seis traços (sempre muda ao menos um), no máximo cinco vezes por personagem. */
    private void startRoll() {
        if (rolling() || soulRolls >= MAX_SOUL_ROLLS) return;
        int[] old = traits.clone();
        int[] next;
        do {
            next = CharacterOptions.rollTraits(rnd);                       // com uma chance pequena, os seis saem no máximo
        } while (java.util.Arrays.equals(old, next));                       // sempre muda ao menos um
        System.arraycopy(next, 0, traits, 0, traits.length);
        soulRolls++;
        java.util.Arrays.fill(settled, false);
        rollDone = false;
        doneAt = 0L;
        rollAt = System.currentTimeMillis();
        edit(TAB_SOUL);
        sfx(SND_ROLL, 1.0F);
    }

    /**
     * Alma: o jogador não escolhe os traços, ele gira. Seis plaquinhas (uma por eixo: farol, balança, raposa, gaivota,
     * caneca e nuvem) mostram o traço sorteado e a força dele; ao girar, cada uma embaralha e assenta com um tique.
     * Embaixo o painel revela o destino da alma (força e contradição), letra por letra.
     */
    private void soul() {
        boolean rolling = rolling(), rolled = soulRolled();
        legendGlow = rolled && !rolling && CharacterDestiny.of(traits).legendary();      // jackpot: tudo no máximo
        int cw = (RW - 2 * TILE_GAP) / 3, x0 = RX + (RW - (3 * cw + 2 * TILE_GAP)) / 2;
        for (int a = 0; a < CharacterOptions.TRAITS; a++) {
            int x = x0 + (a % 3) * (cw + TILE_GAP), y = TILE_Y + (a / 3) * (TILE_H + TILE_GAP);
            boolean over = in(x, y, cw, TILE_H);
            boolean spinning = rolling && !settled[a];
            boolean known = rolled && !spinning;
            if (g != null) soulTile(a, x, y, cw, over, spinning, known);
            if (over && stage == EDIT) {
                if (!known) {
                    tip(tr("tab.4"), tr("soul.tile.unknown"), null, 0, mx + 10, my + 8);
                } else {
                    int v = traits[a], mid = CharacterOptions.TRAIT_MID, side = v < mid ? 0 : v > mid ? 1 : -1;
                    String title = side < 0 ? tr("soul.level.mid") : tr("trait." + a + "." + side);
                    String desc = side < 0 ? tr("soul.axis.mid", tr("trait." + a + ".0"), tr("trait." + a + ".1")) : tr("trait." + a + "." + side + ".desc");
                    tip(title, desc, side < 0 ? null : tr(Math.abs(v - mid) == 2 ? "soul.level.strong" : "soul.level.mild"), KIND_COLOR[0], mx + 10, my + 8);
                }
            }
        }

        // botão de girar
        boolean can = !rolling && soulRolls < MAX_SOUL_ROLLS;
        boolean over = can && in(RX, ROLL_Y, RW, ROLL_H);
        if (g != null) {
            if (can && !rolled) {                                              // convida a girar: o contorno de ouro respira
                int a = 60 + (int) (110 * (0.5D + 0.5D * Math.sin(t / 300.0D)));
                MapArt.outline(g, RX - 2, ROLL_Y - 2, RW + 4, ROLL_H + 4, MapArt.argb(a, 0xFFD27A));
            }
            MapArt.button(g, RX, ROLL_Y, RW, ROLL_H, can ? MapArt.SELECTED : MapArt.DISABLED, t);
            if (over) MapArt.rect(g, RX + 2, ROLL_Y + 2, RW - 4, ROLL_H - 4, MapArt.argb(46, 0xFFE9A0));
            String label = rolling ? tr("soul.rolling") : soulRolls >= MAX_SOUL_ROLLS ? tr("soul.roll.limit") : tr(rolled ? "soul.roll.again" : "soul.roll");
            String left = (MAX_SOUL_ROLLS - soulRolls) + "X";                     // quantas vezes ainda dá para girar
            int lw = w(label, BOLD), cwid = w(left, BOLD), total = MapIcons.SIZE + 3 + cwid + 6 + lw, bx = RX + (RW - total) / 2;
            int shake = rolling ? (int) Math.round(Math.sin(t / 40.0D) * 1.5D) : 0;
            MapIcons.draw(g, MapIcons.TAB_RANDOM, bx, ROLL_Y + 2 + shake);
            int cx0 = bx + MapIcons.SIZE + 3;
            text(left, cx0 + 1, ROLL_Y + 7, 0xFF140A04, BOLD);
            text(left, cx0, ROLL_Y + 6, can ? 0xFFFFC070 : 0xFF9A8F78, BOLD);
            MapArt.rect(g, cx0 + cwid + 2, ROLL_Y + 4, 1, ROLL_H - 8, MapArt.argb(can ? 140 : 70, 0xD9A441));   // fio separando o contador do texto
            fitText(label, cx0 + cwid + 6, ROLL_Y + 6, RX + RW - 6 - (cx0 + cwid + 6), can ? MapArt.GOLD_LIGHT : 0xFF9A8F78, BOLD);
        }
        if (in(RX, ROLL_Y, RW, ROLL_H)) tip(tr("soul.roll"), tr("soul.roll.desc", soulRolls, MAX_SOUL_ROLLS), null, 0, mx + 10, ROLL_Y - 52);
        if (can && hit(RX, ROLL_Y, RW, ROLL_H)) {
            startRoll();
            pulse(RX, ROLL_Y, RW, ROLL_H);
        }

        // painel do destino
        if (g == null) return;
        int py = SOUL_PANEL_Y, ph = SOUL_PANEL_H, pw = RW + 4, px = RX - 2, tx = px + 7, tw = pw - 14;
        MapArt.tipBox(g, px, py, pw, ph, t);
        if (rolling) {
            String dots = ".".repeat(1 + (int) ((t / 260L) % 3L));
            textC(tr("soul.rolling").replace("...", "") + dots, px + pw / 2, py + ph / 2 - 4, MapArt.GOLD_LIGHT, BOLD);
        } else if (!rolled) {
            paragraph(tr("soul.hint"), tx, py + 6, tw, ph - 12, 0xFFEBDDB8, NOTE, 9);        // o texto inteiro dentro da plaquinha (encolhe um pouco se precisar)
        } else {
            CharacterDestiny dest = CharacterDestiny.of(traits);
            long rv = doneAt == 0L ? Long.MAX_VALUE / 2 : Math.max(0L, t - doneAt);                 // quanto tempo desde a revelação
            boolean legend = dest.legendary();
            String title = (legend ? "\u2605 " : "") + tr(dest.key()) + (legend ? " \u2605" : "");
            int shown = rv >= Long.MAX_VALUE / 4 ? title.length() : (int) Math.min(title.length(), rv / 24L);
            int c1 = legend ? MapArt.lerp(0xFFFFC070, 0xFFFFF2C8, 0.5D + 0.5D * Math.sin(t / 200.0D)) : 0xFFFFC070;
            if (legend) MapArt.outline(g, px - 1, py - 1, pw + 2, ph + 2, MapArt.argb(70 + (int) (110 * (0.5D + 0.5D * Math.sin(t / 260.0D))), 0xFFD27A));
            fitText(title.substring(0, shown) + (shown < title.length() ? "_" : ""), tx, py + 5, tw, c1, BOLD);
            long lineAt = (long) title.length() * 24L + 120L;
            MapArt.rect(g, tx, py + 17, pw - 14, 1, MapArt.argb(110, 0xD9A441));
            if (rv >= lineAt) {
                MapArt.status(g, tx, py + 21, 0);
                fitText(tr(dest.strengthKey()), tx + 13, py + 21, tw - 13, 0xFFB8E6A0, NOTE);
            }
            if (rv >= lineAt + 220L) {
                MapArt.status(g, tx, py + 32, 2);
                fitText(tr(dest.flawKey()), tx + 13, py + 32, tw - 13, 0xFFF0B060, NOTE);
            }
        }
    }

    /** Texto desenhado a {@code scale} do tamanho normal; se ainda passar de {@code maxW}, encolhe mais (até 60%) e por fim corta. */
    private void fitScaled(String s, int x, int y, int maxW, int color, int kind, float scale) {
        if (g == null || s.isEmpty()) return;
        int full = w(s, kind);
        float sc = scale;
        if (full * sc > maxW) sc = Math.max(0.60F, maxW / (float) full);
        String out = full * sc > maxW + 0.5F ? clip(s, (int) (maxW / sc), kind) : s;
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(sc, sc, 1.0F);
        MapFont.draw(g, font, out, 0, 0, color, kind);
        g.pose().popPose();
    }

    /** Uma plaquinha de eixo: ícone, traço (ou "?") e a marca da força (cinco pontos). */
    private void soulTile(int a, int x, int y, int cw, boolean over, boolean spinning, boolean known) {
        int mid = CharacterOptions.TRAIT_MID;
        int v = spinning ? hash(a, (int) ((t - rollAt) / 85L)) % CharacterOptions.TRAIT_STEPS : traits[a];
        long fa = t - flashAt[a];
        boolean flash = known && flashAt[a] != 0L && fa >= 0L && fa < 520L;
        MapArt.rect(g, x + 1, y + 2, cw, TILE_H, 0x40000000);
        if (over && !spinning) MapArt.hoverGlow(g, x, y, cw, TILE_H, t);
        MapArt.notched(g, x, y, cw, TILE_H, 0xFF1E120A);
        MapArt.notched(g, x + 1, y + 1, cw - 2, TILE_H - 2, legendGlow && known ? MapArt.lerp(MapArt.GOLD, 0xFFFFF2C8, 0.5D + 0.5D * Math.sin(t / 220.0D + a)) : flash ? MapArt.GOLD_LIGHT : over && !spinning ? MapArt.lerp(MapArt.GOLD, MapArt.GOLD_LIGHT, 0.5D + 0.5D * Math.sin(t / 260.0D)) : known ? MapArt.GOLD : spinning ? MapArt.goldPulse(t) : MapArt.GOLD_DARK);
        MapArt.notched(g, x + 2, y + 2, cw - 4, TILE_H - 4, legendGlow && known ? MapArt.lerp(0xFF4A3414, 0xFF6A4A1C, 0.5D + 0.5D * Math.sin(t / 300.0D + a)) : over ? 0xFF45301A : 0xFF2B1A0E);
        if (flash) MapArt.rect(g, x + 2, y + 2, cw - 4, TILE_H - 4, MapArt.argb((int) (120 * (1.0D - fa / 520.0D)), 0xFFE9A0));
        int ix = x + (cw - MapIcons.SIZE) / 2, iy = y + 4 + (spinning ? (int) Math.round(Math.sin(t / 55.0D + a) * 1.6D) : 0);
        MapIcons.draw(g, SOUL_AXIS_ICON[a], ix, iy);
        if (!known && !spinning) MapArt.rect(g, ix - 1, iy - 1, MapIcons.SIZE + 2, MapIcons.SIZE + 2, MapArt.argb(165, over ? 0x45301A : 0x2B1A0E));      // apagado até girar (na cor do fundo, senão vira um quadrado escuro no hover)
        if ((over || legendGlow && known) && !spinning) MapArt.sweep(g, x + 2, y + 2, cw - 4, TILE_H - 4, t, 46);    // o brilho passa por cima do ícone também

        // linha de cima: o nome do traço (sempre visível); linha de baixo: o valor que saiu no sorteio
        String axis = tr("soul.axis.name." + a);
        int axW = Math.min(w(axis, NOTE), cw - 6);
        fitText(axis, x + (cw - axW) / 2, y + 21, cw - 6, known || spinning ? 0xFFD9BE8A : 0xFFA8946A, NOTE);
        String label;
        int color;
        if (!known && !spinning) {
            label = "?";
            color = 0xFF8A7A5A;
        } else {
            int side = v < mid ? 0 : v > mid ? 1 : -1;
            label = side < 0 ? tr("soul.level.mid") : tr("trait." + a + "." + side);
            color = spinning ? 0xFFB09870 : MapArt.GOLD_LIGHT;
        }
        int kind = BOLD;
        float vs = 0.8F;                                                    // o que saiu no sorteio, em letra menor que o nome do eixo
        int lw = Math.min(Math.round(w(label, kind) * vs), cw - 6);
        fitScaled(label, x + (cw - lw) / 2, y + 31, cw - 6, color, kind, vs);

        int py = y + TILE_H - 5, step = (cw - 14) / 4, px0 = x + (cw - step * 4) / 2;
        MapArt.rect(g, px0, py, step * 4 + 1, 1, MapArt.argb(150, 0x8A6A3A));
        for (int i = 0; i < CharacterOptions.TRAIT_STEPS; i++) {
            int cx = px0 + i * step;
            if ((known || spinning) && i == v) {
                MapArt.disc(g, cx, py, 3, 0xFF140A04);
                MapArt.disc(g, cx, py, 2, known ? MapArt.GOLD : 0xFFB09870);
                MapArt.rect(g, cx - 1, py - 2, 1, 1, MapArt.GOLD_LIGHT);
            } else {
                MapArt.disc(g, cx, py, 1, !known && !spinning ? 0xFF45321F : 0xFF6A4E2E);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ porte: altura (a largura acompanha) e busto

    private static final int SZ_ROW_H = 22, SZ_HANDLE_W = 30, SZ_LABEL_W = 50;   // plaquinha igual à da idade; o nome fica à esquerda da barra
    private int sizeDrag = -1;                                                  // 0 altura, 1 busto; -1 nenhum
    private final int[] sizeRowY = {-1, -1};
    private long sizeTickAt;

    private static int szMin(int k) { return k == 1 ? CharacterOptions.BUST_MIN : CharacterOptions.HEIGHT_MIN; }

    private static int szMax(int k) { return k == 1 ? CharacterOptions.BUST_MAX : CharacterOptions.HEIGHT_MAX; }

    private static int szDef(int k) { return k == 1 ? CharacterOptions.BUST_DEFAULT : CharacterOptions.HEIGHT_DEFAULT; }

    private int szGet(int k) { return k == 0 ? height : bust; }

    private static int szX0() { return RX + SZ_LABEL_W + SZ_HANDLE_W / 2 + 1; }

    private static int szX1() { return RX + RW - SZ_HANDLE_W / 2 - 3; }

    private int szHandleX(int k) {
        float f = (szGet(k) - szMin(k)) / (float) (szMax(k) - szMin(k));
        return szX0() + Math.round(f * (szX1() - szX0()));
    }

    private String szText(int k) {
        return k == 1 ? bust + "%" : String.format(java.util.Locale.ROOT, "%.2f", height / 100.0D);      // altura em blocos (1.80)
    }

    private void szSetFromX(int k, double x) {
        double f = (x - szX0()) / (double) (szX1() - szX0());
        szSet(k, szMin(k) + (int) Math.round(Math.max(0.0D, Math.min(1.0D, f)) * (szMax(k) - szMin(k))));
    }

    private void szSet(int k, int v) {
        v = Math.max(szMin(k), Math.min(szMax(k), v));
        if (k == 0 && Math.abs(v - CharacterOptions.HEIGHT_DEFAULT) <= 2) v = CharacterOptions.HEIGHT_DEFAULT;       // gruda na altura padrão (1,80 bloco)
        if (v == szGet(k)) return;
        if (k == 0) height = v; else bust = v;
        picked();
        long now = System.currentTimeMillis();
        if (now - sizeTickAt > 45L) {
            sizeTickAt = now;
            hooks.sound(SND_TICK, 0.75F + 0.5F * (v - szMin(k)) / (float) (szMax(k) - szMin(k)));
        }
    }

    /** Uma barra deslizante de porte (altura ou busto), igual à da idade: plaquinha com o valor dentro, sulco que dourado até a plaquinha e marcas na barra (arrastar, clicar na barra ou usar a roda do mouse). Devolve o y da próxima linha. */
    private int sizeSlider(int k, int y) {
        sizeRowY[k] = y;
        int hx = szHandleX(k);
        int st = sizeDrag == k ? MapArt.SELECTED : stateOf(MapArt.NORMAL, hx - SZ_HANDLE_W / 2, y, SZ_HANDLE_W, AGE_ROW_H);
        if (g != null) {
            fitText(tr("size." + k), RX + 2, y + 6, SZ_LABEL_W - 4, MapArt.INK, BOLD);
            int gx = RX + SZ_LABEL_W, gw = RW - SZ_LABEL_W - 2;
            MapArt.rect(g, gx, y + 8, gw, 4, 0xFF1E120A);                                    // sulco da barra
            MapArt.rect(g, gx + 1, y + 9, gw - 2, 2, 0xFF6A5030);
            if (hx > gx + 1) MapArt.rect(g, gx + 1, y + 9, hx - gx - 1, 2, MapArt.GOLD);     // parte já percorrida
            int step = k == 1 ? 25 : 10;                                                      // marcas: a cada 0,10 bloco (altura; a de 1,80 é a do padrão) ou 25% (busto)
            for (int a = szMin(k) + step - szMin(k) % step; a < szMax(k); a += step) {
                int tx = szX0() + Math.round((a - szMin(k)) / (float) (szMax(k) - szMin(k)) * (szX1() - szX0()));
                if (Math.abs(tx - hx) > SZ_HANDLE_W / 2) MapArt.rect(g, tx, y + 13, 1, a == szDef(k) && k == 0 ? 4 : 3, MapArt.argb(a == szDef(k) && k == 0 ? 220 : 150, 0x8A6A3A));
            }
            MapArt.button(g, hx - SZ_HANDLE_W / 2, y, SZ_HANDLE_W, AGE_ROW_H, st, t);
            textC(szText(k), hx, y + 6, inkOf(st), BOLD);
        }
        if (in(RX, y, RW, SZ_ROW_H)) tip(tr("size." + k), tr("size." + k + ".note"), null, 0, mx + 10, my - 40);
        if (hit(RX, y, RW, AGE_ROW_H)) {
            sizeDrag = k;
            szSetFromX(k, mx);
        }
        return y + SZ_ROW_H;
    }

    public boolean isDraggingSize() { return sizeDrag >= 0; }

    public void dragSize(double x) { if (sizeDrag >= 0) szSetFromX(sizeDrag, x); }

    public void endSizeDrag() { sizeDrag = -1; }

    /** Passado: idade (o povo é só humano: 16 a 100 anos), mar natal e o lema. */
    private void past() {
        int y0 = TOP;
        int y = heading(MapIcons.H_AGE, tr("age"), TOP, tr("age.phase." + CharacterOptions.agePhase(age)), MapArt.INK_FAINT);
        // idade: controle deslizante (arrastar a plaquinha, clicar na barra ou usar a roda do mouse)
        ageRowY = y;
        int hx = ageHandleX();
        int st = ageDragging ? MapArt.SELECTED : stateOf(MapArt.NORMAL, hx - AGE_HANDLE_W / 2, y, AGE_HANDLE_W, AGE_ROW_H);
        if (g != null) {
            int gx = RX + 2, gw = RW - 4;
            MapArt.rect(g, gx, y + 8, gw, 4, 0xFF1E120A);                                    // sulco da barra
            MapArt.rect(g, gx + 1, y + 9, gw - 2, 2, 0xFF6A5030);
            if (hx > gx + 1) MapArt.rect(g, gx + 1, y + 9, hx - gx - 1, 2, MapArt.GOLD);     // parte já percorrida
            int x0 = ageTrackX0(), x1 = ageTrackX1();
            for (int a = 20; a < CharacterOptions.AGE_MAX; a += 10) {                         // marcas de dezena
                int tx = x0 + Math.round((a - CharacterOptions.AGE_MIN) / (float) (CharacterOptions.AGE_MAX - CharacterOptions.AGE_MIN) * (x1 - x0));
                if (Math.abs(tx - hx) > AGE_HANDLE_W / 2) MapArt.rect(g, tx, y + 13, 1, 3, MapArt.argb(150, 0x8A6A3A));
            }
            MapArt.button(g, hx - AGE_HANDLE_W / 2, y, AGE_HANDLE_W, AGE_ROW_H, st, t);
            textC(String.valueOf(age), hx, y + 6, inkOf(st), BOLD);
        }
        if (hit(RX, y, RW, AGE_ROW_H)) {
            ageDragging = true;
            setAgeFromX(mx);
        }
        if (in(RX, y0, RW, 45)) tip(tr("age"), tr("age.note"), null, 0, mx + 10, my + 10);
        y = divider(y + 24);

        int seaTop = y;
        y = cycleSection(MapIcons.H_SEA, "sea", y, sea, CharacterOptions.SEAS, v -> sea = v, "sea.", false, false);
        if (g != null) fitText(tr("sea." + sea + ".flavor"), RX, y, RW, MapArt.INK_SOFT, NOTE);
        if (in(RX, seaTop, RW, y + 11 - seaTop)) {
            tip(tr("sea." + sea), tr("sea." + sea + ".flavor"), tr("sea." + sea + ".climate") + "\n" + tr("sea." + sea + ".fact") + "\n" + tr("sea.note"),
                    0xFFC9B58A, mx + 10, my - 60);
        }
        y = divider(y + 11);

        int len = motto.codePointCount(0, motto.length());
        heading(MapIcons.H_MOTTO, tr("motto"), y, len + "/" + CharacterOptions.MOTTO_MAX,
                len >= CharacterOptions.MOTTO_MAX ? MapArt.ORANGE : MapArt.INK_FAINT);
        // o campo é uma placa que mostra o lema; clicar nela abre o editor grande
        boolean over = in(MOTTO_X, MOTTO_Y, MOTTO_W, MOTTO_H);
        if (g != null) {
            MapArt.field(g, MOTTO_X, MOTTO_Y, MOTTO_W, MOTTO_H, over ? 0xD9A441 : 0x8A6A3A, over);
            int room = MOTTO_W - 12 - 12;
            if (motto.isEmpty()) {
                String ex = tr("motto.example", tr("motto.example." + (int) ((t / 3500L) % 4)));
                fitText(ex, MOTTO_X + 6, MOTTO_Y + 5, room, 0xFF9A8460, NOTE);
            } else {
                fitText(motto, MOTTO_X + 6, MOTTO_Y + 5, room, 0xFFF6E9C4, BODY);
            }
            MapArt.mono(g, PENCIL, MOTTO_X + MOTTO_W - 14, MOTTO_Y + 5, over ? MapArt.GOLD_LIGHT : 0xFF9A8460);
        }
        if (over) tip(tr("motto.edit"), tr("motto.edit.desc"), null, 0, mx + 10, my - 56);
        if (hit(MOTTO_X, MOTTO_Y, MOTTO_W, MOTTO_H)) openMotto();
    }

    // ------------------------------------------------------------------------------------------ editor do lema

    private void openMotto() {
        mottoOpen = true;
        mottoOpenAt = System.currentTimeMillis();
        hooks.mottoBoxVisible(true);
        hooks.focusMottoBox();
        sfx(SND_PAGE, 1.0F);
    }

    private void closeMotto() {
        mottoOpen = false;
        sfx(SND_PAGE, 1.1F);
    }

    /**
     * Quebra {@code s} em linhas de no máximo {@code maxW} (unidades do texto, antes da escala) e devolve onde cada linha
     * começa: a linha {@code k} vai de {@code starts[k]} até {@code starts[k + 1]} (a última, até o fim). Quebra nos espaços
     * e, se uma palavra sozinha não cabe, no meio dela. Guarda os índices, ao contrário de {@link #wrap}, para o cursor.
     */
    private int[] mottoStarts(String s, int maxW) {
        List<Integer> st = new ArrayList<>();
        st.add(0);
        int ls = 0, sp = -1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') sp = i;
            if (i > ls && w(s.substring(ls, i + 1), BODY) > maxW) {
                int brk = sp > ls ? sp + 1 : i;
                st.add(brk);
                ls = brk;
                sp = s.lastIndexOf(' ', i);
                if (sp < ls) sp = -1;
            }
        }
        int[] out = new int[st.size()];
        for (int i = 0; i < out.length; i++) out[i] = st.get(i);
        return out;
    }

    /** Em que linha está o cursor (no começo de uma linha quebrada, é a de baixo). */
    private static int caretLine(int[] starts, int cursor) {
        int k = 0;
        for (int i = 0; i < starts.length; i++) if (starts[i] <= cursor) k = i;
        return k;
    }

    /** Posição do texto mais perto do ponto ({@code lx}, {@code ly}), dado em unidades do texto a partir do canto do campo. */
    private int mottoIndexAt(String v, int[] starts, double lx, double ly) {
        int k = Math.max(0, Math.min(starts.length - 1, (int) Math.floor(ly / ME_PITCH)));
        int a = starts[k], b = k + 1 < starts.length ? starts[k + 1] : v.length();
        if (k + 1 < starts.length && b > a && v.charAt(b - 1) == ' ') b--;      // o espaço da quebra fica na linha de cima: o cursor não cai nele
        int best = a;
        double bd = Double.MAX_VALUE;
        for (int i = a; i <= b; i++) {
            double d = Math.abs(w(v.substring(a, i), BODY) - lx);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** A janelinha do lema: papel preso com prego sobre o mapa escurecido, texto grande quebrado em linhas, contador e botões. */
    private void mottoEditor() {
        int cw = ME_W, ch = ME_H, cx0 = (MapArt.BW - cw) / 2, cy0 = (MapArt.BH - ch) / 2, mid = cx0 + cw / 2;
        int fx = cx0 + 18, fy = cy0 + 46, fw = cw - 36, fh = 80;
        int ix = fx + 7, iy = fy + 7, iw = (int) ((fw - 14) / ME_SCALE);          // área do texto: iw em unidades do texto, antes da escala
        double cu = Math.max(0.0D, Math.min(1.0D, (System.currentTimeMillis() - mottoOpenAt) / 220.0D)), cease = 1.0D - Math.pow(1.0D - cu, 3);
        if (g != null) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);                          // acima do jogador (que o jogo desenha em z = 50)
            MapArt.rect(g, -3000, -3000, 6400, 6400, MapArt.argb((int) (150 * cu), 0x05030A));
            g.pose().pushPose();
            g.pose().translate(0.0F, (float) Math.round((1.0D - cease) * 16.0D), 0.0F);
            MapArt.card(g, cx0, cy0, cw, ch, t);
            textCS(tr("motto.editor.title"), mid, cy0 + 16, MapArt.INK, MapArt.argb(150, 0xFFF4D0), TITLE);
            textC(clip(tr("motto.editor.note"), cw - 60, NOTE), mid, cy0 + 28, WARM, NOTE);
            MapArt.divider(g, cx0 + 34, cy0 + 38, cw - 68);
        }

        // o texto (o EditBox do jogo guarda o que foi digitado e o cursor; quem desenha é o mapa)
        String v = hooks.mottoText();
        int[] st = mottoStarts(v, iw);
        if (g != null) {
            MapArt.field(g, fx, fy, fw, fh, 0xD9A441, true);
            g.pose().pushPose();
            g.pose().translate((float) ix, (float) iy, 0.0F);
            g.pose().scale(ME_SCALE, ME_SCALE, 1.0F);
            int[] sel = hooks.mottoSelection();
            for (int k = 0; k < st.length; k++) {
                int a = st[k], b = k + 1 < st.length ? st[k + 1] : v.length();
                if (sel != null) {
                    int s0 = Math.max(sel[0], a), s1 = Math.min(sel[1], b);
                    if (s1 > s0) {
                        int x0 = w(v.substring(a, s0), BODY), x1 = w(v.substring(a, s1), BODY);
                        MapArt.rect(g, x0, k * ME_PITCH - 1, x1 - x0, ME_PITCH, MapArt.argb(110, 0xD9A441));
                    }
                }
                text(v.substring(a, b), 0, k * ME_PITCH, 0xFFF6E9C4, BODY);
            }
            if (v.isEmpty()) {
                String ex = tr("motto.example", tr("motto.example." + (int) ((t / 3500L) % 4)));
                lines(wrap(ex, iw, BODY), 0, 0, 0xFF9A8460, BODY, ME_PITCH);
            }
            if (((t / 500L) & 1L) == 0L) {                                   // cursor piscando
                int cur = Math.max(0, Math.min(v.length(), hooks.mottoCursor()));
                int k = caretLine(st, cur);
                MapArt.rect(g, w(v.substring(st[k], cur), BODY), k * ME_PITCH - 1, 1, 10, 0xFFF6E9C4);
            }
            g.pose().popPose();
        }
        if (hit(fx, fy, fw, fh)) {                                           // clicar no texto leva o cursor até ali
            hooks.moveMottoCursor(mottoIndexAt(v, st, (mx - ix) / (double) ME_SCALE, (my - iy) / (double) ME_SCALE));
            dead = true;                                                     // sem o som de escolha a cada clique no texto
        }

        // contador e dica de teclas
        int len = v.codePointCount(0, v.length());
        String cnt = len + "/" + CharacterOptions.MOTTO_MAX;
        if (g != null) {
            text(clip(tr("motto.editor.keys"), fw - w(cnt, BODY) - 10, NOTE), fx, fy + fh + 4, MapArt.INK_FAINT, NOTE);
            text(cnt, fx + fw - w(cnt, BODY), fy + fh + 4, len >= CharacterOptions.MOTTO_MAX ? MapArt.ORANGE : MapArt.INK_FAINT, BODY);
        }

        // botões: limpar e pronto
        int bw2 = 108, by = cy0 + ch - 32;
        int bx = mid - 8 - bw2;
        boolean empty = v.isEmpty();
        int st1 = empty ? MapArt.DISABLED : stateOf(MapArt.NORMAL, bx, by, bw2, 20);
        if (g != null) {
            MapArt.button(g, bx, by, bw2, 20, st1, t);
            textC(tr("motto.editor.clear"), bx + bw2 / 2, by + 6, inkOf(st1), BOLD);
        }
        if (!empty && hit(bx, by, bw2, 20)) {
            hooks.setMottoText("");
            hooks.focusMottoBox();
            sfx(SND_TICK, 0.8F);
        }
        bx = mid + 8;
        int st2 = stateOf(MapArt.NORMAL, bx, by, bw2, 20);
        if (g != null) {
            MapArt.button(g, bx, by, bw2, 20, st2, t);
            MapArt.mono(g, MapArt.CHECK, bx + 14, by + 7, MapArt.GREEN);
            textC(tr("motto.editor.done"), bx + 22 + (bw2 - 22) / 2, by + 6, inkOf(st2), BOLD);
        }
        if (hit(bx, by, bw2, 20)) closeMotto();

        // clicar no resto do papel não faz nada; clicar fora dele fecha (nada vaza para o mapa de baixo)
        if (click && !consumed) {
            consumed = true;
            dead = true;
            if (!in(cx0, cy0, cw, ch)) mottoOpen = false;
        }
        if (g != null) {
            g.pose().popPose();
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------------------------------ finalização

    private void startSealing() {
        stage = SEALING;
        stageStart = System.currentTimeMillis();
        holdStart = -1L;
        heldMs = 0L;
        fadeReleased = false;
        sealSnd = false;
        if (!finalizeSent) {
            finalizeSent = true;
            hooks.finalizeNow(data());
        }
    }

    private void overlay() {
        if (g != null) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);    // acima do jogador (que o jogo desenha em z = 50)
        }
        if (stage == CONFIRM) confirmCard(); else sealing();
        if (g != null) g.pose().popPose();
    }


    /** Volta para a edição, já na aba (e sub-aba) de onde veio a linha clicada. */
    private void jump(int toTab, int toGroup) {
        stage = EDIT;
        setTab(toTab);
        if (toGroup >= 0) bodyGroup = toGroup;
    }

    /** Faixa de destaque de uma linha do resumo: entra da esquerda para a direita, com trilho de ouro e um brilho que passeia. */
    private void hoverBar(int idx, boolean over, int x, int y, int wd, int h) {
        if (g == null) return;
        rowHover[idx] += ((over ? 1.0F : 0.0F) - rowHover[idx]) * (1.0F - (float) Math.exp(-18.0D * dt));
        float k = rowHover[idx];
        if (k < 0.02F) return;
        int bw = (int) Math.round(wd * (1.0D - Math.pow(1.0D - k, 3)));
        if (bw < 2) return;
        MapArt.rect(g, x, y, bw, h, MapArt.argb((int) Math.round(78 * k), 0xB8862A));
        MapArt.rect(g, x, y, bw, 1, MapArt.argb((int) Math.round(120 * k), 0xE8B84A));
        MapArt.rect(g, x, y + h - 1, bw, 1, MapArt.argb((int) Math.round(120 * k), 0xE8B84A));
        MapArt.rect(g, x, y, 2, h, MapArt.GOLD_DARK);
        int sx = x + (int) ((t / 14L) % (wd + 24)) - 12;                      // brilho passando pela faixa
        int a0 = Math.max(x + 2, sx), a1 = Math.min(x + bw, sx + 9);
        if (a1 > a0) MapArt.rect(g, a0, y + 1, a1 - a0, h - 2, MapArt.argb((int) Math.round(46 * k), 0xFFF0B0));
    }

    /** Uma linha do resumo: rótulo pequeno, valor, faixa de destaque e um balão bonito (sem esperar) com mais detalhes; clicar leva de volta à aba. */
    private void summaryRow(int idx, int x, int y, int wd, String label, String value, int valueColor, int toTab, int toGroup, int h,
                            String tipT, String tipD, String tipE) {
        boolean over = in(x - 4, y - 3, wd + 8, h);
        hoverBar(idx, over, x - 4, y - 3, wd + 8, h);
        if (g != null) {
            if (over) {
                int nudge = (int) Math.round(0.5D + 0.5D * Math.sin(t / 160.0D));                       // a setinha convida: vai e volta 1 px
                MapArt.chevron(g, x + wd - 1 - nudge, y + 1, 0xFF7A4A10);
            }
            text(label, x, y, over ? MapArt.INK : MapArt.INK_FAINT, NOTE);
            fitText(value, x + 52, y, wd - 52 - (over ? 8 : 0), valueColor, BODY);
        }
        if (over && tipT != null) {
            tipQuick = true;
            tip(tipT, tipD, tipE, MapArt.ORANGE, x + 40, y + h + 2);
        }
        if (hit(x - 4, y - 3, wd + 8, h)) jump(toTab, toGroup);
    }

    private void confirmCard() {
        int cw = CARD_W, ch = CARD_H, cx0 = (MapArt.BW - cw) / 2, cy0 = (MapArt.BH - ch) / 2, mid = cx0 + cw / 2;
        long ce = System.currentTimeMillis() - stageStart;
        double cu = Math.max(0.0D, Math.min(1.0D, ce / 260.0D)), cease = 1.0D - Math.pow(1.0D - cu, 3);
        if (g != null) {
            MapArt.rect(g, -3000, -3000, 6400, 6400, MapArt.argb((int) (150 * cu), 0x05030A));      // o fundo escurece e o cartão sobe
            g.pose().pushPose();
            g.pose().translate(0.0F, (float) Math.round((1.0D - cease) * 20.0D), 0.0F);
            MapArt.card(g, cx0, cy0, cw, ch, t);
        }
        CharacterData d = data();
        CharacterDestiny dest = CharacterDestiny.of(d);
        if (g != null) {
            textCS(tr("confirm.title"), mid, cy0 + 16, MapArt.INK, MapArt.argb(150, 0xFFF4D0), TITLE);
            textC(clip(tr("confirm.ready"), cw - 60, NOTE), mid, cy0 + 28, WARM, NOTE);
            MapArt.divider(g, cx0 + 34, cy0 + 38, cw - 68);
        }

        // retrato
        int pw = 92, ph = 96, pxx = cx0 + 20, pyy = cy0 + 46, pcx = pxx + pw / 2, feet = pyy + ph - 12;
        if (g != null) {
            MapArt.portrait(g, pxx, pyy, pw, ph, t);
            MapArt.groundShadow(g, pcx, feet, 15, 3);
            hooks.drawPlayer(g, pcx, feet, (int) ((feet - (pyy + 12)) / 1.95D), mx, my, 0.0F);
        }
        // cores: cada uma leva de volta à aba dela
        int[] cols = {CharacterOptions.SKIN[d.skin], CharacterOptions.HAIR[d.hair], CharacterOptions.EYES[d.eyes],
                CharacterOptions.OUTFIT_COLOR[d.outfitColor]};
        String[] kinds = {"skin", "hair", "eyes", "outfit"};
        int[] ids = {d.skin, d.hair, d.eyes, d.outfitColor};
        int[] toTab = {TAB_BODY, TAB_HAIR, TAB_BODY, TAB_OUTFIT}, toGroup = {0, -1, 1, -1};
        int sx0 = pcx - (4 * 14 + 3 * 5) / 2;
        for (int i = 0; i < 4; i++) {
            int sx = sx0 + i * 19, sy = pyy + ph + 6;
            boolean over = in(sx - 1, sy - 1, 16, 16);
            if (g != null) MapArt.swatch(g, sx, sy, 14, 14, cols[i], false, over, t);
            if (over) tip(tr("color." + kinds[i] + "." + ids[i]), null, null, 0, mx + 10, my - 24);
            if (hit(sx - 1, sy - 1, 16, 16)) jump(toTab[i], toGroup[i]);
        }

        // linhas do resumo
        int rx = cx0 + 128, rw = cw - 128 - 22, ry = cy0 + 48;
        int nk = MapMode.nameKind(d.name);
        boolean nOver = in(rx - 4, ry - 4, rw + 8, 17);
        hoverBar(5, nOver, rx - 4, ry - 4, rw + 8, 17);
        if (g != null) {
            if (nOver) MapArt.chevron(g, rx + rw - 1, ry + 3, 0xFF7A4A10);
            final int nkind = nk == TITLE ? TITLE : BOLD;
            final int nameW = w(d.name, nkind), epiW = d.epithet.isEmpty() ? 0 : w(d.epithet, NOTE) + 8, room = rw - 10;
            if (epiW == 0 || nameW + epiW <= room) {                                                         // cabe tudo no tamanho normal
                fitText(d.name, rx, ry, room - epiW, WARM, nkind);
                if (epiW > 0) fitText(d.epithet, rx + room - epiW + 8, ry + 2, epiW - 8, 0xFF7A5A48, NOTE);   // o epíteto fica na ponta da linha do nome
            } else {                                                                                         // nome e epíteto encolhem juntos (até 60%) em vez de o epíteto ser cortado
                final float k = Math.max(0.60F, room / (float) (nameW + epiW));
                final int nw2 = (int) Math.ceil(nameW * k), ew2 = (int) Math.ceil(epiW * k);
                fitScaled(d.name, rx, ry + Math.round((1.0F - k) * 3.0F), nw2 + 1, WARM, nkind, k);
                fitScaled(d.epithet, rx + room - ew2 + Math.round(8 * k), ry + 2 + Math.round((1.0F - k) * 3.0F), ew2, 0xFF7A5A48, NOTE, k);
            }
        }
        if (hit(rx - 4, ry - 4, rw + 8, 17)) jump(TAB_IDENTITY, -1);
        int y = ry + 17, pitch = 14;
        int ph0 = CharacterOptions.agePhase(d.age);
        String ageTxt = tr("confirm.age", d.age, tr("age.phase." + ph0));
        summaryRow(0, rx, y, rw, tr("age"), ageTxt, MapArt.INK, TAB_PAST, -1, 13, ageTxt, tr("age.phase." + ph0 + ".desc"), null);
        y += pitch;
        summaryRow(1, rx, y, rw, tr("sea"), tr("sea." + d.sea), MapArt.INK, TAB_PAST, -1, 13, tr("sea." + d.sea), tr("sea." + d.sea + ".flavor"),
                tr("sea." + d.sea + ".climate") + "\n" + tr("sea." + d.sea + ".fact"));
        y += pitch;
        summaryRow(2, rx, y, rw, tr("gender"), tr("gender." + d.gender), MapArt.INK, TAB_IDENTITY, -1, 13, tr("gender." + d.gender), tr("gender.note"), null);
        y += pitch;
        summaryRow(3, rx, y, rw, tr("tab.4"), tr(dest.key()), WARM, TAB_SOUL, -1, 13, tr(dest.key()), tr(dest.strengthKey()), tr(dest.flawKey()));
        y += pitch;
        boolean mOver = in(rx - 4, y - 3, rw + 8, 31);
        hoverBar(4, mOver, rx - 4, y - 3, rw + 8, 31);
        if (mOver && !d.motto.isEmpty()) {
            tipQuick = true;
            tip(tr("motto"), "“" + d.motto + "”", null, 0, rx + 40, y + 26);
        }
        if (g != null) {
            text(tr("motto"), rx, y, mOver ? MapArt.INK : MapArt.INK_FAINT, NOTE);
            if (d.motto.isEmpty()) text(tr("confirm.no_motto"), rx + 52, y, 0xFF8A7A5A, NOTE);
            else paragraph("“" + d.motto + "”", rx + 52, y, rw - 52, 30, MapArt.INK_SOFT, NOTE, 10);       // o lema inteiro (até 3 linhas), sem reticências
        }
        if (hit(rx - 4, y - 3, rw + 8, 31)) jump(TAB_PAST, -1);
        if (in(cx0 + 12, cy0 + 44, cw - 24, 120) && tipTitle == null) tip(tr("confirm.title"), tr("confirm.edit"), null, 0, mx + 10, my + 12);

        // aviso e botões
        if (g != null) {
            String wt = tr("confirm.warn");
            List<String> warn = wrap(wt, cw - 36, NOTE);
            if (warn.size() > 1) warn = wrap(wt, w(wt, NOTE) / 2 + 16, NOTE);          // duas linhas equilibradas (sem uma palavra sozinha na segunda)
            for (int i = 0; i < Math.min(2, warn.size()); i++) textC(warn.get(i), mid, cy0 + 168 + i * 9, MapArt.INK_SOFT, NOTE);
        }
        int bw2 = 128, by = cy0 + ch - 30;
        // revisar
        int bx = mid - 8 - bw2, st = stateOf(MapArt.NORMAL, bx, by, bw2, 20);
        if (g != null) MapArt.button(g, bx, by, bw2, 20, st, t);
        textC(tr("confirm.back"), bx + bw2 / 2, by + 6, inkOf(st), BOLD);
        if (hit(bx, by, bw2, 20)) stage = EDIT;
        // começar
        bx = mid + 8;
        st = stateOf(MapArt.NORMAL, bx, by, bw2, 20);
        if (g != null) {
            MapArt.button(g, bx, by, bw2, 20, st, t);
            MapArt.seal(g, bx + 14, by + 10, 7, 0.0D);
        }
        textC(tr("confirm.seal"), bx + 22 + (bw2 - 22) / 2, by + 6, inkOf(st), BOLD);
        if (hit(bx, by, bw2, 20)) startSealing();
        // clique no resto do cartão não faz nada, mas também não "vaza" para o mapa de baixo
        if (click && !consumed) {
            consumed = true;
            dead = true;
        }
        if (g != null) g.pose().popPose();
        if (g != null && tipTitle != null) {
            applyTipDelay();
            tooltip();
        }
    }

    private void sealing() {
        if (g == null) return;
        long el = System.currentTimeMillis() - stageStart;
        int cx = MapArt.BW / 2, cy = MapArt.BH / 2 - 10;
        int dim = (int) Math.round(Math.min(215, 150 + el / 6) * exitDim());      // o escurecimento some primeiro e deixa o mundo aparecer
        if (dim > 0) MapArt.rect(g, -3000, -3000, 6400, 6400, MapArt.argb(dim, 0x05030A));

        double u = Math.min(1.0D, el / 300.0D), ease = 1.0D - Math.pow(1.0D - u, 3);
        int r = 16 + (int) Math.round(14 * (1.0D - ease));
        double press = el < 300 ? 0.0D : el < 420 ? 1.0D : 0.5D;
        if (el >= 300 && el < 800) {                       // onda de choque quando o selo bate no papel
            int rr = 18 + (int) ((el - 300) / 5);
            int a = (int) (200 * (1.0D - (el - 300) / 500.0D));
            for (int k = 0; k < rr * 8; k++) {
                double ang = k / (double) (rr * 8) * Math.PI * 2.0D;
                MapArt.rect(g, cx + (int) Math.round(Math.cos(ang) * rr), cy + (int) Math.round(Math.sin(ang) * rr), 1, 1,
                        MapArt.argb(a, 0xFFE9A0));
            }
        }
        if (!sealSnd && el >= 300L) {                       // o selo bate no papel
            sealSnd = true;
            hooks.sound(SND_SEAL, 1.0F);
        }
        if (el >= 300L && el < 1150L) {                    // faíscas douradas saem do selo
            double age = (el - 300L) / 1000.0D;
            for (int k = 0; k < 30; k++) {
                double ang = k * 2.399963D + (hash(k, 7) % 100) / 100.0D, sp = 38.0D + hash(k, 3) % 60;
                double life = 0.55D + (hash(k, 5) % 30) / 100.0D;
                if (age > life) continue;
                int a = (int) (240 * (1.0D - age / life));
                int sx = cx + (int) Math.round(Math.cos(ang) * sp * age), sy = cy + (int) Math.round(Math.sin(ang) * sp * age + 28.0D * age * age);
                int size = k % 4 == 0 ? 2 : 1;
                MapArt.rect(g, sx, sy, size, size, MapArt.argb(a, k % 3 == 0 ? 0xFFFFFF : 0xFFD27A));
            }
        }
        MapArt.seal(g, cx, cy, r, press);

        if (el >= 450) {
            int a = (int) Math.min(255, (el - 450) * 255 / 300);
            String title = tr("sealed"), sub = tr("sealed.welcome", data().name);
            int sk = MapFont.kindFor(sub, BODY);
            MapFont.draw(g, font, title, cx - MapFont.width(font, title, TITLE) / 2, cy + 36, MapArt.argb(a, 0xFFE9A0), TITLE);
            MapFont.draw(g, font, sub, cx - MapFont.width(font, sub, sk) / 2, cy + 54, MapArt.argb(a, 0xF6E9C4), sk);
        }
        if (el >= 900L) {                                  // enquanto a tela segura, o selo respira e brasas sobem devagar
            double pulse = 0.5D + 0.5D * Math.sin((el - 900L) / 380.0D);
            int rr = r + 6 + (int) Math.round(pulse * 2.0D), ra = (int) (40 + 70 * pulse);
            for (int k = 0; k < rr * 8; k++) {
                double ang = k / (double) (rr * 8) * Math.PI * 2.0D;
                MapArt.rect(g, cx + (int) Math.round(Math.cos(ang) * rr), cy + (int) Math.round(Math.sin(ang) * rr), 1, 1, MapArt.argb(ra, 0xFFE9A0));
            }
            for (int k = 0; k < 14; k++) {
                double ph = ((el - 900L) / 5200.0D + Math.abs(hash(k, 11) % 100) / 100.0D) % 1.0D;
                int ex = cx + Math.abs(hash(k, 13) % 140) - 70 + (int) Math.round(Math.sin(ph * 9.0D + k) * 3.0D);
                int ey = cy + 34 - (int) Math.round(ph * 100.0D);
                int ea = (int) Math.round(210 * Math.sin(Math.PI * ph));
                MapArt.rect(g, ex, ey, k % 3 == 0 ? 2 : 1, k % 3 == 0 ? 2 : 1, MapArt.argb(ea, k % 2 == 0 ? 0xFFD27A : 0xFFF0B0));
            }
        }
    }

    /** Quanto do fade final já passou: 0 até o fade começar, 1 quando a tela pode fechar (0 fora do selo). */
    public float exitProgress() {
        if (stage != SEALING) return 0.0F;
        long el = fadeClock(), from = SEAL_TOTAL - SEAL_FADE_MS;
        if (el <= from) return 0.0F;
        return (float) Math.min(1.0D, (el - from) / (double) SEAL_FADE_MS);
    }

    private static double smooth(double u) {
        u = Math.max(0.0D, Math.min(1.0D, u));
        return u * u * (3.0D - 2.0D * u);
    }

    /** Opacidade da tela inteira: 1 durante a criação e o selo; no fade final cai suave a 0 e o jogo aparece por baixo. */
    public float exitAlpha() {
        float u = exitProgress();
        if (u <= 0.0F) return 1.0F;
        return (float) (1.0D - smooth((u - 0.2D) / 0.8D));      // o pergaminho e o selo ficam um pouco mais e somem por último
    }

    /** Opacidade só do escurecimento (fundo preto): 1 durante o selo; some na primeira parte do fade, antes do pergaminho. */
    public float exitDim() {
        float u = exitProgress();
        if (u <= 0.0F) return 1.0F;
        return (float) (1.0D - smooth(u / 0.65D));
    }

    /** Pequeno auxiliar de fonte para nomes digitados pelo jogador. */
    private static final class MapMode {
        static int nameKind(String s) {
            return MapFont.kindFor(s, TITLE);
        }
    }
}
