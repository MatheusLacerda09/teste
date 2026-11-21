import br.com.lasthorizon.akuma.character.*;
import java.nio.file.*;
import java.util.*;

/** Testes de Java puro da lógica da criação (sem Minecraft). Uso: tools/tests/run.sh */
public class CharacterLogicTest {
    static int fails;
    /** O tecido ganha luz e sombra do pintor: a cor continua da mesma família (cada canal a até {@code tol} da cor da roupa). */
    static boolean near(int a, int b, int tol) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) <= tol && Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) <= tol && Math.abs((a & 255) - (b & 255)) <= tol;
    }

    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FALHOU: " + what); } else System.out.println("ok: " + what); }

    /** As duas skins (64 de largura) são iguais no retângulo {@code x0, y0, w, h}? */
    static boolean sameRegion(int[] a, int[] b, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) if (a[y * 64 + x] != b[y * 64 + x]) return false;
        return true;
    }

    /** Quantos pixels diferem entre as duas skins FORA das duas laterais da cabeça (a esquerda em x 16 a 23 e a direita em x 0 a 7, linhas 8 a 15). */
    static int diffOutsideEars(int[] a, int[] b) {
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            int x = i % 64, y = i / 64;
            final boolean ear = y >= 8 && y < 16 && (x < 8 || (x >= 16 && x < 24));
            if (!ear && a[i] != b[i]) n++;
        }
        return n;
    }

    /** Lê os números de um trecho de código como {@code 24, 20, 28.5F, 24}. */
    static float[] nums(String s) {
        String[] t = s.trim().split("\\s*,\\s*");
        float[] r = new float[t.length];
        for (int i = 0; i < t.length; i++) r[i] = Float.parseFloat(t[i].trim().replaceAll("[Ff]$", ""));
        return r;
    }

    /**
     * Regra das texturas do peito: a região da skin {@code uv = {u0, v0, u1, v1}} numa face de {@code horiz} x {@code vert} pixels do modelo
     * não pode esticar o desenho. Em cada eixo ela tem o tamanho exato da face (1 pixel da skin = 1 pixel do modelo) ou só 1 pixel (uma
     * linha ou coluna que se repete ao longo da face, sem desenho para duplicar). Qualquer outra medida (por exemplo 3 colunas em 4 pixels)
     * dá pixels de largura desigual, com cara de pixel duplicado.
     */
    static boolean uvOk(float[] uv, float horiz, float vert) {
        float du = Math.abs(uv[2] - uv[0]), dv = Math.abs(uv[3] - uv[1]);
        return (Math.abs(du - horiz) < 1e-3 || Math.abs(du - 1) < 1e-3) && (Math.abs(dv - vert) < 1e-3 || Math.abs(dv - 1) < 1e-3);
    }

    public static void main(String[] a) throws Exception {
        // nomes aleatórios todos válidos e sem repetição imediata
        Set<String> seen = new HashSet<>();
        boolean allValid = true;
        for (String n : CharacterOptions.RANDOM_NAMES) { allValid &= CharacterOptions.validName(n); seen.add(n); }
        check(allValid, "todos os nomes aleatórios são válidos");
        check(seen.size() == CharacterOptions.RANDOM_NAMES.length, "nomes aleatórios sem duplicata");
        Random r = new Random(1);
        boolean differs = true;
        for (int i = 0; i < 500; i++) { String c = CharacterOptions.RANDOM_NAMES[r.nextInt(50)]; differs &= !CharacterOptions.randomName(r, c).equals(c); }
        check(differs, "nome aleatório nunca repete o atual");

        // cores novas: índices antigos continuam iguais e personagens antigos continuam válidos
        check(CharacterOptions.SKIN[7] == 0xFAD9C9 && CharacterOptions.HAIR[9] == 0xD23C8A && CharacterOptions.EYES[5] == 0xB02E2E && CharacterOptions.OUTFIT_COLOR[7] == 0x7A4A21, "índices antigos das paletas preservados");
        CharacterData old = CharacterData.fromInts("Velho", "", new int[]{7, 9, 5, 4, 3, 4, 7, 3});
        check(old.isValid(), "personagem salvo antes das cores novas continua válido");
        int[] last = {CharacterOptions.SKIN.length - 1, CharacterOptions.HAIR.length - 1, CharacterOptions.EYES.length - 1, 0, 0, 0, CharacterOptions.OUTFIT_COLOR.length - 1, 0};
        CharacterData n = CharacterData.fromInts("Novo", "", last);
        check(n.isValid(), "última cor de cada paleta é aceita");
        check(SkinPainter.paint(n).length == 64 * 64, "skin pinta com as cores novas");
        int[] bad = last.clone(); bad[0] = CharacterOptions.SKIN.length;
        check(!CharacterData.fromInts("Novo", "", bad).isValid(), "cor fora da faixa é recusada");

        // barba e acessório: registros antigos (18 números) ficam sem; valores novos são conferidos
        CharacterData prev = CharacterData.fromInts("Antigo", "", new int[]{1, 1, 1, 0, 0, 0, 1, 0, 0, 25, 0, 0, 2, 2, 2, 2, 2, 2});
        check(prev.isValid() && prev.beard == 0 && prev.accessory == 0, "registro de 18 números vira barba 0 e acessório 0");
        CharacterData full = CharacterData.fromInts("Novo", "", new int[]{1, 1, 1, CharacterOptions.HAIR_STYLES - 1, CharacterOptions.FACE_STYLES - 1, 0, 1, 0, 0, 25, 0, 0, 2, 2, 2, 2, 2, 2, CharacterOptions.BEARDS - 1, CharacterOptions.ACCESSORIES - 1});
        check(full.isValid() && full.ints().length == CharacterData.INT_COUNT && full.ints()[19] == CharacterOptions.ACCESSORIES - 1, "último estilo, barba e acessório são aceitos e salvos");
        check(SkinPainter.paint(full).length == 64 * 64, "skin pinta com todos os estilos novos");
        boolean allPaint = true;
        for (int h = 0; h < CharacterOptions.HAIR_STYLES; h++) for (int f = 0; f < CharacterOptions.FACE_STYLES; f++) for (int b = 0; b < CharacterOptions.BEARDS; b++) for (int ac = 0; ac < CharacterOptions.ACCESSORIES; ac++) {
            int[] px = SkinPainter.paint(CharacterData.fromInts("Ab", "", new int[]{1, 1, 1, h, f, 0, 1, 0, 0, 25, 0, 0, 2, 2, 2, 2, 2, 2, b, ac}));
            for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) allPaint &= (px[y * 64 + x] >>> 24) == 255;   // a frente da cabeça nunca tem buraco
        }
        check(allPaint, "nenhuma combinação deixa buraco na frente da cabeça");
        CharacterData badAcc = CharacterData.fromInts("Novo", "", new int[]{1, 1, 1, 0, 0, 0, 1, 0, 0, 25, 0, 0, 2, 2, 2, 2, 2, 2, 0, CharacterOptions.ACCESSORIES});
        check(!badAcc.isValid(), "acessório fora da faixa é recusado");

        // estilos de olhos
        check(CharacterData.defaults().eyeStyle == 0 && prev.eyeStyle == 0, "personagem novo e registro antigo ficam com o olho padrão (estilo 0)");
        boolean eyesOk = true, eyesDiffer = true;
        int[] baseEyes = null;
        for (int g = 0; g < 2; g++) for (int es = 0; es < CharacterOptions.EYE_STYLES; es++) for (int bd = 0; bd < CharacterOptions.BEARDS; bd++) {
            int[] v = {1, 1, 3, 4, 0, 5, 6, 0, g, 25, 0, 0, 2, 2, 2, 2, 2, 2, bd, 0, es};
            CharacterData d = CharacterData.fromInts("Ab", "", v);
            eyesOk &= d.isValid() && d.eyeStyle == es && d.ints()[20] == es;
            int[] px = SkinPainter.paint(d);
            for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) eyesOk &= (px[y * 64 + x] >>> 24) == 255;
            if (g == 1 && bd == 0) {
                int[] face = new int[64];
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) face[y * 8 + x] = px[(8 + y) * 64 + 8 + x];
                if (es == 0) baseEyes = face; else eyesDiffer &= !java.util.Arrays.equals(face, baseEyes);
            }
        }
        check(eyesOk, "todos os estilos de olhos pintam o rosto inteiro e são salvos");
        check(eyesDiffer, "cada estilo de olhos desenha um rosto diferente do padrão");
        // piscar: a textura de olhos fechados só muda os olhos (linhas 1 a 5 do rosto), para todos os estilos
        boolean blinkOk = true;
        for (int es = 0; es < CharacterOptions.EYE_STYLES; es++) {
            CharacterData bd0 = CharacterData.fromInts("Ab", "", new int[]{1, 1, 3, 4, 0, 5, 6, 0, 1, 25, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, es});
            int[] open = SkinPainter.paint(bd0), shut = SkinPainter.paintBlink(bd0), half = SkinPainter.paintHalf(bd0);
            boolean shutDiffers = false;
            for (int i = 0; i < open.length; i++) {
                int x = i % 64, y = i / 64;
                boolean eyeZone = y >= 9 && y <= 13 && x >= 8 && x < 16;
                if (!eyeZone) blinkOk &= open[i] == shut[i] && open[i] == half[i];
                else shutDiffers |= open[i] != shut[i];
            }
            blinkOk &= shutDiffers || es == 16 || es == 18 || es == 19;     // 16, 18 e 19 já são olhos fechados (só traços): o piscar não muda nada
        }
        check(blinkOk, "olhos fechados (piscar) mudam só os olhos e todos os estilos mudam algo");
        // olhar: sem desvio é a skin de sempre; com desvio só os olhos mudam, e quase todos os estilos reagem
        boolean gazeOk = true;
        int[] moved = new int[4];
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int es = 0; es < CharacterOptions.EYE_STYLES; es++) {
            CharacterData gd = CharacterData.fromInts("Ab", "", new int[]{1, 1, 3, 4, 0, 5, 6, 0, 1, 25, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, es});
            int[] open = SkinPainter.paint(gd);
            gazeOk &= java.util.Arrays.equals(open, SkinPainter.paint(gd, 0, 0, 0));
            for (int q = 0; q < 4; q++) for (int blink = 0; blink < 2; blink++) {
                int[] look = SkinPainter.paint(gd, blink, dirs[q][0], dirs[q][1]), ref = SkinPainter.paint(gd, blink, 0, 0);
                boolean diff = false;
                for (int i = 0; i < open.length; i++) {
                    int x = i % 64, y = i / 64;
                    boolean eyeZone = y >= 9 && y <= 13 && x >= 8 && x < 16;
                    if (!eyeZone) gazeOk &= look[i] == ref[i]; else diff |= look[i] != ref[i];
                }
                if (blink == 0 && diff) moved[q]++;
            }
        }
        check(gazeOk, "o olhar mexe só nos olhos (e sem desvio a skin é igual)");
        check(moved[0] >= 40 && moved[1] >= 40 && moved[2] >= 10 && moved[3] >= 5, "a maioria dos estilos de olhos acompanha o olhar (esq " + moved[0] + ", dir " + moved[1] + ", cima " + moved[2] + ", baixo " + moved[3] + ")");
        CharacterData badEye = CharacterData.fromInts("Novo", "", new int[]{1, 1, 1, 0, 0, 0, 1, 0, 0, 25, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, CharacterOptions.EYE_STYLES});
        check(!badEye.isValid(), "estilo de olhos fora da faixa é recusado");

        // corpo sombreado e traje de partida
        check(CharacterOptions.OUTFITS == 6 && CharacterOptions.OUTFIT_DEFAULT == 5 && CharacterData.defaults().outfit == 5, "o traje de partida (roupa 5) é o padrão de quem começa");
        boolean bodyOpaque = true, noBlotch = true, starterDiffers = true;
        for (int g = 0; g < 2; g++) for (int sk = 0; sk < CharacterOptions.SKIN.length; sk++) for (int o = 0; o < CharacterOptions.OUTFITS; o++) {
            int[] px = SkinPainter.paint(CharacterData.fromInts("Ab", "", new int[]{sk, 1, 2, 0, 0, o, 1, 0, g, 25, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0}));
            int aw = g == 1 ? 3 : 4;
            // todo pixel da camada base do corpo (cabeça, tronco, braços, pernas) está pintado
            int[][] base = {{0, 0, 8, 8, 8}, {16, 16, 8, 12, 4}, {40, 16, aw, 12, 4}, {32, 48, aw, 12, 4}, {0, 16, 4, 12, 4}, {16, 48, 4, 12, 4}};
            for (int[] b : base) {
                int u = b[0], v = b[1], w = b[2], h = b[3], d = b[4];
                int[][] faces = {{u + d, v, w, d}, {u + d + w, v, w, d}, {u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h}, {u + 2 * d + w, v + d, w, h}};
                for (int[] f : faces) for (int y = f[1]; y < f[1] + f[3]; y++) for (int x = f[0]; x < f[0] + f[2]; x++) bodyOpaque &= (px[y * 64 + x] >>> 24) == 255;
            }
            // pele nua do tronco (estilo 5 tem o torso quase todo à mostra no masculino): só tons da própria cor de pele, sem pixel "estranho" (cicatriz, mancha)
            if (o == 5 && g == 0) {
                int skinRgb = CharacterOptions.SKIN[sk];
                for (int y = 20; y < 26; y++) for (int x = 20; x < 28; x++) {   // peito e abdômen (frente do tronco, linhas 0 a 5)
                    int c = px[y * 64 + x] & 0xFFFFFF;
                    int dr = ((c >> 16) & 255) - ((skinRgb >> 16) & 255), dg = ((c >> 8) & 255) - ((skinRgb >> 8) & 255), db = (c & 255) - (skinRgb & 255);
                    noBlotch &= Math.abs(dr) <= 105 && Math.abs(dg) <= 105 && Math.abs(db) <= 105;      // nunca fica longe da cor escolhida (sombra funda e mancha rosada entram na conta)
                }
            }
        }
        check(bodyOpaque, "corpo todo pintado (sem buraco) em todos os tons, gêneros e roupas");
        check(noBlotch, "sombreado e manchas do peito e abdômen ficam perto da cor de pele escolhida (nada de pixel estranho)");
        int[] m5 = SkinPainter.paint(CharacterData.fromInts("Ab", "", new int[]{3, 1, 2, 0, 0, 5, 1, 0, 0, 25, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0}));
        int[] f5 = SkinPainter.paint(CharacterData.fromInts("Ab", "", new int[]{3, 1, 2, 0, 0, 5, 1, 0, 1, 25, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0}));
        int cloth = 0xFF000000 | CharacterOptions.OUTFIT_COLOR[1];
        check(near(m5[31 * 64 + 20], cloth, 70) && near(m5[22 * 64 + 4], cloth, 70) && !near(m5[27 * 64 + 4], cloth, 30), "traje de partida masculino cobre a cintura e a coxa (e deixa a perna de fora abaixo)");
        boolean femTop = false, femBottom = false;
        for (int x = 20; x < 28; x++) { femTop |= near(f5[(20 + 2) * 64 + x], cloth, 70); femBottom |= near(f5[(20 + 11) * 64 + x], cloth, 70); }
        check(femTop && femBottom, "traje de partida feminino cobre o peito e a parte de baixo");
        int[] same = SkinPainter.paint(CharacterData.fromInts("Ab", "", new int[]{3, 1, 2, 0, 0, 5, 1, 0, 0, 25, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0}));
        check(java.util.Arrays.equals(m5, same), "a skin é sempre igual para os mesmos dados (nada aleatório)");

        // rascunho
        Path dir = Files.createTempDirectory("draft");
        Path f = dir.resolve("config").resolve("draft.properties");
        check(CharacterDraft.load(f) == null, "sem arquivo: sem rascunho");
        CharacterData d = CharacterData.fromInts("Ana", "O mar é meu lar.", new int[]{3, 4, 2, 1, 2, 3, 4, 2, 1, 33, 3, 4, 0, 1, 2, 3, 4, 0});
        CharacterDraft.save(f, new CharacterDraft(d, 0b10111, 0b00101, 5, 0));
        CharacterDraft back = CharacterDraft.load(f);
        check(back != null && back.data.equals(d) && back.visited == 0b10111 && back.touched == 0b00101 && back.tab == 5, "rascunho grava e volta igual (com acentos)");
        CharacterDraft partial = new CharacterDraft(CharacterData.fromInts("A", "", d.ints()), 1, 0, 0, 0);
        CharacterDraft.save(f, partial);
        check(CharacterDraft.load(f).data.name.equals("A"), "nome ainda curto (sendo digitado) volta como está");
        Files.writeString(f, "version=2\nname=Ok\nv=1,2,3,99,0,0,0,0\n");
        check(CharacterDraft.load(f) == null, "índice fora da faixa: rascunho descartado");
        Files.writeString(f, "isto nao e um rascunho \u0000\u0001");
        check(CharacterDraft.load(f) == null, "arquivo corrompido: sem rascunho");
        Files.writeString(f, "version=2\nname=Ok\nv=1,2,x\n");
        check(CharacterDraft.load(f) == null, "número inválido: sem rascunho");
        Files.writeString(f, "version=2\nname=Ok\u00A7\u00A7\nmotto=a\u00A7b\nv=1,1,1,0,0,0,1,0\n");
        CharacterDraft c = CharacterDraft.load(f);
        check(c != null && c.data.name.isEmpty() && c.data.motto.isEmpty(), "nome com caractere proibido e lema inválido são limpos");
        CharacterDraft.delete(f);
        check(!Files.exists(f), "rascunho apagado ao selar");

        // destino da alma continua igual
        check(CharacterDestiny.of(new int[]{2, 2, 2, 2, 2, 2}).balanced(), "alma equilibrada");
        check(CharacterDestiny.of(new int[]{0, 2, 2, 2, 2, 4}).key().equals("soul.0.0"), "alma: primeiro eixo mais forte vence o empate");
        check(!CharacterDestiny.of(new int[]{0, 4, 0, 4, 0, 3}).legendary(), "alma: um traço fora do máximo não é lendária");
        CharacterDestiny leg = CharacterDestiny.of(new int[]{0, 4, 0, 4, 0, 4});
        check(leg.legendary() && leg.key().equals("soul.legend") && leg.strengthKey().equals("soul.legend.strength"), "alma lendária: todos no máximo");
        java.util.Random jr = new java.util.Random(7);
        int jackpots = 0, rolls = 20000;
        java.util.Set<String> combos = new java.util.HashSet<>();
        for (int i = 0; i < rolls; i++) {
            int[] t = CharacterOptions.rollTraits(jr);
            for (int v : t) check(v >= 0 && v < CharacterOptions.TRAIT_STEPS, "rollTraits dentro da faixa");
            CharacterDestiny dd = CharacterDestiny.of(t);
            check(dd.strengthVariant >= 0 && dd.strengthVariant < CharacterDestiny.VARIANTS && dd.flawVariant >= 0 && dd.flawVariant < CharacterDestiny.VARIANTS, "variantes na faixa");
            check(dd.strengthKey().equals(CharacterDestiny.of(t.clone()).strengthKey()), "mesmos traços, mesmo texto");
            if (dd.legendary()) jackpots++;
            combos.add(dd.key() + "|" + dd.strengthKey() + "|" + dd.flawKey());
        }
        check(jackpots > rolls * 0.015 && jackpots < rolls * 0.06, "jackpot raro (~3%): " + jackpots + "/" + rolls);
        check(combos.size() > 100, "muitas combinações de alma diferentes: " + combos.size());
        // epíteto: opcional, até 24, sem § nem controle, sem espaço nas pontas; volta no rascunho; registros antigos ficam sem
        check(CharacterOptions.validEpithet("") && CharacterOptions.validEpithet("Lobo do Mar"), "epíteto vazio e normal são válidos");
        check(!CharacterOptions.validEpithet("x".repeat(25)) && !CharacterOptions.validEpithet(" a") && !CharacterOptions.validEpithet("a\u00A7b") && !CharacterOptions.validEpithet(null), "epíteto grande, com espaço na ponta, § ou nulo é recusado");
        CharacterData withEpi = CharacterData.fromInts("Luffy", "", "Lobo do Mar", CharacterData.defaults().ints());
        check(withEpi.isValid() && withEpi.epithet.equals("Lobo do Mar") && !withEpi.equals(CharacterData.defaults().withName("Luffy")), "personagem com epíteto é válido e diferente");
        check(CharacterData.fromInts("Luffy", "", "x".repeat(30), CharacterData.defaults().ints()).isValid() == false, "epíteto grande invalida o personagem (servidor recusa)");
        check(withEpi.withName("Zoro").epithet.equals("Lobo do Mar"), "trocar o nome mantém o epíteto");
        Path fe = Files.createTempFile("draft_epi", ".properties");
        CharacterDraft.save(fe, new CharacterDraft(withEpi, 1, 1, 0, 0));
        check(CharacterDraft.load(fe).data.epithet.equals("Lobo do Mar"), "rascunho guarda o epíteto");
        Files.writeString(fe, "version=2\nname=Ok\nv=1,1,1,0,0,0,1,0\n");
        check(CharacterDraft.load(fe).data.epithet.isEmpty(), "rascunho antigo sem epíteto volta sem epíteto");
        // adereços do rosto: máscara de bits, no máximo 5, só bits conhecidos; vai e volta no rascunho, nos números e em registros antigos
        check(CharacterOptions.validAdorn(0) && CharacterOptions.validAdorn(1 | 4 | 2048) && CharacterOptions.validAdorn(31), "adereços: 0, 3 e 5 marcados valem");
        check(!CharacterOptions.validAdorn(63) && !CharacterOptions.validAdorn(1L << CharacterOptions.FACE_ITEMS) && !CharacterOptions.validAdorn(-1), "adereços: 6 marcados, bit desconhecido e negativo são recusados");
        int[] av = CharacterData.defaults().ints();
        av[21] = 1 | 16 | 128;
        CharacterData withAd = CharacterData.fromInts("Luffy", "", "", av);
        check(withAd.isValid() && withAd.adorn == (1 | 16 | 128) && withAd.ints()[21] == withAd.adorn, "personagem com 3 adereços é válido e guarda a máscara");
        av[21] = 63;
        check(!CharacterData.fromInts("Luffy", "", "", av).isValid(), "personagem com 6 adereços é recusado (servidor)");
        Path fa = Files.createTempFile("draft_ad", ".properties");
        CharacterDraft.save(fa, new CharacterDraft(withAd, 1, 1, 0, 0));
        check(CharacterDraft.load(fa).data.adorn == withAd.adorn, "rascunho guarda os adereços");
        Files.writeString(fa, "version=2\nname=Ok\nv=1,1,1,0,0,0,1,0\n");
        check(CharacterDraft.load(fa).data.adorn == 0, "rascunho antigo (sem adereços) volta sem adereços");
        {   // porte: altura em centésimos de bloco (150 a 250, padrão 180; a largura acompanha) e busto 0..100; registros antigos ficam no padrão
            int[] old32 = new int[32];
            System.arraycopy(CharacterData.defaults().ints(), 0, old32, 0, 32);
            CharacterData od32 = CharacterData.fromInts("Teste", "", "", old32);
            check(od32.isValid() && od32.height == CharacterOptions.HEIGHT_DEFAULT && od32.bust == CharacterOptions.BUST_DEFAULT, "registro antigo (sem porte) fica com a altura de 1,8 bloco e o busto padrão");
            int[] v = CharacterData.defaults().ints();
            check(v[32] == 180 && v[33] == 0 && v[34] == 100, "padrão: altura 1,8 bloco (180), número reservado da largura 0 e busto 100");
            v[32] = 250; v[34] = 0;
            check(CharacterData.fromInts("Teste", "", "", v).isValid(), "altura de 2,5 blocos e busto 0 são válidos");
            v[32] = 150;
            check(CharacterData.fromInts("Teste", "", "", v).isValid(), "altura de 1,5 bloco é válida");
            v[32] = 251;
            check(!CharacterData.fromInts("Teste", "", "", v).isValid(), "altura acima de 2,5 blocos é recusada");
            v[32] = 79;
            check(!CharacterData.fromInts("Teste", "", "", v).isValid(), "altura abaixo do mínimo é recusada");
            v[32] = 0;
            check(!CharacterData.fromInts("Teste", "", "", v).isValid(), "altura 0 é recusada (não vira o mínimo)");
            v[32] = -40;
            check(!CharacterData.fromInts("Teste", "", "", v).isValid(), "altura negativa é recusada");
            v[32] = 180; v[34] = 101;
            check(!CharacterData.fromInts("Teste", "", "", v).isValid(), "busto acima do atual (100) é recusado");
            // registros antigos guardavam a altura em % do padrão (80 a 140, 100 = 1,8 bloco) e a largura em v[33]: a altura vira bloco e a largura antiga é ignorada
            int[] lv = CharacterData.defaults().ints();
            int[] legacyIn = {100, 80, 140, 90, 120, 149}, legacyOut = {180, 150, 250, 162, 216, 250};
            boolean conv = true;
            for (int i = 0; i < legacyIn.length; i++) {
                lv[32] = legacyIn[i]; lv[33] = 140;
                CharacterData lg = CharacterData.fromInts("Teste", "", "", lv);
                if (!lg.isValid() || lg.height != legacyOut[i] || lg.ints()[33] != 0) { conv = false; System.out.println("  altura antiga " + legacyIn[i] + "% deu " + lg.height); }
            }
            check(conv, "altura antiga em % vira centésimos de bloco (100% = 1,8; 80% = 1,5 no mínimo; 140% = 2,5 no máximo) e a largura antiga some");
            lv[32] = 150; lv[33] = 0;
            check(CharacterData.fromInts("Teste", "", "", lv).height == 150, "150 já é em blocos (1,5): não é convertido de novo");
            // a conversão é idempotente: salvar e ler de novo não muda nada
            lv[32] = 120; lv[33] = 100;
            CharacterData once = CharacterData.fromInts("Teste", "", "", lv);
            check(CharacterData.fromInts("Teste", "", "", once.ints()).height == once.height, "ler de novo o que já foi convertido não muda a altura");
        }
        check(CharacterData.defaults().adorn == 0 && CharacterData.INT_COUNT == 27, "padrão sem adereços; 27 números por personagem");
        // cor dos lábios: 0 = clássico (igual ao de sempre); outras cores mudam o batom e o brilho; fora da faixa é recusada
        check(CharacterData.defaults().lip == 0, "cor dos lábios padrão: clássico");
        int[] lv = CharacterData.defaults().ints(); lv[3] = CharacterOptions.HAIR_STYLE_DEFAULT; lv[21] = 1 << CharacterOptions.LIP_BIT_STICK;
        int[] lipA = SkinPainter.paint(CharacterData.fromInts("x", "", "", lv)); lv[24] = 4;
        check(!java.util.Arrays.equals(lipA, SkinPainter.paint(CharacterData.fromInts("x", "", "", lv))), "a cor dos lábios muda o batom");
        long glossBit = 1L << CharacterOptions.LIP_BIT_GLOSS;
        lv[21] = (int) (glossBit & 0x7FFFFFFFL); lv[23] = (int) (glossBit >>> 31); lv[24] = 0;
        int[] glA = SkinPainter.paint(CharacterData.fromInts("x", "", "", lv)); lv[24] = 5;
        check(!java.util.Arrays.equals(glA, SkinPainter.paint(CharacterData.fromInts("x", "", "", lv))), "a cor dos lábios muda o brilho labial");
        lv[24] = CharacterOptions.LIPS;
        check(!CharacterData.fromInts("Luffy", "", "", lv).isValid(), "cor dos lábios fora da faixa é recusada");
        // 5 itens marcados valem; as orelhas aparecem nas duas laterais da cabeça e acompanham o metal
        check(CharacterOptions.validAdorn((1L << 47) | (1L << 48) | (1L << 49) | (1L << 50) | (1L << 52)), "5 piercings de orelha ao mesmo tempo valem");
        int[] ev = CharacterData.defaults().ints(); ev[3] = CharacterOptions.HAIR_STYLE_DEFAULT;
        int[] earNone = SkinPainter.paint(CharacterData.fromInts("x", "", "", ev));
        long earBit = 1L << CharacterOptions.EAR_BIT_BASE;
        ev[21] = (int) (earBit & 0x7FFFFFFFL); ev[23] = (int) (earBit >>> 31);
        int[] earOn = SkinPainter.paint(CharacterData.fromInts("x", "", "", ev));
        boolean rightSide = false, leftSide = false;
        for (int i = 0; i < earOn.length; i++) if (earOn[i] != earNone[i]) { int x = i % 64, y = i / 64; if (y >= 8 && y < 16 && x < 8) rightSide = true; if (y >= 8 && y < 16 && x >= 16 && x < 24) leftSide = true; }
        check(rightSide && leftSide, "a argola aparece nas duas orelhas");
        int[] sv = ev.clone(); sv[22] = 0; sv[25] = 1;
        int[] onlyLeft = SkinPainter.paint(CharacterData.fromInts("x", "", "", sv));
        boolean lr = false, ll = false;
        for (int i = 0; i < onlyLeft.length; i++) if (onlyLeft[i] != earNone[i]) { int x = i % 64, y = i / 64; if (y >= 8 && y < 16 && x < 8) lr = true; if (y >= 8 && y < 16 && x >= 16 && x < 24) ll = true; }
        check(ll && !lr, "orelha só a esquerda pinta só a lateral esquerda");
        sv[25] = 2;
        int[] onlyRight = SkinPainter.paint(CharacterData.fromInts("x", "", "", sv));
        lr = false; ll = false;
        for (int i = 0; i < onlyRight.length; i++) if (onlyRight[i] != earNone[i]) { int x = i % 64, y = i / 64; if (y >= 8 && y < 16 && x < 8) lr = true; if (y >= 8 && y < 16 && x >= 16 && x < 24) ll = true; }
        check(lr && !ll, "orelha só a direita pinta só a lateral direita");
        sv[25] = CharacterOptions.EAR_SIDES;
        check(!CharacterData.fromInts("x", "", "", sv).indexesValid(), "lado da orelha fora da faixa é recusado");
        long industrial = 1L << (CharacterOptions.EAR_BIT_BASE + 6);
        sv[25] = 0; sv[21] = (int) (industrial & 0x7FFFFFFFL); sv[23] = (int) (industrial >>> 31);
        check(CharacterData.fromInts("x", "", "", sv).indexesValid() && !java.util.Arrays.equals(SkinPainter.paint(CharacterData.fromInts("x", "", "", sv)), earNone), "o transversal é válido e aparece");
        ev[22] = 1;                                                    // metal dos piercings do rosto: não mexe nas orelhas
        check(java.util.Arrays.equals(earOn, SkinPainter.paint(CharacterData.fromInts("x", "", "", ev))), "o metal dos piercings do rosto não muda a cor dos brincos");
        ev[22] = 0; ev[26] = 1; ev[36] = 1;                            // metal das orelhas: as duas em ouro (v[26] a esquerda, v[36] a direita)
        int[] earGold = SkinPainter.paint(CharacterData.fromInts("x", "", "", ev));
        check(!java.util.Arrays.equals(earOn, earGold), "o metal das orelhas muda a cor dos brincos");
        CharacterData goldEars = CharacterData.fromInts("x", "", "", ev);
        check(CharacterData.INT_COUNT == 37 && ev.length == 37 && goldEars.ints().length == 37, "37 números por personagem (o metal da orelha direita é o último)");
        check(CharacterData.defaults().earMetalL == 0 && CharacterData.defaults().earMetalR == 0 && goldEars.earMetalL == 1 && goldEars.earMetalR == 1 && goldEars.indexesValid(), "metal das orelhas: padrão prata nas duas, dourado é válido e guardado em cada uma");
        ev[26] = CharacterOptions.METALS;
        check(!CharacterData.fromInts("x", "", "", ev).isValid(), "metal da orelha esquerda fora da faixa é recusado");
        ev[26] = 1; ev[36] = CharacterOptions.METALS;
        check(!CharacterData.fromInts("x", "", "", ev).isValid(), "metal da orelha direita fora da faixa é recusado");
        ev[36] = -1;
        check(!CharacterData.fromInts("x", "", "", ev).isValid(), "metal da orelha direita negativo é recusado");
        ev[36] = 1; ev[22] = 1;
        check(java.util.Arrays.equals(earGold, SkinPainter.paint(CharacterData.fromInts("x", "", "", ev))), "orelhas douradas ficam iguais com o rosto em ouro ou prata");
        // um metal para cada orelha: cada lateral da cabeça sai no metal da sua orelha, e nada mais na skin muda
        ev[22] = 0; ev[26] = 1; ev[36] = 0;
        int[] goldSilver = SkinPainter.paint(CharacterData.fromInts("x", "", "", ev));          // esquerda dourada, direita prateada
        ev[26] = 0; ev[36] = 1;
        int[] silverGold = SkinPainter.paint(CharacterData.fromInts("x", "", "", ev));          // esquerda prateada, direita dourada
        ev[26] = 0; ev[36] = 0;
        int[] earSilver = SkinPainter.paint(CharacterData.fromInts("x", "", "", ev));           // as duas prateadas (o mesmo que earOn)
        check(java.util.Arrays.equals(earSilver, earOn), "orelhas prateadas nas duas: igual à skin de antes (prata era o padrão)");
        check(sameRegion(goldSilver, earGold, 16, 8, 8, 8) && !sameRegion(goldSilver, earGold, 0, 8, 8, 8), "esquerda dourada e direita prateada: a esquerda é como a das duas douradas e a direita não");
        check(sameRegion(goldSilver, earSilver, 0, 8, 8, 8) && !sameRegion(goldSilver, earSilver, 16, 8, 8, 8), "esquerda dourada e direita prateada: a direita é como a das duas prateadas e a esquerda não");
        check(sameRegion(silverGold, earGold, 0, 8, 8, 8) && !sameRegion(silverGold, earGold, 16, 8, 8, 8), "esquerda prateada e direita dourada: a direita é como a das duas douradas e a esquerda não");
        check(sameRegion(silverGold, earSilver, 16, 8, 8, 8) && !sameRegion(silverGold, earSilver, 0, 8, 8, 8), "esquerda prateada e direita dourada: a esquerda é como a das duas prateadas e a direita não");
        check(diffOutsideEars(goldSilver, earSilver) == 0 && diffOutsideEars(silverGold, earSilver) == 0 && diffOutsideEars(earGold, earSilver) == 0, "o metal das orelhas só mexe nas duas laterais da cabeça");
        check(!java.util.Arrays.equals(goldSilver, silverGold), "metais trocados entre as orelhas dão skins diferentes");
        // o metal de uma orelha sem item não aparece (nada para pintar), mesmo dourado: a skin é a mesma
        int[] noItems = ev.clone(); noItems[21] = 0; noItems[23] = 0; noItems[26] = 1; noItems[36] = 1;
        int[] noItemsSilver = noItems.clone(); noItemsSilver[26] = 0; noItemsSilver[36] = 0;
        check(java.util.Arrays.equals(SkinPainter.paint(CharacterData.fromInts("x", "", "", noItems)), SkinPainter.paint(CharacterData.fromInts("x", "", "", noItemsSilver))), "sem brinco nenhum o metal das orelhas não muda a skin");
        // brinco só na orelha esquerda, com a direita em outro metal: a lateral direita continua limpa
        int[] oneEar = ev.clone(); oneEar[28] = 1; oneEar[29] = 0; oneEar[21] = 0; oneEar[23] = 0; oneEar[26] = 1; oneEar[36] = 0;
        int[] oneEarSkin = SkinPainter.paint(CharacterData.fromInts("x", "", "", oneEar));
        check(sameRegion(oneEarSkin, earNone, 0, 8, 8, 8) && !sameRegion(oneEarSkin, earNone, 16, 8, 8, 8), "brinco só na esquerda (dourado): só a lateral esquerda muda, mesmo com a direita em outro metal");
        // ida e volta: o que sai de ints() volta igual, e a direita faz diferença na igualdade
        ev[26] = 1; ev[36] = 0;
        CharacterData rt = CharacterData.fromInts("x", "", "", ev);
        check(rt.ints()[26] == 1 && rt.ints()[36] == 0 && rt.earMetalL == 1 && rt.earMetalR == 0 && java.util.Arrays.equals(rt.ints(), CharacterData.fromInts("x", "", "", rt.ints()).ints()), "metal de cada orelha: ida e volta pelos números");
        ev[36] = 1;
        check(!rt.equals(CharacterData.fromInts("x", "", "", ev)) && rt.equals(CharacterData.fromInts("x", "", "", rt.ints())), "o metal da orelha direita conta na igualdade do personagem");
        ev[26] = 1; ev[36] = 1;
        int[] oldEar = java.util.Arrays.copyOf(ev, 26); oldEar[22] = 1;      // registro antigo (26 números): as orelhas seguem o metal que já tinham
        CharacterData oldEarData = CharacterData.fromInts("x", "", "", oldEar);
        check(oldEarData.earMetalL == 1 && oldEarData.earMetalR == 1 && oldEarData.ints().length == CharacterData.INT_COUNT, "registro de 26 números: o metal das duas orelhas é o mesmo dos piercings");
        oldEar[22] = 0;
        oldEarData = CharacterData.fromInts("x", "", "", oldEar);
        check(oldEarData.earMetalL == 0 && oldEarData.earMetalR == 0, "registro de 26 números com prata: orelhas em prata");
        int[] oldEar36 = java.util.Arrays.copyOf(ev, 36); oldEar36[22] = 0; oldEar36[26] = 1;   // registro de antes do metal de cada orelha (36 números): um metal só, o do v[26]
        oldEarData = CharacterData.fromInts("x", "", "", oldEar36);
        check(oldEarData.earMetalL == 1 && oldEarData.earMetalR == 1 && oldEarData.indexesValid() && oldEarData.ints().length == CharacterData.INT_COUNT, "registro de 36 números (metal único das orelhas, dourado): as duas orelhas ficam douradas");
        check(java.util.Arrays.equals(SkinPainter.paint(oldEarData), earGold), "registro de 36 números dourado: a skin sai igual à das duas orelhas douradas (nada muda para quem já criou)");
        oldEar36[22] = 1; oldEar36[26] = 0;                                  // o metal do rosto não conta quando o das orelhas já existia
        oldEarData = CharacterData.fromInts("x", "", "", oldEar36);
        check(oldEarData.earMetalL == 0 && oldEarData.earMetalR == 0, "registro de 36 números: manda o metal das orelhas, não o do rosto");
        int[] oldEar27 = java.util.Arrays.copyOf(ev, 27); oldEar27[26] = 1;  // 27 números: o metal único das orelhas já existia (v[26]), o da direita não
        oldEarData = CharacterData.fromInts("x", "", "", oldEar27);
        check(oldEarData.earMetalL == 1 && oldEarData.earMetalR == 1, "registro de 27 números: a orelha direita segue o metal único");
        ev[22] = 0; ev[26] = 0; ev[36] = 0;
        check(CharacterData.defaults().metal == 0, "metal padrão: prata");
        int[] mv = CharacterData.defaults().ints(); mv[22] = 1;
        check(CharacterData.fromInts("Luffy", "", "", mv).isValid() && CharacterData.fromInts("Luffy", "", "", mv).metal == 1, "piercings dourados são válidos e guardados");
        mv[22] = 2;
        check(!CharacterData.fromInts("Luffy", "", "", mv).isValid(), "metal desconhecido é recusado");
        int[] mtl = CharacterData.defaults().ints(); mtl[21] = 1 | 2 | 4; mtl[3] = CharacterOptions.HAIR_STYLE_DEFAULT; mtl[20] = 0;
        int[] silverPx = SkinPainter.paint(CharacterData.fromInts("x", "", "", mtl)); mtl[22] = 1;
        check(!java.util.Arrays.equals(silverPx, SkinPainter.paint(CharacterData.fromInts("x", "", "", mtl))), "o metal muda a cor dos piercings na skin");
        java.util.Set<Integer> inClasses = new java.util.TreeSet<>();
        for (int[] cls : CharacterOptions.ITEM_CLASS_ORDER) for (int b : cls) inClasses.add(b);
        check(inClasses.size() == CharacterOptions.FACE_ITEMS && inClasses.stream().allMatch(b -> b >= 0 && b < CharacterOptions.FACE_ITEMS), "as classes cobrem os itens do rosto, cada um uma vez");
        // faces x olhos: cada face mexe só onde deve, para QUALQUER estilo de olhos; adereços e animação nunca quebram
        java.lang.reflect.Method eyeTop = Class.forName("br.com.lasthorizon.akuma.character.SkinPainter").getDeclaredMethod("eyeTop", int.class, int.class);
        eyeTop.setAccessible(true);
        int badF = 0, smoke = 0;
        for (int gender = 0; gender < 2; gender++) for (int es = 0; es < CharacterOptions.EYE_STYLES; es++) {
            int tl = (int) eyeTop.invoke(null, es, 0), tr = (int) eyeTop.invoke(null, es, 1);
            if (tl < 0 || tl > 4 || tr < 0 || tr > 4) { badF++; System.out.println("eyeTop fora da faixa em " + es); }
            int[] base = CharacterData.defaults().ints();
            base[8] = gender; base[20] = es; base[3] = CharacterOptions.HAIR_STYLE_DEFAULT;     // careca: a testa inteira é pele
            int[] A = SkinPainter.paint(CharacterData.fromInts("x", "", "", base));
            for (int fs = 1; fs < CharacterOptions.FACE_STYLES; fs++) {
                int[] v = base.clone(); v[21] = 1 << (CharacterOptions.FACE_BIT_BASE + fs);
                int[] B = SkinPainter.paint(CharacterData.fromInts("x", "", "", v));
                for (int i = 0; i < A.length; i++) {
                    if (A[i] == B[i]) continue;
                    int x = i % 64, y = i / 64;
                    boolean inFace = x >= 8 && x < 16 && y >= 8 && y < 16;
                    if (!inFace) { badF++; System.out.println("face " + fs + " pintou fora do rosto (" + x + "," + y + ") olhos " + es); continue; }
                    int row = y - 8, col = x - 8, top = col < 4 ? tl : tr;
                    if (fs == 1 && row >= top) { badF++; System.out.println("sobrancelha dentro/abaixo do olho: linha " + row + " topo " + top + " olhos " + es); }
                    if ((fs == 3 || fs == 5 || fs == 6) && row < 4) { badF++; System.out.println("face " + fs + " acima da linha 4: " + row + " olhos " + es); }
                }
            }
            for (int mask : new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 1 | 64 | 2048, 1 << 12 | 1 << 13 | 1 << 18, 1 << 12 | 1 << 17 | 1 << 4}) for (int blink = 0; blink < 3; blink++) for (int gx = -1; gx <= 1; gx++) for (int gy = -1; gy <= 1; gy += 2) {
                int[] v = base.clone(); v[21] = mask;
                int[] P = SkinPainter.paint(CharacterData.fromInts("x", "", "", v), blink, gx, gy);
                if (P.length != 64 * 64) badF++;
                smoke++;
            }
            // cada adereço muda o rosto (aparece de frente) e só ele mexe: sem ele a skin volta igual
            for (int k = 0; k < CharacterOptions.FACE_ITEMS; k++) {
                int[] v = base.clone(); v[21] = (int) ((1L << k) & 0x7FFFFFFFL); v[23] = (int) ((1L << k) >>> 31);
                int[] B = SkinPainter.paint(CharacterData.fromInts("x", "", "", v));
                boolean adDiffers = !java.util.Arrays.equals(A, B);
                if (!adDiffers) { badF++; System.out.println("adereço " + k + " não aparece com olhos " + es); }
            }
        }
        for (int q = 0; q < CharacterOptions.PIERCE_EXTRA; q++) for (int es : new int[]{0, 4, 17, 44, 49, 51}) {
            int[] pv = CharacterData.defaults().ints(); pv[3] = CharacterOptions.HAIR_STYLE_DEFAULT; pv[20] = es;
            int[] none = SkinPainter.paint(CharacterData.fromInts("x", "", "", pv));
            pv[21] = 1 << (CharacterOptions.PIERCE_BIT_BASE + q);
            CharacterData pd = CharacterData.fromInts("Luffy", "", "", pv);
            if (!pd.isValid() || java.util.Arrays.equals(none, SkinPainter.paint(pd))) { badF++; System.out.println("piercing extra " + q + " não aparece/inválido com olhos " + es); }
        }
        check(badF == 0, "faces e adereços combinam com os " + CharacterOptions.EYE_STYLES + " estilos de olhos (" + smoke + " skins de teste), problemas: " + badF);
        // faces e adereços são uma lista só: o estilo de rosto antigo vira o bit dele; 3 itens de qualquer tipo valem, 4 não
        int[] ov = CharacterData.defaults().ints();
        ov[4] = 3;
        CharacterData oldFace = CharacterData.fromInts("Luffy", "", "", ov);
        check(oldFace.isValid() && oldFace.faceStyle == 0 && oldFace.adorn == (1 << (CharacterOptions.FACE_BIT_BASE + 3)), "registro antigo com rosto 3 (sardas) vira o bit dele");
        ov[21] = 1 | 2 | 4 | 8;
        check(CharacterData.fromInts("Luffy", "", "", ov).isValid(), "rosto antigo + 4 adereços = 5 itens, válido");
        ov[21] = 1 | 2 | 4 | 8 | 16;
        check(!CharacterData.fromInts("Luffy", "", "", ov).isValid(), "rosto antigo + 5 adereços = 6 itens, recusado");
        ov[4] = 9; ov[21] = 0;
        check(!CharacterData.fromInts("Luffy", "", "", ov).isValid(), "estilo de rosto fora da faixa continua recusado");
        check(CharacterOptions.validAdorn((1 << 12) | (1 << 13) | (1 << 5)) && !CharacterOptions.validAdorn(1L << CharacterOptions.FACE_ITEMS), "2 faces + 1 adereço valem; bit desconhecido não");
        // itens novos (bits 26 a 46): máscara longa guardada em dois números, registros antigos continuam valendo, cada item cabe numa classe
        {
            long hi = (1L << 46) | (1L << 40) | (1L << 31);
            int[] nv = CharacterData.defaults().ints();
            nv[21] = (int) (hi & 0x7FFFFFFFL); nv[23] = (int) (hi >>> 31);
            CharacterData nd = CharacterData.fromInts("Luffy", "", "", nv);
            check(nd.isValid() && nd.adorn == hi && java.util.Arrays.equals(nd.ints(), nv), "3 itens novos (bits 31, 40 e 46) são válidos e voltam iguais pelos dois números");
            check(!CharacterOptions.validAdorn(1L << CharacterOptions.FACE_ITEMS) && !CharacterOptions.validAdorn(-1L) && CharacterOptions.validAdorn(1L << (CharacterOptions.FACE_ITEMS - 1)), "bit 61 é o último; 62 e negativo são recusados");
            int[] ov23 = java.util.Arrays.copyOf(CharacterData.defaults().ints(), 23);
            ov23[21] = 1 | (1 << 25);
            CharacterData od = CharacterData.fromInts("Luffy", "", "", ov23);
            check(od.isValid() && od.adorn == (1L | (1L << 25)) && od.ints().length == CharacterData.INT_COUNT, "registro de 23 números (antes dos itens novos) continua válido e igual");
            nv[23] = -5;
            check(!CharacterData.fromInts("Luffy", "", "", nv).isValid(), "parte alta negativa vinda da rede é recusada");
            java.util.Set<Integer> seenBits = new java.util.HashSet<>();
            int most = 0;
            for (int[] cls : CharacterOptions.ITEM_CLASS_ORDER) { most = Math.max(most, cls.length); for (int b : cls) seenBits.add(b); }
            check(most <= 18 && seenBits.size() == CharacterOptions.FACE_ITEMS, "nenhuma classe passa de 18 itens (3 fileiras) e todos os " + CharacterOptions.FACE_ITEMS + " itens aparecem na tela");
            // cada item novo, sozinho e para TODOS os estilos de olhos: muda só o rosto (frente da cabeça), nunca um pixel de olho, e aparece
            int badN = 0;
            java.lang.reflect.Method eyeTopM = Class.forName("br.com.lasthorizon.akuma.character.SkinPainter").getDeclaredMethod("eyeTop", int.class, int.class);
            eyeTopM.setAccessible(true);
            for (int gender = 0; gender < 2; gender++) for (int es = 0; es < CharacterOptions.EYE_STYLES; es++) for (int skinI = 0; skinI < CharacterOptions.SKIN.length; skinI += 5) {
                int[] b0 = CharacterData.defaults().ints();
                b0[8] = gender; b0[20] = es; b0[0] = skinI; b0[3] = CharacterOptions.HAIR_STYLE_DEFAULT;
                int[] none = SkinPainter.paint(CharacterData.fromInts("x", "", "", b0));
                int[] noneEyesClosed = SkinPainter.paint(CharacterData.fromInts("x", "", "", b0), 2, 0, 1);
                for (int e = 0; e < CharacterOptions.EXTRA_COUNT; e++) {
                    long m = 1L << (CharacterOptions.EXTRA_BIT_BASE + e);
                    int[] v = b0.clone(); v[21] = (int) (m & 0x7FFFFFFFL); v[23] = (int) (m >>> 31);
                    int[] A = SkinPainter.paint(CharacterData.fromInts("x", "", "", v));
                    int diff = 0;
                    for (int i = 0; i < A.length; i++) {
                        if (A[i] == none[i]) continue;
                        diff++;
                        int x = i % 64, y = i / 64;
                        final boolean ear = e >= CharacterOptions.EAR_BIT_BASE - CharacterOptions.EXTRA_BIT_BASE;      // brincos e piercings de orelha pintam nas laterais da cabeça
                        final boolean onSide = y >= 8 && y < 16 && ((x >= 0 && x < 8) || (x >= 16 && x < 24));
                        if ((x < 8 || x >= 16 || y < 8 || y >= 16) && !(ear && onSide)) { badN++; System.out.println("item novo " + e + " pintou fora do rosto (" + x + "," + y + ")"); }
                    }
                    if (diff == 0) { badN++; System.out.println("item novo " + e + " não aparece (olhos " + es + ", pele " + skinI + ", gênero " + gender + ")"); }
                    // o mesmo item com o olho fechado: os pixels que mudam de um estado do olho para o outro são só os do olho (a marca não dança)
                    int[] C = SkinPainter.paint(CharacterData.fromInts("x", "", "", v), 2, 0, 1);
                    for (int i = 0; i < C.length; i++) {
                        boolean markDiffersFromBase = A[i] != none[i];
                        boolean markDiffersClosed = C[i] != noneEyesClosed[i];
                        if (markDiffersFromBase != markDiffersClosed) { badN++; System.out.println("item novo " + e + " muda com o piscar no pixel " + (i % 64) + "," + (i / 64) + " (olhos " + es + ")"); break; }
                    }
                }
            }
            // cicatrizes do corpo: cada uma sozinha aparece, não mexe na cabeça nem na camada externa, só pinta pele nua (roupa intacta) e vale em todos os tons e nos dois gêneros
            int badB = 0;
            for (int gender = 0; gender < 2; gender++) for (int skinI = 0; skinI < CharacterOptions.SKIN.length; skinI++) {
                int[] b0 = CharacterData.defaults().ints();
                b0[8] = gender; b0[0] = skinI; b0[3] = CharacterOptions.HAIR_STYLE_DEFAULT;
                int[] none = SkinPainter.paint(CharacterData.fromInts("x", "", "", b0));
                for (int b = 0; b < CharacterOptions.BODY_SCAR_COUNT; b++) {
                    long m = 1L << (CharacterOptions.BODY_SCAR_BASE + b);
                    int[] v = b0.clone(); v[21] = (int) (m & 0x7FFFFFFFL); v[23] = (int) (m >>> 31);
                    CharacterData cd = CharacterData.fromInts("Luffy", "", "", v);
                    if (!cd.isValid() || cd.adorn != m) { badB++; System.out.println("cicatriz de corpo " + b + " inválida"); continue; }
                    int[] A = SkinPainter.paint(cd);
                    int diff = 0;
                    for (int i = 0; i < A.length; i++) {
                        if (A[i] == none[i]) continue;
                        diff++;
                        int x = i % 64, y = i / 64;
                        if (y < 16) { badB++; System.out.println("cicatriz de corpo " + b + " pintou a cabeça (" + x + "," + y + ")"); break; }
                        boolean outer = (y >= 32 && y < 48 && x < 56) || (y >= 48 && x < 16) || (y >= 48 && x >= 48);   // camada externa (casaco, mangas, calças)
                        if (outer) { badB++; System.out.println("cicatriz de corpo " + b + " pintou a camada externa (" + x + "," + y + ")"); break; }
                    }
                    if (diff == 0) { badB++; System.out.println("cicatriz de corpo " + b + " não aparece (pele " + skinI + ", gênero " + gender + ")"); }
                    // com o traje de pirata (camisa e calça) cobrindo o corpo, a cicatriz do peito não pinta a camisa
                    if (b == 0) {
                        int[] bov = v.clone(); bov[5] = 0;
                        int[] ovNone = b0.clone(); ovNone[5] = 0;
                        int[] shirt = SkinPainter.paint(CharacterData.fromInts("x", "", "", bov)), shirtNone = SkinPainter.paint(CharacterData.fromInts("x", "", "", ovNone));
                        for (int y = 20; y < 32; y++) for (int x = 20; x < 28; x++) if (shirtNone[y * 64 + x] != none[y * 64 + x] && shirt[y * 64 + x] != shirtNone[y * 64 + x]) { badB++; System.out.println("cortes no peito pintaram por cima da roupa (" + x + "," + y + ")"); y = 99; break; }
                    }
                }
            }
            check(badB == 0, "as " + CharacterOptions.BODY_SCAR_COUNT + " cicatrizes do corpo aparecem em todos os tons e gêneros, só no corpo nu, sem tocar na cabeça (problemas: " + badB + ")");
            check(badN == 0, "os " + CharacterOptions.EXTRA_COUNT + " itens novos aparecem, só mexem no rosto e não dançam com o piscar em todos os olhos, peles e gêneros (problemas: " + badN + ")");
        }
        // cicatrizes e sobrancelha nunca mudam o desenho dos olhos: o pixel de olho fica igual com e sem a marca (olho vs olho)

        // busto menor (BreastLayer): a caixa do peito NÃO encolhe (largura, altura e fundo do modelo); só fica menos inclinada e afunda no peitoral
        {
            final float ANG = 37.0F, REACH = 4.0F, DEPTH = 3.0F;                  // o modelo (BreastLayer.BASE): 37 graus, 4 de altura, 3 de fundo
            float[] box100 = BustShape.retract(ANG, REACH, DEPTH, 1.0F);
            check(box100[0] == ANG && box100[1] == 0.0F, "busto 100%: o peito é o do modelo (ângulo do modelo e nada afundado)");
            float model = BustShape.protrusion(ANG, REACH, 0.0F);                  // quanto a ponta de baixo sai no modelo (2,41 px)
            boolean prop = true, propLow = true, angOk = true, monoAng = true, monoOut = true, sinkOk = true, backOk = true, deepOk = true, tiltOk = true, upOk = true;
            float prevAng = 999, prevOut = 999;
            for (int pct = 99; pct >= 5; pct--) {
                float s = pct / 100.0F;
                float[] q = BustShape.retract(ANG, REACH, DEPTH, s);
                float out = BustShape.protrusion(q[0], REACH, q[1]);
                if (pct >= 50) prop &= Math.abs(out - model * s) < 1e-3;                                      // de 50% a 99%: a ponta de baixo sai s vezes o que sai no modelo
                else propLow &= Math.abs(out - model * 0.5F * (float) Math.sqrt(s / 0.5F)) < 1e-3;           // abaixo de 50%: a raiz do busto, a partir da saliência de 50%
                angOk &= q[0] > 0 && q[0] < ANG;                                                              // menos inclinada que o modelo, mas nunca deitada de vez
                monoAng &= q[0] < prevAng; prevAng = q[0];
                monoOut &= out < prevOut; prevOut = out;                                                      // sai menos a cada 1% a menos
                tiltOk &= q[0] > ANG * (float) Math.sqrt(s) && q[0] <= ANG * (float) Math.pow(s, 0.3) + 1e-3; // entre a curva antiga (raiz de s, mais deitada) e a da versão s^0,3 (mais inclinada)
                upOk &= out >= model * s - 1e-3;                                                              // nunca sai menos do que a regra proporcional antiga
                sinkOk &= q[1] >= 0;
                backOk &= -2 + DEPTH * Math.cos(Math.toRadians(q[0])) + q[1] <= 2;                    // o canto de trás não passa das costas (frente do corpo em z = -2, costas em 2)
                float[] deep = BustShape.retract(ANG, REACH, 3.9F, s);
                deepOk &= -2 + 3.9F * Math.cos(Math.toRadians(deep[0])) + deep[1] <= 2;
            }
            check(prop, "busto de 50% a 99%: a ponta de baixo do peito sai do corpo exatamente busto x o que sai no modelo");
            check(propLow, "busto de 5% a 49%: a ponta de baixo sai a raiz do busto (a partir de 50%), sem a trava das costas segurar");
            check(upOk, "busto menor: a ponta sai pelo menos o que saía com a regra proporcional (o 25% e o 10% não ficam lisos)");
            check(angOk && monoAng, "busto menor: o peito fica menos inclinado (e menos a cada 1%), nunca deitado de vez");
            check(monoOut, "busto menor: sai menos do corpo a cada 1% a menos, de 99% até 5%");
            check(tiltOk, "busto menor: o ângulo fica entre a curva antiga (raiz de s, mais deitada) e a versão s^0,3 (mais inclinada, e pontuda em 25% e 10%), de 5% a 99%");
            float[] q50 = BustShape.retract(ANG, REACH, DEPTH, 0.5F), q25 = BustShape.retract(ANG, REACH, DEPTH, 0.25F), q10 = BustShape.retract(ANG, REACH, DEPTH, 0.1F), q5 = BustShape.retract(ANG, REACH, DEPTH, 0.05F);
            float[] q75 = BustShape.retract(ANG, REACH, DEPTH, 0.75F);
            check(Math.abs(q75[0] - 33.9F) < 0.3F && Math.abs(q50[0] - 30.1F) < 0.3F && Math.abs(q25[0] - 22.0F) < 0.3F && Math.abs(q10[0] - 14.6F) < 0.3F && Math.abs(q5[0] - 10.7F) < 0.3F,
                    "busto menor: ângulos de 75% (34°), 50% (30°), 25% (22°), 10% (14,6°) e 5% (10,7°)");
            float o75 = BustShape.protrusion(q75[0], REACH, q75[1]), o50 = BustShape.protrusion(q50[0], REACH, q50[1]), o25 = BustShape.protrusion(q25[0], REACH, q25[1]);
            float o10 = BustShape.protrusion(q10[0], REACH, q10[1]), o5 = BustShape.protrusion(q5[0], REACH, q5[1]);
            check(Math.abs(o75 - 1.81F) < 0.02F && Math.abs(o50 - 1.20F) < 0.02F && Math.abs(o25 - 0.85F) < 0.02F && Math.abs(o10 - 0.54F) < 0.02F && Math.abs(o5 - 0.38F) < 0.02F,
                    "busto menor: o que a ponta de baixo sai do corpo em 75% / 50% / 25% / 10% / 5% (1,81 / 1,20 / 0,85 / 0,54 / 0,38 px)");
            // 50% e acima ficam exatamente como na versão anterior (só o peito pequeno mudou): s^0,3 no ângulo e saliência proporcional
            boolean sameTop = true;
            for (int pct = 50; pct <= 99; pct++) {
                float s = pct / 100.0F, prevAngle = ANG * (float) Math.pow(s, 0.3F);
                float[] q = BustShape.retract(ANG, REACH, DEPTH, s);
                float prevSink = REACH * (float) Math.sin(Math.toRadians(prevAngle)) - model * s;
                sameTop &= Math.abs(q[0] - prevAngle) < 1e-4 && Math.abs(q[1] - prevSink) < 1e-4;
            }
            check(sameTop, "busto de 50% a 99%: o mesmo ângulo e o mesmo afundamento da versão anterior (só abaixo de 50% mudou)");
            // as duas curvas se encontram em 50% sem salto
            float[] qa = BustShape.retract(ANG, REACH, DEPTH, 0.4999F), qb = BustShape.retract(ANG, REACH, DEPTH, 0.5F);
            check(Math.abs(qa[0] - qb[0]) < 0.05F && Math.abs(qa[1] - qb[1]) < 0.02F, "busto: as curvas de cima e de baixo de 50% se encontram sem salto");
            // o peito pequeno se lê de frente: faixa escura de baixo (saliência x tangente do ângulo) e parte visível da frente (saliência / (altura x seno))
            float band25 = o25 * (float) Math.tan(Math.toRadians(q25[0])), band10 = o10 * (float) Math.tan(Math.toRadians(q10[0])), band5 = o5 * (float) Math.tan(Math.toRadians(q5[0]));
            float vis25 = o25 / (REACH * (float) Math.sin(Math.toRadians(q25[0]))), vis10 = o10 / (REACH * (float) Math.sin(Math.toRadians(q10[0]))), vis5 = o5 / (REACH * (float) Math.sin(Math.toRadians(q5[0])));
            check(band25 >= 0.30F && band10 >= 0.12F && band5 >= 0.06F, "peito pequeno: a faixa escura de baixo tem pelo menos 0,30 / 0,12 / 0,06 px em 25% / 10% / 5% (era 0,27 / 0,08 / 0,03)");
            check(vis25 >= 0.50F && vis10 >= 0.45F && vis5 >= 0.42F, "peito pequeno: aparece pelo menos 50% / 45% / 42% da frente da caixa em 25% / 10% / 5% (era 36% / 19% / 12%; com 47% / 34% / 26% ele ficava só uma ponta)");
            check(q25[1] <= 0.70F && q10[1] <= 0.55F && q5[1] <= 0.45F, "peito pequeno: afunda no máximo 0,70 / 0,55 / 0,45 px em 25% / 10% / 5% (não fica enterrado com só a pontinha de baixo de fora, que passava de 1 px)");
            check(o25 - o10 >= 0.25F && o10 - o5 >= 0.10F && q25[0] - q10[0] >= 2.0F && q10[0] - q5[0] >= 1.5F,
                    "peito pequeno: 25%, 10% e 5% continuam diferentes entre si (saliência e ângulo)");
            check(sinkOk, "busto menor: o peito afunda no corpo (nunca sai para fora do modelo)");
            check(backOk, "busto menor: o peito afundado não atravessa as costas do corpo");
            check(deepOk, "caixa mais funda que a do modelo: mesmo assim o peito afundado não atravessa as costas");
            float[] half = BustShape.retract(ANG, REACH, DEPTH, 0.5F);
            check(half[0] > ANG * 0.6F && half[0] < ANG && half[1] > 0 && Math.abs(BustShape.protrusion(half[0], REACH, half[1]) - model / 2) < 1e-3, "busto 50%: ângulo menor, afundado, e a ponta sai metade do que sai no modelo");
        }
        // texturas do peito (BreastLayer): nenhuma face estica um desenho da skin (colunas e linhas de largura desigual = pixel duplicado)
        {
            check(uvOk(new float[]{28, 24, 24, 25}, 4, 3) && uvOk(new float[]{24, 20, 28, 24}, 4, 4) && uvOk(new float[]{24, 20, 25, 24}, 3, 4),
                    "regra das texturas: tamanho exato da face, ou só uma linha / coluna repetida, vale");
            check(!uvOk(new float[]{27, 24, 24, 26}, 4, 3) && !uvOk(new float[]{24, 20, 27, 22}, 4, 4),
                    "regra das texturas: 3 colunas x 2 linhas esticadas em 4 x 3 (a face de baixo antiga) e 3 colunas em 4 pixels são recusadas");
            Path src = Paths.get(System.getProperty("lh.root", "."), "src/main/java/br/com/lasthorizon/akuma/client/BreastLayer.java");
            if (!Files.exists(src)) {
                System.out.println("ok (pulado): BreastLayer.java não encontrado em " + src);
            } else {
                String text = new String(Files.readAllBytes(src), java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "new Box\\(\\s*([-0-9.Ff,\\s]+?),\\s*new float\\[\\]\\{([^}]*)\\},\\s*new float\\[\\]\\{([^}]*)\\},\\s*new float\\[\\]\\{([^}]*)\\},"
                                + "\\s*new float\\[\\]\\{([^}]*)\\},\\s*new float\\[\\]\\{([^}]*)\\}\\)").matcher(text);
                final String[] face = {"frente", "leste", "oeste", "topo", "baixo"};
                int boxes = 0;
                boolean allOk = true, frontExact = true;
                StringBuilder stretched = new StringBuilder();
                while (m.find()) {
                    boxes++;
                    float[] g = nums(m.group(1));                                  // x0, y0, z0, x1, y1, z1, pivô y, pivô z, ângulo, folga
                    float w = g[3] - g[0], h = g[4] - g[1], dp = g[5] - g[2];
                    float[][] size = {{w, h}, {dp, h}, {dp, h}, {w, dp}, {w, dp}};     // (horizontal, vertical) da imagem de cada face: frente, leste, oeste, topo, baixo
                    for (int fi = 0; fi < 5; fi++) {
                        float[] uv = nums(m.group(2 + fi));
                        if (!uvOk(uv, size[fi][0], size[fi][1])) { allOk = false; stretched.append(" caixa ").append(boxes).append(" ").append(face[fi]).append(Arrays.toString(uv)); }
                        if (fi == 0 && !(Math.abs(Math.abs(uv[2] - uv[0]) - w) < 1e-3 && Math.abs(Math.abs(uv[3] - uv[1]) - h) < 1e-3)) frontExact = false;
                    }
                }
                check(boxes == 4, "BreastLayer: as 4 caixas do peito (2 da base e 2 do casaco) foram lidas (" + boxes + ")");
                check(allOk, "peito: nenhuma face estica um desenho da skin (nada de pixel duplicado)" + stretched);
                check(frontExact, "peito: a frente lê 4 x 4 pixels da skin em 4 x 4 do modelo (1 pixel = 1 pixel)");
            }
        }
        // altura (BodyScale): o corpo inteiro cresce junto, na mesma proporção (a largura acompanha a altura)
        {
            final int lo = CharacterOptions.HEIGHT_MIN, hi = CharacterOptions.HEIGHT_MAX, def = CharacterOptions.HEIGHT_DEFAULT;
            check(lo == 150 && hi == 250 && def == 180, "altura: de 1,5 a 2,5 blocos, padrão 1,8 (em centésimos de bloco)");
            check(BodyScale.of(def) == 1.0F && BodyScale.isStandard(def) && !BodyScale.isStandard(lo) && !BodyScale.isStandard(hi), "altura padrão: escala 1 exata (o modelo do jogo, sem ajuste nenhum)");
            boolean blocksOk = true, grows = true;
            StringBuilder badSc = new StringBuilder();
            for (int h = lo; h <= hi; h++) {
                float sc = BodyScale.of(h);
                if (Math.abs(1.8F * sc - h / 100.0F) > 1e-4F) { blocksOk = false; badSc.append(' ').append(h); }
                if (h > lo && !(sc > BodyScale.of(h - 1))) grows = false;
            }
            check(blocksOk, "a hitbox em pé (1,8 x escala) dá exatamente a altura escolhida em blocos, de 1,5 a 2,5" + badSc);
            check(grows, "mais altura nunca deixa o personagem menor");
            check(Math.abs(BodyScale.of(lo) - 1.5F / 1.8F) < 1e-6F && Math.abs(BodyScale.of(hi) - 2.5F / 1.8F) < 1e-6F, "escala de 0,83x (1,5 bloco) a 1,39x (2,5 blocos)");
            check(BodyScale.of(0) == BodyScale.of(lo) && BodyScale.of(-50) == BodyScale.of(lo) && BodyScale.of(Integer.MIN_VALUE) == BodyScale.of(lo)
                    && BodyScale.of(1000) == BodyScale.of(hi) && BodyScale.of(Integer.MAX_VALUE) == BodyScale.of(hi), "altura fora da faixa é presa nos limites (nunca NaN nem infinito)");
            // contraprova: a conta antiga (largura, altura, largura no modelo todo) deixava a perna mais de 20% mais comprida no meio do passo com 1,4x de largura; com a escala igual nos três eixos não muda
            double oldLen = Math.sqrt(Math.pow(12 * Math.cos(Math.toRadians(45)) * 1.0, 2) + Math.pow(12 * Math.sin(Math.toRadians(45)) * 1.4, 2));
            double newLen = Math.sqrt(Math.pow(12 * Math.cos(Math.toRadians(45)) * BodyScale.of(hi), 2) + Math.pow(12 * Math.sin(Math.toRadians(45)) * BodyScale.of(hi), 2));
            check(oldLen > 12 * 1.2 && Math.abs(newLen - 12 * BodyScale.of(hi)) < 1e-6, "contraprova: a conta antiga esticava a perna no meio do passo (" + Math.round(oldLen * 10) / 10.0 + " em vez de 12); com a escala única o comprimento só muda com a altura");
            // o mixin, a hitbox, o retrato e os textos usam a mesma conta (e a largura não existe mais)
            String root = System.getProperty("lh.root", ".");
            Path mixin = Paths.get(root, "src/main/java/br/com/lasthorizon/akuma/mixin/PlayerRendererMixin.java");
            Path events = Paths.get(root, "src/main/java/br/com/lasthorizon/akuma/character/CharacterEvents.java");
            Path map = Paths.get(root, "src/main/java/br/com/lasthorizon/akuma/client/CharacterMap.java");
            Path ptJson = Paths.get(root, "src/main/resources/assets/lasthorizon/lang/pt_br.json");
            Path enJson = Paths.get(root, "src/main/resources/assets/lasthorizon/lang/en_us.json");
            if (!Files.exists(mixin) || !Files.exists(events) || !Files.exists(map) || !Files.exists(ptJson) || !Files.exists(enJson)) {
                System.out.println("ok (pulado): fontes e textos não encontrados em " + root);
            } else {
                java.nio.charset.Charset u8 = java.nio.charset.StandardCharsets.UTF_8;
                String tm = new String(Files.readAllBytes(mixin), u8), te = new String(Files.readAllBytes(events), u8), tc = new String(Files.readAllBytes(map), u8);
                String pt = new String(Files.readAllBytes(ptJson), u8), en = new String(Files.readAllBytes(enJson), u8);
                check(tm.contains("BodyScale.of(d.height)") && tm.contains("pose.scale(s, s, s)") && !tm.contains("d.width") && !tm.contains(".xScale") && !tm.contains(".yScale") && !tm.contains(".zScale"),
                        "mixin do modelo: uma escala só, igual nos três eixos, vinda do BodyScale (nada de largura nem de escala por peça)");
                check(te.contains("BodyScale.of(d.height)") && te.contains("scale(s, s)") && !te.contains("d.width"), "hitbox (CharacterEvents.onSize): largura e altura com a mesma escala do BodyScale");
                check(tc.contains("BodyScale.of(height)") && !tc.contains("d.width") && !tc.contains("SIZE_DEFAULT") && !tc.contains("SIZE_MIN"), "tela: o retrato usa o BodyScale e não sobrou nada da largura nem das faixas antigas");
                check(pt.contains("\"character.lasthorizon.size.0\": \"Altura\"") && pt.contains("\"character.lasthorizon.size.1\": \"Busto\"") && !pt.contains("lasthorizon.size.2") && !pt.contains("\"Largura\"")
                        && en.contains("\"character.lasthorizon.size.0\": \"Height\"") && en.contains("\"character.lasthorizon.size.1\": \"Bust\"") && !en.contains("lasthorizon.size.2") && !en.contains("\"Width\""),
                        "textos: só Altura e Busto (a largura saiu) nos dois idiomas");
                check(pt.contains("de 1,5 a 2,5") && pt.contains("1,8") && en.contains("from 1.5 to 2.5") && en.contains("1.8"), "a dica da altura diz a faixa em blocos (1,5 a 2,5) e o padrão (1,8)");
            }
        }
        System.out.println(fails == 0 ? "TUDO OK" : fails + " falha(s)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
