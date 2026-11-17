# Guia de avaliação do Power Grid Control Center

O Power Grid Control Center é um sistema web para acompanhar equipamentos e ocorrências de uma rede elétrica. Neste roteiro, a banca vai entrar com perfis diferentes, ver leituras aparecerem na tela, acompanhar uma perda de comunicação e registrar uma falha de equipamento pela interface.

O apresentador pode preparar a base e as contas com antecedência. Para a demonstração de perda de comunicação, reserve cerca de seis minutos. Depois da preparação, o roteiro principal usa o navegador e o terminal. A conferência por SQL é opcional.

## 1. Preparar o ambiente

### O que instalar e abrir

Use **Java 24**, **Maven**, **PostgreSQL**, **Node.js com npm** e **Python 3.10 ou superior**. Abra a raiz do projeto, a pasta que contém `backend/`, `frontend/` e `scripts/`. Os comandos deste guia são para PowerShell.

| Terminal | O que fica rodando | Como saber se está pronto |
| --- | --- | --- |
| Backend | A aplicação Java que recebe medições e registra alertas. | A inicialização termina e a API responde, por padrão na porta 8080. |
| Frontend | O site que a banca vai usar. | O Vite mostra o endereço local, normalmente `http://localhost:5173`. |
| Simulador | O programa Python que envia leituras e imprime cada etapa. | O terminal mostra o início dos ciclos e os resultados de cada envio. |

### Configurar o PostgreSQL

Inicie o PostgreSQL e crie uma base local vazia chamada `power_grid_control_center`. No pgAdmin, conecte-se ao servidor local, clique com o botão direito em **Databases**, escolha **Create → Database**, informe o nome e clique em **Save**. Essa preparação é feita uma vez. Na primeira subida, o backend cria as tabelas e carrega os dados de demonstração.

Escolha como o backend deve encontrar essa base:

**Opção A — Usar variáveis de ambiente.** Digite as linhas abaixo no terminal onde você vai iniciar o Java. Troque o endereço, o usuário e a senha pelos dados do seu PostgreSQL.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/power_grid_control_center'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'sua-senha-local'
```

Uma variável de ambiente é um valor que o terminal passa para o programa ao iniciá-lo. `DB_URL` indica onde está a base. `DB_USERNAME` e `DB_PASSWORD` são as credenciais do **PostgreSQL**, distintas do e-mail e da senha usados para entrar no site. Se abrir outro terminal para iniciar o Java, defina as variáveis nele também. Se alterar um valor depois da subida, reinicie o backend para aplicar a configuração.

**Opção B — Usar os padrões do projeto.** Se preferir iniciar sem definir as variáveis, configure o PostgreSQL local com endereço `localhost:5432`, base `power_grid_control_center`, usuário `postgres` e senha local `1234`. O backend usa esses valores quando as três variáveis estão ausentes.

**Opção C — Ajustar o arquivo da aplicação.** Se preferir iniciar sem variáveis e o seu PostgreSQL usa outros dados, abra `backend/src/main/resources/application.yml`. Em `spring.datasource`, ajuste os valores padrão de `url`, `username` e `password`, preservando a indentação. Na expressão `${DB_PASSWORD:1234}`, o texto depois dos dois-pontos é o valor usado na ausência da variável. Recompile antes de executar o JAR para incorporar o arquivo editado. Guarde as credenciais próprias no ambiente local de ensaio.

### Iniciar o backend

No primeiro terminal, na raiz, execute:

```powershell
mvn -f backend/pom.xml verify
java -jar backend/target/power-grid-control-center-0.0.1-SNAPSHOT.jar --scada.provisionamento.admin-local.enabled=true --scada.monitoramento.comunicacao.enabled=true --scada.monitoramento.comunicacao.timeout-segundos=300 --scada.monitoramento.comunicacao.fixed-delay=30000
```

A primeira linha compila, executa os testes e gera o JAR. Aguarde sua conclusão antes da segunda linha, que inicia o backend com duas funções de demonstração:

- **Administrador inicial.** Fora do perfil `prod`, o provisionador cria a conta local `admin@powergrid.com`, senha `Admin@Power2026` e perfil **Gerente de Operações**, se o e-mail ainda estiver livre. Se a conta já existia, use a senha que foi cadastrada para ela.
- **Verificação automática de comunicação.** O backend procura fontes que passaram mais de cinco minutos sem comunicar. Aguarda 30 segundos entre o fim de uma rodada e o início da próxima.

O Flyway aplica as mudanças de banco na subida. A V24 cria duas subestações, um alimentador, a linha `DEMO-V24-LINHA`, o transformador `DEMO-V24-TRANSFORMADOR` e uma fonte simulada para cada um desses dois ativos. Uma **fonte** identifica quem envia as leituras de um equipamento. Deixe o backend rodando até o fim da apresentação.

Se o backend parar na inicialização, use a mensagem do terminal para localizar o problema. **Conexão recusada** indica que vale conferir se o PostgreSQL está iniciado e se a porta está correta. **Falha de autenticação** pede uma revisão do usuário e da senha da etapa anterior. Se o terminal informar que a base não existe, crie-a com o mesmo nome usado em `DB_URL` ou no arquivo da aplicação.

### Iniciar o frontend e fazer o primeiro login

1. Abra o segundo terminal na raiz. Se ainda não existir um `frontend/.env.local`, copie o exemplo com a primeira linha abaixo. Em um arquivo já existente, confira `VITE_API_URL=http://localhost:8080`. Esse endereço diz ao site onde encontrar a API.

   ```powershell
   Copy-Item frontend/.env.example frontend/.env.local
   Set-Location frontend
   npm ci
   npm run dev
   ```

