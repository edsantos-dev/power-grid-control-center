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

A requisição exige ativoId e responsavelUsuarioId positivos e existentes, categoria MANOBRA, DESLIGAMENTO_PROGRAMADO ou MANUTENCAO, descrição não vazia e inicioPrevisto com fuso. TerminoPrevisto é opcional e não pode anteceder o início, considerando os instantes; igualdade e previsões passadas são permitidas. Criticidade é opcional, com até 32 caracteres. Entrada inválida retorna 400; ativo ou responsável inexistente, 404.

O registrador é obtido da sessão pelo serviço. A criação fixa ABERTO e mantém as datas efetivas nulas. A resposta contém os IDs do ativo e dos usuários, categoria, descrição, criticidade, situação e datas, sem credenciais. A evolução de situação e as datas efetivas usam a API de transições da US18. Integração com o frontend e registro granular de executores permanecem pendentes.

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


## Persistência de transições de eventos — US18

A V12 cria historico_evento_operacional, com evento e usuário obrigatórios, estados anterior/novo, alterado_em e comentario obrigatório de até 1.000 caracteres. O CHECK aceita apenas ABERTO → EM_ANDAMENTO e EM_ANDAMENTO → CONCLUIDO. A entidade é imutável para atualizações pelo Hibernate e o repositório expõe somente inserção e consultas. A leitura por evento é paginada.

A migração normaliza espaços e maiúsculas de situações conhecidas. Datas, situações desconhecidas/nulas e vínculos existentes são preservados, sem histórico retroativo. As datas efetivas já estão mapeadas em EventoOperacional. O serviço transacional e os endpoints de transições estão implementados.


### Serviço de transição de eventos

EventoOperacionalService.transicionar recebe o ID do evento, situacaoEsperada, situacaoDestino e comentário obrigatório de até 1.000 caracteres. A identidade vem da sessão; as permissões usam o perfil salvo no banco. GERENTE_OPERACOES atua em qualquer evento; OPERADOR e TECNICO_MANUTENCAO somente nos eventos dos quais são responsáveis. Falta de sessão/usuário válido resulta em 401; falta de permissão, em 403.

O evento é bloqueado pessimisticamente e a situação esperada é conferida antes da transição. São permitidos apenas ABERTO → EM_ANDAMENTO e EM_ANDAMENTO → CONCLUIDO. Conflitos de situação, transições inválidas e dados efetivos legados inconsistentes resultam em 409; evento inexistente resulta em 404; argumentos ou comentário inválidos resultam em 400.

O servidor preenche datas efetivas em UTC, com precisão de microssegundos: preserva o início existente e valida início obrigatório/término não anterior ao início na conclusão. Estado, datas e histórico são confirmados na mesma transação. Falha no histórico desfaz as alterações do evento. Os endpoints de criação e consulta de transições estão disponíveis conforme o contrato abaixo.


### Endpoints de transições — US18

POST /api/eventos-operacionais/{id}/transicoes exige sessão e CSRF. O corpo contém situacaoEsperada, situacaoDestino e comentario não branco de até 1.000 caracteres. O autor é obtido da sessão; campos extras de usuário ou instante não definem a auditoria. Sucesso retorna 201, header Location e HistoricoEventoResponseDTO (id, eventoId, usuarioId, situacaoAnterior, situacaoNova, alteradoEm UTC, comentario). Validação retorna 400, permissão insuficiente 403, evento ausente 404 e conflito de situação/transição 409.

GET /api/eventos-operacionais/{id}/transicoes exige sessão e retorna PageResponseDTO com padrões page=0/size=20 e máximo 100. A ordem é fixa alteradoEm DESC/id DESC; sort, parâmetros não suportados e paginação inválida retornam 400. Evento existente sem transições retorna página vazia; evento inexistente retorna 404. A consulta é limitada e contada no banco por evento.

GET /api/eventos-operacionais/{id}/transicoes/{transicaoId} exige sessão e retorna o detalhe. Transição inexistente ou pertencente a outro evento retorna 404. As leituras seguem as permissões das consultas existentes de eventos; o POST aplica a restrição de responsável ou gerente. Transições são novas operações de auditoria: repetir uma solicitação com situação esperada ultrapassada retorna 409.


## Consultas de manutenção por ativo — US19

EventoOperacionalRepository.buscarManutencoesPorAtivo seleciona somente eventos MANUTENCAO do ativo informado. Suporta situação, responsável e período opcional sobre inicioEfetivo (início inclusivo/fim exclusivo). A paginação ocorre no banco, com ordem fixa inicioEfetivo DESC NULLS LAST/id DESC. Eventos sem início efetivo ficam ao final e não atendem filtros temporais.

