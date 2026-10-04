// Compilar com: gta3sc compile BUS301.sc --config=gtasa --guesser --cs -o BUS301.cs
// (o SCRIPT_NAME do .cs vem do nome do arquivo .sc, em maiusculas)
// ------------------------------------------------------------------
//  Bus Route Evolved - Rota 301 - Rota 301 - The Deuce (Las Venturas Strip)
//  Arquivo GERADO AUTOMATICAMENTE por tools/generate_script.py.
//  Dados de origem: data/routes/301_route.json
//  Nao edite este arquivo a mao: altere a rota no editor e gere de novo.
//
//  Fluxo:
//    1. o script espera o jogador chegar em um dos pontos;
//    2. o onibus nasce na parada ANTERIOR (como no mod original) e
//       percorre o trecho ate a parada do jogador;
//    3. durante a parada as portas abrem, o jogador aperta Y para
//       embarcar e a passagem e cobrada;
//    4. o onibus segue a rota inteira, em ciclo, ate o jogador se
//       afastar demais (ai a viagem e encerrada e tudo recomeca).
// ------------------------------------------------------------------

// --- configuracao (vem de settings no JSON da rota) ---
CONST_INT   MODELO_ONIBUS_A    431
CONST_INT   MODELO_ONIBUS_B    437
CONST_INT   MODELO_MOTORISTA   7
// valor da passagem (cobrado como desconto no dinheiro do jogador)
CONST_INT   PASSAGEM_DESCONTO  -2
CONST_INT   ESPERA_MIN         5000
CONST_INT   ESPERA_MAX         10000
CONST_INT   PAUSA_MIN          25000
CONST_INT   PAUSA_MAX          40000
CONST_INT   TEMPO_MOTORISTA    20000
CONST_FLOAT ALTURA_NASCIMENTO  10.5
CONST_FLOAT CFG_RAIO_PONTO      20.0
CONST_FLOAT CFG_RAIO_EMBARQUE   10.0
CONST_FLOAT CFG_RAIO_CHEGADA    3.0
CONST_FLOAT CFG_DIST_ABANDONO   250.0
CONST_FLOAT PASSO_PORTA        0.05
CONST_INT   MS_PORTA           30
CONST_INT   ESPERA_TIME_OUT    30000

