# Backlog do projeto

O backlog reúne as histórias, os critérios de aceitação, as tarefas e as pendências do Power Grid Control Center. As Sprints preservam o planejamento de cada período; este documento registra o estado atual do desenvolvimento.

## Estado do projeto

O frontend possui páginas com dados simulados, filtros e navegação local. O backend possui configuração PostgreSQL/JPA/Flyway, schema SQL inicial, persistência de usuários e autenticação HTTP por sessão, com CORS e CSRF. A V2 provisiona os três perfis e a segurança por método está habilitada. As demais entidades Java e a integração com o frontend ainda não foram implementadas.

US01 está **Em andamento**, com persistência, criação, consulta e edição de subestações em revisão; US02 está **Em andamento**, com backend de Alimentador em revisão; US03 está **Em andamento**, com persistência e API de Transformador em revisão; US16 está **Em andamento**, com persistência e API de eventos em revisão; US20 está **Em andamento**, com persistência e API de alertas em revisão; US04–US15, US17–US19 e US21–US24 permanecem **A fazer** para o trabalho restante. O recorte de backend de US25 está **Em revisão**, com persistência, autenticação HTTP, contas iniciais e segurança por método. A presença de uma tela não representa a conclusão da história. O mapa geográfico ainda não está implementado, e o login do frontend realiza apenas validações locais antes da navegação.

Os arquivos de serviço de API e autenticação do frontend estão vazios. Os testes existentes cobrem contexto, persistência, credenciais, autenticação HTTP, sessão, CORS e CSRF. A política de gestão de acesso restringe as futuras operações ao Gerente de Operações; esses endpoints ainda não existem.

## Stack tecnológica

| Componente | Tecnologia |
|---|---|
| Linguagem do backend | Java 24 |
| Framework | Spring Boot 4.1.1 |
| Segurança | Spring Security, na versão gerenciada pelo Spring Boot 4.1.1 |
| Banco de dados | PostgreSQL |
| Migrações | Flyway, integrado ao Spring Boot |
| Build do backend | Maven, com estrutura gerada pelo Spring Initializr |
| Frontend existente | React, TypeScript e Vite |

O backend utiliza Java 24 com Spring Boot 4.1.1. As versões das dependências gerenciadas pelo framework seguem seu BOM. JPA, PostgreSQL e Flyway compõem a configuração de persistência; H2 é exclusivo dos testes. Spring Security fornece a base de credenciais e o encoder BCrypt.

A autenticação utiliza sessão HTTP com cookie HttpOnly e proteção CSRF, sem JWT. A `SecurityFilterChain` configura login/logout, rotas públicas e exigência de autenticação nas demais rotas. Os contratos HTTP estão no README do backend. Login e obtenção de CSRF são públicos; as demais rotas exigem autenticação. Futuras operações de gestão de acesso devem usar `@PreAuthorize("hasRole('GERENTE_OPERACOES')")`, com `@EnableMethodSecurity` habilitado.

PostgreSQL é o banco relacional e Flyway é responsável pelas migrations do schema. A conexão utiliza variáveis de ambiente. A versão do servidor permanece a definir; o suporte a PostgreSQL está presente nas dependências do Flyway.

### Estrutura do pom.xml

