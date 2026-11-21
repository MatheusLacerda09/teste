# Sea Sovereign

Base inicial para **Minecraft Java 1.20.1 + Forge 47.2.0 + Java 17**.

## O que já está preparado
- Item `Gomu Gomu no Mi`, com pilha máxima de 1.
- Textura pixel art própria em `assets/lasthorizon/textures/item/gomu_fruit.png`.
- Nome e dicas em português brasileiro e inglês.
- O item pode ser consumido como comida e mostra uma mensagem ao terminar.
- Estrutura Java com registro do item.

## O que ainda não está implementado
Esta é a primeira versão do item, não um mod completo com todos os poderes. O soco elástico, Gatling, efeitos visuais, obtenção em baús e salvamento permanente do poder ainda precisam ser programados.

## Como abrir e testar
1. Instale **Java JDK 17**.
2. Baixe o MDK do Minecraft Forge **1.20.1 - 47.2.0** e use-o como base de desenvolvimento, ou configure o Gradle deste projeto com ForgeGradle.
3. Abra a pasta no IntelliJ IDEA ou Eclipse como projeto Gradle.
4. Execute a configuração `runClient`.
5. No mundo de teste, use `/give @p lasthorizon:gomu_fruit` para obter a fruta.

Para gerar o `.jar`, execute `gradlew build` (ou `./gradlew build` no Linux/macOS) depois de configurar o wrapper Gradle conforme a instalação do Forge MDK. O arquivo compilado aparecerá em `build/libs/`.

## Criação de personagem
Na **primeira vez** que um jogador entra num mundo, abre a tela "Crie seu personagem": um mapa de pergaminho com dois rolos nas laterais.
- **Bandeiras** hasteadas na borda esquerda (Identidade, Corpo, Cabelo, Roupa, Alma, Passado, Sonho) e a bandeira xadrez do **Aleatório**. Cada bandeira é pixel art feita em código (`MapArt.flag`): ondula ao vento (mais forte na escolhida e sob o mouse), tem dobras que andam com a onda, barra dourada, ponta de formato próprio e mastro com argolas de latão. A pedra de estado fica no alto do mastro:
  cinza (não visitada), dourada (visitada), vermelha (falta algo obrigatório: só o nome) e verde (concluída = o jogador fez ao menos uma escolha nela).
  A **barra de progresso** no alto do painel da esquerda mostra as sete de uma vez (clicar leva à aba). O balão com nome, descrição e estado só aparece depois do mouse ficar parado **15 segundos** sobre a bandeira (`FLAG_TIP_DELAY_MS` em `CharacterMap.java`); a bandeira reage na hora (estica e ondula mais).
