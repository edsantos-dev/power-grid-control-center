# Power Grid Control Center — Backend

Base do backend em Java 24 e Spring Boot 4.1.1.

## Pré-requisitos

- JDK 24.
- Maven 3.6.3 ou superior.
- Acesso à internet para baixar as dependências na primeira execução.
- PostgreSQL disponível para executar a aplicação; os testes utilizam H2 em memória.

Configure `JAVA_HOME` para o diretório do JDK 24 e confira a versão usada pelo Maven:

```bash
mvn --version
```

## Build e testes

A partir da raiz do repositório:

```bash
cd backend
mvn verify -Dsurefire.useFile=false -DdisableXmlReport=true -Dmaven.test.redirectTestOutputToFile=false
```

O comando compila o projeto, executa os testes de contexto, persistência, credenciais e integração HTTP e gera o JAR em `target/`. Os resultados são exibidos no terminal, sem relatórios TXT/XML. Os testes utilizam JUnit Jupiter 6 e AssertJ; a persistência utiliza H2 em memória, com as mesmas migrations Flyway usadas pela aplicação.

## Banco de dados

Crie o banco PostgreSQL e disponibilize credenciais de um usuário com permissão de criação de tabelas. Configure as variáveis de ambiente:

| Variável | Valor padrão |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/power_grid_control_center` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | Obrigatória, sem valor padrão |
| `SERVER_PORT` | `8080` |
| `FRONTEND_URL` | `http://localhost:5173` |

Flyway aplica as migrations de `src/main/resources/db/migration/`. Hibernate utiliza `ddl-auto: validate` e não cria nem atualiza tabelas automaticamente. A entidade `Usuario` mapeia a tabela `usuario`, com um `PerfilAcesso` por usuário; e-mail e matrícula são obrigatórios e únicos. Subestacao e Alimentador utilizam herança JOINED com AtivoRede, com ID compartilhado entre base e subtipo. Alimentador possui vínculo obrigatório LAZY com Subestacao e API de criação, listagem, consulta por ID e edição. Subestações possuem criação, consulta/listagem e edição, com DTO validado e serviço transacional.

## Execução

Dentro de `backend/`:

```bash
mvn spring-boot:run
```

O servidor utiliza a porta `8080` por padrão. A `SecurityFilterChain` expõe os contratos de autenticação e exige sessão para as demais rotas. O endpoint POST /api/subestacoes cadastra subestações; os demais cadastros de ativos permanecem pendentes. Use `Ctrl+C` para encerrar.

## Dependências

- `spring-boot-starter-web`.
- `spring-boot-starter-validation`.
- `spring-boot-starter-data-jpa`.
- `spring-boot-starter-security`.
- `spring-boot-flyway` e `flyway-core`.
- `flyway-database-postgresql`.
- `postgresql`, no escopo de execução.
- `lombok`, como dependência opcional e processador de anotações, excluído do JAR executável.
- `spring-boot-starter-test`, no escopo de testes.
- `spring-boot-starter-data-jpa-test`, no escopo de testes.
- `spring-boot-starter-webmvc-test`, no escopo de testes.
- `h2`, no escopo de testes.

O `pom.xml` utiliza Java 24 e mantém a estrutura e a formatação do Spring Initializr. As versões das dependências são gerenciadas pelo parent do Spring Boot 4.1.1.

## Credenciais

E-mails recebem `trim` e conversão para minúsculas com `Locale.ROOT` antes de inserções e atualizações JPA e antes da consulta de credenciais. `UsuarioUserDetailsService` carrega o usuário pelo e-mail normalizado e converte seu perfil em uma autoridade `ROLE_...`. O bean `PasswordEncoder` utiliza BCrypt.

O fluxo HTTP utiliza sessão com cookie HttpOnly e proteção CSRF, sem JWT. Login e consulta do token CSRF são públicos; as demais rotas exigem autenticação. A segurança por método está habilitada com `@EnableMethodSecurity`. Futuras operações de gestão de acesso deverão usar `@PreAuthorize("hasRole('GERENTE_OPERACOES')")`; ainda não existem endpoints desse tipo. O cadastro deverá exigir senha com pelo menos oito caracteres; essa validação será implementada junto ao cadastro.

A migration `V2__inserir_usuarios_admin.sql` cria as contas de demonstração abaixo, com e-mails normalizados e hashes BCrypt distintos. A senha inicial de todas é `SenhaSegura123`.

| E-mail | Matrícula | Perfil |
|---|---|---|
| `operador@powergrid.local` | `INI-001` | OPERADOR |
| `gerente@powergrid.local` | `INI-002` | GERENTE_OPERACOES |
| `tecnico@powergrid.local` | `INI-003` | TECNICO_MANUTENCAO |

