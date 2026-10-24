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

O comando compila o projeto, executa os testes de contexto, persistência e credenciais e gera o JAR em `target/`. Os resultados são exibidos no terminal, sem relatórios TXT/XML. Os testes utilizam JUnit Jupiter 6 e AssertJ; a persistência utiliza H2 em memória, com a mesma migration Flyway usada pela aplicação.

## Banco de dados

Crie o banco PostgreSQL e disponibilize credenciais de um usuário com permissão de criação de tabelas. Configure as variáveis de ambiente:

| Variável | Valor padrão |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/power_grid_control_center` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | Obrigatória, sem valor padrão |
| `SERVER_PORT` | `8080` |

Flyway aplica as migrations de `src/main/resources/db/migration/`. Hibernate utiliza `ddl-auto: validate` e não cria nem atualiza tabelas automaticamente. A entidade `Usuario` mapeia a tabela `usuario`, com um `PerfilAcesso` por usuário; e-mail e matrícula são obrigatórios e únicos. As demais entidades ainda não estão mapeadas.

## Execução

Dentro de `backend/`:

```bash
mvn spring-boot:run
```

O servidor utiliza a porta `8080` por padrão. A aplicação ainda não possui endpoints de domínio nem uma `SecurityFilterChain` própria; as requisições seguem a configuração automática do Spring Security. Use `Ctrl+C` para encerrar.

## Dependências

- `spring-boot-starter-web`.
- `spring-boot-starter-data-jpa`.
- `spring-boot-starter-security`.
- `spring-boot-flyway` e `flyway-core`.
- `flyway-database-postgresql`.
- `postgresql`, no escopo de execução.
- `lombok`, como dependência opcional e processador de anotações, excluído do JAR executável.
- `spring-boot-starter-test`, no escopo de testes.
- `spring-boot-starter-data-jpa-test`, no escopo de testes.
- `h2`, no escopo de testes.

O `pom.xml` utiliza Java 24 e mantém a estrutura e a formatação do Spring Initializr. As versões das dependências são gerenciadas pelo parent do Spring Boot 4.1.1.

## Credenciais

E-mails recebem `trim` e conversão para minúsculas com `Locale.ROOT` antes de inserções e atualizações JPA e antes da consulta de credenciais. `UsuarioUserDetailsService` carrega o usuário pelo e-mail normalizado e converte seu perfil em uma autoridade `ROLE_...`. O bean `PasswordEncoder` utiliza BCrypt.

O fluxo HTTP utilizará sessão com cookie HttpOnly e proteção CSRF, sem JWT. Os contratos de login/logout, CORS e a matriz de autorização por rotas permanecem pendentes. O cadastro deverá exigir senha com pelo menos oito caracteres; essa validação será implementada junto ao cadastro.

O provisionamento inicial será feito pela futura migration `V2__inserir_usuarios_admin.sql`, com e-mails normalizados e hashes BCrypt previamente calculados para pelo menos um usuário de cada perfil. Essa migration ainda não existe, e a V1 não insere contas.
