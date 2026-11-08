# Simulador de telemetria

Produtor Python 3.10+ para a API SCADA. As medições são identificadas como simuladas pela fonte cadastrada no backend.

## Status e ambiente backend

**Simulador e aceite funcional em PostgreSQL: CONCLUÍDOS.** O roteiro validou ingestão 201, replay 200, conflito 409, perda após 305 segundos, replay sem renovar contato e recuperação por uma nova leitura com atualização do snapshot. O aceite visual das telas é responsabilidade da integração frontend.

Antes de preparar o Python, suba a API com PostgreSQL seguindo o [README do backend](../backend/README.md). Para criar o administrador exclusivamente local, use a flag `--scada.provisionamento.admin-local.enabled=true` descrita no manual. Ela fica desligada por padrão, não redefine contas existentes e é bloqueada no perfil prod. A V24 fornece a topologia demonstrativa; confira os IDs dos ativos e fontes nas APIs antes de preencher o arquivo local.

## Preparação

Na raiz do projeto, com PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r scripts/requirements.txt
Copy-Item scripts/.env-example scripts/.env
```

Preencha scripts/.env com URL, e-mail de uma conta real de OPERADOR ou GERENTE_OPERACOES, IDs dos ativos e IDs de suas fontes. Se SCADA_SENHA estiver vazia, o programa solicita a senha com entrada protegida. Variáveis de ambiente prevalecem sobre o arquivo. O leitor aceita NOME=valor, comentários em linhas iniciadas por # e valores entre aspas, sem expansão de variáveis.

O transformador precisa de capacidade positiva e fonte SIMULADA associada. A fonte é cadastrada previamente por POST /api/transformadores/{id}/fontes. A linha é opcional e deve ter tensão operacional positiva. Para enviar P/Q, sua fonte SIMULADA deve possuir uma subestação de referência que seja extremidade da linha.

O programa consulta tensão e capacidade pelo cadastro. O backend recebe a potência aparente e captura sua própria capacidade de referência. A capacidade não é enviada como campo de medição.

Use ativos/fontes exclusivos do cenário. Outro produtor da mesma fonte pode renovar o contato durante a pausa, e outra fonte do mesmo transformador pode vencer a seleção do snapshot. O programa confere os resultados e interrompe se não comprovar o cenário esperado.

## Roteiro guiado da US23

```powershell
.\.venv\Scripts\python.exe scripts/simulador_telemetria.py --env-file scripts/.env --modo demonstracao
```

1. Envia três ciclos normais, por padrão espaçados por cinco segundos. Se houver linha configurada, envia também suas grandezas. Confirma a carga do transformador no snapshot.
2. Reenvia leitura e chave, exige 200 com o mesmo ID e recebimento. Reutiliza a chave com potência diferente e exige 409.
3. Pausa todos os envios deste processo por 305 segundos. O período deve superar o timeout real do backend, cujo padrão é 300 segundos.
4. Confirma episódio ATIVO da fonte em /api/alertas-comunicacao e orienta o operador a observar a tela de alertas.
5. Reenvia a leitura antiga e comprova que o episódio permanece ATIVO.
6. Envia nova leitura e chave. Exige RESTABELECIDO no mesmo episódio, com data igual ao novo recebimento, e confere a carga atualizada no snapshot.

O episódio da US23 pertence a alerta_comunicacao. A recuperação não conclui EventoOperacional nem muda o estado elétrico do equipamento.

### Motor manual ou agendado

AVALIACAO_COMUNICACAO=manual usa POST /api/jobs/avaliar-comunicacao. Antes de começar, compara a tolerância devolvida pelo motor com TIMEOUT_COMUNICACAO_SEGUNDOS. Após a pausa, dispara uma avaliação.

AVALIACAO_COMUNICACAO=agendada aguarda o scheduler do backend. Habilite previamente scada.monitoramento.comunicacao.enabled=true em uma única instância. O padrão do job é 30 segundos após o término de cada avaliação. O cliente aguarda até ESPERA_ALERTA_SEGUNDOS pela detecção e falha claramente se ela não ocorrer.

Para ensaios rápidos, configure no backend um timeout menor e ajuste conjuntamente TIMEOUT_COMUNICACAO_SEGUNDOS e PAUSA_SEGUNDOS no arquivo local. A pausa é validada como estritamente maior que a tolerância.

## Envio contínuo

```powershell
.\.venv\Scripts\python.exe scripts/simulador_telemetria.py --env-file scripts/.env --modo continuo
```

O intervalo padrão entre ciclos é 30 segundos. --ciclos 5 limita a execução a cinco ciclos; zero continua até Ctrl+C. O modo contínuo somente produz leituras.

## Sessão, precisão e retries

O cliente obtém CSRF, autentica e renova o token após o login, conservando cookies. Expiração 401 causa nova autenticação; 403 permite renovar CSRF dentro do limite de tentativas. Recusa persistente interrompe a operação. Timeout, falha de conexão, 429 e erros transitórios de servidor têm retry limitado com espera exponencial. Conflitos 409 não são retentados como falhas de transporte.

Cada leitura nova recebe um UUID. Tentativas repetem exatamente payload e chave. O arquivo local .estado-telemetria.json conserva a leitura em andamento por substituição atômica. Após reinicialização, o produtor valida ativo/fonte e retoma a pendência antes de gerar novos dados. Use um processo por arquivo de configuração. Corrija a configuração ou revise a pendência se houver erro permanente de contrato.

SEMENTE reproduz a sequência de grandezas. Timestamps usam relógio atual, UTC e microssegundos. Decimal gera valores enviados como strings decimais com até seis casas, aceitas pelos DTOs BigDecimal da API. Tensão e corrente determinam uma potência aparente trifásica simplificada; P e Q são coerentes com ela. A potência aparente do transformador fica entre 70% e 90% da capacidade cadastrada. O cenário didático não resolve fluxo de carga da rede.

.env, pendências e arquivos temporários são ignorados pelo Git. Logs exibem etapas, endpoints, status, IDs de medição/alerta e carga, sem senha, cookies, token ou payload.

## Testes

```powershell
.\.venv\Scripts\python.exe -B -m unittest discover -s scripts -p "test_*.py" -v
```

Os testes locais verificam o protocolo HTTP com servidor de teste e a orquestração com relógio/esperas controlados. O aceite funcional contra a API real em PostgreSQL foi concluído. Para reproduzi-lo em outro ambiente, configure o provisionamento, as fontes e o banco conforme os manuais.