HistoricoEventoOperacionalRepository.buscarUltimasTransicoes consulta em lote os IDs da página e retorna apenas a última transição por alteradoEm/id. Lista vazia não executa SQL e eventos sem histórico não geram linha. A consulta completa usa contagem, página e histórico em lote, evitando buscas individuais por evento. ManutencaoService orquestra as consultas e o endpoint retorna a página com o evento e sua última transição.


### Endpoint de manutenção por ativo

GET /api/ativos-rede/{ativoId}/manutencoes exige sessão e permite os três perfis. Valida a existência na tabela pai ativo_rede: inexistente retorna 404; existente sem manutenção correspondente retorna 200 com página vazia. Aceita os subtipos de ativos cadastrados.

A resposta usa PageResponseDTO com page=0/size=20 por padrão e máximo 100. Cada item contém evento (EventoOperacionalResponseDTO) e ultimaTransicao (HistoricoEventoResponseDTO ou nulo), com instantes UTC. O histórico completo é consultado por /api/eventos-operacionais/{eventoId}/transicoes.

Filtros opcionais: situacao (ABERTO/EM_ANDAMENTO/CONCLUIDO), responsavelUsuarioId positivo, dataInicio inclusiva e dataFim exclusiva sobre inicioEfetivo. Datas usam ISO 8601 com offset e início deve ser anterior ao fim. Sem período, inclui todos os estados; início efetivo nulo fica ao final. Com período, eventos sem início efetivo são excluídos. A ordem fixa é inicioEfetivo DESC NULLS LAST/id DESC. Sort, parâmetros não suportados e valores inválidos/vazios/repetidos retornam 400. Exemplo: /api/ativos-rede/1/manutencoes?situacao=CONCLUIDO&size=10.

A composição usa quatro consultas para uma página preenchida, incluindo validação do ativo; a última transição é buscada em lote, sem N+1 na montagem dos DTOs. Página vazia usa apenas validação e contagem. Responsável e autor da transição não comprovam execução da manutenção; executores granulares e relatórios técnicos estruturados permanecem fora deste recorte.


### Disponibilidade do cadastro de manutenção

POST /api/eventos-operacionais aceita MANOBRA, DESLIGAMENTO_PROGRAMADO e MANUTENCAO. A manutenção usa os mesmos campos obrigatórios e validações temporais do cadastro existente. Não exige ativo em FALHA nem altera seu estado operacional. A criação define ABERTO, autoria pela sessão e datas efetivas nulas. INTERRUPCAO_FORNECIMENTO, SOBRECARGA e FALHA_EQUIPAMENTO permanecem bloqueadas no POST manual. ManutencaoFluxoE2ETest cobre a integração ponta a ponta da API: cadastro, início, conclusão e consulta por ativo, com sessão/CSRF e transações independentes por requisição.


## Cadastro e consulta de equipes — US04

POST /api/equipes recebe nome obrigatório (não branco, até 255 caracteres após trim), responsabilidades e especialidades opcionais. Retorna 201, Location e EquipeResponseDTO com id, campos textuais e situacao. A situação inicial é sempre FORA_DE_SERVICO; o corpo não define a disponibilidade. PUT /api/equipes/{id} substitui somente os campos textuais e preserva a situação existente. Campos opcionais omitidos ficam nulos. Ambas as operações exigem sessão, CSRF e perfil GERENTE_OPERACOES ou TECNICO_MANUTENCAO. OPERADOR recebe 403 nas escritas.

GET /api/equipes/{id} e GET /api/equipes permitem todos os perfis autenticados. Equipe inexistente retorna 404. A listagem usa PageResponseDTO, page=0/size=20 por padrão, máximo 100 e ordem fixa id ASC. Aceita nome (trecho sem diferenciar maiúsculas/minúsculas, com caracteres de LIKE tratados literalmente) e situacao (DISPONIVEL, EM_ATENDIMENTO, FORA_DE_SERVICO). Sort, parâmetros desconhecidos e valores inválidos, vazios ou repetidos retornam 400. Ausência de correspondências retorna 200 com página vazia.

EquipeManutencao e VinculoEquipe reutilizam equipe_manutencao e vinculo_equipe da V1. O vínculo conserva os períodos encerrados e permite integrantes em equipes diferentes. O início de vigência é obrigatório no mapeamento JPA; a coluna legada ainda aceita nulo no banco. A API gerencia integrantes e disponibilidade manual conforme o contrato abaixo. O cadastro da equipe não atribui atendimento a eventos nem comprova execução de manutenção.


### Integrantes e disponibilidade de equipes

GET /api/equipes/{id}/integrantes retorna somente vínculos abertos, com paginação page=0/size=20, máximo 100 e ordem id ASC. Exige sessão e permite todos os perfis. O DTO expõe id do vínculo, equipeId, usuarioId, nome, perfil e instantes de vigência UTC; não expõe credenciais. Sort, parâmetros desconhecidos e paginação inválida retornam 400. Equipe inexistente retorna 404; equipe sem integrantes retorna página vazia.