- **Navegar:** setas nas pontas da faixa de título, `Page Up` / `Page Down`, `Ctrl + 1..7`. **Limpar aba:** seta curva ao lado do título ou botão direito na aba.
- **Aleatório:** sorteia aparência, personalidade, idade, mar natal e sonho (e um nome, se estiver vazio). Gênero e lema não mudam. `Shift` + clique: só a aparência.
- **Retrato:** arraste para girar (duplo clique volta de frente), roda do mouse ou botões `+` `-` para aproximar/afastar, botão de seta curva para ver de costas. A cabeça segue o mouse.
- **Identidade:** sub-abas Nome (contador, avisos ao vivo, dado de nome aleatório, gênero) e Epíteto (opcional, até 24 letras, dado e 6 sugestões para clicar). Com epíteto, a plaqueta do retrato mostra o epíteto no lugar de "Seu futuro começa aqui."; ele aparece também no resumo e em `/sheet`.
- **Corpo sombreado:** a pele deixou de ser uma cor chapada. `character/BodyShading.java` dá volume e músculos (peitoral, abdômen, ombros, braços, pernas; mais suave no feminino), no estilo das skins de autor: sombras rosadas (nunca cinza), luz de pêssego, manchas rosadas e claras suaves (ruído determinístico em 3D, `PxNoise.java`) mais fortes em cotovelos, joelhos e ombros, bochechas e orelhas, em qualquer tom da paleta (a cor escolhida continua sendo a cor base). Roupas ganham luz de cilindro e vincos, o cabelo ganha mechas e tufos na camada externa e o rosto ganha nariz, queixo e pálpebra sombreados. Cada face é um mapa de texto (`H h . s S` = mais claro ... mais escuro), fácil de ajustar. As roupas que deixam a pele à mostra (mãos, pescoço, pernas) devolvem a pele já sombreada.
- **Traje de partida (roupa 5, o padrão):** peças simples na cor da roupa; entra no fim da lista para não mexer em personagens salvos. A descrição na tela é neutra de propósito ("Peças simples e discretas, na cor da roupa.").
- **Corpo:** sub-abas Pele / Íris (cor dos olhos) / Olhos (15 estilos de olhos) / Rosto (8 faces + até 5 itens). **Faces** (Padrão, Determinado, Cicatriz, Sardas, Marcas de expressão, Pintura facial, Cicatriz na bochecha, Olhar cansado) se medem pelo desenho do estilo de olhos escolhido (`SkinPainter.eyeTop`): sobrancelhas e cicatriz sobem ou descem com o olho, marcas ficam acima/abaixo dele, nunca cobrem o desenho do olho nem a franja do cabelo (só pele nua). **Adereços** (12: piercing na sobrancelha, no nariz, argola no septo, piercing no lábio, batom, esparadrapo no nariz, curativo na bochecha, joia na testa [cor dos olhos], lágrima pintada, bochechas coradas, pinta, purpurina): seleção múltipla, no máximo 5 ao mesmo tempo (`CharacterOptions.ADORN_MAX`); Batom e Brilho labial têm cor própria (`CharacterData.lip`, índice 24) e a classe Orelhas (bits 47 a 52) pinta brincos nas laterais da cabeça, cada orelha com a sua lista e o seu metal, prata ou dourado (`CharacterData.earMetalL`, índice 26, e `earMetalR`, índice 36); guardados numa máscara de bits (`CharacterData.adorn`, índice 21), pintados na camada externa da cabeça por `SkinPainter.adornments`. Adereço novo: some 1 em `ADORNMENTS` (só no fim), acrescente o `case` em `adornments` e os textos `adorn.N` / `adorn.N.desc` nos arquivos de idioma. Os estilos de olhos pintam só os olhos (3 linhas do rosto, `SkinPainter.EYE_STYLE_GRID`, letras `k w I i c h`), seguem a cor da íris e o tom da pele, e a barba nunca os cobre. Nomes em `character.lasthorizon.eye_style.N` (0 Padrão, 1 Pontinhos, 2 Gotas, 3 Cílios, 4 Intensos, 5 Felinos, 6 Asas, 7 Marcantes, 8 Amendoados, 9 Grandes, 10 Dramáticos, 11 Sonolentos, 12 Brilhantes, 13 Arregalados, 14 Contorno). Para criar um estilo novo: acrescente uma linha ao fim de `EYE_STYLE_GRID`, some 1 em `CharacterOptions.EYE_STYLES` e escreva nome e descrição nos arquivos de idioma. **Cabelo:** sub-abas Cabelo (cor e 8 estilos) / Barba (nenhuma, bigode, cavanhaque, cheia; usa a cor do cabelo). **Roupa:** sub-abas Roupa / Acessório (nenhum, bandana, chapéu de pirata, tapa-olho, óculos, brinco). Cores com nome e descrição do estilo. São só visuais.
- **Sem Origem:** o jogador não escolhe vantagem na criação (decide o que quer ser depois de criar o personagem). Não há mais bônus de dano, armadura, velocidade ou vida; `CharacterAttributes` só remove os bônus antigos de quem tinha. A posição da origem continua reservada (valor 0) no formato dos dados, então personagens salvos antes continuam válidos.
- **Alma:** o jogador não escolhe os traços: o botão **Girar a alma** sorteia os seis eixos (6 plaquinhas com ícone próprio; cada uma embaralha e assenta com um tique) e o painel revela o destino da alma (força e contradição). Pode girar de novo sem limite (nenhum limite foi definido). Só descreve personalidade: não muda atributos.
- **Passado:** idade, mar natal (dica com clima e curiosidade) e lema (com exemplos). **Sonho:** a ficha escolhida se abre com descrição e objetivo. Idade, mar e sonho são só narrativos.
- **Resumo da Jornada:** retrato e todas as escolhas; clicar numa linha volta à aba dela. Botões Revisar / Começar aventura (ESC volta, ENTER sela).
- **Sons e animações:** sons sintetizados do zero (`tools/build_ui_sounds.py`: virar página, escolha, dados, tique, sino, selo, negado, brilho) tocados só no cliente; páginas deslizam ao trocar de aba, abas saem do rolo com suavidade, anel dourado ao escolher, brilho ao concluir uma aba, cartão do resumo sobe, selo solta faíscas. Cada aba tem faixa de título na sua cor e marca-d'água do seu ícone.
- **Rascunho automático:** as escolhas são gravadas em `config/lasthorizon-character-draft.properties` (no computador do jogador) e voltam se o jogo fechar no meio. Apagado ao selar.
- Depois de selar o personagem fica salvo no próprio jogador, dentro do mundo: sobrevive a fechar o mundo, à morte e à troca de dimensão. **Mundo novo = criar de novo.**
- No servidor cada jogador (UUID) tem o seu personagem, e o servidor valida tudo (só aceita uma vez, nome e índices conferidos). O protocolo de rede é o "9" (cliente e servidor na mesma versão). Cores novas só entram no fim das paletas (`CharacterOptions`), então personagens antigos não mudam.
- Enquanto não finaliza, o jogador não toma dano (a tela de criação não fecha com ESC).
- Admin: `/character reset <player>` apaga o personagem e abre a criação de novo (útil para testar). Veja todos os comandos em "Comandos".
- Código: `character/` (dados, rascunho, pintor de skin, rede, eventos), `client/CharacterMap.java` (a interface), `client/MapArt.java` (arte), `client/MapIcons.java`, `client/MapFont.java`, `client/PlayerPreview.java` (boneco 3D), `client/CharacterCreationScreen.java` (campo do nome, mouse, teclado, rede, arquivo do rascunho).
- Testes sem o Minecraft: `tools/tests/run.sh` (lógica) e `tools/preview_java/run_map.sh <pasta> passo...` (renderiza a interface real em PNG).