SCRIPT_START
{
    SCRIPT_NAME BUS301

    // 32 variaveis locais e o limite de um script CLEO - usamos 24.
    LVAR_INT   cara onibus motorista blip modelo sorteio
    LVAR_INT   viagem_ativa embarque espera pausa perto timeout
    LVAR_FLOAT tx ty tz veloc angulo
    LVAR_FLOAT nasc_x nasc_y nasc_h
    LVAR_FLOAT raio_espera raio_embarque raio_chegada dist_abandono

    // ---- configuracao inicial ----
    viagem_ativa = 0
    embarque = 0
    perto = 0
    espera = 0
    raio_espera = CFG_RAIO_PONTO
    raio_embarque = CFG_RAIO_EMBARQUE
    raio_chegada = CFG_RAIO_CHEGADA
    dist_abandono = CFG_DIST_ABANDONO
    GET_PLAYER_CHAR 0 cara

    GOTO iniciar  // o fluxo principal comeca depois das sub-rotinas

    // ==================================================================
    //  SUB-ROTINAS COMPARTILHADAS
    // ==================================================================

    // Cria o onibus (modelo sorteado) e o motorista na parada anterior.
    nascer_onibus:
        GENERATE_RANDOM_INT_IN_RANGE 0 2 sorteio
        IF sorteio = 0
            modelo = MODELO_ONIBUS_A
        ELSE
            modelo = MODELO_ONIBUS_B
        ENDIF
        REQUEST_MODEL modelo
        REQUEST_MODEL MODELO_MOTORISTA
        LOAD_ALL_MODELS_NOW
        WHILE NOT HAS_MODEL_LOADED modelo
            WAIT 0
        ENDWHILE
        WHILE NOT HAS_MODEL_LOADED MODELO_MOTORISTA
            WAIT 0
        ENDWHILE
        // pequena espera aleatoria, como no mod original
        GENERATE_RANDOM_INT_IN_RANGE 0 2000 sorteio
        WAIT sorteio
        CREATE_CAR modelo nasc_x nasc_y ALTURA_NASCIMENTO onibus
        SET_CAR_HEADING onibus nasc_h
        CHANGE_CAR_COLOUR onibus 1 1
        CREATE_CHAR_INSIDE_CAR onibus PEDTYPE_CIVMALE MODELO_MOTORISTA motorista
        MARK_MODEL_AS_NO_LONGER_NEEDED modelo
        MARK_MODEL_AS_NO_LONGER_NEEDED MODELO_MOTORISTA
        viagem_ativa = 1
        embarque = 0
        perto = 0
        ADD_BLIP_FOR_CAR onibus blip
        RETURN

    // Faz o onibus ir ate tx/ty/tz na velocidade veloc.
    dirigir:
        SET_CAR_CRUISE_SPEED onibus veloc
        CAR_WANDER_RANDOMLY onibus
        CAR_GOTO_COORDINATES_ACCURATE onibus tx ty tz
        RETURN

    // Espera o onibus chegar em tx/ty/tz (com checagens de abandono).
    esperar_chegada:
        timeout = 0
        WHILE viagem_ativa = 1
            IF LOCATE_CAR_2D onibus tx ty raio_chegada raio_chegada false
                RETURN
            ENDIF
            WAIT MS_PORTA
            timeout += MS_PORTA
            IF timeout > ESPERA_TIME_OUT
                viagem_ativa = 0
            ENDIF
            GOSUB checar_viagem
        ENDWHILE
        RETURN

    // Encerra a viagem se o jogador se afastou demais ou se o
    // onibus/motorista morreu. O primeiro "perto" e obrigatorio:
    // algumas paradas ficam a mais de dist_abandono uma da outra e o
    // onibus precisa chegar ate o jogador antes de valer o abandono.
    checar_viagem:
        IF LOCATE_CHAR_ANY_MEANS_CAR_2D cara onibus dist_abandono dist_abandono false
            perto = 1
        ELSE
            IF perto = 1
                viagem_ativa = 0
            ENDIF
        ENDIF
        IF IS_CAR_DEAD onibus
            viagem_ativa = 0
        ENDIF
        IF IS_CHAR_DEAD motorista
            viagem_ativa = 0
        ENDIF
        RETURN

    // Embarque/desembarque do jogador + cobranca da passagem.
    // embarque: 0 = fora do onibus, 1 = embarcou (paga), 2 = pagou
    checar_embarque:
        IF IS_CHAR_IN_CAR cara onibus
            IF embarque = 1
                PRINT_HELP BUSFARE
                ADD_SCORE 0 PASSAGEM_DESCONTO  // 0 = jogador local
                embarque = 2
                WAIT 1000
            ENDIF
        ELSE
            embarque = 0
            IF LOCATE_CHAR_ANY_MEANS_CAR_2D cara onibus raio_embarque raio_embarque false
                IF IS_KEY_PRESSED VK_KEY_Y
                    TASK_ENTER_CAR_AS_PASSENGER cara onibus -1 -1
                    OPEN_CAR_DOOR_A_BIT onibus FRONT_RIGHT_DOOR 1.0
                    embarque = 1
                ENDIF
            ENDIF
        ENDIF
        RETURN

    // Pausa do motorista no terminal: desce, vai ate o ponto de
    // descanso (tx/ty/tz), espera e volta para o onibus.
    pausa_motorista:
        TASK_LEAVE_CAR motorista onibus
        WAIT 2000
        TASK_GO_STRAIGHT_TO_COORD motorista tx ty tz 4 -1
        GENERATE_RANDOM_INT_IN_RANGE PAUSA_MIN PAUSA_MAX pausa
        WAIT pausa
        TASK_ENTER_CAR_AS_DRIVER motorista onibus TEMPO_MOTORISTA
        WAIT TEMPO_MOTORISTA
        RETURN

    // Animacao das portas: o mod original abre/fecha em passos de 0.05
    // a cada 30 ms, dando o efeito de porta de onibus.
    abrir_portas:
        angulo = 0.0
        WHILE angulo < 1.0
            angulo += PASSO_PORTA
            IF angulo > 1.0
                angulo = 1.0
            ENDIF
            OPEN_CAR_DOOR_A_BIT onibus FRONT_RIGHT_DOOR angulo
            OPEN_CAR_DOOR_A_BIT onibus REAR_LEFT_DOOR angulo
            OPEN_CAR_DOOR_A_BIT onibus REAR_RIGHT_DOOR angulo
            WAIT MS_PORTA
        ENDWHILE
        RETURN

    fechar_portas:
        angulo = 1.0
        WHILE angulo > 0.0
            angulo -= PASSO_PORTA
            IF angulo < 0.0
                angulo = 0.0
            ENDIF
            OPEN_CAR_DOOR_A_BIT onibus REAR_RIGHT_DOOR angulo
            OPEN_CAR_DOOR_A_BIT onibus REAR_LEFT_DOOR angulo
            OPEN_CAR_DOOR_A_BIT onibus FRONT_RIGHT_DOOR angulo
            WAIT MS_PORTA
        ENDWHILE
        RETURN

    // ==================================================================
    //  FIM DA VIAGEM
    // ==================================================================
    encerrar:
        SET_CAR_CRUISE_SPEED onibus 0.0
        IF IS_CHAR_IN_CAR cara onibus
            TASK_LEAVE_CAR_IMMEDIATELY cara onibus
            WAIT 500
        ENDIF
        REMOVE_BLIP blip
        DELETE_CAR onibus
        MARK_CHAR_AS_NO_LONGER_NEEDED motorista
        viagem_ativa = 0
        embarque = 0
        espera = 0
        perto = 0
        GOTO parada_1

    // ==================================================================
    //  FLUXO PRINCIPAL - espera do jogador, trechos e paradas
    // ==================================================================
    iniciar:
        WAIT 15000
        PRINT_HELP DEUCE

    // ==================================================================
    //  PARADA 1 - TER_2  [TERMINAL]
    //  Ponto de espera: 2139.093, 1048.8521  (a pe, raio 20.0)
    // ==================================================================
    parada_1:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2139.093 1048.8521 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 21 e dirige ate aqui
            nasc_x = 2039.75
            nasc_y = 1005.35
            nasc_h = 180.0
            GOSUB nascer_onibus
            GOTO trecho_1
        ENDIF
        GOTO parada_2

    // ==================================================================
    //  PARADA 2 - EXC
    //  Ponto de espera: 2075.05, 1178.6  (a pe, raio 20.0)
    // ==================================================================
    parada_2:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2075.05 1178.6 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 1 e dirige ate aqui
            nasc_x = 2139.093
            nasc_y = 1048.8521
            nasc_h = 300.0
            GOSUB nascer_onibus
            GOTO trecho_2
        ENDIF
        GOTO parada_3

    // ==================================================================
    //  PARADA 3 - LUX
    //  Ponto de espera: 2075.05, 1355.35  (a pe, raio 20.0)
    // ==================================================================
    parada_3:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2075.05 1355.35 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 2 e dirige ate aqui
            nasc_x = 2075.05
            nasc_y = 1178.6
            nasc_h = 0.0
            GOSUB nascer_onibus
            GOTO trecho_3
        ENDIF
        GOTO parada_4

    // ==================================================================
    //  PARADA 4 - CRH
    //  Ponto de espera: 2075.05, 1514.85  (a pe, raio 20.0)
    // ==================================================================
    parada_4:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2075.05 1514.85 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 3 e dirige ate aqui
            nasc_x = 2075.05
            nasc_y = 1355.35
            nasc_h = 0.0
            GOSUB nascer_onibus
            GOTO trecho_4
        ENDIF
        GOTO parada_5

    // ==================================================================
    //  PARADA 5 - CAE
    //  Ponto de espera: 2075.05, 1661.95  (a pe, raio 20.0)
    // ==================================================================
    parada_5:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2075.05 1661.95 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 4 e dirige ate aqui
            nasc_x = 2075.05
            nasc_y = 1514.85
            nasc_h = 0.0
            GOSUB nascer_onibus
            GOTO trecho_5
        ENDIF
        GOTO parada_6

    // ==================================================================
    //  PARADA 6 - CIR
    //  Ponto de espera: 2152.96, 1863.55  (a pe, raio 20.0)
    // ==================================================================
    parada_6:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2152.96 1863.55 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 5 e dirige ate aqui
            nasc_x = 2075.05
            nasc_y = 1661.95
            nasc_h = 0.0
            GOSUB nascer_onibus
            GOTO trecho_6
        ENDIF
        GOTO parada_7

    // ==================================================================
    //  PARADA 7 - BAR
    //  Ponto de espera: 2154.76, 1994.45  (a pe, raio 20.0)
    // ==================================================================
    parada_7:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2154.76 1994.45 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 6 e dirige ate aqui
            nasc_x = 2152.96
            nasc_y = 1863.55
            nasc_h = 333.0
            GOSUB nascer_onibus
            GOTO trecho_7
        ENDIF
        GOTO parada_8

    // ==================================================================
    //  PARADA 8 - MER
    //  Ponto de espera: 2154.76, 2113.25  (a pe, raio 20.0)
    // ==================================================================
    parada_8:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2154.76 2113.25 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 7 e dirige ate aqui
            nasc_x = 2154.76
            nasc_y = 1994.45
            nasc_h = 0.0
            GOSUB nascer_onibus
            GOTO trecho_8
        ENDIF
        GOTO parada_9

    // ==================================================================
    //  PARADA 9 - FRE
    //  Ponto de espera: 2154.76, 2191.95  (a pe, raio 20.0)
    // ==================================================================
    parada_9:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2154.76 2191.95 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 8 e dirige ate aqui
            nasc_x = 2154.76
            nasc_y = 2113.25
            nasc_h = 0.0
            GOSUB nascer_onibus
            GOTO trecho_9
        ENDIF
        GOTO parada_10

    // ==================================================================
    //  PARADA 10 - LAP
    //  Ponto de espera: 2228.8601, 2393.55  (a pe, raio 20.0)
    // ==================================================================
    parada_10:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2228.8601 2393.55 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 9 e dirige ate aqui
            nasc_x = 2154.76
            nasc_y = 2191.95
            nasc_h = 0.0
            GOSUB nascer_onibus
            GOTO trecho_10
        ENDIF
        GOTO parada_11

    // ==================================================================
    //  PARADA 11 - LAD
    //  Ponto de espera: 2087.26, 2455.55  (a pe, raio 20.0)
    // ==================================================================
    parada_11:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2087.26 2455.55 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 10 e dirige ate aqui
            nasc_x = 2228.8601
            nasc_y = 2393.55
            nasc_h = 356.0
            GOSUB nascer_onibus
            GOTO trecho_11
        ENDIF
        GOTO parada_12

    // ==================================================================
    //  PARADA 12 - DROP
    //  Ponto de espera: 1997.36, 2395.75  (a pe, raio 20.0)
    // ==================================================================
    parada_12:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 1997.36 2395.75 raio_espera raio_espera false
            PRINT_HELP BUS_T1
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 11 e dirige ate aqui
            nasc_x = 2087.26
            nasc_y = 2455.55
            nasc_h = 90.0
            GOSUB nascer_onibus
            GOTO trecho_12
        ENDIF
        GOTO parada_13

    // ==================================================================
    //  PARADA 13 - TER_1  [TERMINAL]
    //  Ponto de espera: 1997.468, 2471.4719  (a pe, raio 20.0)
    // ==================================================================
    parada_13:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 1997.468 2471.4719 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 12 e dirige ate aqui
            nasc_x = 1997.36
            nasc_y = 2395.75
            nasc_h = 90.0
            GOSUB nascer_onibus
            GOTO trecho_13
        ENDIF
        GOTO parada_14

    // ==================================================================
    //  PARADA 14 - LVP
    //  Ponto de espera: 2055.3601, 2322.95  (a pe, raio 20.0)
    // ==================================================================
    parada_14:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2055.3601 2322.95 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 13 e dirige ate aqui
            nasc_x = 1997.468
            nasc_y = 2471.4719
            nasc_h = 0.0
            GOSUB nascer_onibus
            GOTO trecho_14
        ENDIF
        GOTO parada_15

    // ==================================================================
    //  PARADA 15 - BGS
    //  Ponto de espera: 2119.76, 2178.3501  (a pe, raio 20.0)
    // ==================================================================
    parada_15:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2119.76 2178.3501 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 14 e dirige ate aqui
            nasc_x = 2055.3601
            nasc_y = 2322.95
            nasc_h = 307.0
            GOSUB nascer_onibus
            GOTO trecho_15
        ENDIF
        GOTO parada_16

    // ==================================================================
    //  PARADA 16 - MEA
    //  Ponto de espera: 2119.76, 2063.75  (a pe, raio 20.0)
    // ==================================================================
    parada_16:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2119.76 2063.75 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 15 e dirige ate aqui
            nasc_x = 2119.76
            nasc_y = 2178.3501
            nasc_h = 180.0
            GOSUB nascer_onibus
            GOTO trecho_16
        ENDIF
        GOTO parada_17

    // ==================================================================
    //  PARADA 17 - MIR
    //  Ponto de espera: 2119.76, 1882.65  (a pe, raio 20.0)
    // ==================================================================
    parada_17:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2119.76 1882.65 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 16 e dirige ate aqui
            nasc_x = 2119.76
            nasc_y = 2063.75
            nasc_h = 180.0
            GOSUB nascer_onibus
            GOTO trecho_17
        ENDIF
        GOTO parada_18

    // ==================================================================
    //  PARADA 18 - TRE
    //  Ponto de espera: 2039.75, 1545.0  (a pe, raio 20.0)
    // ==================================================================
    parada_18:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2039.75 1545.0 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 17 e dirige ate aqui
            nasc_x = 2119.76
            nasc_y = 1882.65
            nasc_h = 180.0
            GOSUB nascer_onibus
            GOTO trecho_18
        ENDIF
        GOTO parada_19

    // ==================================================================
    //  PARADA 19 - BAL
    //  Ponto de espera: 2039.75, 1346.75  (a pe, raio 20.0)
    // ==================================================================
    parada_19:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2039.75 1346.75 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 18 e dirige ate aqui
            nasc_x = 2039.75
            nasc_y = 1545.0
            nasc_h = 180.0
            GOSUB nascer_onibus
            GOTO trecho_19
        ENDIF
        GOTO parada_20

    // ==================================================================
    //  PARADA 20 - FLM
    //  Ponto de espera: 2039.75, 1145.0  (a pe, raio 20.0)
    // ==================================================================
    parada_20:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2039.75 1145.0 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 19 e dirige ate aqui
            nasc_x = 2039.75
            nasc_y = 1346.75
            nasc_h = 180.0
            GOSUB nascer_onibus
            GOTO trecho_20
        ENDIF
        GOTO parada_21

    // ==================================================================
    //  PARADA 21 - IMP
    //  Ponto de espera: 2039.75, 1005.35  (a pe, raio 20.0)
    // ==================================================================
    parada_21:
        WAIT 0
        IF LOCATE_CHAR_ANY_MEANS_2D cara 2039.75 1005.35 raio_espera raio_espera false
            PRINT_HELP BUS_T2
            WAIT 3000
            PRINT_HELP ON_BUS
            // nasce na parada 20 e dirige ate aqui
            nasc_x = 2039.75
            nasc_y = 1145.0
            nasc_h = 180.0
            GOSUB nascer_onibus
            GOTO trecho_21
        ENDIF
        GOTO parada_1

    // ---- trecho 21 -> 1 (13 ponto(s)) ----
    trecho_1:
        // ponto 1/13
        tx = 2162.4199
        ty = 975.553
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/13
        tx = 2167.1201
        ty = 983.953
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/13
        tx = 2162.3201
        ty = 992.353
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 4/13
        tx = 2142.3201
        ty = 994.953
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 5/13
        tx = 2122.3201
        ty = 994.953
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 6/13
        tx = 2103.52
        ty = 989.353
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 7/13
        tx = 2092.6201
        ty = 999.553
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 8/13
        tx = 2092.6201
        ty = 1012.95
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 9/13
        tx = 2100.8201
        ty = 1025.15
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 10/13
        tx = 2108.02
        ty = 1031.35
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 11/13
        tx = 2115.8201
        ty = 1037.05
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 12/13
        tx = 2129.0901
        ty = 1043.35
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 13/13
        tx = 2139.093
        ty = 1048.8521
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 1: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE PAUSA_MIN PAUSA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP EXC
        ENDIF
        // terminal: pausa do motorista
        tx = 2164.8899
        ty = 1078.26
        tz = 11.597
        GOSUB pausa_motorista
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

        GOTO trecho_2

    // ---- trecho 1 -> 2 (9 ponto(s)) ----
    trecho_2:
        // ponto 1/9
        tx = 2147.6931
        ty = 1053.489
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/9
        tx = 2153.6521
        ty = 1053.3669
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/9
        tx = 2158.2471
        ty = 1051.48
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 4/9
        tx = 2160.05
        ty = 1041.5
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 5/9
        tx = 2155.6499
        ty = 1021.5
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 6/9
        tx = 2155.6499
        ty = 1007.6
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 7/9
        tx = 2165.3501
        ty = 995.6
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 8/9
        tx = 2072.05
        ty = 1158.6
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 9/9
        tx = 2075.05
        ty = 1178.6
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 2: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP LUX
        ENDIF

        GOTO trecho_3

    // ---- trecho 2 -> 3 (2 ponto(s)) ----
    trecho_3:
        // ponto 1/2
        tx = 2072.05
        ty = 1335.35
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/2
        tx = 2075.05
        ty = 1355.35
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 3: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP CRH
        ENDIF

        GOTO trecho_4

    // ---- trecho 3 -> 4 (2 ponto(s)) ----
    trecho_4:
        // ponto 1/2
        tx = 2072.05
        ty = 1494.85
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/2
        tx = 2075.05
        ty = 1514.85
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 4: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP CAE
        ENDIF

        GOTO trecho_5

    // ---- trecho 4 -> 5 (2 ponto(s)) ----
    trecho_5:
        // ponto 1/2
        tx = 2072.05
        ty = 1641.95
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/2
        tx = 2075.05
        ty = 1661.95
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 5: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP CIR
        ENDIF

        GOTO trecho_6

    // ---- trecho 5 -> 6 (2 ponto(s)) ----
    trecho_6:
        // ponto 1/2
        tx = 2144.3601
        ty = 1850.15
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/2
        tx = 2152.96
        ty = 1863.55
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 6: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP BAR
        ENDIF

        GOTO trecho_7

    // ---- trecho 6 -> 7 (2 ponto(s)) ----
    trecho_7:
        // ponto 1/2
        tx = 2151.76
        ty = 1974.45
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/2
        tx = 2154.76
        ty = 1994.45
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 7: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP MER
        ENDIF

        GOTO trecho_8

    // ---- trecho 7 -> 8 (3 ponto(s)) ----
    trecho_8:
        // ponto 1/3
        tx = 2153.6599
        ty = 2083.25
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2154.76
        ty = 2093.25
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2154.76
        ty = 2113.25
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 8: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP FRE
        ENDIF

        GOTO trecho_9

    // ---- trecho 8 -> 9 (3 ponto(s)) ----
    trecho_9:
        // ponto 1/3
        tx = 2149.1599
        ty = 2161.95
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2154.76
        ty = 2171.95
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2154.76
        ty = 2191.95
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 9: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP LAP
        ENDIF

        GOTO trecho_10

    // ---- trecho 9 -> 10 (3 ponto(s)) ----
    trecho_10:
        // ponto 1/3
        tx = 2225.1599
        ty = 2377.6499
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2228.3601
        ty = 2389.95
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2228.8601
        ty = 2393.55
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 10: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP LAD
        ENDIF

        GOTO trecho_11

    // ---- trecho 10 -> 11 (1 ponto(s)) ----
    trecho_11:
        // ponto 1/1
        tx = 2087.26
        ty = 2455.55
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 11: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP DROP
        ENDIF

        GOTO trecho_12

    // ---- trecho 11 -> 12 (2 ponto(s)) ----
    trecho_12:
        // ponto 1/2
        tx = 2007.36
        ty = 2395.75
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/2
        tx = 1997.36
        ty = 2395.75
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 12: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP TER_1
        ENDIF

        GOTO trecho_13

    // ---- trecho 12 -> 13 (7 ponto(s)) ----
    trecho_13:
        // ponto 1/7
        tx = 1957.36
        ty = 2395.75
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/7
        tx = 1964.42
        ty = 2416.02
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/7
        tx = 1987.42
        ty = 2414.6279
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 4/7
        tx = 1991.642
        ty = 2434.7051
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 5/7
        tx = 1993.5811
        ty = 2449.967
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 6/7
        tx = 1996.47
        ty = 2460.47
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 7/7
        tx = 1997.468
        ty = 2471.4719
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 13: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE PAUSA_MIN PAUSA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP LVP
        ENDIF
        // terminal: pausa do motorista
        tx = 1973.72
        ty = 2474.6299
        tz = 11.726
        GOSUB pausa_motorista
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

        GOTO trecho_14

    // ---- trecho 13 -> 14 (14 ponto(s)) ----
    trecho_14:
        // ponto 1/14
        tx = 1997.03
        ty = 2481.6201
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/14
        tx = 1986.73
        ty = 2484.9199
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/14
        tx = 1983.12
        ty = 2468.0601
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 4/14
        tx = 1983.12
        ty = 2453.8899
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 5/14
        tx = 1983.12
        ty = 2440.1899
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 6/14
        tx = 1969.3199
        ty = 2432.5481
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 7/14
        tx = 1958.37
        ty = 2432.5481
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 8/14
        tx = 1947.42
        ty = 2432.5481
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 9/14
        tx = 1943.96
        ty = 2410.324
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 10/14
        tx = 1948.76
        ty = 2400.3201
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 11/14
        tx = 1963.96
        ty = 2391.02
        tz = 11.0
        veloc = 5.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 12/14
        tx = 2041.0601
        ty = 2336.75
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 13/14
        tx = 2048.76
        ty = 2328.75
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 14/14
        tx = 2055.3601
        ty = 2322.95
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 14: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP BGS
        ENDIF

        GOTO trecho_15

    // ---- trecho 14 -> 15 (3 ponto(s)) ----
    trecho_15:
        // ponto 1/3
        tx = 2122.26
        ty = 2198.3501
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2119.76
        ty = 2188.3501
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2119.76
        ty = 2178.3501
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 15: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP MEA
        ENDIF

        GOTO trecho_16

    // ---- trecho 15 -> 16 (3 ponto(s)) ----
    trecho_16:
        // ponto 1/3
        tx = 2122.26
        ty = 2083.75
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2119.76
        ty = 2073.75
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2119.76
        ty = 2063.75
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 16: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP MIR
        ENDIF

        GOTO trecho_17

    // ---- trecho 16 -> 17 (3 ponto(s)) ----
    trecho_17:
        // ponto 1/3
        tx = 2122.26
        ty = 1902.65
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2119.76
        ty = 1892.65
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2119.76
        ty = 1882.65
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 17: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP TRE
        ENDIF

        GOTO trecho_18

    // ---- trecho 17 -> 18 (3 ponto(s)) ----
    trecho_18:
        // ponto 1/3
        tx = 2041.75
        ty = 1565.0
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2039.75
        ty = 1555.0
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2039.75
        ty = 1545.0
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 18: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP BAL
        ENDIF

        GOTO trecho_19

    // ---- trecho 18 -> 19 (3 ponto(s)) ----
    trecho_19:
        // ponto 1/3
        tx = 2041.75
        ty = 1366.75
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2039.75
        ty = 1356.75
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2039.75
        ty = 1346.75
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 19: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP FLM
        ENDIF

        GOTO trecho_20

    // ---- trecho 19 -> 20 (3 ponto(s)) ----
    trecho_20:
        // ponto 1/3
        tx = 2041.75
        ty = 1165.0
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2039.75
        ty = 1155.0
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2039.75
        ty = 1145.0
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 20: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP IMP
        ENDIF

        GOTO trecho_21

    // ---- trecho 20 -> 21 (3 ponto(s)) ----
    trecho_21:
        // ponto 1/3
        tx = 2041.75
        ty = 1025.35
        tz = 11.0
        veloc = 15.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 2/3
        tx = 2039.75
        ty = 1015.35
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // ponto 3/3
        tx = 2039.75
        ty = 1005.35
        tz = 11.0
        veloc = 10.0
        GOSUB dirigir
        GOSUB esperar_chegada
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF

    // ---- parada 21: portas, embarque, anuncio ----
        SET_CAR_CRUISE_SPEED onibus 0.0
        WAIT 2000
        GOSUB abrir_portas
        GENERATE_RANDOM_INT_IN_RANGE ESPERA_MIN ESPERA_MAX espera
        WHILE espera > 0
            WAIT MS_PORTA
            GOSUB checar_viagem
            GOSUB checar_embarque
            espera -= MS_PORTA
        ENDWHILE
        GOSUB fechar_portas
        IF viagem_ativa = 0
            GOTO encerrar
        ENDIF
        // avisa os passageiros qual e a proxima parada
        IF IS_CHAR_IN_CAR cara onibus
            PRINT_HELP TER_2
        ENDIF

        GOTO trecho_1

}
SCRIPT_END

// paradas: 21 | total de pontos de trajeto: 86