Essas credenciais são destinadas à demonstração local. A V2 é aplicada em todo banco que recebe as migrations; as credenciais devem ser substituídas antes de qualquer exposição externa. A integração React e o cadastro de usuários ficam para incrementos posteriores.

## API de autenticação

| Método | Caminho | Entrada | Resposta |
|---|---|---|---|
| GET | `/api/auth/csrf` | Acesso público | 200 com `token` e `headerName` |
| POST | `/api/auth/login` | Formulário `application/x-www-form-urlencoded`, campos `email` e `password`, token CSRF | 200 com `id`, `nome`, `email` e `perfil`; 401 para credenciais inválidas |
| GET | `/api/auth/me` | Sessão autenticada | 200 com o mesmo DTO seguro; 401 sem autenticação |
| POST | `/api/auth/logout` | Token CSRF da sessão atual | 204, invalidando a sessão e removendo o cookie |

Login e logout rejeitam token CSRF ausente ou inválido com 403. O login renova o identificador da sessão e invalida o token CSRF anterior. Após autenticar, o cliente deve consultar `/api/auth/csrf` novamente antes das próximas operações de escrita.

O frontend deve enviar cookies com `credentials: 'include'` em todas as requisições desse fluxo e enviar o token no cabeçalho indicado por `headerName`. O CORS permite credenciais somente para a origem exata de `FRONTEND_URL`, com métodos GET, POST, PUT e OPTIONS e cabeçalhos Content-Type e X-CSRF-TOKEN. As respostas de usuário não incluem senha nem hash.

## Cadastro de subestações

`POST /api/subestacoes` recebe JSON, exige sessão autenticada e token CSRF renovado após o login. Todos os perfis autenticados seguem a política geral de acesso.

```json
{
  "nome": "Subestação Central",
  "latitude": -25.4284001,
  "longitude": -49.2733001,
  "tensaoNominalKv": 138.123456,
  "capacidadeMva": 100.654321,
  "dataInstalacao": "2099-01-15",
  "estadoOperacional": "OPERACIONAL"
}
```

Todos os campos são obrigatórios, exceto estadoOperacional, que assume OPERACIONAL quando omitido ou nulo. Nome recebe trim, não pode ser vazio e admite até 255 caracteres. Coordenadas aceitam até sete casas decimais, nos intervalos [-90, 90] e [-180, 180]. Tensão e capacidade devem ser positivas, com até 13 dígitos inteiros e seis decimais; excesso de precisão é rejeitado antes da persistência. Datas futuras são permitidas. Os estados aceitos são OPERACIONAL, FALHA e MANUTENCAO.

A resposta 201 contém os campos cadastrados e o ID gerado; Location identifica /api/subestacoes/{id}. GET desse endereço retorna o cadastro ou 404 se inexistente. Requisições inválidas retornam 400; ausência de autenticação com CSRF válido retorna 401; CSRF ausente ou inválido retorna 403.

### Consulta e edição de subestações

| Método | Caminho | Resposta |
|---|---|---|
| GET | `/api/subestacoes` | 200 com lista completa; lista vazia quando não há registros |
| GET | `/api/subestacoes/{id}` | 200 com DTO; 404 se inexistente |
| PUT | `/api/subestacoes/{id}` | 200 com DTO atualizado; 404 se inexistente; 400 para entrada inválida |

Consultas exigem sessão autenticada. PUT exige também CSRF e substitui os dados pelo mesmo contrato JSON da criação, preservando o ID. Estado omitido ou nulo assume OPERACIONAL, inclusive se o estado anterior era outro. Uma entrada inválida não altera o cadastro. A listagem não é paginada e não garante ordenação.

## Cadastro de alimentadores

| Método | Caminho | Resposta |
|---|---|---|
| POST | `/api/alimentadores` | 201 com DTO e Location |
| GET | `/api/alimentadores` | 200 com lista completa, sem ordenação garantida ou paginação |
| GET | `/api/alimentadores/{id}` | 200 com DTO; 404 se inexistente |
| PUT | `/api/alimentadores/{id}` | 200 com DTO atualizado; 404 se alimentador ou subestação inexistente |

Todas as rotas exigem sessão autenticada; POST e PUT exigem também CSRF válido. A requisição JSON de criação e edição possui o formato abaixo; subestacaoId deve referenciar uma subestação existente.

```json
{
  "nome": "Alimentador Central",
  "latitude": -25.4284001,
  "longitude": -49.2733001,
  "tensaoNominalKv": 13.812345,
  "subestacaoId": 1,
  "dataInstalacao": "2099-01-15",
  "estadoOperacional": "OPERACIONAL"
}
```