PUT /api/equipes/{id}/integrantes/{usuarioId} não exige corpo. Retorna 201 ao abrir um vínculo, com inicioVigencia gerado pelo servidor; vínculo já aberto retorna 200 com os dados originais. DELETE na mesma rota encerra a vigência com instante UTC e retorna 204. Repetir a exclusão, ou remover usuário existente sem vínculo, retorna 204 sem alterar o histórico. Equipe ou usuário inexistente retorna 404. Um novo ingresso após encerramento cria outro vínculo, preservando o anterior. Todos os perfis podem ser integrantes e um usuário pode integrar várias equipes simultaneamente.

PATCH /api/equipes/{id}/situacao recebe situacao obrigatória: DISPONIVEL, EM_ATENDIMENTO ou FORA_DE_SERVICO, retornando a equipe com 200. DISPONIVEL e EM_ATENDIMENTO exigem ao menos um vínculo aberto; sem integrante ativo, retorna 409. Remover o último integrante nesses estados também retorna 409, exigindo colocar a equipe FORA_DE_SERVICO antes. A disponibilidade é declarada manualmente; não reserva capacidade individual nem é derivada de eventos.

PUT de integrantes, DELETE e PATCH de situação exigem sessão, CSRF e perfil GERENTE_OPERACOES ou TECNICO_MANUTENCAO. As três operações usam transação e bloqueio pessimista na equipe pai. A edição textual também usa esse bloqueio, evitando sobrescrita concorrente da situação. A prevenção de vínculos ativos duplicados ocorre no serviço sob o mesmo bloqueio, sem nova constraint no schema legado. A origem local do frontend aceita DELETE no CORS. Designação de equipe em eventos, registro de executores e integração da interface permanecem fora deste recorte.


## Persistência de usinas geradoras — US05

UsinaGeradora herda AtivoRede pela estratégia JOINED, compartilhando a identidade entre ativo_rede e usina_geradora. O cadastro representa um nó único de geração, sem unidades geradoras individuais. TipoGeracao admite HIDRELETRICA, SOLAR, EOLICA e TERMICA. Capacidade máxima é armazenada em MW como NUMERIC(19,6), obrigatória e estritamente positiva; não representa geração instantânea. Cada usina referencia uma subestação obrigatória por associação LAZY, sem cascata de persistência ou exclusão para a subestação. O repositório permite consultas paginadas por tipo de geração e subestação, isolados ou combinados.

A V13 acrescenta subestacao_id, sua FK, obrigatoriedade de tipo e capacidade e os CHECKs correspondentes. Como a V1 não registra a subestação da usina, não há backfill automático confiável. A migration remove cadastros inconsistentes somente quando não possuem referências externas, excluindo a usina e seu registro pai. Eventos e seu histórico, alertas, fontes com suas medições e outros subtipos de ativo são preservados. Cadastros já regularizados explicitamente são mantidos. Se restarem usinas referenciadas incompatíveis com as novas restrições, a migração falha, exigindo regularização manual. Não são criadas associações fictícias.

Service, DTOs e endpoints de usinas estão disponíveis conforme o contrato abaixo. A API não disponibiliza exclusão ou inativação cadastral de usinas nesta etapa.


### API de usinas geradoras

POST /api/usinas retorna 201, Location e UsinaResponseDTO. PUT /api/usinas/{id} retorna 200 e substitui os atributos cadastrais. Ambos exigem sessão, CSRF e perfil GERENTE_OPERACOES; OPERADOR e TECNICO_MANUTENCAO recebem 403. O corpo exige nome não branco com trim e máximo de 255 caracteres, latitude/longitude dentro dos intervalos inclusivos [-90,90]/[-180,180] com até sete casas decimais, tipoGeracao, capacidadeMaximaMw positiva com até 13 dígitos inteiros e seis decimais e subestacaoId positivo. Precisão excedida retorna 400, sem arredondamento silencioso. Data de instalação é opcional, permitindo datas futuras. Estado omitido ou nulo assume OPERACIONAL na criação e atualização; valores aceitos são OPERACIONAL, FALHA e MANUTENCAO. Subestação inexistente retorna 404.

GET /api/usinas/{id} permite todos os perfis autenticados e retorna o cadastro ou 404. O DTO expõe id, nome, coordenadas, tipoGeracao, capacidadeMaximaMw, subestacaoId, dataInstalacao e estadoOperacional. GET /api/usinas usa PageResponseDTO, page=0/size=20 por padrão e máximo 100, ordenado por id ASC. Filtros opcionais: nome (trecho sem diferenciar maiúsculas/minúsculas, caracteres de LIKE tratados literalmente), tipoGeracao, estadoOperacional e subestacaoId positivo. Sem correspondência, retorna 200 com página vazia. Sort, parâmetros desconhecidos, vazios, repetidos ou inválidos retornam 400.