O projeto Maven usa o `pom.xml` retornado pelo [Spring Initializr](https://start.spring.io/) para Spring Boot 4.1.1, preservando a formatação, a ordem dos elementos, o parent e os plugins.

A propriedade `java.version` utiliza o valor 24. As dependências são Spring Web, Jakarta Validation via starter-validation, Spring Data JPA, Spring Security, integração Spring Boot/Flyway, Flyway Core, suporte Flyway/PostgreSQL e driver PostgreSQL. Lombok é opcional e possui processamento de anotações configurado pelo `maven-compiler-plugin`; o `spring-boot-maven-plugin` o exclui do JAR executável. Spring Boot Test, Spring Boot Data JPA Test, Spring Boot Web MVC Test e H2 utilizam escopo `test`, com JUnit Jupiter 6 e AssertJ nas versões gerenciadas pelo framework.

### Referências técnicas

- [Requisitos do Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html).
- [Gerenciamento de dependências do Spring Boot](https://docs.spring.io/spring-boot/appendix/dependency-versions/index.html).
- [Configuração Java do Spring Security](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html).
- [Inicialização do banco e Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
- [Versões e suporte do JDK](https://ops.java/releases/?lang=en).

## Organização do trabalho

| Status | Condição |
|---|---|
| A fazer | Trabalho restante identificado, ainda não iniciado |
| Em andamento | Desenvolvimento de uma tarefa com escopo e critérios definidos |
| Em revisão | Alteração preparada, com verificações pertinentes e documentação atualizada |
| Concluído | Critérios atendidos, testes pertinentes verificados e revisão finalizada |
| Bloqueado | Impedimento concreto, com causa e dependência identificadas |

Os identificadores US01–US25 e PEN-01–PEN-07 são mantidos. Critérios e tarefas usam o prefixo da história correspondente. Critérios marcados **A detalhar** dependem da definição de contratos, fórmulas ou parâmetros antes da implementação.

Cada alteração deve formar uma unidade pequena de código, testes e documentação relacionados. As tarefas listadas podem ser divididas em recortes menores durante o planejamento.

Antes do commit, executar os testes pertinentes, revisar o diff e atualizar o backlog. Utilizar Conventional Commits, como `feat`, `fix`, `test`, `docs`, `refactor` e `chore`. Os commits são locais durante a implementação; o push da branch ocorre ao concluir seu escopo. As instruções devem incluir o comando de push final. Integrações na branch principal passam por Pull Request e revisão, com título e descrição em Markdown preparados ao concluir o escopo da branch. Cada entrega deve indicar o próximo incremento proposto.

Testes automatizados acompanham o código e verificam os critérios de aceitação. Os resultados são apresentados na revisão de cada incremento. Logs de erros, relatórios de execução e relatos dos passos técnicos realizados não integram os arquivos do repositório.

## Histórias de usuário

US01–US24 derivam da [Sprint 1](Sprint1_PowerGridControlCenter.pdf), páginas 4–5. US25 e os critérios detalhados de US01, US03, US16 e US20 constam na [Sprint 4](Sprint4_Replanejamento_PowerGridControlCenter.pdf), páginas 3–5. Os critérios de cadastro e relacionamentos seguem o [modelo de domínio](modelo-dominio.md).

### US01 — Cadastrar subestações

Como operador, quero cadastrar subestações com informações técnicas e localização geográfica, para manter o registro da infraestrutura sempre atualizado.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** Em andamento.
- **Implementação existente:** Interface com dados simulados. Backend com AtivoRede e Subestacao em herança JOINED, repositório e endpoints de criação, listagem, consulta por ID e edição, com validações, sessão e CSRF.
- **Trabalho pendente:** Revisão do backend, formulário e integração. O tipo da tela ainda não contempla todos os campos.
- **Dependências:** PEN-06; autenticação do backend em US25.
- **Referências no frontend:** [Subestacoes/index.tsx](../frontend/src/pages/Subestacoes/index.tsx#L6) (linhas 6,17,38).

**Critérios de aceitação:**

- [ ] **US01-CA01:** Persistir nome, latitude, longitude, tensão nominal, capacidade em MVA, status e data de instalação (S4, p. 4).
- [ ] **US01-CA02:** Rejeitar campos obrigatórios vazios e coordenadas fora do intervalo válido (S4, p. 4); explicitar os obrigatórios no contrato.
- [ ] **US01-CA03:** Subestação salva aparece na listagem e pode ser editada (S4, p. 4).
- [ ] **US01-CA04:** Utilizar status principal Operacional, Falha ou Manutenção, separado de alertas.

**Tarefas:**

- [x] **US01-T01:** Definir o contrato de criação, campos obrigatórios, precisão, estado padrão e cenários de validação.
- [ ] **US01-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US01-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US01-T04:** Verificar integração/aceite e apresentar resultados e limitações.

**Contrato de criação:** Nome, latitude, longitude, tensão nominal, capacidade e data de instalação obrigatórios. Nome sem espaços nas extremidades, não vazio, até 255 caracteres e sem unicidade. Latitude entre -90 e 90; longitude entre -180 e 180; até sete casas decimais. Tensão e capacidade positivas, com até 13 dígitos inteiros e seis decimais. Excesso de precisão deve ser rejeitado, sem arredondamento. Data válida em YYYY-MM-DD, admitindo datas futuras. Estado opcional: OPERACIONAL quando omitido ou nulo; rejeitar valor fora de OPERACIONAL/FALHA/MANUTENCAO. ID gerado pelo sistema.

**US01-T02 — backend em revisão:** Mapeamento JOINED, repositório, DTOs validados e serviço transacional disponíveis. POST retorna 201 e Location; GET da coleção e por ID retornam 200; PUT substitui os dados e retorna 200 mantendo o ID. GET por ID e PUT retornam 404 se o registro não existir. PUT usa as mesmas validações e estado padrão do POST; entradas inválidas retornam 400 sem alterar dados. Consultas exigem autenticação, e escritas exigem também CSRF. A V1 permanece inalterada. A integração com a interface e o aceite de US01-CA03 permanecem pendentes.

### US02 — Cadastrar linhas e alimentadores

Como operador, quero cadastrar linhas de transmissão e alimentadores, para ter uma visão completa da rede de distribuição.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** Em andamento.
- **Implementação existente:** Frontend com duas listagens locais. Backend com Alimentador em herança JOINED, repositório, vínculo obrigatório LAZY com Subestacao e API de criação, listagem, consulta e edição.
- **Trabalho pendente:** Revisão do backend e integração de Alimentador; contrato e implementação de LinhaTransmissao. Strings de origem/destino não são topologia implementada.
- **Dependências:** PEN-01; PEN-06; US25; US01; definição de vínculos e extremidades.
- **Referências no frontend:** [Linhas/index.tsx](../frontend/src/pages/Linhas/index.tsx#L6) (linhas 6,17,38); [Alimentadores/index.tsx](../frontend/src/pages/Alimentadores/index.tsx#L6) (linhas 6,17,38).

**Critérios de aceitação:**

- [ ] **US02-CA01:** Cadastrar Alimentador como entidade própria vinculada a uma subestação.
- [ ] **US02-CA02:** Salvar e consultar linhas e alimentadores com os campos e extremidades previstos no contrato. **A detalhar.**
- [ ] **US02-CA03:** Rejeitar referências e entradas inválidas segundo esse contrato; demonstrar sucesso e falhas. **A detalhar.**

**Tarefas:**

- [ ] **US02-T01:** Definir campos, extremidades, contratos e vínculos; resolver detalhes de domínio pertinentes.
- [ ] **US02-T02:** Implementar cadastro mínimo de Alimentador com testes/documentação; pode preceder US03 sem concluir toda US02.
- [ ] **US02-T03:** Implementar cadastro de LinhaTransmissao com testes/documentação após definir extremidades.
- [ ] **US02-T04:** Integrar as duas interfaces e verificar critérios de linhas e alimentadores, com evidências.

**US02-T02 — backend em revisão:** Alimentador compartilha o ID com AtivoRede e referencia uma Subestacao existente por associação ManyToOne obrigatória e LAZY. Não há cascata de persistência ou remoção para a subestação. A V1 permanece inalterada.

**Contrato de criação de Alimentador:** Nome, latitude, longitude, tensão nominal, data de instalação e subestacaoId obrigatórios. Nome recebe trim, não pode ser vazio e admite até 255 caracteres, sem unicidade. Coordenadas admitem sete casas decimais, nos intervalos [-90, 90] e [-180, 180]. Tensão estritamente positiva, com até 13 dígitos inteiros e seis decimais. Precisão excedida é rejeitada sem arredondamento. Datas futuras são permitidas. SubestacaoId deve ser inteiro positivo compatível com Long; referência inexistente retorna 404 sem criar o alimentador. Entradas inválidas retornam 400. Estado omitido ou nulo assume OPERACIONAL; os valores aceitos são OPERACIONAL, FALHA e MANUTENCAO. ID gerado pelo sistema; capacidadeMva não integra o cadastro. Não há igualdade obrigatória entre tensões nem restrição pelo estado da subestação. DTOs validados, serviço transacional e endpoints HTTP implementados. POST retorna 201 com Location; GET e PUT retornam 200; GET/PUT com alimentador inexistente retornam 404. POST/PUT com subestação inexistente retornam 404 sem incluir ou alterar registros. PUT preserva o ID e admite troca para outra subestação existente. Respostas expõem subestacaoId, sem serializar a entidade relacionada.

### US03 — Cadastrar transformadores

Como operador, quero cadastrar transformadores com sua capacidade operacional, para acompanhar a carga de cada equipamento.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** Em andamento.
- **Implementação existente:** Interface com dados simulados. Backend com Transformador em herança JOINED, repositório e API de criação, listagem, consulta por ID e edição; Alimentador obrigatório e subestação derivada nas respostas.
- **Trabalho pendente:** Integração com o frontend e aceite do recorte. O tipo do frontend não possui alimentador. Telemetria será tratada em incremento futuro de Monitoramento.
- **Dependências:** PEN-06; US25; US01; cadastro mínimo de alimentadores.
- **Referências no frontend:** [Transformadores/index.tsx](../frontend/src/pages/Transformadores/index.tsx#L6) (linhas 6,16,37,143).

**Critérios de aceitação:**

- [ ] **US03-CA01:** Persistir nome identificador e capacidade operacional; obter a subestação vinculada por meio do alimentador (S4, p. 5).
- [ ] **US03-CA02:** Aceitar somente capacidade positiva (S4, p. 5).
- [ ] **US03-CA03:** Exibir percentual de carga atual na listagem, admitindo valor simulado (S4, p. 5).
- [ ] **US03-CA04:** Exigir um Alimentador no cadastro do transformador; derivar a subestação desse vínculo, sem coluna duplicada no transformador.

**Tarefas:**

- [x] **US03-T01:** Definir contrato de cadastro estrutural, vínculo obrigatório, subestação derivada e cenários de validação.
- [ ] **US03-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US03-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US03-T04:** Verificar integração/aceite e apresentar resultados e limitações.

**US03-T02 — persistência e API em revisão:** Transformador compartilha o ID com AtivoRede, armazena capacidadeMva e referencia Alimentador obrigatório e LAZY, sem cascata. Não possui subestacaoId nem tensaoNominalKv. A V1 permanece inalterada.

**Contrato de criação:** Nome, latitude, longitude, capacidadeMva, dataInstalacao e alimentadorId obrigatórios. Nome recebe trim, não pode ser vazio e admite até 255 caracteres, sem unicidade. Coordenadas aceitam sete casas decimais e respeitam os intervalos [-90, 90] e [-180, 180]. Capacidade estritamente positiva, com até 13 dígitos inteiros e seis decimais; precisão excedida será rejeitada sem arredondamento. Datas futuras são permitidas. AlimentadorId inteiro positivo compatível com Long, referenciando alimentador existente; o contrato HTTP prevê 404 para referência inexistente e 400 para entrada inválida. Estado omitido ou nulo assume OPERACIONAL; catálogo OPERACIONAL/FALHA/MANUTENCAO. ID gerado pelo sistema. O cliente não informa subestacaoId: a subestação é obtida pelo alimentador. POST /api/transformadores retorna 201 com Location; GET da coleção, GET por ID e PUT retornam 200. Transformador inexistente retorna 404. O PUT aplica o mesmo contrato da criação. As respostas incluem alimentadorId e subestacaoId derivado, inclusive após troca do alimentador. TransformadorControllerTest cobre US03-CA01, CA02 e CA04 no backend, validação, vínculo inexistente, consultas, edição, autenticação e CSRF.

**Telemetria adiada:** US03-CA03 permanece aberto para uma futura entrega de Monitoramento. Percentual de carga e temperatura não integram o cadastro estrutural nem terão valores presumidos.

### US04 — Cadastrar equipes e responsabilidades

Como técnico de manutenção, quero cadastrar equipes de manutenção e suas responsabilidades, para saber quem está disponível para cada tipo de atendimento.

- **Perfil e prioridade:** Técnico de Manutenção. Prioridade: Média.
- **Status:** A fazer.
- **Implementação existente:** Listagem e filtros; quantidade de membros numérica e responsável textual.
- **Trabalho pendente:** Sem integrantes identificados, persistência ou regra de disponibilidade. Execução granular e múltiplas equipes simultâneas adiadas.
- **Dependências:** PEN-06; US25; regras de integrantes e disponibilidade.
- **Referências no frontend:** [Equipes/index.tsx](../frontend/src/pages/Equipes/index.tsx#L7) (linhas 7,18,44).

**Critérios de aceitação:**

- [ ] **US04-CA01:** Cada integrante referencia conta de usuário no MVP; contagem não substitui vínculos.
- [ ] **US04-CA02:** Persistir e consultar equipe e responsabilidades/especialidades conforme contrato definido. **A detalhar.**
- [ ] **US04-CA03:** Apresentar disponibilidade segundo regra verificável, sem presumir exclusividade ou limite de atendimentos. **A detalhar.**

**Tarefas:**

- [ ] **US04-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US04-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US04-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US04-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US05 — Cadastrar usinas vinculadas à rede

Como gerente, quero cadastrar as usinas geradoras vinculadas à rede, para ter controle sobre as fontes de geração de energia.

- **Perfil e prioridade:** Gerente de Operações. Prioridade: Média.
- **Status:** A fazer.
- **Implementação existente:** Listagem, filtros e somatórios sobre capacidade/geração simuladas.
- **Trabalho pendente:** Sem cadastro, localização e vínculo persistente com a rede.
- **Dependências:** PEN-06; US25; vínculos com a rede definidos em US02.
- **Referências no frontend:** [Usinas/index.tsx](../frontend/src/pages/Usinas/index.tsx#L7) (linhas 7,17,44,50).

**Critérios de aceitação:**

- [ ] **US05-CA01:** Persistir usina com identificação, localização, fonte, capacidade e vínculo com a rede definidos no contrato. **A detalhar.**
- [ ] **US05-CA02:** Consultar dados salvos, distinguindo capacidade nominal de geração observada. **A detalhar.**
- [ ] **US05-CA03:** Demonstrar rejeição de campos/referências inválidos conforme regras definidas. **A detalhar.**

**Tarefas:**

- [ ] **US05-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US05-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US05-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US05-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US06 — Cadastrar medidores vinculados a consumidores

Como operador, quero cadastrar medidores inteligentes vinculados aos consumidores, para acompanhar o consumo de energia individualmente.

- **Perfil e prioridade:** Operador. Prioridade: Média.
- **Status:** A fazer.
- **Implementação existente:** Listagem e filtros; consumidor/alimentador são strings e última leitura é relativa.
- **Trabalho pendente:** Schema inicial disponível, mas sem operações de cadastro, consulta ou instalação. A associação direta simulada ao alimentador diverge do modelo de domínio.
- **Dependências:** PEN-02; PEN-03; PEN-06; US25; cadastro mínimo do titular em US07; US03 e alimentadores de US02.
- **Referências no frontend:** [SmartMeters/index.tsx](../frontend/src/pages/SmartMeters/index.tsx#L6) (linhas 6,16,36).

**Critérios de aceitação:**

- [ ] **US06-CA01:** Permitir medidores em estoque sem titular ou transformador; admitir zero ou vários medidores por consumidor.
- [ ] **US06-CA02:** Usar a localização do medidor como referência temporária do endereço de fornecimento, sem UnidadeConsumidora.
- [ ] **US06-CA03:** Quando vinculado, referenciar Transformador e obter Alimentador por ele, sem vínculo cadastral direto Medidor–Alimentador. As condições de instalação serão detalhadas no contrato.
- [ ] **US06-CA04:** Persistir e consultar cadastro com referências válidas e consumo/instante de fonte definida; transferências não integram este aceite inicial. **A detalhar.**

**Tarefas:**

- [ ] **US06-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US06-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US06-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US06-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US07 — Cadastrar consumidores titulares

Como operador, quero cadastrar os consumidores da rede elétrica, para vincular os pontos de consumo aos ativos correspondentes.

- **Perfil e prioridade:** Operador. Prioridade: Baixa.
- **Status:** A fazer.
- **Implementação existente:** Listagem/filtros; endereço e alimentador textuais; consumo médio simulado.
- **Trabalho pendente:** Sem documento, cadastro de titular ou relações reais. Inadimplência/suspensão dos mocks não são regras definidas.
- **Dependências:** PEN-02; PEN-03; PEN-06; US25; consulta dos vínculos após US06.
- **Referências no frontend:** [Consumidores/index.tsx](../frontend/src/pages/Consumidores/index.tsx#L7) (linhas 7,17,44).

**Critérios de aceitação:**

- [ ] **US07-CA01:** Persistir titular PF/PJ com atributo documento e permitir zero ou vários medidores.
- [ ] **US07-CA02:** Representar provisoriamente fornecimento pelas localizações dos medidores e declarar a limitação histórica aceita.
- [ ] **US07-CA03:** Consultar vínculos aos ativos pelo caminho Medidor → Transformador → Alimentador.
- [ ] **US07-CA04:** Validar campos segundo contrato definido, sem implementar regras financeiras apenas por existirem na interface. **A detalhar.**

**Tarefas:**

- [ ] **US07-T01:** Definir contrato do titular e critérios de documento, respeitando a limitação histórica definida.
- [ ] **US07-T02:** Implementar cadastro mínimo do titular com testes/documentação; pode preceder US06 sem concluir a consulta dos vínculos.
- [ ] **US07-T03:** Integrar interface e consulta de vínculos após US06, com testes/documentação.
- [ ] **US07-T04:** Verificar aceite e apresentar resultados e limitações do MVP.

### US08 — Monitorar tensão e corrente de linhas

Como operador, quero visualizar em tempo real a tensão e a corrente das linhas de transmissão, para identificar rapidamente variações anormais.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Valores fixos em Linhas e gráfico estático em Monitoramento.
- **Trabalho pendente:** Sem fonte de medições, identidade de linha na série e atualização; tensão nominal/medida não separadas.
- **Dependências:** PEN-06; US25; US02; fonte de medições ou simulador.
- **Referências no frontend:** [Linhas/index.tsx](../frontend/src/pages/Linhas/index.tsx#L11) (linhas 11,17); [Monitoramento/index.tsx](../frontend/src/pages/Monitoramento/index.tsx#L13) (linhas 13,134).

**Critérios de aceitação:**

- [ ] **US08-CA01:** Exibir tensão/corrente da linha identificada, com unidades, instante e origem real/simulada. **A detalhar.**
- [ ] **US08-CA02:** Novas medições de cenário controlado atualizam a interface na cadência a aprovar. **A detalhar.**
- [ ] **US08-CA03:** Distinguir dados ausentes/atrasados de zero e demonstrar situações normais e anormais definidas. **A detalhar.**

**Tarefas:**

- [ ] **US08-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US08-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US08-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US08-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US09 — Monitorar potência ativa e reativa

Como operador, quero acompanhar a potência ativa e reativa da rede, para avaliar a qualidade do fornecimento de energia.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Gráfico e cartão com valores constantes.
- **Trabalho pendente:** Sem origem/escopo definidos, leituras persistidas e atualização.
- **Dependências:** PEN-06; US25; infraestrutura de medições de US08.
- **Referências no frontend:** [Monitoramento/index.tsx](../frontend/src/pages/Monitoramento/index.tsx#L22) (linhas 22,74,148).

**Critérios de aceitação:**

- [ ] **US09-CA01:** Exibir potências ativa/reativa com unidades, escopo e instante identificados. **A detalhar.**
- [ ] **US09-CA02:** Conferir valores com cenário conhecido e demonstrar atualização segundo política definida. **A detalhar.**
- [ ] **US09-CA03:** Tratar ausência/qualidade insuficiente sem fabricar leitura. **A detalhar.**

**Tarefas:**

- [ ] **US09-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US09-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US09-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US09-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US10 — Monitorar frequência e fator de potência

Como operador, quero monitorar a frequência e o fator de potência da rede, para garantir a estabilidade do sistema elétrico.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Frequência em série fixa e fator de potência literal.
- **Trabalho pendente:** Sem fonte/escopo ou definição do cálculo e atualização.
- **Dependências:** PEN-06; US25; infraestrutura de medições.
- **Referências no frontend:** [Monitoramento/index.tsx](../frontend/src/pages/Monitoramento/index.tsx#L13) (linhas 13,85,96).

**Critérios de aceitação:**

- [ ] **US10-CA01:** Exibir frequência e fator de potência com origem, instante e escopo definidos. **A detalhar.**
- [ ] **US10-CA02:** Conferir leitura/cálculo com cenário conhecido e demonstrar atualização. **A detalhar.**
- [ ] **US10-CA03:** Indicar ausência/atraso conforme política definida, sem inferir estabilidade por valores fixos. **A detalhar.**

**Tarefas:**

- [ ] **US10-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US10-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US10-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US10-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US11 — Monitorar carga dos transformadores

Como operador, quero visualizar a carga dos transformadores em tempo real, para prevenir sobrecargas nos equipamentos.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Percentuais fixos e média local; há exemplo de carga de 108%.
- **Trabalho pendente:** Sem cálculo rastreável e atualização; o modelo de domínio não define por si a fórmula de carga.
- **Dependências:** US03; US06 se o cálculo utilizar medidores; infraestrutura de medições.
- **Referências no frontend:** [Transformadores/index.tsx](../frontend/src/pages/Transformadores/index.tsx#L11) (linhas 11,20,46,147).

**Critérios de aceitação:**

- [ ] **US11-CA01:** Quando o recorte utilizar medidores, obter seu agrupamento por transformador/alimentador pelo caminho cadastral definido; o método de medição/cálculo continua a definir.
- [ ] **US11-CA02:** Exibir carga por transformador com capacidade de referência, instante, unidades e método definidos. **A detalhar.**
- [ ] **US11-CA03:** Conferir resultado conhecido, incluindo sobrecarga; não limitar observações automaticamente a 100%. **A detalhar.**
- [ ] **US11-CA04:** Demonstrar atualização e comportamento com dados insuficientes conforme política definida. **A detalhar.**

**Tarefas:**

- [ ] **US11-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US11-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US11-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US11-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US12 — Acompanhar perdas técnicas

Como gerente, quero acompanhar as perdas técnicas na distribuição, para identificar os pontos da rede com maior ineficiência.

- **Perfil e prioridade:** Gerente de Operações. Prioridade: Média.
- **Status:** A fazer.
- **Implementação existente:** Percentuais literais globais e por alimentador.
- **Trabalho pendente:** Sem fronteira, período, fórmula, entradas compatíveis ou cálculo.
- **Dependências:** Cadastros e medições; fórmula, fronteira e período de apuração.
- **Referências no frontend:** [Monitoramento/index.tsx](../frontend/src/pages/Monitoramento/index.tsx#L39) (linhas 39,107); [Alimentadores/index.tsx](../frontend/src/pages/Alimentadores/index.tsx#L12) (linhas 12,17).

**Critérios de aceitação:**

- [ ] **US12-CA01:** Calcular perdas para fronteira/período definidos, com fórmula e unidades definidas. **A detalhar.**
- [ ] **US12-CA02:** Demonstrar resultado conhecido e rastrear seus dados de entrada. **A detalhar.**
- [ ] **US12-CA03:** Indicar insuficiência de dados e apresentar comparação sem tratar literal como cálculo. **A detalhar.**

**Tarefas:**

- [ ] **US12-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US12-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US12-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US12-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US13 — Visualizar mapa geográfico de ativos

Como operador, quero visualizar o mapa geográfico dos ativos da rede, para localizar rapidamente os equipamentos durante uma ocorrência.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Mapa ausente; Dashboard tem KPIs/gráficos, sem componente geográfico.
- **Trabalho pendente:** Sem mapa, camadas, coordenadas/traçados consumidos ou interação. Dependência Leaflet não comprova funcionalidade.
- **Dependências:** US01; US02; coordenadas e traçados dos demais cadastros.
- **Referências no frontend:** [Dashboard/index.tsx](../frontend/src/pages/Dashboard/index.tsx#L32) (linhas 32).

**Critérios de aceitação:**

- [ ] **US13-CA01:** Representar ativos do recorte definido com suas coordenadas persistidas. **A detalhar.**
- [ ] **US13-CA02:** Selecionar ativo no mapa e identificar o cadastro correspondente. **A detalhar.**
- [ ] **US13-CA03:** Representar linhas conforme geometria definida e tratar coordenadas ausentes/inválidas sem inventar localização. **A detalhar.**

**Tarefas:**

- [ ] **US13-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US13-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US13-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US13-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US14 — Visualizar KPIs de disponibilidade e desempenho

Como gerente, quero visualizar KPIs de disponibilidade e desempenho da rede, para avaliar a eficiência operacional da operadora.

- **Perfil e prioridade:** Gerente de Operações. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Cartões fixos e média local de disponibilidades fictícias.
- **Trabalho pendente:** Sem fórmula/período/fonte definidos e cálculo operacional real.
- **Dependências:** Cadastros, eventos e medições conforme a definição de cada indicador.
- **Referências no frontend:** [Dashboard/index.tsx](../frontend/src/pages/Dashboard/index.tsx#L45) (linhas 45); [Subestacoes/index.tsx](../frontend/src/pages/Subestacoes/index.tsx#L44) (linhas 44).

**Critérios de aceitação:**

- [ ] **US14-CA01:** Definir fórmula, unidade, período e fonte de cada KPI. **A detalhar.**
- [ ] **US14-CA02:** Calcular e exibir resultado verificável a partir de dados persistidos de cenário conhecido. **A detalhar.**
- [ ] **US14-CA03:** Distinguir indisponibilidade de dados de indisponibilidade do ativo. **A detalhar.**

**Tarefas:**

- [ ] **US14-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US14-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US14-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US14-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US15 — Consultar histórico de consumo e perdas

Como gerente, quero acompanhar gráficos históricos de consumo e perdas, para identificar tendências ao longo do tempo.

- **Perfil e prioridade:** Gerente de Operações. Prioridade: Média.
- **Status:** A fazer.
- **Implementação existente:** Gráfico Consumo x Geração com pontos constantes e relatórios fictícios.
- **Trabalho pendente:** Sem série persistida de perdas, consulta histórica ou download funcional.
- **Dependências:** US12; medições e histórico.
- **Referências no frontend:** [Dashboard/index.tsx](../frontend/src/pages/Dashboard/index.tsx#L12) (linhas 12,105); [Relatorios/index.tsx](../frontend/src/pages/Relatorios/index.tsx#L17) (linhas 17).

**Critérios de aceitação:**

- [ ] **US15-CA01:** Consultar consumo e perdas do período/escopo definidos; geração não substitui perdas. **A detalhar.**
- [ ] **US15-CA02:** Conferir agregações/unidades e persistência das séries com conjunto histórico conhecido. **A detalhar.**
- [ ] **US15-CA03:** Tratar período sem dados com estado vazio; PDF não é critério implícito desta história. **A detalhar.**

**Tarefas:**

- [ ] **US15-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US15-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US15-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US15-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US16 — Registrar manobras e desligamentos programados

Como operador, quero registrar eventos operacionais como manobras e desligamentos programados, para manter a rastreabilidade das ações realizadas na rede.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** Em andamento.
- **Implementação existente:** Frontend com tabela, busca e filtro local. Backend com entidade, repositório, criação e consultas de eventos; associações obrigatórias com ativo, registrador e responsável.
- **Trabalho pendente:** Revisão do backend, integração com frontend e aceite funcional.
- **Dependências:** PEN-06; US25; ao menos US01 para o ativo; contratos de datas e situação.
- **Referências no frontend:** [Eventos/index.tsx](../frontend/src/pages/Eventos/index.tsx#L8) (linhas 8,21,51).

**Critérios de aceitação:**

- [ ] **US16-CA01:** Operador registra manobra/desligamento com ativo, categoria, início, descrição e responsável (S4, p. 5).
- [ ] **US16-CA02:** Distinguir registradorUsuario autenticado e responsavelUsuario selecionado por identidade.
- [ ] **US16-CA03:** Separar inicioPrevisto e inicioEfetivo; detalhar obrigatoriedade por categoria/estado antes do comando.
- [ ] **US16-CA04:** Criar evento com estado inicial definido e explícito no contrato (S4, p. 5).
- [ ] **US16-CA05:** Rejeitar fim anterior ao início (S4, p. 5), definindo o par temporal pertinente.
- [ ] **US16-CA06:** Listar data/hora, ativo, categoria e situação (S4, p. 5).
- [ ] **US16-CA07:** Usar as categorias MANOBRA e DESLIGAMENTO_PROGRAMADO no registro de eventos deste recorte.

**Tarefas:**

- [x] **US16-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US16-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US16-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US16-T04:** Verificar integração/aceite e apresentar resultados e limitações.

**US16-T02 — persistência e API em revisão:** Relacionamentos ManyToOne LAZY, sem cascata; datas previstas e efetivas mapeadas com fuso. EventoOperacionalRepositoryTest cobre vínculos polimórficos, obrigatoriedade, referências inexistentes e períodos previstos/efetivos. Designação de equipe permanece sem mapeamento Java neste recorte. A V1 permanece inalterada.

**Contrato de criação:** MANOBRA ou DESLIGAMENTO_PROGRAMADO; ativoId, responsavelUsuarioId, descrição não vazia e inicioPrevisto obrigatórios. IDs positivos e existentes. Registrador obtido da sessão, nunca do DTO. Situação inicial ABERTO; datas efetivas e equipe nulas. TerminoPrevisto opcional e maior ou igual a inicioPrevisto; datas com fuso e previsões passadas permitidas. Criticidade opcional, até 32 caracteres, sem catálogo presumido. Registrador e responsável podem coincidir, sem restrição adicional de perfil do responsável. Entrada inválida retorna 400; referências inexistentes, 404. POST /api/eventos-operacionais retorna 201 com Location; GET da coleção e GET /{id} retornam 200, ou 404 para evento inexistente. O serviço obtém o registrador pelo SecurityContextHolder. O DTO não permite atribuir registrador, situação ou datas efetivas. EventoOperacionalControllerTest cobre US16-CA01–CA07 no recorte de backend, incluindo autoria, CSRF, categorias permitidas, vínculos e validações temporais.

### US17 — Registrar interrupções de fornecimento

Como operador, quero registrar interrupções de fornecimento com data, horário e ativo envolvido, para documentar a ocorrência e permitir análises posteriores.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Uma interrupção simulada aparece na lista e pode ser filtrada.
- **Trabalho pendente:** Sem operação de registro e relação persistente com ativo.
- **Dependências:** Infraestrutura de eventos de US16; US25.
- **Referências no frontend:** [Eventos/index.tsx](../frontend/src/pages/Eventos/index.tsx#L23) (linhas 23,55).

**Critérios de aceitação:**

- [ ] **US17-CA01:** Usar INTERRUPCAO_FORNECIMENTO para perda efetiva de fornecimento, distinta da ação programada.
- [ ] **US17-CA02:** Persistir interrupção com ativo identificado, instante efetivo e campos comuns definidos. **A detalhar.**
- [ ] **US17-CA03:** Consultar ocorrência salva e demonstrar validações temporais e de referências. **A detalhar.**

**Tarefas:**

- [ ] **US17-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US17-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US17-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US17-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US18 — Atualizar situação de evento

Como técnico de manutenção, quero atualizar a situação de um evento operacional registrado, para manter o status da ocorrência sempre correto.

- **Perfil e prioridade:** Técnico de Manutenção. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Estados e contagens visíveis, sem ação de atualização.
- **Trabalho pendente:** Sem transições autorizadas, gravação ou histórico.
- **Dependências:** US16; US25; transições e permissões.
- **Referências no frontend:** [Eventos/index.tsx](../frontend/src/pages/Eventos/index.tsx#L6) (linhas 6,45,166).

**Critérios de aceitação:**

- [ ] **US18-CA01:** Usuário autorizado altera situação conforme transições explicitadas no contrato. **A detalhar.**
- [ ] **US18-CA02:** Persistir autor, instante e estados anterior/novo; apresentar alteração após nova consulta. **A detalhar.**
- [ ] **US18-CA03:** Rejeitar usuário/transição inválidos sem modificar evento e validar término na conclusão. **A detalhar.**

**Tarefas:**

- [ ] **US18-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US18-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US18-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US18-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US19 — Consultar histórico de manutenção por ativo

Como técnico de manutenção, quero consultar o histórico de manutenções de um ativo, para decidir com mais precisão as ações necessárias em campo.

- **Perfil e prioridade:** Técnico de Manutenção. Prioridade: Média.
- **Status:** A fazer.
- **Implementação existente:** Manutenção simulada filtrável e relatório fictício; sem histórico por identidade.
- **Trabalho pendente:** Sem manutenções persistidas, detalhes técnicos e consulta histórica.
- **Dependências:** US16; US18; US25; registro de manutenção.
- **Referências no frontend:** [Eventos/index.tsx](../frontend/src/pages/Eventos/index.tsx#L26) (linhas 26,55); [Relatorios/index.tsx](../frontend/src/pages/Relatorios/index.tsx#L22) (linhas 22).

**Critérios de aceitação:**

- [ ] **US19-CA01:** Manutenção é evento de categoria MANUTENCAO; não inferir executor pelos integrantes atuais.
- [ ] **US19-CA02:** Consultar intervenções persistidas de um ativo com datas, estado e detalhes técnicos definidos. **A detalhar.**
- [ ] **US19-CA03:** Preservar registros e demonstrar ordenação/estado vazio; executor granular adiado no primeiro recorte. **A detalhar.**

**Tarefas:**

- [ ] **US19-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US19-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US19-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US19-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US20 — Consultar alertas ativos por criticidade

Como operador, quero visualizar os alertas ativos classificados por criticidade, para priorizar o atendimento das ocorrências mais urgentes.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** Em andamento.
- **Implementação existente:** Frontend com dados simulados, filtros e contadores. Backend com entidade, enums, repositório e API de criação e consulta de alertas ativos, ordenada por criticidade, instante e ID.
- **Trabalho pendente:** Revisão do backend e integração com frontend; substituir o ativo textual fictício Rede Geral pelo escopo REDE_MONITORADA.
- **Dependências:** PEN-06; US25; ativo cadastrado ou escopo geral; criticidade e estados ativos.
- **Referências no frontend:** [Alertas/index.tsx](../frontend/src/pages/Alertas/index.tsx#L17) (linhas 17,42,122,156).

**Critérios de aceitação:**

- [ ] **US20-CA01:** Listar apenas alertas ativos, da maior à menor criticidade e com indicação visual (S4, p. 5); definir estados ativos e ordem no contrato.
- [ ] **US20-CA02:** Mostrar tipo, instante e criticidade, além do ativo ou do escopo REDE_MONITORADA.
- [ ] **US20-CA03:** ATIVO exige referência real; REDE_MONITORADA usa ativoId nulo e escopo explícito.
- [ ] **US20-CA04:** Sem alertas ativos, exibir mensagem apropriada (S4, p. 5).

**Tarefas:**

- [x] **US20-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US20-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US20-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US20-T04:** Verificar integração/aceite e apresentar resultados e limitações.

**US20-T02 — persistência e API em revisão:** AlertaRepository.buscarAtivos seleciona ABERTO e RECONHECIDO e ordena CRITICA, ALTA, MEDIA e BAIXA, depois geradoEm e id decrescentes. AlertaRepositoryTest cobre as restrições de escopo da V1, vínculos, estados ativos e ordenação. O relacionamento com AtivoRede é opcional, LAZY e sem cascata.

**Contrato de geração e consulta:** Tipo obrigatório, com trim e até 128 caracteres; criticidade e escopo obrigatórios. ATIVO exige ativoId positivo existente; REDE_MONITORADA exige ativoId nulo. ID gerado pelo banco, geradoEm definido pelo backend em UTC e situação inicial ABERTO. A API rejeita entrada inválida com 400 e referência inexistente com 404. Consulta vazia retorna lista vazia. Geração automática, reconhecimento e resolução com auditoria permanecem para incrementos posteriores.

**API:** POST /api/alertas retorna 201 com os sete campos da tabela; GET /api/alertas retorna 200 com os alertas ativos ordenados. Tipo é a mensagem principal exibida, sem campo adicional de descrição. O serviço valida a combinação escopo/ativo antes de persistir. Situação e instante de geração são definidos pelo servidor, sem aceitar atribuição pelo DTO. As rotas exigem sessão e o POST exige CSRF. AlertaControllerTest cobre o recorte de backend de US20-CA01–CA04; indicação visual, mensagem de vazio e integração permanecem pendentes no frontend.

### US21 — Gerar alerta automático de sobrecarga

Como operador, quero receber alertas automáticos de sobrecarga em transformadores, para agir antes que ocorra uma falha maior.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Alerta estático; coincidência com carga de 108% não é regra executada.
- **Trabalho pendente:** Sem detector, parâmetros definidos ou geração persistida.
- **Dependências:** US11; US20; medições e limites de detecção.
- **Referências no frontend:** [Alertas/index.tsx](../frontend/src/pages/Alertas/index.tsx#L18) (linhas 18); [Transformadores/index.tsx](../frontend/src/pages/Transformadores/index.tsx#L20) (linhas 20).

**Critérios de aceitação:**

- [ ] **US21-CA01:** Avaliar carga segundo limite/condição definidos e gerar alerta do transformador correto. **A detalhar.**
- [ ] **US21-CA02:** Demonstrar abaixo/no/acima do limite e dados ausentes. **A detalhar.**
- [ ] **US21-CA03:** Verificar persistência/apresentação e definir repetição/recuperação antes de implementá-las. **A detalhar.**

**Tarefas:**

- [ ] **US21-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US21-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US21-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US21-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US22 — Gerar alertas de tensão anormal

Como operador, quero receber alertas de variação anormal de tensão, para evitar danos aos equipamentos conectados à rede.

- **Perfil e prioridade:** Operador. Prioridade: Alta.
- **Status:** A fazer.
- **Implementação existente:** Alerta estático e gráfico sem mecanismo de detecção.
- **Trabalho pendente:** Sem faixas, classificação e avaliação automática.
- **Dependências:** US08; US20; faixas de tensão e regras de geração.
- **Referências no frontend:** [Alertas/index.tsx](../frontend/src/pages/Alertas/index.tsx#L19) (linhas 19); [Monitoramento/index.tsx](../frontend/src/pages/Monitoramento/index.tsx#L13) (linhas 13).

**Critérios de aceitação:**

- [ ] **US22-CA01:** Gerar alertas abaixo/acima de faixas definidas, identificando ativo e instante. **A detalhar.**
- [ ] **US22-CA02:** Demonstrar limites, condição normal e ausência de leitura. **A detalhar.**
- [ ] **US22-CA03:** Apresentar alerta gerado pela regra; recorrência/recuperação dependem de política definida. **A detalhar.**

**Tarefas:**

- [ ] **US22-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US22-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US22-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US22-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US23 — Gerar alertas de perda de comunicação

Como operador, quero receber alertas de perda de comunicação com dispositivos, para identificar falhas de monitoramento rapidamente.

- **Perfil e prioridade:** Operador. Prioridade: Média.
- **Status:** A fazer.
- **Implementação existente:** Medidor offline e alerta definidos como literais.
- **Trabalho pendente:** Sem instante real de última comunicação, relógio de avaliação ou detector.
- **Dependências:** US06; US20; registro da última comunicação e política de tolerância.
- **Referências no frontend:** [SmartMeters/index.tsx](../frontend/src/pages/SmartMeters/index.tsx#L19) (linhas 19); [Alertas/index.tsx](../frontend/src/pages/Alertas/index.tsx#L20) (linhas 20).

**Critérios de aceitação:**

- [ ] **US23-CA01:** Registrar última comunicação e detectar ausência segundo tolerância definida. **A detalhar.**
- [ ] **US23-CA02:** Gerar alerta do dispositivo correto, sem equivaler comunicação perdida a falta de energia. **A detalhar.**
- [ ] **US23-CA03:** Demonstrar ausência inicial, atraso e recuperação com relógio controlado e regras explícitas. **A detalhar.**

**Tarefas:**

- [ ] **US23-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US23-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US23-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US23-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US24 — Consultar histórico de alertas por criticidade

Como gerente, quero visualizar o histórico de alertas gerados por criticidade, para avaliar a recorrência de problemas na rede.

- **Perfil e prioridade:** Gerente de Operações. Prioridade: Baixa.
- **Status:** A fazer.
- **Implementação existente:** Filtro por nível em array local de ativos e resolvidos.
- **Trabalho pendente:** Sem histórico persistente ou ações rastreadas.
- **Dependências:** US20; persistência e histórico de alertas.
- **Referências no frontend:** [Alertas/index.tsx](../frontend/src/pages/Alertas/index.tsx#L17) (linhas 17,39).

**Critérios de aceitação:**

- [ ] **US24-CA01:** Consultar alertas persistidos por criticidade e período/escopo definidos, incluindo resolvidos. **A detalhar.**
- [ ] **US24-CA02:** Preservar ações/instantes e distinguir consulta histórica da lista de ativos de US20. **A detalhar.**
- [ ] **US24-CA03:** Conferir resultado com conjunto histórico conhecido e tratar ausência de resultados. **A detalhar.**

**Tarefas:**

- [ ] **US24-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US24-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US24-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US24-T04:** Verificar integração/aceite e apresentar resultados e limitações.

### US25 — Autenticar e restringir acesso por perfil

Como usuário, quero acessar o sistema com minhas credenciais e visualizar as funcionalidades do meu perfil, para realizar minhas atividades com acesso controlado.

- **Perfil e prioridade:** Usuário dos perfis documentados. Prioridade: Fundação — inclusão S4.
- **Status:** Em revisão — escopo do backend.
- **Implementação existente:** Persistência e credenciais de usuários, login/logout por sessão, consulta do usuário autenticado em DTO seguro, token CSRF e CORS para o frontend local. A V2 inclui três contas iniciais com BCrypt; segurança por método habilitada. O formulário do frontend ainda realiza apenas validação local.
- **Trabalho pendente:** Revisão do backend. Integração React e cadastro de usuários com validação de senha pertencem a incrementos posteriores e não bloqueiam esta PR.
- **Dependências:** PEN-06; PostgreSQL configurado para execução da aplicação.
- **Referências no frontend:** [Login/index.tsx](../frontend/src/pages/Login/index.tsx#L12) (linhas 12,35).

**Critérios de aceitação:**

- [ ] **US25-CA01:** Credenciais válidas levam ao dashboard e inválidas exibem erro (S4, p. 4).
- [ ] **US25-CA02:** Armazenar senha com hash (S4, p. 4).
- [ ] **US25-CA03:** Rotas internas só abrem a usuário autenticado (S4, p. 4).
- [ ] **US25-CA04:** Operador, Gerente e Técnico acessam somente telas permitidas ao perfil (S4, p. 4); política inicial: autenticação nas rotas privadas e perfil GERENTE_OPERACOES nas futuras operações de gestão de acesso. A aplicação dessa política nas telas fica fora desta PR.
- [ ] **US25-CA05:** Um perfil por usuário no MVP; cargo não concede permissões.
- [ ] **US25-CA06:** Normalizar e-mail com trim e minúsculas antes de salvar e durante a tentativa de login.
- [ ] **US25-CA07:** Exigir senha com no mínimo oito caracteres no cadastro.
- [ ] **US25-CA08:** Disponibilizar pelo menos um usuário inicial de cada perfil, com e-mail normalizado e senha previamente calculada em BCrypt.

**Tarefas:**

- [ ] **US25-T01:** Detalhar contrato e critérios propostos, resolver as decisões pertinentes e definir cenários de teste.
- [ ] **US25-T02:** Implementar o recorte de backend com testes pertinentes e documentação.
- [ ] **US25-T03:** Integrar a interface com testes pertinentes e documentação.
- [ ] **US25-T04:** Verificar integração/aceite e apresentar resultados e limitações.
- [ ] **US25-T05:** Provisionar as três contas pela V2 e validar seus logins e perfis — Em revisão.

**Escopo de backend em revisão — US25-T02/US25-T05:** API de autenticação com DTO seguro, sessão HttpOnly, CORS, CSRF, provisionamento dos três perfis e segurança por método. O aceite deste recorte exige autenticação das contas da V2, rejeição de credenciais inválidas, proteção das rotas, renovação/invalidação da sessão e restrição de métodos de gestão ao gerente. Não existem endpoints de gestão de acesso neste incremento.

US25-CA01, CA03 e CA04 preservam os requisitos de interface da Sprint 4; a integração React será acompanhada em US25-T03. US25-CA07 será atendido com o futuro cadastro de usuários. Esses itens permanecem abertos, sem ampliar o escopo desta PR.

## Pendências

### PEN-01 — Incluir Alimentador no modelo

**Status:** Em andamento. Alimentador está descrito no domínio e na V1, com subestação obrigatória. Transformador referencia obrigatoriamente o alimentador; diagrama relacional e implementação Java permanecem pendentes.

- [ ] **PEN-01-CA01:** Representar Alimentador ligado à Subestacao e ao Transformador nos modelos de domínio e de dados.
- [ ] **PEN-01-CA02:** Manter consistência entre modelos, schema e implementação.
- [x] **PEN-01-T01:** Documentar Alimentador e o caminho Medidor → Transformador → Alimentador.
- [ ] **PEN-01-T02:** Sincronizar o diagrama relacional com as cardinalidades e a obrigatoriedade definidas no domínio e na V1.
- [ ] **PEN-01-T03:** Conferir as relações no schema e no código.

### PEN-02 — Alinhar Consumidor e Medidor

**Status:** Em andamento. A V1 representa o titular com zero ou vários medidores e permite medidores em estoque sem titular ou transformador. Contratos, diagrama relacional, código e testes funcionais permanecem pendentes.

- [ ] **PEN-02-CA01:** Modelo relacional e código representam um titular com zero ou vários medidores; medidor_id no consumidor não representa essa coleção.
- [x] **PEN-02-CA02:** Documentar a localização provisória do fornecimento e a limitação do histórico de titularidade.
- [x] **PEN-02-T01:** Registrar a semântica do titular e sua relação com medidores.
- [ ] **PEN-02-T02:** Alinhar modelo relacional e contratos de US06/US07.
- [ ] **PEN-02-T03:** Verificar cadastros de titular sem medidor e com vários medidores.

### PEN-03 — Corrigir nomenclatura documento

**Status:** Em andamento. O domínio e a V1 usam documento, com restrições NOT NULL e UNIQUE no schema. O diagrama anterior ainda contém dccumento; normalização, validação e implementação do cadastro permanecem pendentes.

- [ ] **PEN-03-CA01:** Usar documento no modelo relacional e no código.
- [ ] **PEN-03-CA02:** Definir obrigatoriedade, unicidade e regras de validação do documento no contrato de Consumidor.
- [x] **PEN-03-T01:** Registrar a nomenclatura no modelo de domínio.
- [ ] **PEN-03-T02:** Aplicar a nomenclatura e as validações ao modelo relacional e ao cadastro.

### PEN-04 — Incluir história de autenticação

**Status:** Concluído. A Sprint 4 incluiu US25 e seus critérios. A implementação da autenticação permanece em US25.

- [x] **PEN-04-CA01:** Existir história US25 com critérios de autenticação e perfis.
- [x] **PEN-04-T01:** Incluir US25 no backlog.

### PEN-05 — Completar README e execução

**Status:** Em andamento. A stack, os links da documentação e as instruções Maven do backend estão registrados. A configuração dos próximos módulos e a execução independente do procedimento permanecem pendentes.

- [ ] **PEN-05-CA01:** Informar tecnologias, pré-requisitos e comandos para frontend e backend.
- [ ] **PEN-05-CA02:** Outro integrante consegue executar o projeto seguindo o README.
- [x] **PEN-05-T01:** Organizar os links da documentação no README.
- [ ] **PEN-05-T02:** Documentar configuração e execução junto à implementação.
- [ ] **PEN-05-T03:** Verificar a execução independente do procedimento e apresentar os resultados.

### PEN-06 — Disponibilizar backend e API

**Status:** Em andamento. Base Java 24/Spring Boot 4.1.1 com persistência, schema SQL inicial, mapeamento de Usuario e autenticação HTTP. As demais entidades Java, endpoints de cadastro e monitoramento e integração com o frontend permanecem pendentes.

- [x] **PEN-06-CA01:** Definir a stack e registrá-la no README.
- [ ] **PEN-06-CA02:** Executar a API localmente conforme as instruções e atender às rotas dos módulos da Sprint 4.
- [ ] **PEN-06-CA03:** Frontend consome a API por variável de ambiente, sem endereço fixo no código.
- [ ] **PEN-06-T01:** Preparar metadados do projeto, versões do ambiente e configuração de execução.
- [ ] **PEN-06-T02:** Gerar a base Maven pelo Spring Initializr, preservar sua estrutura com os ajustes descritos em "Estrutura do pom.xml" e verificar build e inicialização.
- [ ] **PEN-06-T03:** Disponibilizar endpoints incrementalmente pelas tarefas de US25, US01, US03, US16 e US20.
- [ ] **PEN-06-T04:** Integrar o frontend à API e verificar os contratos.

**Em revisão — PEN-06-T01/PEN-06-T02:** Metadados, estrutura Maven, classe principal, teste de contexto e instruções de execução disponíveis. A configuração de persistência inclui PostgreSQL, JPA, Flyway, V1 e H2 para testes. Os critérios de API e integração permanecem pendentes.

### PEN-07 — Registrar evidências de testes

**Status:** Em andamento. Os resultados das verificações são apresentados na revisão dos incrementos, sem arquivos de relatório ou logs no repositório. As evidências funcionais das histórias permanecem pendentes.

- [ ] **PEN-07-CA01:** Cada história concluída possui evidências de teste apresentadas na revisão, fora dos arquivos do repositório.
- [ ] **PEN-07-CA02:** Cada resultado identifica história, critérios, procedimento, ambiente e resultado.
- [x] **PEN-07-T01:** Definir a forma de apresentar os resultados de testes.
- [ ] **PEN-07-T02:** Apresentar os resultados reais da primeira execução na revisão.
- [ ] **PEN-07-T03:** Verificar as evidências na revisão de cada história.

## Sequência de implementação

| Etapa | Entrega | Dependências principais |
|---|---|---|
| 1 | Base Java 24/Spring Boot 4.1.1 baseada no Initializr, build e inicialização | Metadados e ambiente de execução |
| 2 | Autenticação e perfil único — US25 | Matriz de acesso, contratos da sessão HTTP e provisionamento inicial |
| 3 | Cadastro de subestações — US01 | Backend e autenticação |
| 4 | Alimentador mínimo — parte de US02 | Subestação e regras do cadastro |
| 5 | Cadastro de transformadores — US03 | Subestação, alimentador e consistência dos vínculos |
| 6 | Eventos e alertas ativos — US16 e US20 | Usuários, ativos, datas, situações e criticidade |
| 7 | Titular, medidores e vínculos — US07/US06; restante de US02 | Criar titular antes dos medidores e completar depois suas consultas de vínculos |
| 8 | Fonte de medições/simulador e monitoramento — US08–US11 | Cadastros, unidades e contratos de leitura |
| 9 | Interrupções, situação e manutenção — US17–US19 | Infraestrutura de eventos |
| 10 | Mapa, perdas e indicadores — US13, US12, US14 e US15 | Geografia, dados históricos e fórmulas |
| 11 | Alertas automáticos e histórico — US21–US24 | Monitoramento, limites e políticas de recuperação |
| 12 | Equipes, usinas e integrações restantes — US04/US05 | Regras específicas de cada cadastro |

O cadastro mínimo de alimentadores precede US03. A conclusão desse recorte não encerra toda US02.

A base do backend compreende a estrutura gerada, build, teste de inicialização e instruções de execução. Os endpoints de domínio serão entregues nos incrementos seguintes.

## Limitações e funcionalidades posteriores

- O MVP não mantém histórico completo de trocas de titularidade nem uma entidade UnidadeConsumidora.
- A cadeia cadastral não representa toda a topologia elétrica.
- Executores individuais de manutenção e múltiplas equipes simultâneas ficam para uma etapa posterior.
- Demanda, fluxo entre regiões e proteção/automação precisam de critérios detalhados para cobrir integralmente o briefing.
- Fluxos específicos de eventos de sobrecarga e falha precisam de tarefas próprias.
- O simulador integra o planejamento da Sprint 5 para demonstrar o monitoramento.
- Meteorologia, relatórios PDF e recursos de IA permanecem diferenciais futuros.