Nome, coordenadas, tensão, subestacaoId e data são obrigatórios. Nome recebe trim e admite até 255 caracteres, sem unicidade. Coordenadas respeitam os limites geográficos e até sete casas decimais. Tensão é estritamente positiva e admite até 13 dígitos inteiros e seis decimais, sem arredondamento de entrada. SubestacaoId deve ser positivo. Datas futuras são permitidas. Estado omitido ou nulo assume OPERACIONAL; também são aceitos FALHA e MANUTENCAO. Não há capacidadeMva nem igualdade obrigatória entre tensões. O estado da subestação não impede o cadastro.

Entradas inválidas retornam 400. Referência a subestação inexistente retorna 404 antes da inclusão ou alteração. PUT substitui os dados, preserva o ID e permite trocar a subestação; dados anteriores são preservados quando a requisição é rejeitada. A resposta contém ID, campos cadastrais e subestacaoId, sem entidade aninhada.

## API de transformadores

Transformador utiliza herança JOINED com AtivoRede e possui capacidadeMva obrigatória e positiva. O vínculo com Alimentador é obrigatório e LAZY; a subestação é obtida por esse alimentador, sem referência duplicada. A API exige sessão autenticada e token CSRF nas escritas. Carga e temperatura serão tratadas em Monitoramento.

| Método | Caminho | Resultado |
| --- | --- | --- |
| POST | `/api/transformadores` | 201 e Location |
| GET | `/api/transformadores` | 200, lista |
| GET | `/api/transformadores/{id}` | 200 ou 404 |
| PUT | `/api/transformadores/{id}` | 200 ou 404 |

POST e PUT exigem nome (trim, até 255 caracteres), latitude e longitude válidas (até sete casas decimais), capacidadeMva positiva (até 13 dígitos inteiros e seis decimais), dataInstalacao e alimentadorId positivo existente. Datas futuras são permitidas. Estado omitido ou nulo assume OPERACIONAL. Entrada inválida retorna 400, sem arredondamento; alimentador inexistente retorna 404. A resposta inclui alimentadorId e subestacaoId derivado. O cliente não envia subestacaoId nem tensão nominal.

## API de eventos operacionais

POST `/api/eventos-operacionais` cria um evento e retorna 201 com Location. GET no mesmo caminho lista os eventos; GET `/{id}` consulta um evento, retornando 404 se inexistente. As rotas exigem sessão autenticada e a criação exige CSRF.

A requisição exige ativoId e responsavelUsuarioId positivos e existentes, categoria MANOBRA ou DESLIGAMENTO_PROGRAMADO, descrição não vazia e inicioPrevisto com fuso. TerminoPrevisto é opcional e não pode anteceder o início, considerando os instantes; igualdade e previsões passadas são permitidas. Criticidade é opcional, com até 32 caracteres. Entrada inválida retorna 400; ativo ou responsável inexistente, 404.

O registrador é obtido da sessão pelo serviço. A criação fixa ABERTO e mantém as datas efetivas nulas. A resposta contém os IDs do ativo e dos usuários, categoria, descrição, criticidade, situação e datas, sem credenciais. Alteração de situação, execução e integração com frontend ficam para incrementos posteriores.

## API de alertas

POST `/api/alertas` cria um alerta e retorna 201. Exige tipo não vazio (trim, até 128 caracteres), criticidade (BAIXA, MEDIA, ALTA ou CRITICA) e escopo (ATIVO ou REDE_MONITORADA). ATIVO exige ativoId positivo existente; REDE_MONITORADA exige ativoId nulo. Combinação inválida retorna 400; ativo inexistente no escopo ATIVO retorna 404. O servidor define geradoEm no instante atual UTC e situação ABERTO. Tipo é a mensagem principal, sem descrição separada.

GET `/api/alertas` retorna 200 com alertas ABERTO e RECONHECIDO, ordenados por CRITICA, ALTA, MEDIA e BAIXA, depois geradoEm e id decrescentes. Alertas RESOLVIDO são excluídos; ausência de alertas ativos retorna lista vazia. A resposta contém id, tipo, criticidade, escopo, situacao, geradoEm e ativoId (nulo para rede monitorada). As duas rotas exigem sessão autenticada; POST exige CSRF. Detecção automática, transições e integração com frontend ficam para incrementos posteriores.

## API de consumidores

POST `/api/consumidores` cria um titular e retorna 201 com Location. GET da coleção e GET `/{id}` retornam 200; ID inexistente retorna 404. Exigem sessão autenticada; POST também exige CSRF.

A entrada contém nomeRazaoSocial obrigatório (trim, até 255 caracteres), tipoPessoa PF/PJ, documento obrigatório e enderecoCorrespondencia opcional (trim, vazio convertido em nulo). Telefone e e-mail não integram a V1.

O documento aceita formato sem máscara ou máscara convencional. São preservados zeros iniciais; a pontuação é removida e letras convertidas para maiúsculas. CPF exige 11 dígitos; CNPJ exige 14 posições, incluindo o formato alfanumérico. Formato inválido, sequências numéricas repetidas e dígitos verificadores incorretos retornam 400. O cálculo alfanumérico utiliza os valores ASCII menos 48, conforme a [documentação da Receita Federal](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj). A validação não consulta situação cadastral externa.