## Organização das pastas
- `src/main/java/br/com/lasthorizon/akuma/` — código do mod (`character/`, `client/`, `curse/`, `effect/`, `item/`, `mixin/`, `registry/`).
- `src/main/resources/` — assets, dados, `mods.toml` e `lasthorizon.mixins.json`.
- `art/` — arte-fonte que não entra no jogo: `models/` (Blockbench `.bbmodel`) e `icons_32x32/` (ícones da criação de personagem).
- `tools/` — scripts que geram texturas/sons (`art/`, `build_*.py`), testes (`tests/`), preview fora do jogo (`preview_java/`) e versões antigas (`legacy/`).
- `docs/` — textos padrão do Forge MDK (README e créditos originais).
- Raiz: `README.md`, `changelog.txt`, `LICENSE.txt` e arquivos do Gradle.

## Comandos
Todos em inglês (código em `command/ModCommands.java`; as mensagens estão nos arquivos de idioma). "op" = permissão nível 2.
| Comando | Quem | O que faz |
|---|---|---|
| `/sheet` | todos | mostra a ficha do seu personagem |
| `/sheet <player>` | op | mostra a ficha de outro jogador |
| `/character reset <player>` | op | apaga o personagem e reabre a criação |
| `/devilfruit grant <player>` | op | marca o jogador como usuário da fruta (como se tivesse comido) |
| `/devilfruit revoke <player>` | op | remove a marca e os efeitos da maldição |
| `/devilfruit status <player>` | op | diz se o jogador tem a marca |

