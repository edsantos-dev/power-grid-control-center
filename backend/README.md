# Power Grid Control Center — Backend

Base do backend em Java 24 e Spring Boot 4.1.1.

## Pré-requisitos

- JDK 24.
- Maven 3.6.3 ou superior.
- Acesso à internet para baixar as dependências na primeira execução.

Configure `JAVA_HOME` para o diretório do JDK 24 e confira a versão usada pelo Maven:

```bash
mvn --version
```

## Build e testes

A partir da raiz do repositório:

```bash
cd backend
mvn verify
```

O comando compila o projeto, executa o teste `contextLoads()` e gera o JAR em `target/`. O teste verifica o carregamento do contexto Spring.

## Execução

Dentro de `backend/`:

```bash
mvn spring-boot:run
```

O servidor utiliza a porta `8080` por padrão. A aplicação ainda não possui endpoints; uma requisição para `/` retorna HTTP 404. Use `Ctrl+C` para encerrar.

## Dependências

- `spring-boot-starter-web`.
- `spring-boot-starter-test`, no escopo de testes.

O `pom.xml` utiliza Java 24 e mantém a estrutura e a formatação do Spring Initializr. As versões das dependências são gerenciadas pelo parent do Spring Boot 4.1.1.