Documento duplicado após normalização retorna 409, tanto na consulta prévia quanto na violação da constraint de unicidade durante a gravação. A resposta contém id, nomeRazaoSocial, tipoPessoa, documento e enderecoCorrespondencia. O cadastro não exige medidores; seus vínculos serão implementados na US06.

## API de medidores inteligentes

POST `/api/medidores` cria em estoque e retorna 201 com Location. Exige nome (trim, até 255 caracteres) e numeroSerie (trim, até 128 caracteres), preservando capitalização, zeros e pontuação interna. Série duplicada retorna 409, inclusive quando detectada pelo banco. Estado operacional é opcional e assume OPERACIONAL se ausente ou nulo. Dados de instalação não fazem parte do DTO de criação.

GET `/api/medidores` lista os cadastros; GET `/api/medidores/{id}` consulta por ID, retornando 404 se inexistente. Respostas incluem dados herdados, série, endereço e IDs de consumidor, transformador, alimentador e subestação. Os dois últimos são derivados da cadeia cadastral.

PATCH `/api/medidores/{id}/instalacao` exige consumidorId e transformadorId positivos existentes, latitude [-90, 90], longitude [-180, 180] com até sete casas decimais e dataInstalacao não futura. Endereço de fornecimento é opcional (trim, vazio convertido em nulo). Validação inválida retorna 400; referência inexistente 404; presença prévia de qualquer vínculo de instalação 409. A operação preserva ID, série e estado operacional, e usa transação com bloqueio de escrita do medidor. Não há transferência ou reinstalação neste recorte.

Todas as rotas exigem sessão; POST e PATCH exigem CSRF. O CORS permite PATCH para a origem configurada em FRONTEND_URL. Telemetria e integração com frontend permanecem pendentes.

## Medidores por consumidor

GET `/api/consumidores/{id}/medidores` exige sessão autenticada e retorna os medidores atualmente vinculados ao titular, independentemente do estado operacional. Reutiliza MedidorResponseDTO, em ordem crescente de id e sem paginação. IDs inválidos (incluindo zero e negativos) retornam 400; consumidor inexistente retorna 404; consumidor existente sem medidores retorna 200 com lista vazia.

A resposta inclui transformadorId, alimentadorId e subestacaoId pela cadeia cadastral. Se o medidor vinculado não tiver transformador, esses três campos são nulos. Medidores em estoque sem consumidor e medidores de outros titulares não integram a resposta. A consulta não representa histórico de instalações nem toda a topologia elétrica real.

## API de linhas de transmissão

POST /api/linhas-transmissao retorna 201 com Location; GET geral, GET /{id} e PUT /{id} retornam 200. Nome obrigatório com trim e até 255 caracteres; extensaoKm e tensaoOperacaoKv obrigatórios, positivos e limitados a 13 dígitos inteiros e seis decimais. Data de instalação obrigatória, permitindo datas futuras. Coordenadas opcionais em conjunto, com sete decimais e limites geográficos. Origem e destino são IDs positivos de subestações existentes e distintas. Entradas inválidas retornam 400; linha ou subestação inexistente retorna 404. Estado ausente ou nulo assume OPERACIONAL no POST e PUT. Respostas expõem IDs das extremidades; consultas sem paginação. Todas as rotas exigem autenticação e as escritas exigem CSRF. 
A V3 adiciona conexões NOT NULL para subestacao.id e CHECK de extremidades distintas. A migration pressupõe ausência de linhas legadas sem conexões; a V1 não é alterada. Capacidade e telemetria não integram este cadastro.

## Respostas de erro da camada MVC

Os erros tratados por GlobalExceptionHandler retornam timestamp UTC, status numérico, error (descrição HTTP), message, path e fieldErrors. A lista fieldErrors contém field e message para cada campo inválido e fica vazia nos demais casos; valores rejeitados não são incluídos.

Validação de DTO retorna 400. ResponseStatusException preserva o status e a mensagem de negócio nos erros 4xx. Violações de integridade retornam 409 com mensagem genérica, sem SQL ou detalhes das constraints. Exceções inesperadas retornam 500 com mensagem segura. Erros MVC como JSON malformado, conversão de parâmetros e método não permitido preservam seus status; cabeçalhos HTTP, como Allow, são mantidos.

O advice não intercepta respostas produzidas antes do DispatcherServlet pelos filtros de segurança. Login inválido, ausência de sessão e CSRF rejeitado mantêm o comportamento existente. Negação de acesso por método na camada MVC retorna 403 no formato padronizado.

## Ingestão de medições de linhas