## Estrutura principal
- `LastHorizonMod.java`: entrada do mod.
- `registry/ModItems.java`: registro do item.
- `item/GomuGomuNoMiItem.java`: comportamento de consumo e tooltip.
- `assets/lasthorizon/models/item/gomu_fruit.json`: modelo do item.
- `assets/lasthorizon/textures/item/gomu_fruit.png`: textura.

## Maldição da fruta (v9)
Ao comer a Gomu Gomu no Mi o jogador fica marcado para sempre (inclusive depois de morrer):

- **Vertigem do Diabo** (`lasthorizon:dizzy`): tontura temporária que entorta a tela como a náusea, mas é UM efeito só (sem a Náusea do jogo junto). Vem ao comer a fruta (5 s) e depois passa. A tela é entortada por `client/DizzyScreenEffect.java`.
- **Maldição do Mar** (`lasthorizon:sea_curse`): aparece só na água, com duração infinita (não pisca) e some quando sai da água.
  - Nível I - metade do corpo (~1 bloco): lento.
  - Nível II - corpo todo (~2 blocos): bem mais lento, quase não consegue subir.
  - Nível III - mais de 2 blocos de profundidade: é puxado lentamente para o fundo.
  - Depois de 25 s seguidos no fundo (nível III) recebe só a Náusea normal do jogo (10 s), sem a Vertigem; repete a cada 25 s enquanto ficar lá.

Todos os números (profundidades, lentidão, velocidade de afundar, tempos) ficam nas constantes no topo de `curse/GomuCurse.java`.
Ícones animados: `assets/lasthorizon/textures/mob_effect/` (18x18, 6 frames cada, com `.png.mcmeta`).
Para testar: `/give @p lasthorizon:gomu_fruit`, comer em modo sobrevivência e entrar num lago.

## Conquistas
- **Sea Sovereign** (`lasthorizon:root`): sai assim que o jogador entra no mundo. É a aba do mod nas conquistas; o título é fixo ("Sea Sovereign") em qualquer idioma.
- **O Preço do Poder** (`lasthorizon:eat_devil_fruit`, desafio): sai ao comer qualquer item da tag `lasthorizon:devil_fruits` (`data/lasthorizon/tags/items/devil_fruits.json`). Para uma fruta nova valer, é só colocar o id dela nessa tag.
Textos em `lang/pt_br.json` e `lang/en_us.json` (`advancements.lasthorizon.*`).

## Janela de conquistas larga (400x230) e fundo (v2, 382x203 px)
A janela de conquistas do jogo (252x140) é alargada para 400x230 por Mixin (`akuma/mixin/*`, medidas em `client/WideAdvancements.java`); em telas pequenas ela encolhe sozinha. Para mudar o tamanho edite `MAX_W`/`MAX_H` em `WideAdvancements.java` (e, se passar de 400x230, `W, H` em `tools/ocean.py` + rodar `tools/build_backgrounds.py`).

### Fundo
`client/SeaBackground.java` desenha UM cenário só (céu, nuvens e mar em pixel art) que muda de cor aos poucos por 11 momentos: dia, céu azul, turquesa, tempestade, pôr do sol, sol grande, crepúsculo, noite violeta, lua cheia, via láctea e amanhecer.
Tem nuvens em 3 camadas que andam com o vento (parallax) e passam na frente do sol e da lua, lua com crateras, estrelas que cintilam, estrela cadente, via láctea, mar animado em loop, reflexo das nuvens, caminho de brilho que segue o sol/lua e pássaros.
Os PNGs em `assets/lasthorizon/textures/gui/seabg/` são máscaras geradas por `python3 tools/build_backgrounds.py` (requer numpy e Pillow); o Java pinta cada máscara com a cor do momento. Para mudar nuvens/mar edite `tools/ocean.py`; as cores de cada momento ficam em `MOODS` no mesmo arquivo (o script regera `SeaMoods.java`). Tempos e vento: `FRAME_MS`, `HOLD_MS`, `MORPH_MS`, `WIND_*` em `SeaBackground.java`.
Fade: `FADE_MS` (fade-in ao abrir a aba, 110 ms) e `FADE_OUT_MS` (fade-out ao fechar a tela, 110 ms; `0` desliga); `FADE_DARK` é o quão escuro fica (0,55, nunca tela preta) em `SeaBackground.java`. O fade-out é feito em `ClientSetup` (evento `ScreenEvent.Opening` adia o fechamento até o cenário escurecer).
**Blend:** no Minecraft 1.20.1 todo `GuiGraphics.fill()` desliga o blend do OpenGL; por isso `SeaBackground.tint()` religa o blend antes de todo blit. Sem isso as máscaras com transparência (raios de luz, brilho do sol, estrelas, reflexos) saem opacas: os raios viravam uma mancha chapada e a noite aparecia "de uma vez". As cores do dia/noite agora são misturadas em OKLab (transição mais limpa, sem cinza barrento).
O simulador `tools/preview_java` imita esse comportamento do blend (stub de `RenderSystem`/`GuiGraphics`), então o preview mostra o mesmo que o jogo.
Para ver o resultado sem abrir o jogo (`PREVIEW_SIZE=234x113` simula uma janela menor): `tools/preview_java/run.sh <pasta> <tempoMs>...` gera PNGs usando o `SeaBackground.java` real.

