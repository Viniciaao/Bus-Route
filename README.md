# Bus-Route

Atualmente: Esse mod faz algo praticamente inédito: possibilita que uma rota de ônibus passe perfeitamente por Las Venturas, passando por toda a cidade na linha, sendo tanto o Coach quanto o Bus.
A principal vantagem do mod é que você pode entrar no ônibus normalmente pagando uma tarifa de $2 (barato comparado a aqui). A porta irá abrir e fechar normalmente para você entrar e você poderá acompanhar a viagem toda.

Dentro do ônibus, aparecerão mensagens de onde será a próxima parada e tudo mais, deixando uma experiência completa de um ônibus real. Talvez a única desvantagem mesmo é o fato da direção do ônibus não ser perfeita, será comum ele bater em alguns carros, ignorar regras etc.
O mod vem com um .IPL ainda por cima para ter mais pontos de ônibus nas áreas que os ônibus param, servindo de identificação para ficar esperando-o. Após ele chegar no ponto final (terminal) ele irá dar a volta e fazer o lado contrário.

‎**Autor:** Sinan

---

## 🚌 Reescrita em gta3script: Bus Route Evolved

A pasta [`Bus_Route_Evolved/`](Bus_Route_Evolved/LEIA-ME.md) contém a reescrita deste mod em
**gta3script** (Sanny Builder 4 / gta3sc), com a rota separada do código:

- [`Bus_Route_Evolved/CLEO/`](Bus_Route_Evolved/CLEO) — `BUS301.cs` compilado + os textos (`cleo_text/Bus_PTBR.fxt`);
- [`Bus_Route_Evolved/data/routes/301_route.json`](Bus_Route_Evolved/data/routes/301_route.json) — dados da linha 301;
- [`Bus_Route_Evolved/tools/`](Bus_Route_Evolved/tools) — importador do script antigo, gerador de gta3script e `build.sh`;
- [`Bus_Route_Evolved/Editor(Criação de Novas Linhas)/editor_de_linhas.html`](<Bus_Route_Evolved/Editor(Criação de Novas Linhas)/editor_de_linhas.html>) — **editor de linhas** que gera o JSON e o `.sc` para criar rotas novas.

Veja o [LEIA-ME da reescrita](Bus_Route_Evolved/LEIA-ME.md) para instalar, compilar e criar linhas.