POST /api/linhas-transmissao/{linhaId}/medicoes exige sessão e CSRF. O corpo contém fonteId positivo, medidoEm ISO 8601 com offset e ao menos uma grandeza: tensaoKv (kV), correnteA (A), potenciaAtivaMw (MW) ou potenciaReativaMvar (MVAr) ou frequenciaHz (Hz). Tensão e corrente são não negativas; as potências aceitam sinais. Tensão, corrente e potências admitem até 13 dígitos inteiros e seis decimais; frequência admite três inteiros e seis decimais. Campos ausentes permanecem nulos; zero é válido.

Linha ou fonte inexistente retorna 404; fonte de outra linha, conteúdo inválido ou instante além da tolerância futura retorna 400 no formato ApiError. A propriedade medicoes.tolerancia-futura-segundos configura a tolerância (padrão 60, inclusive). Leituras históricas são aceitas. O backend converte medidoEm para UTC e gera recebidoEm com seu relógio, sem aceitar atribuição pelo cliente. A origem REAL/SIMULADA vem da fonte cadastrada.

Retorna 201 com Location e id, linhaId, fonteId, origem, medidoEm, recebidoEm, tensaoKv, correnteA, potenciaAtivaMw, potenciaReativaMvar, subestacaoReferenciaId, frequenciaHz, fatorPotencia e situacaoFatorPotencia. A URL individual indicada por Location ainda não possui GET neste incremento. Fontes podem ser cadastradas pela API e as medições possuem consulta paginada, conforme descrito abaixo. Não há deduplicação, alertas automáticos ou autenticação própria de dispositivos neste recorte.

## Fontes e consulta de medições

POST /api/linhas-transmissao/{linhaId}/fontes exige identificador (trim, não vazio, até 128 caracteres) e origem REAL ou SIMULADA. Preserva capitalização e exige identificador globalmente único. Retorna 201 com id, identificador, linhaId e origem. Linha inexistente retorna 404; entrada inválida 400; duplicidade 409, inclusive quando detectada pela constraint do banco. Exige sessão e CSRF.

GET /api/linhas-transmissao/{linhaId}/medicoes exige sessão. Parâmetros page e size são opcionais, com padrões 0 e 20; page deve ser não negativo e size entre 1 e 100. Parâmetros vazios, repetidos, malformados ou fora dos limites retornam 400. Qualquer presença de sort retorna 400. A ordenação fixa é medidoEm DESC, id DESC. Pageable é construído após validação explícita dos parâmetros.

Filtros opcionais dataInicio e dataFim exigem ISO 8601 com offset e são comparados em UTC sobre medidoEm. O início é inclusivo e o fim exclusivo. Quando ambos são enviados, dataInicio deve ser anterior a dataFim. Cada limite também pode ser usado isoladamente. Linha inexistente retorna 404; ausência de leituras ou página além do total retorna 200 com content vazio.

O envelope contém apenas content, page, size, totalElements, totalPages, first e last. Os itens reutilizam MedicaoLinhaResponseDTO. A consulta inclui todas as fontes da linha informada e não inclui medições de outras linhas. Edição de fontes, deduplicação e atualização da interface são etapas distintas; a edição de fontes não está disponível. Paginação por offset não constitui snapshot entre requisições durante ingestão concorrente.

## Potência ativa e reativa — US09

Os DTOs de fontes aceitam e retornam subestacaoReferenciaId. O campo é opcional para compatibilidade com fontes de tensão/corrente. Quando informado, deve identificar uma subestação existente (404 se ausente) e uma extremidade da linha (400 caso contrário). IDs não positivos retornam 400. A API não permite edição ou reatribuição de fontes.

POST e GET /api/linhas-transmissao/{linhaId}/medicoes incluem potenciaAtivaMw (MW), potenciaReativaMvar (MVAr) e, nas respostas, subestacaoReferenciaId. As potências aceitam valores positivos, negativos e zero, com até 13 dígitos inteiros e seis decimais. Positivo representa entrada na linha pelo terminal de referência; os sinais de P e Q são independentes. Cada leitura exige ao menos uma das quatro grandezas; ausência continua nula. Tensão e corrente permanecem não negativas.

Ingestão contendo P ou Q exige referência de fonte válida como extremidade da linha, retornando 400 caso não exista ou seja incompatível. Não é inferida referência para fontes antigas. A origem, referência e recebidoEm são obtidos pelo servidor; a requisição da medição não permite escolher o terminal. Mantêm-se tolerância temporal, sessão, CSRF, paginação e filtros da US08. Violações de integridade na gravação de medições retornam 400 com mensagem limpa. A V5 fornece persistência das potências e FK opcional da referência.

## Frequência e fator de potência — US10

Request de medição aceita frequenciaHz opcional (Hz), estritamente positiva e com até três inteiros e seis decimais. A leitura exige ao menos uma das cinco grandezas; frequência isolada é válida sem terminal de potência. Frequências positivas fora do nominal didático de 60 Hz são preservadas; não há classificação automática por faixa neste incremento. Mantêm-se as regras de timestamp, fonte, sessão, CSRF e consulta paginada.

