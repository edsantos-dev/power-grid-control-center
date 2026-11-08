"""CLI de envio contínuo e demonstração verificável da US23."""

from __future__ import annotations

import argparse
import getpass
import json
import logging
import random
import time
from dataclasses import replace
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Callable
from uuid import UUID, uuid4

import requests

from configuracao_telemetria import Configuracao
from gerador_telemetria import GeradorTelemetria
from simulador_telemetria import IdempotencyConflict, ScadaClient, ScadaHTTPError


LOGGER = logging.getLogger(__name__)


class Roteiro:
    def __init__(self, client: ScadaClient, config: Configuracao) -> None:
        self.client = client
        self.config = config
        self.gerador = GeradorTelemetria(config.semente)
        self.tensao_linha: Decimal | None = None
        self.capacidade: Decimal | None = None

    def preparar(self) -> None:
        if self.config.linha_id is not None:
            linha = self._executar(lambda: self.client.get_json(f"/api/linhas-transmissao/{self.config.linha_id}"))
            self.tensao_linha = self._positivo(linha.get("tensaoOperacaoKv"), "tensão da linha")
        if self.config.transformador_id is not None:
            transformador = self._executar(
                lambda: self.client.get_json(f"/api/transformadores/{self.config.transformador_id}")
            )
            self.capacidade = self._positivo(transformador.get("capacidadeMva"), "capacidade do transformador")
        self._retomar_pendencia()

    def demonstrar(self) -> None:
        if self.config.transformador_id is None:
            raise ValueError("O roteiro US23 exige TRANSFORMADOR_ID e FONTE_TRANSFORMADOR_ID.")
        if self.config.avaliacao == "manual":
            resumo = self._executar(self.client.avaliar_comunicacao)
            if resumo["toleranciaSegundos"] != self.config.timeout_comunicacao:
                raise ValueError("O timeout informado no simulador diverge do timeout devolvido pelo backend.")

        LOGGER.info("Passo A: iniciando %s ciclos de leituras normais.", self.config.leituras_demo)
        ultima = None
        for ciclo in range(self.config.leituras_demo):
            if ciclo:
                time.sleep(self.config.intervalo_demo)
            ultima = self.enviar_ciclo()
        endpoint, payload, chave, original = ultima
        self._verificar_snapshot(original)
        self._provar_replay(endpoint, payload, chave, original)

        adulterado = dict(payload)
        adulterado["potenciaAparenteMva"] = format(Decimal(payload["potenciaAparenteMva"]) + 1, "f")
        try:
            self._executar(lambda: self.client.send_medicao(endpoint, adulterado, chave))
        except IdempotencyConflict:
            LOGGER.info("Conflito demonstrado: mesma chave com conteúdo diferente retornou 409.")
        else:
            raise RuntimeError("A API aceitou conteúdo diferente para uma chave já utilizada.")

        LOGGER.info("Passo B: iniciando blecaute de comunicação de %s segundos...", self.config.pausa)
        LOGGER.info("Todos os envios deste processo serão pausados. Outros produtores da mesma fonte devem estar parados.")
        time.sleep(self.config.pausa)
        if self.config.avaliacao == "manual":
            self._executar(self.client.avaliar_comunicacao)
        episodio = self._aguardar_ativo()
        LOGGER.info("Passo C: backend confirmou alerta #%s ATIVO. Observe a indicação de perda na tela de alertas.",
                    episodio["id"])

        LOGGER.info("Passo D: reenviando a leitura antiga com sua chave original.")
        self._provar_replay(endpoint, payload, chave, original)
        atual = self._executar(lambda: self.client.get_json(f"/api/alertas-comunicacao/{episodio['id']}"))
        if atual["status"] != "ATIVO":
            raise RuntimeError("O replay alterou o episódio ativo; interrompendo a demonstração.")
        LOGGER.info("Replay confirmado: o episódio continua ATIVO e o recebimento original foi preservado.")

        LOGGER.info("Passo E: restabelecendo com uma nova leitura e uma nova chave.")
        _, _, nova_chave, nova = self.enviar_ciclo()
        if nova_chave == chave:
            raise RuntimeError("A recuperação exige uma chave nova.")
        recuperado = self._executar(lambda: self.client.get_json(f"/api/alertas-comunicacao/{episodio['id']}"))
        if recuperado["status"] != "RESTABELECIDO" or not recuperado.get("restabelecidoEm"):
            raise RuntimeError("O backend não confirmou o restabelecimento do episódio.")
        if self._instante(recuperado["restabelecidoEm"]) != self._instante(nova["recebidoEm"]):
            raise RuntimeError("O horário de restabelecimento diverge do recebimento da nova leitura.")
        self._verificar_snapshot(nova)
        LOGGER.info("Demonstração concluída: RESTABELECIDO e carga atualizada no snapshot.")

    def continuo(self, ciclos: int) -> None:
        contador = 0
        while ciclos == 0 or contador < ciclos:
            self.enviar_ciclo()
            contador += 1
            if ciclos == 0 or contador < ciclos:
                time.sleep(self.config.cadencia)

    def enviar_ciclo(self):
        ultima = None
        if self.config.linha_id is not None:
            payload = self.gerador.linha(
                self.config.fonte_linha_id, self.tensao_linha, datetime.now(timezone.utc)
            )
            endpoint = f"/api/linhas-transmissao/{self.config.linha_id}/medicoes"
            chave = uuid4()
            ultima = (endpoint, payload, chave, self._enviar(endpoint, payload, chave))
        if self.config.transformador_id is not None:
            ultima = self._nova_transformador()
        return ultima

    def _nova_transformador(self):
        payload = self.gerador.transformador(
            self.config.fonte_transformador_id, self.capacidade, datetime.now(timezone.utc)
        )
        endpoint = f"/api/transformadores/{self.config.transformador_id}/medicoes"
        chave = uuid4()
        return endpoint, payload, chave, self._enviar(endpoint, payload, chave)

    def _enviar(self, endpoint: str, payload: dict, chave: UUID) -> dict:
        self.config.pendencia.parent.mkdir(parents=True, exist_ok=True)
        temporario = self.config.pendencia.with_suffix(".tmp")
        temporario.write_text(json.dumps({"endpoint": endpoint, "payload": payload, "chave": str(chave)}),
                              encoding="utf-8")
        temporario.replace(self.config.pendencia)
        resposta = self._executar(lambda: self.client.send_medicao(endpoint, payload, chave))
        medicao = resposta.json()
        if medicao.get("origem") != "SIMULADA":
            raise RuntimeError("A fonte usada não é SIMULADA. Corrija o provisionamento antes de continuar.")
        self.config.pendencia.unlink()
        return medicao

    def _retomar_pendencia(self) -> None:
        if not self.config.pendencia.exists():
            return
        try:
            leitura = json.loads(self.config.pendencia.read_text(encoding="utf-8"))
            endpoint = leitura["endpoint"]
            payload = leitura["payload"]
            chave = UUID(leitura["chave"])
        except (ValueError, KeyError, TypeError):
            raise ValueError("O arquivo de pendência é inválido; revise-o antes de retomar.") from None
        permitidos = {
            f"/api/linhas-transmissao/{self.config.linha_id}/medicoes": self.config.fonte_linha_id,
            f"/api/transformadores/{self.config.transformador_id}/medicoes": self.config.fonte_transformador_id,
        }
        if not isinstance(payload, dict) or not permitidos.get(endpoint) or payload.get("fonteId") != permitidos[endpoint]:
            raise ValueError("A leitura pendente pertence a outro ativo/fonte; revise a configuração.")
        LOGGER.info("Retomando uma leitura pendente com a mesma chave e o mesmo payload.")
        self._enviar(endpoint, payload, chave)

    def _provar_replay(self, endpoint: str, payload: dict, chave: UUID, original: dict) -> None:
        resposta = self._executar(lambda: self.client.send_medicao(endpoint, payload, chave))
        medicao = resposta.json()
        if resposta.status_code != 200 or medicao["id"] != original["id"]:
            raise RuntimeError("A API não confirmou o replay da medição original.")
        if self._instante(medicao["recebidoEm"]) != self._instante(original["recebidoEm"]):
            raise RuntimeError("O replay alterou o instante original de recebimento.")
        LOGGER.info("Replay 200 confirmado para a medição #%s.", medicao["id"])

    def _aguardar_ativo(self) -> dict:
        limite = time.monotonic() + self.config.espera_alerta
        while True:
            pagina = self._executar(lambda: self.client.get_json(
                "/api/alertas-comunicacao",
                {"fonteId": self.config.fonte_transformador_id, "status": "ATIVO", "size": 1},
            ))
            if pagina["content"]:
                episodio = pagina["content"][0]
                if episodio["toleranciaSegundos"] != self.config.timeout_comunicacao:
                    raise RuntimeError("O alerta foi criado com timeout diferente do configurado no roteiro.")
                return episodio
            if time.monotonic() >= limite:
                raise RuntimeError("Nenhum alerta ATIVO foi observado. Confira scheduler, timeout e outros produtores.")
            time.sleep(2)

    def _verificar_snapshot(self, medicao: dict) -> None:
        pagina = self._executar(lambda: self.client.get_json(
            "/api/monitoramento/snapshot", {"ativoId": self.config.transformador_id}
        ))
        itens = pagina["content"]
        if len(itens) != 1 or not itens[0].get("cargaPercentual"):
            raise RuntimeError("O snapshot não retornou a carga do transformador.")
        amostra = itens[0]["cargaPercentual"]
        if (amostra["fonteId"] != self.config.fonte_transformador_id
                or self._instante(amostra["medidoEm"]) != self._instante(medicao["medidoEm"])):
            raise RuntimeError("Outra leitura está vencendo no snapshot. Isole as fontes do cenário.")
        if Decimal(str(amostra["valor"])) != Decimal(str(medicao["cargaPercentual"])):
            raise RuntimeError("A carga do snapshot diverge da leitura ingerida.")
        LOGGER.info("Snapshot confirmado: carga de %s%%.", amostra["valor"])

    def _executar(self, operacao: Callable):
        for tentativa in range(1, self.config.tentativas + 1):
            try:
                return operacao()
            except IdempotencyConflict:
                raise
            except ScadaHTTPError as erro:
                if erro.status_code not in {401, 403, 429, 500, 502, 503, 504}:
                    raise
                if tentativa == self.config.tentativas:
                    raise
                if erro.status_code == 401:
                    self.client.autenticar(self.config.email, self.config.senha)
                elif erro.status_code == 403:
                    self.client.renovar_csrf()
            except (requests.ConnectionError, requests.Timeout):
                if tentativa == self.config.tentativas:
                    raise RuntimeError("Falha de rede após as tentativas configuradas; leitura pendente preservada.") from None
            espera = min(2 ** (tentativa - 1), 8) + random.uniform(0, 0.25)
            LOGGER.warning("Falha transitória; tentativa %s de %s após %.2f s.",
                           tentativa + 1, self.config.tentativas, espera)
            time.sleep(espera)
        raise RuntimeError("Nenhuma tentativa executada.")

    @staticmethod
    def _positivo(valor, campo: str) -> Decimal:
        try:
            decimal = Decimal(str(valor))
        except InvalidOperation:
            raise ValueError(f"O cadastro precisa de {campo} positiva.") from None
        if not decimal.is_finite() or decimal <= 0:
            raise ValueError(f"O cadastro precisa de {campo} positiva.")
        return decimal

    @staticmethod
    def _instante(valor: str) -> datetime:
        return datetime.fromisoformat(valor.replace("Z", "+00:00")).astimezone(timezone.utc)


