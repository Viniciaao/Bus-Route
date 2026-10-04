# O que mudou em relação ao mod original

Referência: `Bus Route/cleo/Bus_Route_301(SannyBuilder).txt` (decompilado, ~6.000
linhas, de **Sinan**). O comportamento do jogo foi mantido de propósito: pontos de
espera, ônibus nascendo na parada anterior, embarque com **Y**, tarifa de **$2**,
anúncio da próxima parada, pausa do motorista nos terminais e volta ao começo se o
jogador se afastar.

## Código

| Mod original | Bus Route Evolved |
|---|---|
| Opcodes do Sanny Builder (`0001: wait`, `00A5: create_car 8@ = ...`) | gta3script (`WAIT`, `CREATE_CAR modelo ... onibus`) |
| Variáveis numeradas (`8@` = ônibus, `9@` = motorista, `14@` = espera) | Nomes (`onibus`, `motorista`, `espera`) |
| Blocos `goto`/`gosub_if_false` encadeados | `IF`/`ELSE`/`ENDIF`, `WHILE`, sub-rotinas nomeadas |
| 6.000 linhas em um arquivo | ~2.000 linhas geradas + modelo em `tools/generate_script.py` |
| Rota embutida no código | Rota em JSON (`data/routes/301_route.json`) |
| Sem blip | Ônibus marcado no mapa (`ADD_BLIP_FOR_CAR`) |

O gta3script não tem arrays úteis em um `.cs` (cada elemento de array ocupa uma
variável local, e o CLEO só tem 32), então os pontos de trajeto continuam sendo
literais no código gerado — a diferença é que eles vêm do JSON, não da mão.

Variáveis usadas: **24 das 32** disponíveis (12 `INT` + 12 `FLOAT`), sem `TIMERA/B`.

## Comportamento (correções e melhorias)

1. **Viagem em paradas distantes** — no original, o teste de abandono
   (`BUS_49589`, raio de 250 m) rodava assim que o ônibus nascia. Na parada 18 o
   ônibus nasce a ~347 m do jogador, então a viagem era cancelada na hora. Agora o
   abandono só vale depois que o ônibus chegou perto pelo menos uma vez (`perto`).
2. **Tempo limite por ponto de trajeto** — se o ônibus ficar preso, 30 s depois a
   viagem é encerrada em vez de esperar para sempre (`ESPERA_TIME_OUT`).
3. **Checagem de abandono** também olha o motorista (`IS_CHAR_DEAD`), não só o
   veículo.
4. **Encerramento único** — o original tinha a rotina `BUS_49589` (abandono), a
   `BUS_49928` (motorista morto) e uma cauda de código inalcançável depois dela;
   sobrou uma rotina só (`encerrar`), que tira o jogador do ônibus, remove o blip,
   marca o motorista como não mais necessário e apaga o carro.
5. **Tarefa de entrada** — o original reemitia `task_enter_car_as_passenger` a
   cada quadro durante a espera (`BUS_49262`); agora ela só é emitida quando o
   jogador está no raio de embarque **e** aperta **Y** (`IS_KEY_PRESSED`), que é
   exatamente o que o texto `ON_BUS` pede.
6. **Cobrança da passagem** — o original usava `12@` com três estados confusos
   (`BUS_49346`/`BUS_49401`); agora `embarque` tem três estados simples
   (0 = fora, 1 = entrou e paga, 2 = pagou) e volta a 0 quando o jogador desce.
7. **Anúncio** — mantido: só toca se o jogador está dentro do ônibus, como no
   original (onde o `gosub_if_false` dependia da última condição da sub-rotina).

## O que foi mantido igual

- Os 21 pontos, coordenadas, trajetos e textos (as coordenadas vieram do script
  original por `tools/import_legacy.py`, conferidas parada por parada).
- O sorteio entre os modelos **431** e **437** e a cor 1/1.
- A animação das portas em passos de **0.05 a cada 30 ms**.
- Os tempos: parada 5–10 s, terminal 25–40 s, pausa do motorista 20 s.
- O `DEUCE` no carregamento e os textos em português no `.fxt`.