POST e GET acrescentam frequenciaHz, fatorPotencia e situacaoFatorPotencia. O FP é calculado no servidor a partir das potências da mesma leitura em regime senoidal sem harmônicos: |P|/sqrt(P²+Q²). Usa BigDecimal, raiz com DECIMAL128 e arredondamento final HALF_EVEN em seis casas, resultando em módulo entre 0 e 1. A direção do fluxo permanece nos sinais de P/Q e na referência da fonte.

Se P ou Q for nulo, FP é nulo e a situação é DADOS_INSUFICIENTES. Se ambos forem zero, FP é nulo com POTENCIA_APARENTE_NULA. Nos demais casos, a situação é CALCULADO. P=0 e Q diferente de zero produz 0; Q=0 e P diferente de zero produz 1. Não há FP no Request, coluna persistida ou preenchimento com potências de leituras distintas.

## Medições e fontes de transformadores — US11

POST /api/transformadores/{id}/fontes exige identificador (trim, não vazio, até 128 caracteres) e origem REAL/SIMULADA. A resposta 201 expõe id, identificador, transformadorId e origem. A unicidade do identificador é global, incluindo fontes de linhas; duplicidade retorna 409. subestacaoReferenciaId é exclusivo de fontes de linhas e, quando enviado para transformador, retorna 400. Transformador inexistente retorna 404.

POST /api/transformadores/{id}/medicoes exige fonteId positivo, medidoEm ISO 8601 com offset e potenciaAparenteMva não negativa com até 13 inteiros e seis decimais. Fonte ou transformador inexistente retorna 404; fonte de outro ativo, incluindo linha, retorna 400. Leituras históricas são aceitas. O limite futuro inclusivo utiliza medicoes.tolerancia-futura-segundos (padrão 60). recebidoEm é gerado em UTC pelo servidor. A capacidadeReferenciaMva é copiada do cadastro durante a ingestão e não pode ser atribuída pelo cliente. Para leitura atrasada, representa a capacidade conhecida na ingestão, sem reconstrução de vigência passada.

A resposta 201 inclui Location, id, transformadorId, fonteId, origem, medidoEm, recebidoEm, potenciaAparenteMva, capacidadeReferenciaMva e cargaPercentual. Carga = S*100/capacidadeReferencia, com BigDecimal e seis casas HALF_EVEN. Zero é válido e sobrecargas não são limitadas a 100%. Edições posteriores da capacidade cadastral não modificam leituras antigas. A URL individual indicada em Location ainda não possui GET.

GET /api/transformadores/{id}/medicoes usa o envelope PageResponseDTO, page=0, size=20 e máximo 100. Ordenação fixa medidoEm DESC, id DESC; qualquer sort é rejeitado. Filtros dataInicio/dataFim usam início inclusivo e fim exclusivo, ISO 8601 com offset. Parâmetros inválidos, vazios ou repetidos retornam 400. Linha e transformador compartilham a validação dos parâmetros. Transformador existente sem leituras retorna conteúdo vazio; inexistente retorna 404. Rotas exigem sessão e escritas exigem CSRF. Não inclui alerta automático, endpoint de edição de fontes nem classificação de atraso.

## Alertas automáticos de sobrecarga — US21

A ingestão de medições de transformadores avalia a cargaPercentual calculada (seis casas HALF_EVEN) contra o limiar fixo 100.000000. Acima do limiar, grava um alerta ATIVO com ativo e medição, carga e limiar em snapshot e geradoEm do relógio UTC do servidor. Exatamente 100% não gera alerta. Arredondamento ocorre antes da comparação, conforme o contrato deste incremento.

Medição e alerta são persistidos na mesma transação; falha na gravação do alerta desfaz a medição. Há no máximo um alerta por medição, garantido por UNIQUE. Reenviar uma leitura criando nova medição pode gerar outro alerta; não há deduplicação da ingestão. Leituras históricas também são avaliadas. ATIVO representa pendência de reconhecimento, sem afirmar que a condição elétrica ainda persiste. Não há recuperação automática.

GET /api/alertas-sobrecarga exige sessão e retorna PageResponseDTO com padrões page=0/size=20 e máximo 100. A ordenação fixa é geradoEm DESC/id DESC, rejeitando sort. Filtros opcionais: ativoId positivo, status ATIVO ou RECONHECIDO e dataInicio/dataFim ISO 8601 com offset sobre geradoEm. Início inclusivo e fim exclusivo; início deve ser anterior ao fim. Parâmetros vazios, repetidos, malformados ou fora dos limites retornam 400. Filtro de ativo sem resultados retorna conteúdo vazio.