def main() -> int:
    parser = argparse.ArgumentParser(description="Produtor de telemetria e demonstração guiada da US23.")
    parser.add_argument("--env-file", type=Path, default=Path(__file__).with_name(".env"))
    parser.add_argument("--modo", choices=("demonstracao", "continuo"), default="demonstracao")
    parser.add_argument("--ciclos", type=int, default=0, help="No modo contínuo, zero envia até Ctrl+C.")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    client = None
    try:
        if args.ciclos < 0:
            raise ValueError("--ciclos deve ser não negativo.")
        config = Configuracao.carregar(args.env_file)
        if not config.senha:
            config = replace(config, senha=getpass.getpass("Senha da conta SCADA: "))
        client = ScadaClient(config.url)
        usuario = client.autenticar(config.email, config.senha)
        if usuario.get("perfil") not in {"OPERADOR", "GERENTE_OPERACOES"}:
            raise ValueError("O roteiro exige conta de operador ou gerente.")
        roteiro = Roteiro(client, config)
        roteiro.preparar()
        if args.modo == "demonstracao":
            roteiro.demonstrar()
        else:
            roteiro.continuo(args.ciclos)
        return 0
    except KeyboardInterrupt:
        LOGGER.info("Execução interrompida. Uma leitura não confirmada será retomada na próxima execução.")
        return 130
    except (ValueError, RuntimeError, OSError, requests.RequestException) as erro:
        # Exceções de transporte podem conter detalhes da conexão. O log é intencionalmente limitado.
        mensagem = str(erro) if isinstance(erro, (ValueError, RuntimeError)) else "Verifique conexão e configuração local."
        LOGGER.error("Roteiro interrompido: %s", mensagem)
        return 1
    finally:
        if client is not None:
            client.close()
