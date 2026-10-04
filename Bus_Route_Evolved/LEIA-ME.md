# Bus Route Evolved

Reescrita em **gta3script** do mod **Bus Route** (linha 301, "The Deuce"), originalmente
escrito por **Sinan** em Sanny Builder (opcodes `0001:`/`0002:`).

O script novo faz o mesmo trajeto do mod antigo, mas com o código organizado em
sub-rotinas, nomes legíveis e as boas práticas do gta3script — e, principalmente,
com a rota separada do código: a linha é descrita em um **JSON** que pode ser
editado no **editor** (que também gera o `.sc` sozinho) e transformado em script
pelo gerador.

---

## Estrutura

```
Bus_Route_Evolved/
├── CLEO/                          <- copie isto para GTA San Andreas/CLEO/
│   ├── BUS301.cs                  (script compilado, pronto para o jogo)
│   └── cleo_text/
│       └── Bus_PTBR.fxt           (textos em português)
├── source/
│   └── BUS301.sc                  (fonte gta3script, gerada — não edite à mão)
├── data/routes/
│   └── 301_route.json             (dados da linha 301, extraídos do mod original)
├── tools/
│   ├── import_legacy.py           (converte o script antigo em JSON de rota)
│   ├── generate_script.py         (JSON -> gta3script)
│   └── build.sh                   (gera + compila todas as linhas)
├── docs/
│   ├── formato-da-rota.md         (referência do JSON)
│   └── diferencas-do-mod-original.md
└── Editor(Criação de Novas Linhas)/
    ├── editor_de_linhas.html      (editor de linhas — abra no navegador)
    └── exemplos/
        └── 301_route.json         (exemplo completo, para abrir no editor)
```

---

## Instalação no jogo

1. Tenha o **CLEO 4** (ou CLEO 5) instalado no GTA San Andreas.
2. Copie `CLEO/BUS301.cs` para `GTA San Andreas/CLEO/`.
3. Copie `CLEO/cleo_text/Bus_PTBR.fxt` para `GTA San Andreas/CLEO/cleo_text/`.
4. (Opcional, para os pontos de ônibus aparecerem no mapa) instale o `BUSSTOP.IPL`
   do mod original e a linha do `GTA.DAT`, como no `leiame` antigo.

**No jogo:** espere em um dos pontos (Downtown Las Vegas ou South Strip Transfer
Terminal). Chegando perto, aparece o aviso; o ônibus nasce na parada anterior e
vem até você. Quando ele parar e abrir as portas, aperte **Y** para embarcar — a
passagem custa **$2**. O ônibus segue a linha inteira em ciclo; se você se afastar
mais de 250 m (ou o ônibus/motorista morrer), a viagem termina e ele volta a
esperar passageiros nos pontos.

---

## Compilando

O gerador precisa do **gta3sc** (https://github.com/thelink2012/gta3sc) no PATH,
ou então aponte com a variável `GTA3SC`.

```bash
./tools/build.sh                       # gera e compila todas as rotas
GTA3SC=/caminho/gta3sc ./tools/build.sh
```

Para uma linha só:

```bash
python3 tools/generate_script.py data/routes/301_route.json -o source/BUS301.sc
gta3sc compile source/BUS301.sc --config=gtasa --guesser --cs -o CLEO/BUS301.cs
```

O `SCRIPT_NAME` do `.cs` vem do **nome do arquivo** `.sc` (em maiúsculas), então
`BUS301.sc` gera um script chamado `BUS301`.

---

## Criando uma linha nova

1. Abra `Editor(Criação de Novas Linhas)/editor_de_linhas.html` no navegador
   (funciona offline; o rascunho fica salvo no próprio navegador).
2. Preencha as configurações, adicione as paradas (ponto de espera + direção) e
   os **pontos de trajeto** de cada parada (o caminho que o ônibus percorre da
   parada anterior até ela). Os últimos pontos devem terminar **em cima do ponto
   de espera**.
3. Clique em **Baixar JSON** / **Copiar JSON** e envie o arquivo ao assistente
   (ele gera o `.sc` e compila), **ou** clique em **Gerar gta3script (.sc)** para
   pegar o fonte na hora e compilar com o `gta3sc`.
4. Coloque o `.cs` em `CLEO/` e os textos novos no `.fxt`.

O formato do JSON está documentado em [`docs/formato-da-rota.md`](docs/formato-da-rota.md).

### Sobre os textos (`.fxt`)

Cada parada usa uma etiqueta de texto de até 7 letras (ex.: `EXC`, `TER_1`) que
precisa existir no `Bus_PTBR.fxt`, no formato:

```
ETIQUETA Texto que aparece na tela.
```

O texto `NNN` da parada é o nome da parada; ele é anunciado quando o ônibus
**sai da parada anterior** ("A próxima parada é ..."). `BUS_T1`/`BUS_T2` são os
avisos de área mostrados para o jogador que está esperando no ponto e `ON_BUS` é
o aviso para apertar Y.

---

## Diferenças em relação ao mod original

Resumo (detalhes em [`docs/diferencas-do-mod-original.md`](docs/diferencas-do-mod-original.md)):

- código em **gta3script** (Sanny Builder 4/gta3sc), com sub-rotinas nomeadas e
  comentários, em vez de 6.000 linhas de opcodes;
- **rota como dados** (JSON) → dá para criar outras linhas sem tocar no código;
- variáveis com nome (`onibus`, `motorista`, `espera`, ...) e apenas 24 das 32
  variáveis locais do CLEO, sem arrays (o mod antigo usava `8@`, `9@`, `14@`);
- **blip** no ônibus no mapa (novo);
- fim do travamento quando o jogador pega o ônibus em paradas distantes: o
  abandono por distância só vale depois que o ônibus chegou perto uma vez;
- tempo limite por ponto de trajeto (30 s) para o ônibus não travar a viagem se
  ficar preso;
- portas animadas em passos de 0.05 a cada 30 ms, como no original, mas em uma
  sub-rotina só (`abrir_portas` / `fechar_portas`);
- a cauda inalcançável do script antigo (depois de `:BUS_49928`) foi removida e
  o encerramento da viagem virou uma rotina única (`encerrar`).

## Créditos

- **Sinan** — autor do mod original *Bus Route* (script, textos e a ideia da linha 301).
- **Junior_Djjr / MixMods** — índices e tutoriais de gta3script/CLEO que serviram de referência.
- **thelink2012** — compilador **gta3sc**.