O vínculo cadastral pode mudar no PUT, sem alterar a identidade da usina. Geração instantânea, unidades geradoras, inativação e exclusão não integram esta API. A integração da tela de usinas permanece pendente.


## Cadastro específico de interrupções — US17

POST /api/interrupcoes exige sessão autenticada e CSRF, com o mesmo acesso do cadastro de eventos operacionais para os três perfis. Recebe ativoId e responsavelUsuarioId positivos e obrigatórios, descricao não branca (com trim), inicioEfetivo obrigatório com fuso e criticidade opcional de até 32 caracteres. O início informado não pode superar o relógio UTC do servidor; não há tolerância de sensor. Datas passadas e o instante atual são aceitos. Ativo ou responsável inexistente retorna 404; contrato ou data inválida retorna 400.

O serviço cria EventoOperacional de categoria INTERRUPCAO_FORNECIMENTO e situação ABERTO. O registrador é obtido da sessão. O instante físico informado é preservado em UTC com precisão de microssegundos, sem substituição pelo horário do cadastro. Previsões e término efetivo permanecem nulos; campos extras de categoria, situação ou autoria não controlam esses valores. O estado do ativo não é modificado. Sucesso retorna 201, EventoOperacionalResponseDTO e Location para /api/eventos-operacionais/{id}, recurso já consultável.

O POST genérico de eventos mantém a interrupção bloqueada para evitar contornar o contrato específico. Não há nova tabela nem migration. A conclusão recebe o término físico conforme o contrato abaixo. Consultas específicas de interrupções e fluxo E2E estão disponíveis conforme o contrato abaixo.


### Término físico e auditoria de interrupções

POST /api/eventos-operacionais/{id}/transicoes aceita o campo opcional terminoEfetivo no DTO compartilhado. Para concluir INTERRUPCAO_FORNECIMENTO, esse campo é obrigatório, não pode estar no futuro e deve ser maior ou igual ao inicioEfetivo. Ausência ou período inválido retorna 400; interrupção legada sem início exige regularização, com 409. O instante informado é convertido para UTC e microssegundos, preservando o início físico existente. Término manual fora da conclusão de interrupções retorna 400. Nas demais categorias, o término continua preenchido pelo relógio UTC do servidor.

A data alteradoEm do histórico sempre vem do servidor, independentemente do término físico informado. É permitido registrar restabelecimento anterior ao início operacional do atendimento, desde que respeite o início físico da ocorrência. As permissões de responsável/gerente, o bloqueio pessimista, a situação esperada e a máquina de estados da US18 permanecem aplicáveis. Estado, término e histórico são persistidos na mesma transação; falha de auditoria desfaz a conclusão. Repetir uma conclusão com situação esperada ultrapassada retorna 409.


### Consultas de interrupções

GET /api/interrupcoes/{id} exige sessão e retorna EventoOperacionalResponseDTO somente para INTERRUPCAO_FORNECIMENTO. ID inexistente ou de outra categoria retorna 404; identificador inválido retorna 400. GET /api/interrupcoes permite os três perfis autenticados, retornando PageResponseDTO com page=0/size=20, máximo 100. A ordenação é fixa inicioEfetivo DESC/id DESC, com início nulo legado ao final. Conteúdo e total são consultados no banco com os mesmos filtros; a página não é formada em memória.

Filtros opcionais: ativoId positivo, situacao (ABERTO, EM_ANDAMENTO, CONCLUIDO), dataInicio inclusiva e dataFim exclusiva sobre inicioEfetivo. Datas devem usar ISO 8601 com fuso; início do período deve anteceder o fim. Parâmetros desconhecidos, vazios ou repetidos, sort e paginação inválida retornam 400. Filtros sem correspondência retornam 200 com página vazia; ativoId válido sem ocorrências também retorna página vazia. Registros sem início efetivo não atendem filtros temporais.

InterrupcaoFluxoE2ETest cobre a jornada HTTP autenticada, com CSRF e transações independentes por requisição: cadastro com início físico passado, atendimento, restabelecimento físico informado, detalhe, listagem filtrada e consulta do histórico. As datas físicas permanecem distintas dos instantes de auditoria do atendimento. Não há novos atributos estruturados de causa, consumidores afetados ou fases; a integração com a interface continua pendente.


## Persistência de energia por alimentador — US12

A V14 adiciona percentual_perda_tecnica opcional a alimentador, sem preencher parâmetros de cadastros existentes, e cria medicao_energia_alimentador. A medição referencia FonteMedicao por associação LAZY obrigatória, sem cascata; o alimentador é derivado do ativo da fonte. O parâmetro cadastral e o snapshot percentualPerdaReferencia usam NUMERIC(9,6), no intervalo [0,100), preservando a precisão na cópia. Energia injetada é armazenada em MWh como NUMERIC(19,6), não negativa.

