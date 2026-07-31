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
- Consumo instantâneo e demanda elétrica
- Carga dos transformadores e perdas na distribuição
- Estado operacional de equipamentos, disponibilidade de subestações, fluxo de energia entre regiões e funcionamento de dispositivos de proteção/automação

### Dashboard Gerencial
- Mapa geográfico com localização de subestações, linhas de transmissão e redes de distribuição
- Indicadores-chave de desempenho (KPIs)
- Gráficos históricos e fluxos de potência
- Estatísticas de consumo, perdas técnicas, disponibilidade de ativos e qualidade do fornecimento

### Registro de Eventos Operacionais
Rastreabilidade completa de manobras, desligamentos programados, interrupções, sobrecargas, falhas em equipamentos e manutenções, registrando data/horário, ativo envolvido, categoria, criticidade, responsável e situação da ocorrência.

### Sistema de Alertas Inteligentes
Geração e classificação de alertas por criticidade para eventos como:
- Sobrecarga em transformadores
- Queda ou elevação anormal de tensão
- Interrupção de fornecimento
- Falhas em subestações
- Sobrecorrente em linhas de transmissão
- Perda de comunicação com dispositivos inteligentes
- Oscilações na frequência da rede

### Diferenciais (funcionalidades futuras/opcionais)
- [ ] Simulador de rede elétrica (geração de medições em tempo real)
- [ ] Integração com APIs públicas de previsão meteorológica
- [ ] Geração automática de relatórios gerenciais em PDF
- [ ] Análise histórica de indicadores operacionais
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

## Tecnologias (a definir)

| Camada         | Tecnologia            |
|----------------|------------------------|
| Frontend       | _a definir_            |
| Backend        | _a definir_            |
| Banco de Dados | _a definir_            |
| Infraestrutura | _a definir_            |

---

## Como Executar

> _Instruções de instalação e execução serão detalhadas conforme o desenvolvimento avança._

```bash
# Clonar o repositório
git clone https://github.com/edsantos-dev/power-grid-control-center.git

# Acessar a pasta do projeto
cd power-grid-control-center

# Instalar dependências
# (comandos a definir)

# Executar a aplicação
# (comandos a definir)
```

---

## Documentação

- [ ] Documentação técnica
- [ ] Modelo de dados
- [ ] Descrição da arquitetura da solução
- [ ] Manual de instalação e execução
- [ ] Evidências de testes realizados
- [ ] Apresentação final do sistema

---

## Equipe

| Nome | Função | GitHub |
|------|--------|--------|
| Eduardo Alan dos Santos | Gerente de Projeto \ Back-end |
| Felipe Augusto Graniska| Documentação |
| Lucas Wessendorf de Araujo | Back-end |
| Victor Gabriel Alves Carneiro | Front-end |
| Vinicius Roth Roque | Teste |
