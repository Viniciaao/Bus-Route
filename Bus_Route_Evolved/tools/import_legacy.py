#!/usr/bin/env python3
"""
import_legacy.py - Converte o script Sanny Builder do mod antigo (Bus Route,
de Sinan) para o formato JSON de rota usado pelo Bus Route Evolved.

Uso:
    python3 import_legacy.py "Bus_Route_301(SannyBuilder).txt" -o ../data/routes/301_sstt_dtc.json

O script antigo e um unico arquivo decompilado, com esta estrutura:

    1) Tabela de "triggers": blocos do tipo
           if locate_char_any_means_2d $PLAYER_ACTOR sphere 0 near_point X Y radius 20.0 20.0
           1@ = <spawn_x>
           2@ = <spawn_y>
           13@ = <spawn_heading>
       Cada trigger e uma parada onde o jogador pode esperar o onibus. O onibus
       nasce em (1@, 2@) - a parada ANTERIOR - e segue o trecho despachado por 2@.

    2) Tabela de despacho:
           if
           2@ == <spawn_y>
           goto_if_false @PROXIMA_COMPARACAO
           goto @LABEL_DO_TRECHO

    3) Um "trecho" (leg) por parada: cadeia de labels com
           set_car_cruise_speed 8@ max_speed_to S
           car_goto_coordinates_accurate 8@ drive_to X Y Z
           if locate_car_2d 8@ near_point X Y radius 3 3
           goto_if_false @MESMO_LABEL
           goto @PROXIMO_LABEL
       ate o "bloco de chegada" (portas, cobranca, anuncio, goto @PROXIMO_TRECHO).

Sem dependencias externas.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

RE_LABEL = re.compile(r"^:([A-Z0-9_]+)\s*$")

# sub-rotinas de anuncio -> GXT label (ordem extraida do script original)
ANNOUNCE_TEXTS = {
    "BUS_49655": "EXC",
    "BUS_49668": "LUX",
    "BUS_49681": "CRH",
    "BUS_49694": "CAE",
    "BUS_49707": "CIR",
    "BUS_49720": "BAR",
    "BUS_49733": "MER",
    "BUS_49746": "FRE",
    "BUS_49759": "LAP",
    "BUS_49772": "LAD",
    "BUS_49785": "DROP",
    "BUS_49798": "TER_1",
    "BUS_49811": "LVP",
    "BUS_49824": "BGS",
    "BUS_49837": "MEA",
    "BUS_49850": "MIR",
    "BUS_49863": "TRE",
    "BUS_49876": "BAL",
    "BUS_49889": "FLM",
    "BUS_49902": "IMP",
    "BUS_49915": "TER_2",
}


def load_blocks(text: str) -> tuple[dict[str, list[str]], list[str]]:
    blocks: dict[str, list[str]] = {}
    order: list[str] = []
    current: str | None = None
    for raw in text.splitlines():
        line = raw.strip()
        m = RE_LABEL.match(line)
        if m:
            current = m.group(1)
            if current not in blocks:
                blocks[current] = []
                order.append(current)
            continue
        if current is not None:
            blocks[current].append(line)
    return blocks, order


def numbers(line: str) -> list[float]:
    return [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?(?:e-?\d+)?", line)]


def fmt(v: float) -> float | int:
    return int(round(v)) if abs(v - round(v)) < 1e-9 else round(v, 6)


# ---------------------------------------------------------------------------


def parse_triggers(blocks: dict[str, list[str]]) -> list[dict]:
    triggers: list[dict] = []
    for label, lines in blocks.items():
        for i, line in enumerate(lines):
            if "locate_char_any_means_2d" not in line or "near_point" not in line:
                continue
            n = numbers(line.split("near_point", 1)[1])
            spawn_x = spawn_y = None
            heading = 0.0
            hint = None
            wait_text = None
            for extra in lines[i + 1 : i + 12]:
                if extra.startswith("1@ ="):
                    spawn_x = numbers(extra.split("=", 1)[1])[0]
                elif extra.startswith("2@ ="):
                    spawn_y = numbers(extra.split("=", 1)[1])[0]
                elif extra.startswith("13@ ="):
                    heading = numbers(extra.split("=", 1)[1])[0]
                elif extra.startswith("print_help"):
                    m = re.search(r"'([^']+)'", extra)
                    if m:
                        # o primeiro print_help e o "texto de area" (BUS_T1/BUS_T2),
                        # o segundo e o aviso ON_BUS
                        if wait_text is None:
                            wait_text = m.group(1)
                        hint = m.group(1)
            if spawn_x is None or spawn_y is None:
                continue
            triggers.append(
                {
                    "sourceLabel": label,
                    "x": fmt(n[0]),
                    "y": fmt(n[1]),
                    "spawn_x": fmt(spawn_x),
                    "spawn_y": fmt(spawn_y),
                    "spawn_heading": fmt(heading),
                    "hint": hint,
                    "wait_text": wait_text,
                }
            )
    return triggers


def parse_dispatch(blocks: dict[str, list[str]], entry: str) -> dict[float, str]:
    dispatch: dict[float, str] = {}
    label: str | None = entry
    seen: set[str] = set()
    while label and label not in seen:
        seen.add(label)
        lines = [l for l in blocks.get(label, []) if l]
        if len(lines) < 4 or lines[0] != "if" or "2@ ==" not in lines[1]:
            break
        value = numbers(lines[1])[-1]
        if not lines[3].startswith("goto @"):
            break
        dispatch[value] = lines[3].split("@")[-1].strip()
        label = lines[2].split("@")[-1].strip() if lines[2].startswith("goto_if_false @") else None
    return dispatch


def parse_leg(blocks: dict[str, list[str]], order: list[str], start: str) -> dict:
    """Segue a cadeia de labels a partir de `start` ate o bloco de chegada.

    O bloco de chegada e identificado pelo par (anuncio + goto para o proximo
    trecho). Entre um passo de direcao e o bloco de chegada o decompilador pode
    ter criado labels intermediarios sem nenhum `goto` - nesse caso seguimos a
    ordem do arquivo.
    """
    waypoints: list[dict] = []
    doors_board: set[int] = set()
    doors_exit: set[int] = set()
    announce: str | None = None
    next_leg: str | None = None
    layover: dict | None = None
    label: str | None = start
    seen: set[str] = set()
    arrival_label: str | None = None

    while label and label not in seen:
        seen.add(label)
        lines = [l for l in blocks.get(label, []) if l]
        speed: float | None = None
        target: tuple[float, float, float] | None = None
        radius: float = 3.0
        goto: str | None = None

        for line in lines:
            if line.startswith("set_car_cruise_speed"):
                speed = numbers(line.split("max_speed_to", 1)[-1])[-1]
            elif line.startswith("car_goto_coordinates_accurate"):
                n = numbers(line.split("drive_to", 1)[-1])
                target = (n[0], n[1], n[2] if len(n) > 2 else 11.0)
            elif line.startswith("locate_car_2d"):
                n = numbers(line.split("radius", 1)[-1])
                radius = n[0] if n else 3.0
            elif line.startswith("open_car_door_a_bit"):
                n = numbers(line)
                door = int(n[1])
                (doors_exit if door == 3 else doors_board).add(door)
            elif line.startswith("gosub_if_false @BUS_49"):
                announce = line.split("@")[-1].strip()
            elif line.startswith("gosub @BUS_49495") or line.startswith("gosub @BUS_49542"):
                sub = line.split("@")[-1].strip()
                point = None
                for s_line in blocks.get(sub, []):
                    if s_line.startswith("task_go_straight_to_coord") and "goto_point" in s_line:
                        n = numbers(s_line.split("goto_point", 1)[1])
                        point = (fmt(n[0]), fmt(n[1]), fmt(n[2]) if len(n) > 2 else 11.0)
                layover = {"driverBreak": True, "sub": sub}
                if point:
                    layover["x"], layover["y"], layover["z"] = point
            elif line.startswith("goto @") and "goto_if_false" not in line[:14]:
                goto = line.split("@")[-1].strip()

        if target is not None:
            waypoints.append(
                {
                    "x": fmt(target[0]),
                    "y": fmt(target[1]),
                    "z": fmt(target[2]),
                    "speed": fmt(speed if speed is not None else 15.0),
                    "radius": fmt(radius),
                }
            )
        if announce is not None:
            arrival_label = arrival_label or label
        if announce is not None and goto:
            next_leg = goto
            break
        if goto and goto != label:
            label = goto
            continue
        # sem goto: a continuacao (codigo de chegada) esta no proximo bloco do arquivo
        try:
            i = order.index(label)
        except ValueError:
            break
        label = order[i + 1] if i + 1 < len(order) else None

    return {
        "startLabel": start,
        "arrivalLabel": arrival_label,
        "waypoints": waypoints,
        "announce": announce,
        "announceText": ANNOUNCE_TEXTS.get(announce or "", None),
        "nextLegLabel": next_leg,
        "layover": layover,
        "doorsBoard": sorted(doors_board) or [4, 5],
        "doorsExit": sorted(doors_exit) or [3],
    }


def build_stops(triggers: list[dict], dispatch: dict[float, str], blocks: dict[str, list[str]], order: list[str]) -> list[dict]:
    assert triggers, "nenhuma parada encontrada"
    stops: list[dict] = []
    for i, t in enumerate(triggers):
        prev = triggers[i - 1]  # a rota e ciclica: o "anterior" do primeiro e o ultimo
        start = None
        for key, lab in dispatch.items():
            if abs(key - prev["y"]) < 0.01:
                start = lab
                break
        leg = parse_leg(blocks, order, start) if start else None
        stops.append(
            {
                "index": i + 1,
                "sourceLabel": t["sourceLabel"],
                "x": t["x"],
                "y": t["y"],
                "z": 11.0,
                "spawnHeading": t["spawn_heading"],  # corrigido abaixo (pertence a parada anterior)
                "waitHint": t["hint"],
                "waitText": t.get("wait_text"),
                "terminal": bool(leg and leg["layover"]),
                "layover": leg["layover"] if leg else None,
                "doorsBoard": leg["doorsBoard"] if leg else [4, 5],
                "doorsExit": leg["doorsExit"] if leg else [3],
                # texto anunciado quando o onibus SAI desta parada (fala da proxima)
                "announceNextLabel": leg["announceText"] if leg else None,
                "leg": {
                    "from": prev["index"] if "index" in prev else i,
                    "to": i + 1,
                    "waypoints": leg["waypoints"] if leg else [],
                },
            }
        )
    # agora cada parada recebe o texto que a anuncia (o anuncio feito na parada anterior)
    n = len(stops)
    for i in range(n):
        prev = stops[(i - 1) % n]
        stops[i]["textLabel"] = prev["announceNextLabel"]
    # a direcao (13@) do trigger pertence a parada ANTERIOR: o onibus nasce la
    headings = [s["spawnHeading"] for s in stops]
    for i in range(n):
        stops[i]["spawnHeading"] = headings[(i + 1) % n]
    for s in stops:
        s["leg"]["from"] = s["index"] - 1 if s["index"] > 1 else n
    return stops


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", help="script .txt (Sanny Builder) do mod antigo")
    ap.add_argument("-o", "--output", default=None, help="arquivo JSON de saida")
    args = ap.parse_args()

    text = Path(args.input).read_text(encoding="utf-8", errors="replace")
    blocks, order = load_blocks(text)

    triggers = parse_triggers(blocks)
    dispatch = parse_dispatch(blocks, "BUS_2493")
    stops = build_stops(triggers, dispatch, blocks, order)

    print(f"paradas: {len(stops)} | trechos: {len(dispatch)}")
    for s in stops:
        print(
            f"  {s['index']:>2} {s['sourceLabel']:<9} ({s['x']}, {s['y']}) "
            f"dir={s['spawnHeading']:>5} wp={len(s['leg']['waypoints']):>2} "
            f"texto={s['textLabel']} {'[TERMINAL]' if s['terminal'] else ''}"
        )

    route = {
        "schemaVersion": 1,
        "id": "301",
        "name": "Rota 301 - The Deuce (Las Venturas Strip)",
        "description": (
            "Rota circular original do mod Bus Route (Sinan): South Strip Transfer "
            "Terminal -> Downtown Transportation Center -> volta pela Strip Sul."
        ),
        "settings": {
            "fare": 2,
            "stopRadius": 20.0,
            "boardRadius": 10.0,
            "abandonDistance": 250.0,
            "dwellTime": 8000,
            "dwellTimeMin": 5000,
            "dwellTimeMax": 10000,
            "layoverTime": 30000,
            "layoverTimeMin": 25000,
            "layoverTimeMax": 40000,
            "vehicleModels": [431, 437],
            "driverModel": 7,
            "colours": [1, 1],
            "blip": True,
        },
        "stops": stops,
    }

    out = json.dumps(route, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        Path(args.output).write_text(out, encoding="utf-8")
        print(f"escrito: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