Fonte, início/fim do período, recebimento, energia e percentual de referência são obrigatórios. O fim deve ser posterior ao início e uma mesma fonte não pode repetir o mesmo período. O repositório verifica sobreposição entre todas as fontes do alimentador pela condição novoInicio < fimPeriodo e novoFim > inicioPeriodo. Intervalos adjacentes não se sobrepõem. A constraint de unicidade impede duplicidade exata por fonte; a prevenção de sobreposição e concorrência entre fontes é aplicada no serviço, sob bloqueio do alimentador. A FK comprova a existência da fonte; seu pertencimento ao alimentador é validado no serviço.

A API disponibiliza configuração, ingestão, consultas e cálculo sob demanda. A estimativa parametrizada prevista é distinta de balanço energético medido: perdaEstimadaMwh = energiaInjetadaMwh × percentualReferencia / 100. O snapshot persistido permite preservar o parâmetro de referência de cada leitura; a ingestão copia o parâmetro configurado e registra a comunicação da fonte na mesma transação. A avaliação de cobertura temporal e o cálculo estão disponíveis conforme o contrato abaixo.


### Configuração e ingestão de energia — US12

PATCH /api/alimentadores/{id}/parametros-perdas recebe percentualPerdaTecnica obrigatório no intervalo [0,100), com até seis casas decimais, e retorna 200 com alimentadorId e percentual. É exclusivo de GERENTE_OPERACOES. POST /api/alimentadores/{id}/fontes recebe identificador e origem, preserva unicidade global e retorna 201. Referência de subestação não se aplica a fontes de alimentadores e retorna 400. Gerente e operador podem cadastrar fontes e ingerir energia; técnico recebe 403. As escritas exigem sessão e CSRF.

POST /api/alimentadores/{id}/fontes/{fonteId}/medicoes-energia recebe inicioPeriodo, fimPeriodo e energiaInjetadaMwh obrigatórios. O intervalo deve estar encerrado, com início anterior ao fim, sem instantes futuros e com fuso; intervalos que colapsam na precisão de microssegundos são rejeitados. Energia é não negativa e aceita até 13 dígitos inteiros e seis casas, sem arredondamento silencioso. Sucesso retorna 201 com IDs, origem, período, recebimento UTC, energia e percentualPerdaReferencia copiado na ingestão.

Alimentador ou fonte inexistente retorna 404; vínculo divergente ou contrato inválido retorna 400. Parâmetro não configurado e sobreposição retornam 409. Intervalos adjacentes são aceitos; sobreposição é verificada entre todas as fontes do alimentador. Configuração e ingestão bloqueiam pessimisticamente o alimentador; a ingestão bloqueia em seguida a fonte, captura o horário do recebimento e reaproveita o registro de comunicação da US23. Medição, última comunicação e restabelecimento de alerta são atômicos. Rejeições não confirmam contato. A edição cadastral anterior do alimentador usa o mesmo bloqueio para preservar o parâmetro em disputas concorrentes. Alterar o percentual não modifica snapshots antigos. Consultas e cálculo ponderado estão disponíveis conforme o contrato abaixo.


### Estimativa ponderada e consultas de energia — US12

GET /api/alimentadores/{id}/perdas-tecnicas exige sessão e permite os três perfis. dataInicio e dataFim são obrigatórios, em ISO 8601 com fuso, e início deve anteceder o fim. Alimentador inexistente retorna 404; parâmetros inválidos, vazios, repetidos ou desconhecidos retornam 400. A resposta contém alimentadorId, período UTC, totalEnergiaInjetadaMwh, totalPerdaEstimadaMwh, percentualPonderado, situacao, percentualCobertura e medicoesConsideradas, com fonte, intervalo, energia e snapshot de cada leitura. O GET não altera dados.

São consideradas somente leituras integralmente contidas no período solicitado, de todas as fontes do alimentador, em ordem crescente de início e ID. Não há rateio de leitura que atravesse uma fronteira solicitada. A cobertura corresponde ao tempo utilizável dessas leituras dividido pela duração solicitada, usando instantes absolutos. É necessário cobrir continuamente todo o período. Lacunas, fronteiras sem leitura completa ou sobreposição legada resultam em DADOS_INSUFICIENTES, com totais e percentual ponderado nulos. As leituras consideradas permanecem disponíveis para rastreabilidade; a cobertura não é duplicada por sobreposições. A situação usa comparação temporal exata, independentemente do arredondamento do percentual exibido.

Com cobertura completa, cada perda é energiaInjetadaMwh × percentualPerdaReferencia / 100; as perdas são somadas antes do arredondamento. O percentual agregado é 100 × somaPerdas / somaEnergia, sem média simples dos percentuais e sem usar o parâmetro cadastral atual para recalcular snapshots. BigDecimal é usado em todas as etapas; somente os resultados finais são arredondados para seis casas com HALF_EVEN. Energia total zero retorna SEM_ENERGIA_INJETADA, perda zero e percentual nulo; os demais resultados completos retornam OK. Trata-se de estimativa parametrizada de simulação, não de perda técnica fisicamente apurada.

