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