GET /api/alertas-sobrecarga/{id} retorna 200 ou 404. Respostas incluem id, ativoId, medicaoId, cargaPercentual, limiarPercentual, status, geradoEm, reconhecidoEm e reconhecidoPorUsuarioId. Os alertas específicos não são duplicados na API genérica da US20. O reconhecimento por operador é disponibilizado pelo PATCH descrito abaixo.

### Reconhecimento de sobrecarga

PATCH /api/alertas-sobrecarga/{id}/reconhecimento exige sessão e CSRF, sem corpo obrigatório. Retorna 200 com AlertaSobrecargaResponseDTO ou 404 se o alerta não existir. A identidade é obtida do usuário autenticado da sessão; corpo e parâmetros não podem escolher o autor nem o horário.

ATIVO passa para RECONHECIDO com reconhecidoPorUsuarioId e reconhecidoEm gerados pelo servidor. O relógio é UTC e o instante é truncado para microssegundos, conforme a precisão do banco. Repetições, inclusive por outro usuário autenticado, retornam 200 e preservam o primeiro autor e horário. A transação adquire bloqueio pessimista no alerta antes de verificar o status. Reconhecimento não representa recuperação elétrica nem encerra automaticamente os demais alertas.

## Alertas automáticos de tensão — US22

A ingestão de linhas avalia somente leituras com tensaoKv. A referência é o cadastro tensaoOperacaoKv, que deve ser não nulo e positivo quando houver tensão informada; caso contrário, retorna 400 antes de persistir. Leituras sem tensão continuam válidas pelas demais grandezas.

Limite inferior = referência*0.95 e superior = referência*1.05, calculados com BigDecimal sem arredondamento intermediário. Fronteiras inclusivas: tensão <=inferior gera SUBTENSAO; tensão >=superior gera SOBRETENSAO; apenas valores estritamente entre os limites não geram alerta. Os percentuais são didáticos, não regulatórios. Zero é subtensão e não implica automaticamente evento de interrupção.

Cada medição anormal gera um alerta ATIVO com ativo, medição, tipo, snapshots da tensão/referência/limites e instante UTC do servidor. Medição e alerta usam a mesma transação; falha no alerta desfaz a leitura. Leituras atrasadas também são avaliadas; nova leitura normal não encerra ocorrências antigas. Não são criadas duplicatas na tabela de alertas genéricos.

GET /api/alertas-tensao retorna PageResponseDTO com page=0/size=20, máximo 100, geradoEm DESC/id DESC e bloqueio de sort. Filtros opcionais: ativoId positivo, tipo SUBTENSAO/SOBRETENSAO, status ATIVO/RECONHECIDO, dataInicio inclusiva e dataFim exclusiva sobre geradoEm (ISO 8601 com offset). Parâmetros inválidos, vazios ou repetidos retornam 400. GET /api/alertas-tensao/{id} retorna detalhe ou 404. Ambas as rotas exigem sessão. PATCH /api/alertas-tensao/{id}/reconhecimento não exige corpo e requer sessão e CSRF válidos. O autor vem da sessão; o instante UTC é gerado pelo servidor com precisão de microssegundos. A operação usa bloqueio pessimista e retorna 200 com o primeiro autor e instante preservados em repetições, inclusive por outro usuário ou em chamadas concorrentes. Alerta inexistente retorna 404; ausência de autenticação retorna 401 quando o CSRF é válido, e CSRF ausente/inválido retorna 403. Dados de autoria enviados no corpo são ignorados. Reconhecimento não comprova recuperação elétrica.


## Persistência de comunicação — US23

A V10 adiciona monitorada_desde e ultima_comunicacao_em às fontes. Fontes existentes recebem o início do monitoramento no instante da migração e o maior recebido_em das medições de linhas e transformadores como último contato; fontes sem leituras mantêm o contato nulo. A migração preserva fontes e leituras existentes. Novas fontes recebem início UTC na persistência.

alerta_comunicacao armazena fonte, ativo, snapshots temporais, tolerância positiva e detecção. ATIVO exige restabelecimento nulo; RESTABELECIDO exige instante preenchido. Fonte e ativo são associações LAZY sem cascata. O motor de avaliação garante um único episódio ativo por fonte com bloqueio pessimista. As ingestões de linhas e transformadores atualizam o contato e restabelecem automaticamente episódios ativos na mesma transação da medição.


### Avaliação manual e consultas de comunicação

POST /api/jobs/avaliar-comunicacao não exige corpo e requer sessão, CSRF e perfil OPERADOR ou GERENTE_OPERACOES. O timeout é configurado em monitoramento.comunicacao.timeout-segundos, com padrão 300 e variável de ambiente COMUNICACAO_TIMEOUT_SEGUNDOS; valores não positivos impedem a inicialização.

