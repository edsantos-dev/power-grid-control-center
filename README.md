# Power Grid Control Center

Plataforma web para supervisão e gerenciamento de uma Rede Elétrica Inteligente (Smart Grid), desenvolvida para a **Operadora Nacional de Energia** (organização fictícia, inspirada em centros de operação reais de sistemas elétricos de potência).


---

## Sobre o Projeto

Atualmente, as informações provenientes de subestações, linhas de transmissão, redes de distribuição e sistemas de medição estão distribuídas em diferentes aplicações, dificultando o monitoramento da infraestrutura elétrica, a identificação de falhas e a tomada de decisões em situações críticas.

O **Power Grid Control Center** tem como objetivo consolidar essas informações operacionais em tempo real, permitindo maior confiabilidade, eficiência energética e rapidez na resposta a eventos que possam comprometer a continuidade do fornecimento de energia elétrica.

### Objetivo Geral

Desenvolver uma plataforma web para monitoramento e gerenciamento de uma rede elétrica inteligente, permitindo acompanhar o estado operacional da infraestrutura, visualizar indicadores em tempo real e apoiar a tomada de decisão dos operadores do Centro de Controle.

---

## Funcionalidades

### Cadastro e Gerenciamento de Ativos
- Subestações
- Linhas de transmissão
- Alimentadores
- Transformadores
- Consumidores
- Medidores inteligentes (Smart Meters)
- Usinas geradoras
- Equipes de manutenção

Cada ativo possui informações técnicas, localização geográfica, capacidade operacional e estado atual de funcionamento.

### Monitoramento em Tempo Real
Acompanhamento contínuo das principais variáveis elétricas da rede:
- Tensão, corrente elétrica, potência ativa e reativa
- Frequência da rede e fator de potência
- Consumo por intervalo em kWh e histórico agregado em MWh
- Carga dos transformadores e perdas na distribuição
- Estado operacional de equipamentos, disponibilidade de subestações, fluxo de energia entre regiões e funcionamento de dispositivos de proteção/automação

### Dashboard Gerencial
- Mapa geográfico com localização de subestações, linhas de transmissão e redes de distribuição
- Indicadores-chave de desempenho (KPIs)
- Gráficos históricos e fluxos de potência
- Estatísticas de consumo, perdas técnicas, disponibilidade de ativos e qualidade do fornecimento

### Registro de Eventos Operacionais
Rastreabilidade de manobras, desligamentos programados, interrupções, falhas em equipamentos e manutenções, registrando data/horário, ativo envolvido, categoria, criticidade, responsável e situação da ocorrência.

### Sistema de Alertas Inteligentes
Geração automática e classificação por criticidade para sobrecarga em transformadores, tensão anormal nas linhas e perda de comunicação das fontes. Alertas genéricos têm reconhecimento e resolução auditados. Sobrecorrente e oscilações de frequência como gatilhos automáticos permanecem ampliações futuras.

### Diferenciais (funcionalidades futuras/opcionais)
- [x] Simulador de telemetria com sessão, CSRF, idempotência e roteiro US23
- [ ] Integração com APIs públicas de previsão meteorológica
- [ ] Geração automática de relatórios gerenciais em PDF
- [x] Histórico de energia, consumo, perdas estimadas e indicadores operacionais no backend
- [ ] Previsão de demanda energética
- [ ] Detecção automática de perdas não técnicas (IA)
- [ ] Identificação de sobrecargas com IA
- [ ] Estimativa de falhas em equipamentos
- [ ] Otimização de despacho de equipes de manutenção

---

## Arquitetura


```
[Frontend] <---> [Backend / API] <---> [Banco de Dados]
                        |
                  [Simulador / Integrações externas]
```

---

## Tecnologias

| Componente | Tecnologia |
|---|---|
| Frontend | React, TypeScript e Vite |
| Linguagem do backend | Java 24 |
| Framework | Spring Boot 4.1.1 |
| Segurança | Spring Security, com versão gerenciada pelo Spring Boot 4.1.1 |
| Banco de dados | PostgreSQL |
| Migrações | Flyway |
| Build do backend | Maven, com estrutura gerada pelo Spring Initializr |

O backend concluiu o escopo aprovado das US01 a US25 do MVP, incluindo cadastros, topologia, telemetria idempotente, snapshot agregado, eventos, alertas, indicadores, históricos e segurança de dados pessoais. O frontend integra as APIs principais; a grade de monitoramento ainda precisa migrar para o snapshot. O simulador reproduzível e o aceite funcional de Backend, PostgreSQL e Simulador estão **CONCLUÍDOS**, incluindo ingestão, replay, conflito e perda/restabelecimento de comunicação. As tarefas visuais e limitações estão no backlog.

O `pom.xml` do backend preserva a estrutura e a formatação do Spring Initializr, com Java 24 e dependências gerenciadas pelo Spring Boot 4.1.1. Os detalhes estão no [backlog](docs/backlog.md).

---

## Como Executar

### Frontend

Com Node.js e npm disponíveis:

```bash
cd frontend
npm ci
npm run dev
```

Os comandos `npm run build` e `npm run lint` estão definidos no frontend para geração da aplicação e verificação estática.

### Backend

Os pré-requisitos e os comandos Maven para build, testes e execução estão no [README do backend](backend/README.md).

Para provisionar o administrador exclusivamente no ambiente local, após o build e com o PostgreSQL disponível, execute na raiz:

```powershell
java -jar backend/target/power-grid-control-center-0.0.1-SNAPSHOT.jar --scada.provisionamento.admin-local.enabled=true
```

A flag cria `admin@powergrid.com` com a senha local `Admin@Power2026` somente se o e-mail não existir. O provisionador fica desligado por padrão e é bloqueado pelo perfil `prod`; não redefine contas existentes. Em produção, o gerente inicial é provisionado administrativamente. O fallback de `DB_PASSWORD` é intencional para o desenvolvimento local, conforme descrito no manual do backend.

### Simulador e handoff

O [roteiro do simulador](scripts/README.md) explica o ambiente virtual, a cópia de `.env-example`, a configuração de ativos/fontes e a execução da demonstração US23. O aceite funcional no PostgreSQL está **CONCLUÍDO**. Para a integração visual, consulte a seção [Pendências de Frontend e Integração Visual](docs/backlog.md#pendências-de-frontend-e-integração-visual).

---

## Documentação

Documentação atual:

- [Modelo de domínio](docs/modelo-dominio.md)
- [Backlog e planejamento](docs/backlog.md)

As Sprints registram o planejamento de cada período. O backlog contém o estado atual das entregas e o modelo descreve os relacionamentos e as regras de domínio.

- [x] Documentação técnica do backend
- [x] Modelo de dados do backend
- [x] Descrição da arquitetura da solução do backend
- [x] Aceite funcional de Backend, PostgreSQL e Simulador concluído; implantação compartilhada segue procedimento próprio
- [x] Testes automatizados do backend versionados e demonstração do simulador em PostgreSQL concluída; aceite visual de navegador acompanhado separadamente
- [ ] Apresentação final do sistema

---

## Equipe

| Nome | Função | GitHub |
|------|--------|--------|
| Eduardo Alan dos Santos | Gerente de Projeto \ Back-end |
| Felipe Augusto Graniska| Documentação |
| Lucas Wessendorf de Araujo | Fullstack |
| Victor Gabriel Alves Carneiro | Front-end |
| Vinicius Roth Roque | Teste |