2. Abra `http://localhost:5173` no navegador. Se mudar o endereço do site, informe a mesma origem em `FRONTEND_URL` antes de iniciar o backend. Se definir outra porta para a API em `SERVER_PORT`, ajuste `VITE_API_URL` no frontend e `SCADA_URL` no simulador para apontar para essa porta.
3. Preencha **E-mail** e **Senha** com os dados do gerente e clique em **Entrar no sistema**. O **Dashboard** deve abrir. Confira a conta identificada no cabeçalho.
4. Em **Visão Geral → Monitoramento**, procure `DEMO-V24-LINHA` em **Linhas de transmissão** e `DEMO-V24-TRANSFORMADOR` em **Carga dos transformadores**. Isso confirma que o site acessou a base preparada. Na primeira execução, é normal aparecer **Sem medição** antes do envio do simulador.

## 2. Testar os perfis pelo navegador

Deixe três contas prontas: **Gerente de Operações**, **Operador** e **Técnico de Manutenção**. O gerente pode preparar as contas de operador e técnico pelo [Apêndice A](#apêndice-a--preparar-contas). Para trocar de conta no site, clique em **Sair** no cabeçalho e entre novamente. Uma janela anônima permite manter outra conta aberta ao mesmo tempo.

### Gerente de Operações

1. Abra **Gestão → Relatórios** e depois **Infraestrutura → Usinas**. Confira que as duas telas abrem. São opções reservadas ao gerente na interface.
2. Abra **Gestão → Consumidores** e clique em **Novo consumidor**. Em **Tipo de pessoa**, selecione **Pessoa física**. Em **Nome**, digite `Consumidor de Ensaio`. Em **CPF**, use o número fictício de teste `111.444.777-35`, se ainda estiver livre. Preencha **Endereço de correspondência (opcional)** com `Rua de Ensaio, 10`. Clique em **Salvar**.
3. Use **Buscar por nome...** para localizar o consumidor. Confira que a tabela mostra nome e tipo de pessoa. CPF e endereço ficam fora da tabela para todos os perfis, inclusive para o gerente (listagem: `GET /api/consumidores`).
4. Abra **Visão Geral → Eventos** e localize **Registrar falha**. Depois vá a **Visão Geral → Alertas → Ativos**, role até **Perda de comunicação** e localize **Avaliar comunicação agora**. Nesta etapa, basta observar os botões.

### Operador

1. Entre como operador. Abra **Monitoramento** e **Alertas** e confirme que consegue acompanhar a rede.
2. Abra **Eventos** e procure **Registrar falha**. Em **Alertas → Ativos → Perda de comunicação**, procure **Avaliar comunicação agora**. Os dois botões ficam disponíveis. O operador fará o registro da etapa 5.
3. Abra **Consumidores** e busque o cadastro criado pelo gerente. A tabela mostra os mesmos dados resumidos. **Novo consumidor** também aparece para o operador.
4. Confira que **Relatórios** e **Usinas** ficam ocultos no menu. Digite `http://localhost:5173/relatorios` na barra de endereço. O site leva o operador de volta ao **Dashboard**.

### Técnico de Manutenção

1. Entre como técnico. Abra **Monitoramento**, **Alertas** e **Eventos** para conferir que o perfil consegue acompanhar os registros.
2. Em **Eventos**, confira que **Registrar falha** fica oculto. Em **Alertas → Ativos → Perda de comunicação**, confira que **Avaliar comunicação agora** fica oculto. Em **Consumidores**, o técnico pode procurar o cadastro de ensaio, mas **Novo consumidor** fica oculto.
3. Compare os mesmos lugares com o que apareceu nas sessões de gerente e operador. Se quiser conferir o bloqueio diretamente no backend, o apêndice explica como testar um **403 Forbidden** com sessão válida.

## 3. Configurar o simulador

O simulador é o programa Python que faz o papel de uma fonte. Ele entra na mesma API usada pelo site, gera leituras fictícias e mostra no terminal o resultado de cada tentativa.

### Criar o arquivo local

Abra o terceiro terminal na raiz. Execute as linhas abaixo. Se já houver um `scripts/.env`, pule a cópia e edite o arquivo existente.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r scripts/requirements.txt
Copy-Item scripts/.env-example scripts/.env
```

Abra `scripts/.env`. Em uma base vazia que recebeu a carga inicial da V24, use os pares abaixo. As fontes normalmente recebem **1** e **2**. Os equipamentos têm IDs próprios: **4** para a linha e **5** para o transformador. Use o e-mail do operador ou do gerente. Com `SCADA_SENHA` vazia, o programa solicita a senha no terminal e oculta os caracteres digitados.

```dotenv
SCADA_URL=http://localhost:8080
SCADA_EMAIL=admin@powergrid.com
SCADA_SENHA=
LINHA_ID=4
FONTE_LINHA_ID=1
TRANSFORMADOR_ID=5
FONTE_TRANSFORMADOR_ID=2
LEITURAS_DEMO=3
INTERVALO_DEMO_SEGUNDOS=5
TIMEOUT_COMUNICACAO_SEGUNDOS=300
PAUSA_SEGUNDOS=305
ESPERA_ALERTA_SEGUNDOS=90
AVALIACAO_COMUNICACAO=agendada
```

| Campo | Para que serve |
| --- | --- |
| `SCADA_URL` | Endereço da API Java, o mesmo informado em `VITE_API_URL` no frontend. |
| `SCADA_EMAIL` e `SCADA_SENHA` | Credenciais da conta que o programa usa para entrar no sistema. |
| `LINHA_ID` e `TRANSFORMADOR_ID` | Números dos equipamentos que aparecem em **Monitoramento**. |
| `FONTE_LINHA_ID` e `FONTE_TRANSFORMADOR_ID` | Números de quem envia a leitura para cada equipamento. Preencha cada par de ativo e fonte em conjunto. |
| `LEITURAS_DEMO` e `INTERVALO_DEMO_SEGUNDOS` | Quantidade de ciclos iniciais e espera entre eles. |
| `TIMEOUT_COMUNICACAO_SEGUNDOS` e `PAUSA_SEGUNDOS` | Limite de silêncio configurado no backend e duração da pausa do simulador. A pausa precisa superar o limite. |
| `AVALIACAO_COMUNICACAO` | Mude o valor do modelo, `manual`, para `agendada`. Assim, o simulador espera o agendador do backend detectar a perda. |
| `ESPERA_ALERTA_SEGUNDOS` | Tempo máximo que o programa aguarda pela detecção depois da pausa. |

O programa também aceita essas chaves como variáveis de ambiente. Quando a mesma chave existe no terminal e no arquivo, prevalece a do terminal. O `scripts/.env` configura o Python. Já as credenciais de banco são fornecidas ao Java pela etapa 1.

### Conferir os IDs pelo navegador se a base já tiver dados

1. Abra **Monitoramento**, pressione **F12** e escolha **Rede** ou **Network**. Digite `snapshot` no filtro e clique em **Atualizar agora** na página. Selecione a requisição que apareceu, abra **Resposta** ou **Response** e procure os nomes de demonstração em `content`. O campo `ativoId` é o número do equipamento (consulta: `GET /api/monitoramento/snapshot`).
2. Se houver leituras da fonte de demonstração, confira o `fonteId` dentro da amostra de tensão da linha ou da carga do transformador na mesma resposta. Confira a origem `SIMULADA`. Para uma fonte que ainda não publicou, aguarde cinco minutos e uma rodada do agendador. Em **Alertas → Ativos → Perda de comunicação**, selecione **Sem comunicação**. A descrição mostra o equipamento e **fonte #** seguido do número.
3. Se houver várias fontes e você não conseguir distinguir qual pertence à demonstração, use uma base limpa ou a consulta opcional do apêndice. Para demonstrar somente o transformador, deixe `LINHA_ID` e `FONTE_LINHA_ID` vazios. O transformador e sua fonte são obrigatórios neste roteiro.

### Rodar

Pare outros simuladores que enviem dados aos mesmos equipamentos. Deixe duas abas do site abertas: **Monitoramento** e **Alertas → Ativos**. Execute na raiz e digite a senha quando o terminal pedir.

```powershell
.\.venv\Scripts\python.exe scripts/simulador_telemetria.py --env-file scripts/.env --modo demonstracao
```

## 4. Acompanhar a telemetria, a queda e a recuperação

### A. Ver as leituras aparecerem no site

1. No terminal, espere **iniciando 3 ciclos de leituras normais**. Cada ciclo envia uma leitura ao transformador e, se configurada, outra à linha. O código **201** acompanhado de **nova medição** indica que o servidor recebeu e gravou um novo registro.
2. Em **Monitoramento**, clique em **Atualizar agora**. Em **Linhas de transmissão**, observe as grandezas e os horários de leitura. Em **Carga dos transformadores**, encontre o ativo de demonstração e compare **Carga mais recente** com a linha **Snapshot confirmado** do terminal. A tela pode arredondar o número para exibição.
3. Clique no nome do transformador para abrir o gráfico. A capacidade de referência da V24 é **10 MVA**. Por exemplo, uma leitura de **7 MVA** para essa capacidade representa **70%**. O valor efetivo do ensaio depende da leitura enviada. O **snapshot** é a consulta que reúne as últimas amostras para exibir no painel (consulta: `GET /api/monitoramento/snapshot`).

### B. Entender o 200 e o 409 no terminal

1. **200 OK — mesma leitura, mesma chave.** Imagine que o servidor gravou uma medição, mas a resposta se perdeu. O simulador pode repetir o envio com a mesma chave, chamada `Idempotency-Key`. Procure **Replay 200 confirmado para a medição #...** e anote o número. O servidor devolve a medição original, com o mesmo ID e o mesmo horário de recebimento. Essa é a proteção contra duplicação.
2. **409 Conflict — valor diferente, mesma chave.** O programa altera a potência e tenta usar a chave de novo. Procure **Conflito demonstrado: mesma chave com conteúdo diferente retornou 409**. Esse aviso é esperado. Uma chave já usada identifica o conteúdo original. A tentativa de trocar o valor é recusada e o registro anterior permanece válido.

### C. Ver o agendador detectar a perda de comunicação

1. Ao aparecer **iniciando blecaute de comunicação de 305 segundos**, comece a contar a pausa. O simulador fica sem enviar dados. O site e a API continuam acessíveis.
2. Abra **Alertas → Ativos**, desça até **Perda de comunicação** e selecione **Sem comunicação**. Perto do fim da pausa, recarregue a página e selecione o filtro novamente. Durante esse ensaio, deixe **Avaliar comunicação agora** sem uso. O botão dispara uma rodada manual. Aqui o objetivo é observar a rodada automática.
3. Depois de mais de **300 segundos sem um novo recebimento**, a próxima rodada agendada registra a perda. Como as rodadas são periódicas, o alerta pode aparecer alguns segundos depois da marca de 305 segundos. No terminal, procure **backend confirmou alerta #... ATIVO** e anote o ID. Na interface, a situação correspondente é **Sem comunicação**. A linha do alerta mostra o ativo, a fonte e o momento da detecção (consulta: `GET /api/alertas-comunicacao`).
4. O roteiro prossegue para a recuperação logo em seguida. Se o estado ativo passar entre duas atualizações da tela, use o registro do terminal e confira o mesmo episódio no filtro da etapa E.

### D. Conferir que a repetição preserva o alerta

Durante a perda, o programa envia novamente a leitura antiga. O terminal deve mostrar outro **200** para o **mesmo número** de medição e confirmar que **o episódio continua ATIVO e o recebimento original foi preservado**. A repetição confirma que o servidor conhece aquela leitura. Para restabelecer a comunicação, a fonte precisa entregar uma leitura nova.

### E. Ver o retorno da comunicação

1. Procure **restabelecendo com uma nova leitura e uma nova chave**. O novo envio recebe **201**. O terminal deve confirmar **RESTABELECIDO e carga atualizada no snapshot**. O recebimento da nova leitura atualiza o último contato da fonte e encerra o episódio.
2. No site, volte a **Alertas → Ativos → Perda de comunicação** e escolha **Restabelecidos**. Se necessário, recarregue a página. Procure o mesmo equipamento e a mesma fonte. O item deve mostrar **Restabelecido**, conservar o horário da detecção e exibir o horário do retorno.
3. Volte a **Monitoramento**, clique em **Atualizar agora** e compare a carga e o horário com o terminal. A leitura nova deve aparecer como a mais recente.
4. Para registrar a evidência, guarde o trecho do terminal com **201 → 200 → 409 → ATIVO → 200 com episódio ainda ativo → 201 → RESTABELECIDO**. Junte a captura do episódio restabelecido e do painel com a carga nova. A banca pode conferir o resultado com essas evidências sem consultar tabelas.

Se o programa parar antes do alerta, confira se o backend foi iniciado com o agendador habilitado, se o limite é de 300 segundos nos dois lados e se outro produtor está enviando para a mesma fonte. Se o snapshot divergir, confira se outra fonte está publicando para o mesmo transformador.

## 5. Registrar uma falha de equipamento pela interface

O exercício anterior acompanha a comunicação de uma fonte. Aqui, o operador vai registrar um problema no equipamento, iniciar o atendimento e concluir o reparo. Faça essa etapa depois da telemetria.

1. Entre como **Operador**. Em **Infraestrutura → Transformadores**, confira que o ativo de demonstração está **Operacional**. Use um equipamento sem falha pendente.
2. Abra **Visão Geral → Eventos** e clique em **Registrar falha**. Em **Ativo com falha**, selecione o transformador. Em **Descrição**, digite `Falha de ensaio da banca`. Em **Responsável**, deixe **Eu (seu nome)**. Em **Criticidade (opcional)**, selecione **Alta**.
3. Clique em **Salvar**. A ocorrência deve entrar na lista como **Aberto**. Em **Transformadores**, confirme que o ativo passou para **Falha** (registro: `POST /api/falhas-equipamento`).
4. Volte a **Eventos** e tente registrar outra falha para o mesmo equipamento. O formulário deve mostrar que já existe uma falha não concluída. Feche o formulário e confira que a lista contém somente a ocorrência criada nesse ensaio (retorno: `409 Conflict`).
5. Localize `Falha de ensaio da banca` pelo campo **Buscar descrição ou ativo...**. Na linha do evento, clique em **Iniciar**. Escreva **Reparo iniciado** em **Comentário** e clique em **Salvar**. A situação muda para **Em andamento**.
6. Na mesma linha, clique em **Concluir**. Informe **Reparo verificado pela banca** em **Comentário**. Em **Estado do ativo após o reparo**, escolha **Em manutenção** e clique em **Salvar**. Confira **Concluído** em Eventos e **Em manutenção** em Transformadores.
7. Na linha do evento, abra o botão de histórico, identificado pela descrição da ocorrência. Confira as duas transições, os horários, os comentários e quem fez cada mudança (consulta: `GET /api/eventos-operacionais/{id}/transicoes`).

## Apêndice A — Preparar contas

A versão atual disponibiliza o cadastro de usuários pela API. Prepare as contas antes da apresentação. O Postman é um aplicativo que permite montar a requisição por abas e observar a resposta. As atividades da banca começam no login do site com as contas prontas.

1. Com o backend rodando, abra o Postman e crie uma requisição **GET** para `http://localhost:8080/api/auth/csrf`. Clique em **Send**. Na resposta, copie o valor de `token` e anote o nome em `headerName`. Mantenha os cookies habilitados e use `localhost` nas requisições seguintes.
2. Crie uma requisição **POST** para `http://localhost:8080/api/auth/login`. Em **Headers**, adicione o nome e o valor copiados. Em **Body**, selecione **x-www-form-urlencoded**. Na coluna **Key**, escreva `email` e `password` em linhas separadas. Na coluna **Value**, preencha com os dados do gerente. Clique em **Send**. A resposta esperada é **200**, com o perfil `GERENTE_OPERACOES`.
3. Volte à requisição de CSRF, clique em **Send** e copie o token atualizado. O login renova a sessão. Esse novo token será usado nas próximas operações de escrita.
4. Crie uma requisição **POST** para `http://localhost:8080/api/usuarios`. Em **Headers**, informe o cabeçalho CSRF com o token atualizado. Em **Body**, selecione **raw** e escolha **JSON**. Cole o exemplo, ajuste o e-mail se já estiver cadastrado e clique em **Send**.

   ```json
   {
     "nome": "Operador de Ensaio",
     "email": "operador.banca@example.com",
     "senha": "EnsaioLocal#2026",
     "perfil": "OPERADOR"
   }
   ```

5. Confira **201 Created**. Para criar o técnico, mantenha a requisição e troque o nome por `Técnico de Ensaio`, o e-mail por `tecnico.banca@example.com` e o perfil por `TECNICO_MANUTENCAO`. Defina uma senha de ensaio com pelo menos 12 caracteres, até 72 bytes em UTF-8, incluindo maiúscula, minúscula, número e símbolo. Clique em **Send**. A resposta inclui o ID da conta, o ID de quem a criou e o horário de criação. A senha fica fora da resposta.
6. Para conferir o bloqueio no servidor, encerre a sessão do gerente no Postman com um **POST** para `/api/auth/logout` e o token atual. Repita o login e a obtenção de token como operador ou técnico. Tente o mesmo cadastro de usuário com um e-mail livre. A resposta esperada é **403 Forbidden**. Usar uma sessão autenticada e o token correto permite testar o perfil, pois uma requisição sem CSRF também pode retornar 403.

Para conferir a proteção dos dados pessoais além da tabela do site, abra uma requisição **GET** para `/api/consumidores/{id}` em cada sessão. O gerente recebe o detalhe com documento, endereço e `Cache-Control: no-store`. Operador e técnico recebem apenas ID, nome e tipo de pessoa.

## Apêndice B — Conferência opcional no PostgreSQL e no H2

Use esta etapa se a banca quiser ver como o sistema gravou os dados. Abra a mesma base indicada em `DB_URL`. No pgAdmin, selecione a base e abra **Query Tool**. As consultas abaixo apenas leem dados.

### Conferir a V24 e os IDs de demonstração

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version = '24';

SELECT f.id AS fonte_id, a.id AS ativo_id, a.nome, f.identificador,
       f.origem, f.subestacao_referencia_id, f.ultima_comunicacao_em
FROM fonte_medicao f
JOIN ativo_rede a ON a.id = f.ativo_id
WHERE f.identificador IN ('DEMO-V24-FONTE-LINHA', 'DEMO-V24-FONTE-TRANSFORMADOR')
ORDER BY f.id;
```

Procure a versão `24` aplicada com sucesso. Use as colunas `ativo_id` e `fonte_id` para montar os pares de configuração do simulador.

### Conferir as medições e a comunicação

```sql
SELECT m.id, m.fonte_id, m.medido_em, m.recebido_em, m.chave_idempotencia,
       m.tensao_kv, m.corrente_a, m.potencia_ativa_mw, m.potencia_reativa_mvar,
       m.frequencia_hz
FROM medicao_linha m
JOIN fonte_medicao f ON f.id = m.fonte_id
WHERE f.identificador = 'DEMO-V24-FONTE-LINHA'
ORDER BY m.medido_em DESC, m.id DESC
LIMIT 10;

SELECT m.id, m.fonte_id, m.medido_em, m.recebido_em, m.chave_idempotencia,
       m.potencia_aparente_mva, m.capacidade_referencia_mva
FROM medicao_transformador m
JOIN fonte_medicao f ON f.id = m.fonte_id
WHERE f.identificador = 'DEMO-V24-FONTE-TRANSFORMADOR'
ORDER BY m.medido_em DESC, m.id DESC
LIMIT 10;

SELECT ac.id, ac.fonte_id, ac.ativo_id, ac.status, ac.tolerancia_segundos,
       ac.ultima_comunicacao_em AS contato_na_deteccao, ac.detectado_em,
       ac.restabelecido_em, f.ultima_comunicacao_em AS contato_atual
FROM alerta_comunicacao ac
JOIN fonte_medicao f ON f.id = ac.fonte_id
WHERE f.identificador = 'DEMO-V24-FONTE-TRANSFORMADOR'
ORDER BY ac.detectado_em DESC, ac.id DESC;
```

O roteiro padrão grava quatro novas medições por ativo configurado, quando começa sem leitura pendente e sem outros produtores. São três no início e uma na recuperação. Compare a medição mais recente do transformador com a carga da tela. A carga é calculada usando potência aparente e capacidade de referência da própria medição.

Na última consulta, localize o alerta pelo ID anotado no terminal. O estado final deve ser `RESTABELECIDO`. O horário de restabelecimento e o último contato devem coincidir com o horário de recebimento da nova medição. O horário de medição indica quando a fonte produziu a leitura. O horário de recebimento indica quando o backend a aceitou.

### Conferir a falha de equipamento

```sql
SELECT fe.evento_operacional_id, e.ativo_id, e.categoria, e.situacao,
       fe.estado_anterior, fe.registrado_em, fe.estado_apos_reparo,
       a.estado_operacional
FROM falha_equipamento fe
JOIN evento_operacional e ON e.id = fe.evento_operacional_id
JOIN ativo_rede a ON a.id = e.ativo_id
WHERE e.descricao = 'Falha de ensaio da banca'
ORDER BY fe.registrado_em DESC;
```

O campo `falha_equipamento.evento_operacional_id` liga a falha ao evento visto na interface. Depois de concluir o exercício, confira `FALHA_EQUIPAMENTO` na categoria, `CONCLUIDO` na situação e `MANUTENCAO` no estado após o reparo e no estado atual do ativo.

O **H2** é o banco em memória usado pela suíte Maven. Os testes aplicam o Flyway até a V23 e preparam seus próprios cenários. Para conferir os fluxos de falha e comunicação na suíte, execute na raiz:

```powershell
mvn -f backend/pom.xml '-Dtest=FalhaEquipamentoFluxoE2ETest,FalhaEquipamentoRepositoryTest,AlertaComunicacaoControllerTest' test
```

A V24 e as medições enviadas durante a apresentação ficam no PostgreSQL usado pelo backend em execução.

## Manuais de referência

- [Backend e provisionamento local](../backend/README.md)
- [Execução do frontend](../frontend/README.md)
- [Configuração e modos do simulador](../scripts/README.md)