A avaliação usa um único instante UTC, percorre IDs em lotes de 100 até o maior ID observado no início e confirma uma transação por fonte, mantendo o bloqueio durante a consulta/criação do episódio. Fontes cadastradas durante a execução podem ficar para a próxima avaliação. Perda exige agora > base + timeout; igualdade ainda está dentro da tolerância. A base é ultimaComunicacaoEm ou monitoradaDesde. A resposta contém avaliadoEm, toleranciaSegundos, fontesAvaliadas, alertasCriados e alertasAtivosExistentes. Repetições não duplicam episódios ativos nem alteram seus snapshots. Uma falha interrompe a execução, mas não reverte fontes já confirmadas.

GET /api/alertas-comunicacao exige sessão e retorna PageResponseDTO, page=0/size=20, máximo 100, ordem detectadoEm DESC/id DESC; sort é rejeitado. Filtros opcionais fonteId/ativoId positivos, status ATIVO/RESTABELECIDO e dataInicio inclusiva/dataFim exclusiva em ISO 8601 com offset, sobre detectadoEm. Parâmetros inválidos, vazios ou repetidos retornam 400. GET /api/alertas-comunicacao/{id} retorna detalhe ou 404. Todos os instantes do DTO são normalizados para UTC. Nenhum scheduler é executado automaticamente.


### Restabelecimento automático de comunicação

As ingestões de linhas e transformadores adquirem o bloqueio da fonte antes de capturar recebidoEm. FonteMedicaoService.registrarComunicacaoValida exige a transação da ingestão e usa o mesmo instante UTC, com precisão de microssegundos, para ultimaComunicacaoEm e restabelecidoEm. Um episódio ATIVO passa para RESTABELECIDO; episódios anteriores mantêm seus snapshots e instante de restabelecimento. Leituras históricas válidas comprovam contato pelo recebimento atual.

Medição, contato, recuperação e geração de alertas elétricos são atômicos. Falhas posteriores ou leituras rejeitadas não confirmam contato nem restabelecimento. Job e ingestões usam a mesma ordem de bloqueio da fonte; uma nova ausência após a tolerância pode gerar outro episódio. Comunicação restabelecida não implica normalidade elétrica. O recorte atual atende fontes de linhas e transformadores; comunicação de medidores e integração com o frontend permanecem pendentes.


## Projeção unificada de alertas — US24

A V11 adiciona criticidade obrigatória aos alertas automáticos e cria vw_alertas_dashboard com UNION ALL dos alertas genéricos, sobrecarga, tensão e comunicação. O backfill atribui ALTA a sobrecarga/tensão e MEDIA à comunicação; a geração mantém a política como snapshot persistido. São aceitos BAIXA, MEDIA, ALTA e CRITICA. A classificação dos alertas genéricos é preservada.

AlertaDashboardView é uma entidade imutável com identidade composta idOrigem/tipoAlerta, sem colisão entre IDs de tabelas diferentes. O repositório oferece somente leitura e paginação. A view preserva os estados originais, auditoria disponível e snapshots técnicos; não duplica registros em tabela auxiliar. Campos inexistentes na origem permanecem nulos. O endpoint unificado disponibiliza consultas paginadas e filtros, preservando as APIs especializadas.


### Consulta do histórico unificado

GET /api/alertas-dashboard requer sessão autenticada e permite todos os perfis existentes. Retorna PageResponseDTO com page=0/size=20, máximo 100. A consulta e a contagem são executadas na view com os mesmos filtros; o conteúdo é limitado no banco. A ordem é ocorridoEm DESC NULLS LAST, tipoAlerta ASC, idOrigem DESC. O parâmetro sort é rejeitado.

Filtros opcionais: tipoAlerta (GENERICO/SOBRECARGA/TENSAO/COMUNICACAO), criticidade (BAIXA/MEDIA/ALTA/CRITICA), escopo (ATIVO/REDE_MONITORADA), ativoId/fonteId positivos, statusOrigem (ABERTO/ATIVO/RECONHECIDO/RESOLVIDO/RESTABELECIDO), dataInicio inclusiva e dataFim exclusiva sobre ocorridoEm. Datas devem usar ISO 8601 com offset, com início anterior ao fim. Valores inválidos, vazios ou repetidos retornam 400; combinações válidas sem correspondência retornam página vazia. Exemplo: /api/alertas-dashboard?size=50&criticidade=ALTA.

O DTO expõe idOrigem e tipoAlerta para identificar a origem sem colisões, além de campos estruturados e descricao em português. A descrição usa separador decimal pt-BR, pelo menos duas casas e até oito casas relevantes; não altera os valores técnicos numéricos. Todos os instantes são normalizados para UTC. Campos legados ausentes permanecem nulos; registros sem data ficam ao final e são excluídos por filtros temporais. O histórico inclui todos os estados por padrão, sem converter reconhecimento em resolução.
