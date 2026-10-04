#!/usr/bin/env python3
"""
generate_script.py - Gera o codigo gta3script de uma linha de onibus a partir
do JSON de rota (data/routes/*.json).

Uso:
    python3 generate_script.py data/routes/301_route.json -o source/BUS301.sc
    python3 generate_script.py data/routes/301_route.json -o source/BUS301.sc --compile

O JSON de rota e produzido pelo editor (`Editor(Criacao de Novas Linhas)/`) ou
pelo `import_legacy.py` (conversao do mod antigo).

O script gerado NAO usa arrays: cada trecho e emitido com as coordenadas
literais, do mesmo jeito que o mod original, porque um script CLEO (.cs) tem
somente 32 variaveis locais. Toda a logica fica em sub-rotinas compartilhadas
(dirigir / esperar_chegada / nascer_onibus / abrir_portas / ...).
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

# enum CAR_DOOR do jogo -> nomes aceitos pelo gta3script
DOOR_NAMES = {
    0: "BONNET",
    1: "BOOT",
    2: "FRONT_LEFT_DOOR",
    3: "FRONT_RIGHT_DOOR",
    4: "REAR_LEFT_DOOR",
    5: "REAR_RIGHT_DOOR",
    6: "EXTRA_DOOR",
}


def num(v) -> str:
    """Formata um numero como literal FLOAT valido no gta3script (sem perder precisao)."""
    f = float(v)
    if f == int(f):
        return f"{int(f)}.0"
    return repr(f)


def sanitize(name: str) -> str:
    """Nome de script/arquivo -> identificador gta3script (A-Z0-9_)."""
    out = "".join(c if c.isalnum() else "_" for c in name.upper())
    while "__" in out:
        out = out.replace("__", "_")
    return out.strip("_")


class ScriptBuilder:
    def __init__(self, route: dict, script_name: str):
        self.route = route
        self.script_name = script_name
        self.lines: list[str] = []
        self.settings = route.get("settings", {})
        self.stops = route["stops"]
        self.doors = self._doors_union()

    # ---------------------------------------------------------------- utils
    def _doors_union(self) -> list[int]:
        doors: set[int] = set()
        for s in self.stops:
            doors.update(s.get("doorsBoard") or [4, 5])
            doors.update(s.get("doorsExit") or [3])
        return sorted(doors)

    def w(self, line: str = "") -> None:
        self.lines.append(line)

    @property
    def text(self) -> str:
        return "\n".join(self.lines).rstrip() + "\n"

    # ---------------------------------------------------------------- partes
    def header(self, json_path: Path) -> None:
        st = self.settings
        self.w("// Compilar com: gta3sc compile BUS301.sc --config=gtasa --guesser --cs -o BUS301.cs")
        self.w("// (o SCRIPT_NAME do .cs vem do nome do arquivo .sc, em maiusculas)")
        self.w("// ------------------------------------------------------------------")
        self.w(f"//  Bus Route Evolved - Rota {self.route.get('id', '?')} - {self.route.get('name', '')}")
        self.w("//  Arquivo GERADO AUTOMATICAMENTE por tools/generate_script.py.")
        self.w(f"//  Dados de origem: {json_path}")
        self.w("//  Nao edite este arquivo a mao: altere a rota no editor e gere de novo.")
        self.w("//")
        self.w("//  Fluxo:")
        self.w("//    1. o script espera o jogador chegar em um dos pontos;")
        self.w("//    2. o onibus nasce na parada ANTERIOR (como no mod original) e")
        self.w("//       percorre o trecho ate a parada do jogador;")
        self.w("//    3. durante a parada as portas abrem, o jogador aperta Y para")
        self.w("//       embarcar e a passagem e cobrada;")
        self.w("//    4. o onibus segue a rota inteira, em ciclo, ate o jogador se")
        self.w("//       afastar demais (ai a viagem e encerrada e tudo recomeca).")
        self.w("// ------------------------------------------------------------------")
        self.w("")
        self.w("// --- configuracao (vem de settings no JSON da rota) ---")
        models = st.get("vehicleModels") or [431, 437]
        self.w(f"CONST_INT   MODELO_ONIBUS_A    {int(models[0])}")
        self.w(f"CONST_INT   MODELO_ONIBUS_B    {int(models[-1])}")
        self.w(f"CONST_INT   MODELO_MOTORISTA   {int(st.get('driverModel', 7))}")
        self.w("// valor da passagem (cobrado como desconto no dinheiro do jogador)")
        self.w(f"CONST_INT   PASSAGEM_DESCONTO  {(-1) * int(st.get('fare', 2))}")
        self.w(f"CONST_INT   ESPERA_MIN         {int(st.get('dwellTimeMin', 5000))}")
        self.w(f"CONST_INT   ESPERA_MAX         {int(st.get('dwellTimeMax', 10000))}")
        self.w(f"CONST_INT   PAUSA_MIN          {int(st.get('layoverTimeMin', 25000))}")
        self.w(f"CONST_INT   PAUSA_MAX          {int(st.get('layoverTimeMax', 40000))}")
        self.w("CONST_INT   TEMPO_MOTORISTA    20000")
        self.w("CONST_FLOAT ALTURA_NASCIMENTO  10.5")
        self.w(f"CONST_FLOAT CFG_RAIO_PONTO      {num(st.get('stopRadius', 20.0))}")
        self.w(f"CONST_FLOAT CFG_RAIO_EMBARQUE   {num(st.get('boardRadius', 10.0))}")
        self.w(f"CONST_FLOAT CFG_RAIO_CHEGADA    {num(st.get('arriveRadius', 3.0))}")
        self.w(f"CONST_FLOAT CFG_DIST_ABANDONO   {num(st.get('abandonDistance', 250.0))}")
        self.w("CONST_FLOAT PASSO_PORTA        0.05")
        self.w("CONST_INT   MS_PORTA           30")
        self.w("CONST_INT   ESPERA_TIME_OUT    30000")
        self.w("")

    def prelude(self) -> None:
        """Variaveis, configuracao e o desvio para as sub-rotinas.

        As sub-rotinas vem ANTES do fluxo principal no arquivo porque o gta3sc
        infere o tipo das entidades (CAR/CHAR/BLIP) na ordem em que o codigo
        aparece - o `GOTO iniciar` pula as sub-rotinas na execucao.
        """
        self.w("SCRIPT_START")
        self.w("{")
        self.w(f"    SCRIPT_NAME {self.script_name}")
        self.w("")
        self.w("    // 32 variaveis locais e o limite de um script CLEO - usamos 24.")
        self.w("    LVAR_INT   cara onibus motorista blip modelo sorteio")
        self.w("    LVAR_INT   viagem_ativa embarque espera pausa perto timeout")
        self.w("    LVAR_FLOAT tx ty tz veloc angulo")
        self.w("    LVAR_FLOAT nasc_x nasc_y nasc_h")
        self.w("    LVAR_FLOAT raio_espera raio_embarque raio_chegada dist_abandono")
        self.w("")
        self.w("    // ---- configuracao inicial ----")
        self.w("    viagem_ativa = 0")
        self.w("    embarque = 0")
        self.w("    perto = 0")
        self.w("    espera = 0")
        self.w("    raio_espera = CFG_RAIO_PONTO")
        self.w("    raio_embarque = CFG_RAIO_EMBARQUE")
        self.w("    raio_chegada = CFG_RAIO_CHEGADA")
        self.w("    dist_abandono = CFG_DIST_ABANDONO")
        self.w("    GET_PLAYER_CHAR 0 cara")
        self.w("")
        self.w("    GOTO iniciar  // o fluxo principal comeca depois das sub-rotinas")
        self.w("")

    # ---------------------------------------------------------- espera no ponto
    def stop_trigger(self, stop: dict, nxt: str) -> None:
        i = stop["index"]
        self.w(f"    // ==================================================================")
        self.w(f"    //  PARADA {i} - {stop.get('textLabel') or '?'}"
               f"{'  [TERMINAL]' if stop.get('terminal') else ''}")
        self.w(f"    //  Ponto de espera: {num(stop['x'])}, {num(stop['y'])}"
               f"  (a pe, raio {num(self.settings.get('stopRadius', 20.0))})")
        self.w(f"    // ==================================================================")
        self.w(f"    parada_{i}:")
        self.w("        WAIT 0")
        self.w("        IF LOCATE_CHAR_ANY_MEANS_2D cara "
               f"{num(stop['x'])} {num(stop['y'])} raio_espera raio_espera false")
        self.w(f"            PRINT_HELP {stop.get('waitText') or 'BUS_T1'}")
        self.w("            WAIT 3000")
        self.w("            PRINT_HELP ON_BUS")
        prev = self.stops[(i - 2) % len(self.stops)]  # o onibus nasce na parada anterior
        self.w(f"            // nasce na parada {prev['index']} e dirige ate aqui")
        self.w(f"            nasc_x = {num(prev['x'])}")
        self.w(f"            nasc_y = {num(prev['y'])}")
        self.w(f"            nasc_h = {num(prev['spawnHeading'])}")
        self.w("            GOSUB nascer_onibus")
        self.w(f"            GOTO trecho_{i}")
        self.w("        ENDIF")
        self.w(f"        GOTO {nxt}")
        self.w("")

    # ---------------------------------------------------------------- trechos
    def leg(self, stop: dict) -> None:
        i = stop["index"]
        wps = stop["leg"]["waypoints"]
        self.w(f"    // ---- trecho {stop['leg']['from']} -> {stop['leg']['to']}"
               f" ({len(wps)} ponto(s)) ----")
        self.w(f"    trecho_{i}:")
        if not wps:
            self.w("        // (trecho sem pontos: segue direto para a parada)")
        for n, wp in enumerate(wps, 1):
            self.w(f"        // ponto {n}/{len(wps)}")
            self.w(f"        tx = {num(wp['x'])}")
            self.w(f"        ty = {num(wp['y'])}")
            self.w(f"        tz = {num(wp.get('z', 11.0))}")
            self.w(f"        veloc = {num(wp.get('speed', 15.0))}")
            self.w("        GOSUB dirigir")
            self.w("        GOSUB esperar_chegada")
            self.w("        IF viagem_ativa = 0")
            self.w("            GOTO encerrar")
            self.w("        ENDIF")
        self.w("")

    # ------------------------------------------------------------ parada/dwell
    def dwell(self, stop: dict) -> None:
        i = stop["index"]
        terminal = bool(stop.get("terminal"))
        faixa = ("PAUSA_MIN", "PAUSA_MAX") if terminal else ("ESPERA_MIN", "ESPERA_MAX")
        self.w(f"    // ---- parada {i}: portas, embarque, anuncio ----")
        self.w("        SET_CAR_CRUISE_SPEED onibus 0.0")
        self.w("        WAIT 2000")
        self.w("        GOSUB abrir_portas")
        self.w(f"        GENERATE_RANDOM_INT_IN_RANGE {faixa[0]} {faixa[1]} espera")
        self.w("        WHILE espera > 0")
        self.w("            WAIT MS_PORTA")
        self.w("            GOSUB checar_viagem")
        self.w("            GOSUB checar_embarque")
        self.w("            espera -= MS_PORTA")
        self.w("        ENDWHILE")
        self.w("        GOSUB fechar_portas")
        self.w("        IF viagem_ativa = 0")
        self.w("            GOTO encerrar")
        self.w("        ENDIF")
        announce = stop.get("announceNextLabel")
        if announce:
            self.w("        // avisa os passageiros qual e a proxima parada")
            self.w("        IF IS_CHAR_IN_CAR cara onibus")
            self.w(f"            PRINT_HELP {announce}")
            self.w("        ENDIF")
        if terminal and stop.get("layover"):
            lay = stop["layover"]
            self.w("        // terminal: pausa do motorista")
            self.w(f"        tx = {num(lay['x'])}")
            self.w(f"        ty = {num(lay['y'])}")
            self.w(f"        tz = {num(lay.get('z', 11.0))}")
            self.w("        GOSUB pausa_motorista")
            self.w("        IF viagem_ativa = 0")
            self.w("            GOTO encerrar")
            self.w("        ENDIF")
        self.w("")

    def stop_block(self, stop: dict, next_label: str) -> None:
        """Emite trecho + parada e salta para o proximo trecho."""
        self.leg(stop)
        self.dwell(stop)
        self.w(f"        GOTO {next_label}")
        self.w("")

    # ------------------------------------------------------------ sub-rotinas
    def subroutines(self) -> None:
        n = len(self.stops)
        w = self.w
        w("    // ==================================================================")
        w("    //  SUB-ROTINAS COMPARTILHADAS")
        w("    // ==================================================================")
        w("")
        w("    // Cria o onibus (modelo sorteado) e o motorista na parada anterior.")
        w("    nascer_onibus:")
        w("        GENERATE_RANDOM_INT_IN_RANGE 0 2 sorteio")
        w("        IF sorteio = 0")
        w("            modelo = MODELO_ONIBUS_A")
        w("        ELSE")
        w("            modelo = MODELO_ONIBUS_B")
        w("        ENDIF")
        w("        REQUEST_MODEL modelo")
        w("        REQUEST_MODEL MODELO_MOTORISTA")
        w("        LOAD_ALL_MODELS_NOW")
        w("        WHILE NOT HAS_MODEL_LOADED modelo")
        w("            WAIT 0")
        w("        ENDWHILE")
        w("        WHILE NOT HAS_MODEL_LOADED MODELO_MOTORISTA")
        w("            WAIT 0")
        w("        ENDWHILE")
        w("        // pequena espera aleatoria, como no mod original")
        w("        GENERATE_RANDOM_INT_IN_RANGE 0 2000 sorteio")
        w("        WAIT sorteio")
        w("        CREATE_CAR modelo nasc_x nasc_y ALTURA_NASCIMENTO onibus")
        w("        SET_CAR_HEADING onibus nasc_h")
        col = self.settings.get("colours") or [1, 1]
        w(f"        CHANGE_CAR_COLOUR onibus {int(col[0])} {int(col[-1])}")
        w("        CREATE_CHAR_INSIDE_CAR onibus PEDTYPE_CIVMALE MODELO_MOTORISTA motorista")
        w("        MARK_MODEL_AS_NO_LONGER_NEEDED modelo")
        w("        MARK_MODEL_AS_NO_LONGER_NEEDED MODELO_MOTORISTA")
        w("        viagem_ativa = 1")
        w("        embarque = 0")
        w("        perto = 0")
        if self.settings.get("blip", True):
            w("        ADD_BLIP_FOR_CAR onibus blip")
        w("        RETURN")
        w("")
        w("    // Faz o onibus ir ate tx/ty/tz na velocidade veloc.")
        w("    dirigir:")
        w("        SET_CAR_CRUISE_SPEED onibus veloc")
        w("        CAR_WANDER_RANDOMLY onibus")
        w("        CAR_GOTO_COORDINATES_ACCURATE onibus tx ty tz")
        w("        RETURN")
        w("")
        w("    // Espera o onibus chegar em tx/ty/tz (com checagens de abandono).")
        w("    esperar_chegada:")
        w("        timeout = 0")
        w("        WHILE viagem_ativa = 1")
        w("            IF LOCATE_CAR_2D onibus tx ty raio_chegada raio_chegada false")
        w("                RETURN")
        w("            ENDIF")
        w("            WAIT MS_PORTA")
        w("            timeout += MS_PORTA")
        w("            IF timeout > ESPERA_TIME_OUT")
        w("                viagem_ativa = 0")
        w("            ENDIF")
        w("            GOSUB checar_viagem")
        w("        ENDWHILE")
        w("        RETURN")
        w("")
        w("    // Encerra a viagem se o jogador se afastou demais ou se o")
        w("    // onibus/motorista morreu. O primeiro \"perto\" e obrigatorio:")
        w("    // algumas paradas ficam a mais de dist_abandono uma da outra e o")
        w("    // onibus precisa chegar ate o jogador antes de valer o abandono.")
        w("    checar_viagem:")
        w("        IF LOCATE_CHAR_ANY_MEANS_CAR_2D cara onibus dist_abandono dist_abandono false")
        w("            perto = 1")
        w("        ELSE")
        w("            IF perto = 1")
        w("                viagem_ativa = 0")
        w("            ENDIF")
        w("        ENDIF")
        w("        IF IS_CAR_DEAD onibus")
        w("            viagem_ativa = 0")
        w("        ENDIF")
        w("        IF IS_CHAR_DEAD motorista")
        w("            viagem_ativa = 0")
        w("        ENDIF")
        w("        RETURN")
        w("")
        w("    // Embarque/desembarque do jogador + cobranca da passagem.")
        w("    // embarque: 0 = fora do onibus, 1 = embarcou (paga), 2 = pagou")
        w("    checar_embarque:")
        w("        IF IS_CHAR_IN_CAR cara onibus")
        w("            IF embarque = 1")
        w("                PRINT_HELP BUSFARE")
        w("                ADD_SCORE 0 PASSAGEM_DESCONTO  // 0 = jogador local")
        w("                embarque = 2")
        w("                WAIT 1000")
        w("            ENDIF")
        w("        ELSE")
        w("            embarque = 0")
        w("            IF LOCATE_CHAR_ANY_MEANS_CAR_2D cara onibus raio_embarque raio_embarque false")
        w("                IF IS_KEY_PRESSED VK_KEY_Y")
        w("                    TASK_ENTER_CAR_AS_PASSENGER cara onibus -1 -1")
        w("                    OPEN_CAR_DOOR_A_BIT onibus FRONT_RIGHT_DOOR 1.0")
        w("                    embarque = 1")
        w("                ENDIF")
        w("            ENDIF")
        w("        ENDIF")
        w("        RETURN")
        w("")
        w("    // Pausa do motorista no terminal: desce, vai ate o ponto de")
        w("    // descanso (tx/ty/tz), espera e volta para o onibus.")
        w("    pausa_motorista:")
        w("        TASK_LEAVE_CAR motorista onibus")
        w("        WAIT 2000")
        w("        TASK_GO_STRAIGHT_TO_COORD motorista tx ty tz 4 -1")
        w("        GENERATE_RANDOM_INT_IN_RANGE PAUSA_MIN PAUSA_MAX pausa")
        w("        WAIT pausa")
        w("        TASK_ENTER_CAR_AS_DRIVER motorista onibus TEMPO_MOTORISTA")
        w("        WAIT TEMPO_MOTORISTA")
        w("        RETURN")
        w("")
        w("    // Animacao das portas: o mod original abre/fecha em passos de 0.05")
        w("    // a cada 30 ms, dando o efeito de porta de onibus.")
        w("    abrir_portas:")
        w("        angulo = 0.0")
        w("        WHILE angulo < 1.0")
        w("            angulo += PASSO_PORTA")
        w("            IF angulo > 1.0")
        w("                angulo = 1.0")
        w("            ENDIF")
        for d in self.doors:
            w(f"            OPEN_CAR_DOOR_A_BIT onibus {DOOR_NAMES.get(d, str(d))} angulo")
        w("            WAIT MS_PORTA")
        w("        ENDWHILE")
        w("        RETURN")
        w("")
        w("    fechar_portas:")
        w("        angulo = 1.0")
        w("        WHILE angulo > 0.0")
        w("            angulo -= PASSO_PORTA")
        w("            IF angulo < 0.0")
        w("                angulo = 0.0")
        w("            ENDIF")
        for d in reversed(self.doors):
            w(f"            OPEN_CAR_DOOR_A_BIT onibus {DOOR_NAMES.get(d, str(d))} angulo")
        w("            WAIT MS_PORTA")
        w("        ENDWHILE")
        w("        RETURN")
        w("")
        w("    // ==================================================================")
        w("    //  FIM DA VIAGEM")
        w("    // ==================================================================")
        w("    encerrar:")
        w("        SET_CAR_CRUISE_SPEED onibus 0.0")
        w("        IF IS_CHAR_IN_CAR cara onibus")
        w("            TASK_LEAVE_CAR_IMMEDIATELY cara onibus")
        w("            WAIT 500")
        w("        ENDIF")
        if self.settings.get("blip", True):
            w("        REMOVE_BLIP blip")
        w("        DELETE_CAR onibus")
        w("        MARK_CHAR_AS_NO_LONGER_NEEDED motorista")
        w("        viagem_ativa = 0")
        w("        embarque = 0")
        w("        espera = 0")
        w("        perto = 0")
        w("        GOTO parada_1")
        w("")
        w("    // ==================================================================")
        w("    //  FLUXO PRINCIPAL - espera do jogador, trechos e paradas")
        w("    // ==================================================================")
        w("    iniciar:")
        w("        WAIT 15000")
        w("        PRINT_HELP DEUCE")
        w("")

    # ---------------------------------------------------------------- montagem
    def build(self, json_path: Path) -> str:
        self.header(json_path)
        self.prelude()
        self.subroutines()  # vem antes do fluxo principal (ver docstring do prelude)
        n = len(self.stops)
        # 1) espera do jogador em cada ponto (cadeia 1 -> 2 -> ... -> 1)
        for i, stop in enumerate(self.stops):
            self.stop_trigger(stop, f"parada_{self.stops[(i + 1) % n]['index']}")
        # 2) trecho + parada de cada ponto, em ciclo
        for i, stop in enumerate(self.stops):
            nxt = self.stops[(i + 1) % n]
            self.stop_block(stop, f"trecho_{nxt['index']}")
        # 3) fechamento
        self.w("}")
        self.w("SCRIPT_END")
        self.w("")
        self.w(f"// paradas: {n} | total de pontos de trajeto: "
               f"{sum(len(s['leg']['waypoints']) for s in self.stops)}")
        return self.text


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("json", help="arquivo JSON da rota")
    ap.add_argument("-o", "--output", required=True, help="arquivo .sc de saida")
    ap.add_argument("-n", "--name", default=None,
                    help="nome do script (padrao: BUS<id da rota>)")
    ap.add_argument("--compile", action="store_true",
                    help="compila com o gta3sc (gera o .cs ao lado do .sc)")
    ap.add_argument("--gta3sc", default="gta3sc", help="caminho do binario gta3sc")
    args = ap.parse_args()

    json_path = Path(args.json)
    route = json.loads(json_path.read_text(encoding="utf-8"))
    script_name = sanitize(args.name or f"BUS{route.get('id', '000')}")

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(ScriptBuilder(route, script_name).build(json_path), encoding="utf-8")

    stops = len(route["stops"])
    wps = sum(len(s["leg"]["waypoints"]) for s in route["stops"])
    print(f"gerado: {out} ({stops} paradas, {wps} pontos de trajeto, script_name='{script_name}')")

    if args.compile:
        cs = out.with_suffix(".cs")
        cmd = [args.gta3sc, "compile", str(out), "--config=gtasa", "--guesser",
               "--cs", "-o", str(cs)]
        print("compilando:", " ".join(cmd))
        r = subprocess.run(cmd, capture_output=True, text=True)
        sys.stdout.write(r.stdout)
        sys.stderr.write(r.stderr)
        if r.returncode != 0:
            return r.returncode
        print(f"compilado: {cs} ({cs.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