GET /api/alimentadores/{id}/medicoes-energia retorna PageResponseDTO, padrão page=0/size=20, máximo 100 e ordem inicioPeriodo DESC/id DESC. Filtros opcionais dataInicio inclusiva e dataFim exclusiva incidem sobre inicioPeriodo. Sort e parâmetros inválidos, desconhecidos, vazios ou repetidos retornam 400. Alimentador existente sem leituras retorna página vazia; inexistente retorna 404. A fonte é carregada em conjunto para compor os DTOs. A integração do frontend permanece pendente.

PerdaTecnicaFluxoE2ETest cobre configuração, cadastro de fonte, duas ingestões contíguas a 3,5%, alteração para 5%, terceira ingestão e consultas. O cenário de 400 MWh produz estimativa de 15,5 MWh e percentual ponderado de 3,875%, preservando os snapshots; ampliar o período sem novas leituras retorna dados insuficientes. As requisições usam autenticação, CSRF e transações independentes.


## Projeções geográficas — US13

O recorte inicial abrange SUBESTACAO, USINA, TRANSFORMADOR e LINHA. BoundingBox e CoordenadaGeografica validam os limites em graus decimais: latitude [-90,90], longitude [-180,180] e mínimos estritamente inferiores aos máximos. Caixas cruzando o antimeridiano não são aceitas neste recorte. A interpretação adotada para a API de mapa é WGS84; a API GeoJSON está disponível conforme o contrato abaixo.

AtivoRedeRepository.buscarPontos retorna projeções escalares de identificação, nome, estado, classe e coordenadas, filtradas por tipo, estado e BBOX no banco. Coordenadas ausentes não produzem localização substituta. Alimentadores e medidores ficam fora das camadas iniciais. LinhaTransmissaoRepository.buscarCandidatas projeta também os IDs e coordenadas das duas subestações, omitindo extremidades incompletas ou geograficamente coincidentes. O estado filtrado é o da própria linha. O ponto cadastral herdado pela linha não substitui a geometria de conexão.

O pré-filtro de linhas testa a interseção entre o envelope das extremidades e a BBOX, incluindo bordas. Ele pode retornar falsos positivos geométricos e precisa ser seguido por IntersecaoSegmentoRetangulo.intersecta. O teste exato usa orientação e contenção de segmentos com BigDecimal, sem divisão, epsilon ou conversão para double. Abrange segmentos cruzando a área com ambas as extremidades fora, tangências e coincidência com bordas. A conexão permanece uma representação simplificada entre subestações, sem afirmar o traçado físico.

As projeções não expõem entidades ou associações LAZY ao consumidor. Ambas as consultas têm ordem fixa por ID e permitem avanço por aposId e lotes via Pageable. Isso permite percorrer candidatas sem perder linhas válidas após falsos positivos do pré-filtro. O serviço aplica o limite global de Features e retorna 422 quando excedido, após a seleção geométrica correta. Não há migration espacial ou PostGIS neste recorte.


### API GeoJSON do mapa — US13

GET /api/mapa/ativos exige sessão e permite OPERADOR, GERENTE_OPERACOES e TECNICO_MANUTENCAO. Retorna application/geo+json com type=FeatureCollection, features e metadados (totalFeatures e totalAtivosSemGeometria). Cada Feature possui type, id textual estável, geometry e properties. Point usa [longitude, latitude]; LineString usa as coordenadas completas das extremidades. Properties contém ativoId, tipoAtivo, nome, estadoOperacional e detalheUri; linhas também incluem subestacaoOrigemId, subestacaoDestinoId e tipoRepresentacao=CONEXAO_SIMPLIFICADA. A geometria não representa o traçado físico nem a direção de fluxo de potência.

São obrigatórios minLat, maxLat, minLng e maxLng, com limites geográficos válidos e mínimos inferiores aos máximos. Não há suporte a caixas cruzando o antimeridiano. Tipos opcionais: USINA, SUBESTACAO, TRANSFORMADOR e LINHA; estados opcionais: OPERACIONAL, FALHA e MANUTENCAO. Listas são delimitadas por vírgula, com espaços nas extremidades dos itens removidos; itens repetidos representam o mesmo filtro. Sem listas, todas as camadas e estados são consultados. Parâmetros ausentes, vazios, repetidos, desconhecidos ou inválidos retornam 400.

O parâmetro opcional limite aceita inteiros de 1 a 5000, com padrão 1000. Features qualificadas são ordenadas pelo ID global do ativo. O serviço percorre pontos e candidatas de linhas em lotes por ID, sem interromper a busca por encontrar falsos positivos geométricos. Exceder o limite de Features efetivamente elegíveis retorna ApiError com 422 e mensagem orientando reduzir a área ou os filtros; nenhum mapa parcial é retornado. Falsos positivos do envelope não consomem o limite.

