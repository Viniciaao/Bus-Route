# Formato do JSON de rota (`schemaVersion: 1`)

O arquivo descreve **uma linha** de ônibus. Ele é produzido pelo editor
(`Editor(Criação de Novas Linhas)/editor_de_linhas.html`) e consumido por
`tools/generate_script.py`, que gera o gta3script.

```jsonc
{
  "schemaVersion": 1,
  "id": "301",
  "name": "Rota 301 - The Deuce",
  "description": "texto livre (opcional)",

  "settings": {
    "fare": 2,                  // valor cobrado ao embarcar ($)
    "stopRadius": 20.0,         // raio (a pé) em que o jogador é reconhecido no ponto
    "boardRadius": 10.0,        // distância do ônibus em que a tecla Y embarca
    "arriveRadius": 3.0,        // raio usado para considerar que o ônibus "chegou"
    "abandonDistance": 250.0,   // se o jogador passar disso do ônibus, a viagem acaba
    "dwellTimeMin": 5000,       // parada normal: tempo sorteado entre min e max (ms)
    "dwellTimeMax": 10000,
    "layoverTimeMin": 25000,    // terminal: pausa do motorista (ms)
    "layoverTimeMax": 40000,
    "vehicleModels": [431, 437],// modelos sorteados (431 = Bus, 437 = Coach)
    "driverModel": 7,           // modelo do motorista (7 = MALE01)
    "colours": [1, 1],          // cor primária/secundária do ônibus
    "blip": true                // marca o ônibus no mapa
  },

  "stops": [
    {
      "index": 1,               // 1..N, na ordem da linha (o editor mantém)
      "textLabel": "TER_1",     // NOME da parada (etiqueta no .fxt, até 7 letras).
                                // É este texto que a parada ANTERIOR anuncia.
      "waitText": "BUS_T1",     // texto mostrado enquanto o jogador espera no ponto
      "x": 2139.093,            // ponto de espera (onde o ônibus para e o jogador embarca)
      "y": 1048.8521,
      "z": 11.0,
      "spawnHeading": 300.0,    // direção do ônibus quando ele NASCE nesta parada
                                // (0 = norte, 90 = oeste, 180 = sul, 270 = leste)
      "terminal": true,         // parada final: o motorista faz a pausa longa
      "doorsBoard": [4, 5],     // portas abertas na parada (4/5 = traseiras)
      "doorsExit": [3],         // portas usadas na saída (3 = dianteira direita)
      "layover": {              // só em terminais: onde o motorista descansa
        "x": 2164.8899, "y": 1078.26, "z": 11.597, "driverBreak": true
      },
      "leg": {                  // trajeto que o ônibus percorre
        "from": 21,             //   da parada 21 ...
        "to": 1,                //   ... até esta parada
        "waypoints": [          // pontos do caminho, em ordem
          { "x": 2162.4199, "y": 975.553, "z": 11.0, "speed": 10.0, "radius": 3.0 }
        ]
      }
    }
  ]
}
```

## Como o script usa esses dados

Para **cada** parada `k` (a linha é circular, então a última para `from` = `to` = 1):

1. **Espera**: o script verifica se o jogador está a menos de `stopRadius` de
   `(x, y)`; se estiver, mostra `waitText` e, 3 s depois, `ON_BUS`.
2. **Nascimento**: o ônibus nasce na parada **anterior** (`k-1`), usando o
   `x`/`y`/`spawnHeading` dela — é o comportamento do mod original: o ônibus
   aparece um ponto atrás e dirige até você.
3. **Trajeto**: percorre os `waypoints` do `leg` da parada `k`: cada ponto é um
   `CAR_GOTO_COORDINATES_ACCURATE` com a velocidade do ponto (5 = manobra,
   10 = rua, 15 = avenida). O último ponto deve ser o próprio ponto de espera.
4. **Parada**: espera 2 s, abre as portas (`doorsBoard` + `doorsExit`), sorteia o
   tempo de espera (`dwellTimeMin/Max`, ou `layoverTimeMin/Max` se `terminal`),
   cobra a passagem quando o jogador entra, fecha as portas, **anuncia**
   `stops[k+1].textLabel` (só se o jogador estiver no ônibus) e, em terminais,
   faz a pausa do motorista em `layover`.
5. **Fim da viagem**: `encerrar` — se o jogador se afastar mais de
   `abandonDistance` (depois de já ter chegado perto uma vez) ou se o
   ônibus/motorista morrer.

## Regras práticas

- O `leg` da primeira parada vem da última (`from` = N): o sentido da linha é a
  ordem das paradas.
- Um ponto de trajeto a cada curva; para ruas longas, um ponto a cada ~40–60
  unidades. Raio 3.0 funciona bem (é o raio de chegada).
- `z` deve ser o nível da rua (≈ 11.0 em Las Venturas). Não use o Z do telhado.
- Etiquetas de texto: até 7 caracteres, `A-Z0-9_`, e **precisam existir** no
  `cleo_text/Bus_PTBR.fxt`.
- Uma parada sem `waypoints` faz o ônibus ir direto ao ponto (útil para testar).