### Moldura dourada
`client/SeaFrame.java` desenha uma moldura dourada (mesmas cores pulsantes da borda do tooltip da Gomu Gomu no Mi, contorno roxo-escuro, cantos ornamentados, losangos no meio das bordas e um brilho que corre pelas bordas) por cima da moldura cinza, só na aba Sea Sovereign. É chamada por `AdvancementsScreenMixin` no fim de `renderWindow`. Os desenhos dos cantos e do ornamento do meio são as matrizes de caracteres `CORNER` e `MID` no topo do arquivo (G ouro, H luz, S sombra, D contorno).
As texturas do fundo são pré-carregadas no `FMLClientSetupEvent` (`ClientSetup`) para a aba abrir sem travar.

## Ícones das conquistas (v13, animados)
Os 5 ícones do texto das conquistas (fruta, poder, caveira, leme e bússola) têm 18x18 px, o mesmo tamanho dos ícones de efeito, e **6 frames** de animação: a fruta balança e brilha, o raio pulsa e solta faíscas, a caveira acende brasas nos olhos, o leme gira e a bússola balança a agulha.
- `assets/lasthorizon/textures/font/icons.png` (90x108): 5 colunas (ícones) x 6 linhas (frames). `font/icons.json` lista os 6 frames em `chars` (`\ue000-\ue004` = frame 0, `\ue005-\ue009` = frame 1, ...).
- Os JSONs das conquistas continuam usando só `\ue000-\ue004`. `mixin/FontIconAnimMixin.java` troca pelo frame atual na hora de desenhar (`client/IconAnim.java`: `FRAME_MS` = 150 ms por frame, `STAGGER` = defasagem entre ícones, 0 sincroniza todos).
- A arte é 18x18, mas é exibida com 9 px de altura (`height`/`ascent` em `font/icons.json`), alinhada ao texto; no GUI x2 fica 1 px de arte por pixel de tela.
- Nos JSONs das conquistas o primeiro item da descrição é um `{"text":""}` vazio e os ícones levam `"color":"white"`: itens de um array herdam o estilo do primeiro (sem isso o texto saía na fonte dos ícones, como quadradinhos) e a cor da conquista tingiria os ícones.
- Para editar os desenhos: `python3 tools/build_advancement_icons.py <saida.png> [previa.png]` (gera também um GIF de prévia). A fruta vem de `textures/item/gomu_fruit.png`.

## Preview da tela fora do jogo
`tools/preview_java/run_map.sh <pasta> passos...` compila o `CharacterMap` real com stubs do Minecraft e gera PNGs (simula
cliques). Ex.: `tools/preview_java/run_map.sh /tmp/prev name=Luffy shot=nome click=262,131 shot=feminino`.
Passos: `name=`, `click=x,y`, `mouse=x,y`, `sleep=ms`, `shot=arquivo`; `LANG_CODE=en_us` troca o idioma.