TotalAtivosSemGeometria considera camadas e estados selecionados, independentemente da BBOX: inclui coordenadas ausentes/inválidas e linhas com extremidades geograficamente coincidentes. Ativos válidos fora da área não entram nessa contagem. A leitura usa transação somente leitura com REPEATABLE_READ para manter a mesma visão dos dados entre lotes e contagens. MapaGeograficoFluxoE2ETest valida o fluxo com cadastros persistidos em transação confirmada e autenticação real por sessão: usina interna, subestação externa, transformador sem coordenadas e linha cruzando a BBOX com extremidades externas. A resposta contém dois elementos, contabiliza um ativo sem geometria e permite consultar os cadastros pelo detalheUri. A integração com o frontend permanece pendente.

## Indicadores operacionais — US14

GET /api/indicadores/operacionais é exclusivo de GERENTE_OPERACOES. dataInicio e dataFim são obrigatórios em ISO 8601 com fuso, início anterior ao fim e fim não futuro. Filtros opcionais ativoId, tipos e subestacaoId são combinados por interseção. tipos aceita SUBESTACAO, ALIMENTADOR, TRANSFORMADOR, LINHA e USINA separados por vírgula; omitir consulta todos. Parâmetros desconhecidos, repetidos, vazios ou inválidos retornam 400; ativo ou subestação inexistente retorna 404. A resposta é agregada, sem paginação, e não aceita page, size ou sort.

O retorno contém calculadoEm e período em UTC, filtrosAplicados, quantidadeAtivos por tipo/estado atual, totalAtivos, contagens de interrupções e os tempos em segundos. quantidadeIniciadasNoPeriodo usa o início físico na janela; quantidadeSobrepostasAoPeriodo conta ocorrências com interseção temporal positiva; quantidadeSemTerminoFisico conta ocorrências iniciadas antes do fim e ainda sem término preenchido. quantidadeRestabelecimentosNoPeriodo informa o denominador do tempo médio: interrupções CONCLUIDO cujo término físico está na janela. Tempos sobrepostos são fundidos por equipamento antes da soma.

O tempo observado é a duração da janela multiplicada pela população cadastral atual. A disponibilidade estimada usa esse denominador e recebe COBERTURA_NAO_AFERIDA, inclusive quando não há interrupções registradas. Sem população, retorna SEM_POPULACAO_ATIVA com percentual nulo; sem restabelecimentos elegíveis, o tempo médio é nulo. Registros de interrupção inconsistentes que possam afetar a janela são contados separadamente e produzem DADOS_INCONSISTENTES, com disponibilidade nula. Início ausente impede aferir a sobreposição com segurança; conclusão sem término também é inconsistente. Agregados temporais utilizáveis permanecem disponíveis, sem caracterizar uma disponibilidade completa. Cálculos finais usam seis casas com HALF_EVEN e as consultas compartilham transação somente leitura com REPEATABLE_READ.

IndicadoresFluxoE2ETest cobre o cadastro de um transformador, registro e conclusão de interrupção por HTTP e consulta dos KPIs com autenticação e CSRF nas escritas. Uma interrupção física de 10:00 a 12:00, em janela de 08:00 a 18:00 filtrada por ativoId, resulta em 7.200 segundos interrompidos, média de restabelecimento de 7.200 segundos, 36.000 segundos observados e disponibilidade estimada de 80,000000%. A situação permanece COBERTURA_NAO_AFERIDA. Datas de auditoria no dia seguinte não substituem os instantes físicos. O backend da US14 está concluído; a integração visual do frontend permanece pendente.

## Histórico de energia injetada e perdas — US15

GET /api/alimentadores/{id}/historico-energia é exclusivo de GERENTE_OPERACOES. Exige dataInicio e dataFim em ISO 8601 com fuso, início anterior ao fim, fim não futuro e granularidade=DIA ou MES. O calendário utiliza UTC e admite no máximo 366 pontos, incluindo os pontos parciais nas extremidades da janela. Parâmetros inválidos, desconhecidos, repetidos ou vazios e excesso de pontos retornam 400. Alimentador inexistente retorna 404; os demais perfis autenticados recebem 403. Não há paginação nem ordenação personalizada.

HistoricoEnergiaResponseDTO contém alimentadorId, dataInicio, dataFim, granularidade, fusoHorario=UTC, possuiDados e pontos em ordem cronológica crescente. Cada ponto contém inicioPeriodo, fimPeriodo, energiaInjetadaMwh, perdaEstimadaMwh, percentualPerdaPonderado, quantidadeLeituras e situacao. Os limites de cada ponto correspondem à interseção do dia/mês UTC com a janela solicitada. possuiDados indica se a consulta retornou leituras integralmente contidas na janela macro, mesmo quando elas não permitem formar pontos completos.

São somadas somente leituras integralmente contidas em cada ponto. Não há rateio: leituras que cruzem fronteiras tornam os pontos atravessados insuficientes. quantidadeLeituras conta somente as leituras contidas no ponto, não as leituras atravessadas. Lacunas, inclusive de microssegundos, e sobreposições legadas produzem DADOS_INSUFICIENTES, com totais e percentual nulos. Dias/meses sem leituras permanecem no calendário com valores nulos. Zero não substitui ausência de dados: SEM_ENERGIA_INJETADA exige cobertura completa com energia zero, perda zero e percentual nulo.

Cada perda estimada usa energia × percentualPerdaReferencia / 100. O percentual do ponto é ponderado pela energia; a consulta mensal usa as leituras originais, sem somar dias arredondados. BigDecimal preserva precisão intermediária; somente os resultados finais recebem seis casas com HALF_EVEN. O serviço usa projeções escalares em uma consulta, agrupa em Java e lê sob REPEATABLE_READ. Não há migration, novos índices, cache ou scheduler. Energia injetada e perdas estimadas não representam consumo medido dos consumidores.

HistoricoEnergiaFluxoE2ETest valida o cadastro e a ingestão por HTTP com autenticação e CSRF, seguidos de consulta de cinco dias em UTC. Os Dias 1, 2 e 4 retornam OK com 100/50/200 MWh e perdas de 4,4/2,5/4 MWh; os Dias 3 e 5 permanecem na lista com DADOS_INSUFICIENTES e valores nulos. O primeiro dia combina snapshots de 3,5% e 5%, produzindo percentual ponderado de 4,4%. Alterar o parâmetro atual para 9% não altera os resultados históricos. O backend deste recorte está concluído; integração visual e consumo medido permanecem fora da entrega atual.

## Telemetria de consumo dos medidores — US06

POST /api/medidores/{id}/fontes recebe somente origem (REAL ou SIMULADA) e retorna 201 com identificador gerado pelo servidor, medidorId, monitoradaDesde e ultimaComunicacaoEm. O identificador utiliza UUID e a unicidade global da tabela; identificador enviado pelo cliente não é utilizado. Medidor inexistente ou sem consumidor, transformador e data de instalação retorna 409 neste cadastro. Fontes novas começam com ultimaComunicacaoEm nula e monitoradaDesde UTC do servidor. Acesso restrito a GERENTE_OPERACOES e OPERADOR, com sessão e CSRF.

POST /api/medidores/{id}/medicoes-consumo recebe fonteId, inicioPeriodo, fimPeriodo e consumoKwh obrigatórios. Consumo é não negativo, com até 13 dígitos inteiros e seis decimais, sem arredondamento silencioso. O período exige fuso, início anterior ao fim e nenhum instante futuro. Após normalização UTC/microssegundos, precisa continuar crescente. Período anterior ao início UTC da data de instalação retorna 409; essa data não comprova a hora exata da instalação. Medidor ou fonte inexistente retorna 404, fonte de outro ativo retorna 400 e instalação incompleta retorna 409.

A ingestão obtém bloqueio pessimista no medidor e depois na fonte. Consulta a topologia para preencher consumidorId, transformadorId, alimentadorId e subestacaoId históricos, sem aceitar esses valores do cliente. Duplicidade ou sobreposição, inclusive entre fontes do mesmo medidor, retorna 409; períodos adjacentes são permitidos. A leitura retorna 201 com Location para a consulta individual.

RecebidoEm é capturado após os bloqueios, pelo relógio UTC do servidor. A leitura e FonteMedicaoService.registrarComunicacaoValida compartilham a transação: ultimaComunicacaoEm e eventual restabelecidoEm recebem o mesmo instante. Falhas posteriores desfazem consumo, contato e restabelecimento; requisições rejeitadas não confirmam contato. O job manual da US23 avalia também essas fontes. O tipo REAL é uma classificação da origem, não autenticação de hardware.

GET /api/medidores/{id}/medicoes-consumo/{medicaoId} é permitido a todos os usuários autenticados e retorna consumo, origem, período, recebidoEm e snapshots persistidos. Medidor/medição inexistente ou leitura de outro medidor retorna 404. Mudanças cadastrais posteriores não recalculam as referências históricas. Troca/reinstalação e agregações de consumo da US15 permanecem fora deste incremento. O fluxo E2E integrado está implementado.

MedicaoConsumoFluxoE2ETest cobre toda a cadeia por HTTP: subestação, alimentador, transformador, titular, medidor em estoque, instalação e fonte. Com relógio controlado, o job detecta ausência após 300 segundos e a leitura posterior de 150,5 kWh restaura a comunicação. A consulta individual confirma os snapshots de consumidor, transformador, alimentador e subestação; a base confirma ultimaComunicacaoEm e restabelecidoEm iguais ao recebidoEm. O backend deste complemento da US06 está concluído; integração visual e histórico agregado de consumo permanecem pendentes.
