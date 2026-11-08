import json
import os
import tempfile
import unittest
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from unittest.mock import patch

import requests

from configuracao_telemetria import Configuracao
from gerador_telemetria import GeradorTelemetria
from roteiro_telemetria import Roteiro
from simulador_telemetria import IdempotencyConflict, ScadaHTTPError


class Resposta:
    def __init__(self, status, dados):
        self.status_code = status
        self.dados = dados

    def json(self):
        return self.dados


class BackendSimulado:
    def __init__(self):
        self.agora = datetime.now(timezone.utc)
        self.leituras = {}
        self.ultima = None
        self.episodio = None
        self.ultima_comunicacao = None
        self.resultados = []
        self.perder_resposta = False

    def send_medicao(self, endpoint, payload, chave):
        chave = str(chave)
        if chave in self.leituras:
            anterior = self.leituras[chave]
            if payload != anterior["payload"]:
                self.resultados.append(409)
                raise IdempotencyConflict(endpoint, 409)
            self.resultados.append(200)
            return Resposta(200, anterior["resposta"])
        resposta = {
            "id": len(self.leituras) + 1, "origem": "SIMULADA",
            "medidoEm": payload["medidoEm"], "recebidoEm": self.agora.isoformat(),
        }
        if "potenciaAparenteMva" in payload:
            resposta["cargaPercentual"] = str(Decimal(payload["potenciaAparenteMva"]) * 10)
        self.leituras[chave] = {"payload": dict(payload), "resposta": resposta}
        if "potenciaAparenteMva" in payload:
            self.ultima = resposta
            self.ultima_comunicacao = self.agora
            if self.episodio and self.episodio["status"] == "ATIVO":
                self.episodio["status"] = "RESTABELECIDO"
                self.episodio["restabelecidoEm"] = resposta["recebidoEm"]
        self.resultados.append(201)
        if self.perder_resposta:
            self.perder_resposta = False
            raise requests.Timeout()
        return Resposta(201, resposta)

    def get_json(self, endpoint, params=None):
        if endpoint == "/api/linhas-transmissao/1":
            return {"tensaoOperacaoKv": "138"}
        if endpoint == "/api/transformadores/2":
            return {"capacidadeMva": "10"}
        if endpoint == "/api/monitoramento/snapshot":
            return {"content": [{"cargaPercentual": {
                "fonteId": 20, "medidoEm": self.ultima["medidoEm"],
                "valor": self.ultima["cargaPercentual"],
            }}]}
        if endpoint == "/api/alertas-comunicacao":
            return {"content": [dict(self.episodio)] if self.episodio and self.episodio["status"] == "ATIVO" else []}
        if endpoint == "/api/alertas-comunicacao/1":
            return dict(self.episodio)
        raise AssertionError(f"Endpoint inesperado: {endpoint}")

    def avaliar_comunicacao(self):
        if (self.ultima_comunicacao and self.agora > self.ultima_comunicacao + timedelta(seconds=300)
                and self.episodio is None):
            self.episodio = {"id": 1, "status": "ATIVO", "toleranciaSegundos": 300, "restabelecidoEm": None}
        return {"toleranciaSegundos": 300}

    def dormir(self, segundos):
        self.agora += timedelta(seconds=segundos)


class GeradorTest(unittest.TestCase):
    def test_mesma_semente_reproduz_grandezas_em_utc_com_seis_casas(self):
        instante = datetime(2026, 11, 7, 10, tzinfo=timezone(timedelta(hours=-3)))
        a = GeradorTelemetria(42)
        b = GeradorTelemetria(42)
        leitura = a.linha(1, Decimal("138"), instante)
        self.assertEqual(leitura, b.linha(1, Decimal("138"), instante))
        self.assertEqual(leitura["medidoEm"], "2026-11-07T13:00:00.000000Z")
        for campo, valor in leitura.items():
            if campo not in {"fonteId", "medidoEm"}:
                self.assertEqual(len(valor.split(".")[1]), 6)
        p = Decimal(leitura["potenciaAtivaMw"])
        q = Decimal(leitura["potenciaReativaMvar"])
        aparente = Decimal(3).sqrt() * Decimal(leitura["tensaoKv"]) * Decimal(leitura["correnteA"]) / 1000
        self.assertLess(abs((p * p + q * q).sqrt() - aparente), Decimal("0.000002"))

    def test_capacidade_so_orienta_geracao_e_nao_e_enviada(self):
        payload = GeradorTelemetria(42).transformador(20, Decimal("10"), datetime.now(timezone.utc))
        self.assertEqual(set(payload), {"fonteId", "medidoEm", "potenciaAparenteMva"})
        self.assertTrue(Decimal("7") <= Decimal(payload["potenciaAparenteMva"]) <= Decimal("9"))

    def test_rejeita_instante_sem_fuso(self):
        with self.assertRaises(ValueError):
            GeradorTelemetria(42).linha(1, Decimal("138"), datetime(2026, 1, 1))


class ConfiguracaoTest(unittest.TestCase):
    def test_ambiente_prevalece_sobre_arquivo_sem_expor_senha_no_repr(self):
        with tempfile.TemporaryDirectory() as pasta:
            env = Path(pasta) / ".env"
            env.write_text('SCADA_EMAIL=operador@example.test\nSCADA_SENHA="valor-privado"\n'
                           "TRANSFORMADOR_ID=2\nFONTE_TRANSFORMADOR_ID=20\nCADENCIA_SEGUNDOS=5\n",
                           encoding="utf-8")
            with patch.dict(os.environ, {"CADENCIA_SEGUNDOS": "30"}, clear=True):
                config = Configuracao.carregar(env)
            self.assertEqual(config.cadencia, 30)
            self.assertEqual(config.senha, "valor-privado")
            self.assertNotIn(config.senha, repr(config))

    def test_rejeita_pausa_insuficiente_e_par_de_ids_incompleto(self):
        for valores in [
            {"TRANSFORMADOR_ID": "2"},
            {"TRANSFORMADOR_ID": "2", "FONTE_TRANSFORMADOR_ID": "20", "PAUSA_SEGUNDOS": "300"},
        ]:
            with self.subTest(valores=valores), patch.dict(
                os.environ, {"SCADA_EMAIL": "operador@example.test", **valores}, clear=True
            ):
                with self.assertRaises(ValueError):
                    Configuracao.carregar(Path("arquivo-inexistente.env"))


class RoteiroTest(unittest.TestCase):
    def setUp(self):
        self.pasta = tempfile.TemporaryDirectory()
        self.addCleanup(self.pasta.cleanup)
        self.config = Configuracao(
            url="http://localhost:8080", email="operador@example.test", senha="privada",
            transformador_id=2, fonte_transformador_id=20,
            pendencia=Path(self.pasta.name) / ".estado-telemetria.json",
        )
        self.backend = BackendSimulado()

    def test_fluxo_completo_comprova_replay_conflito_perda_e_recuperacao(self):
        roteiro = Roteiro(self.backend, self.config)
        with patch("roteiro_telemetria.time.sleep", side_effect=self.backend.dormir) as pausa:
            roteiro.preparar()
            roteiro.demonstrar()
        pausa.assert_any_call(305)
        self.assertEqual(len(self.backend.leituras), 4)
        self.assertEqual(self.backend.resultados.count(200), 2)
        self.assertEqual(self.backend.resultados.count(409), 1)
        self.assertEqual(self.backend.episodio["status"], "RESTABELECIDO")
        self.assertEqual(self.backend.episodio["restabelecidoEm"], self.backend.ultima["recebidoEm"])
        self.assertFalse(self.config.pendencia.exists())

    def test_timeout_apos_gravacao_preserva_chave_e_retorna_replay(self):
        self.backend.perder_resposta = True
        roteiro = Roteiro(self.backend, self.config)
        with patch("roteiro_telemetria.time.sleep"):
            roteiro.preparar()
            _, _, _, resposta = roteiro.enviar_ciclo()
        self.assertEqual(resposta["id"], 1)
        self.assertEqual(self.backend.resultados, [201, 200])
        self.assertEqual(len(self.backend.leituras), 1)

    def test_recuperacao_reinicia_tambem_os_envios_da_linha(self):
        config = replace(self.config, linha_id=1, fonte_linha_id=10)
        roteiro = Roteiro(self.backend, config)
        with patch("roteiro_telemetria.time.sleep", side_effect=self.backend.dormir):
            roteiro.preparar()
            roteiro.demonstrar()
        self.assertEqual(len(self.backend.leituras), 8)
        linhas = [item for item in self.backend.leituras.values() if item["payload"]["fonteId"] == 10]
        self.assertEqual(len(linhas), 4)
        self.assertEqual(linhas[-1]["resposta"]["recebidoEm"], self.backend.ultima["recebidoEm"])

    def test_reinicio_retoma_pendencia_com_mesma_chave(self):
        config = replace(self.config, tentativas=1)
        roteiro = Roteiro(self.backend, config)
        roteiro.preparar()
        self.backend.perder_resposta = True
        with self.assertRaises(RuntimeError):
            roteiro.enviar_ciclo()
        pendente = json.loads(config.pendencia.read_text())
        self.assertNotIn("senha", pendente)
        self.assertNotIn("privada", config.pendencia.read_text())
        Roteiro(self.backend, config).preparar()
        self.assertEqual(self.backend.resultados, [201, 200])
        self.assertIn(pendente["chave"], self.backend.leituras)
        self.assertFalse(config.pendencia.exists())

    def test_rejeita_timeout_do_backend_diferente_do_roteiro(self):
        roteiro = Roteiro(self.backend, replace(self.config, timeout_comunicacao=10, pausa=15))
        roteiro.preparar()
        with self.assertRaises(ValueError):
            roteiro.demonstrar()
        self.assertEqual(self.backend.leituras, {})

    def test_modo_agendado_observa_alerta_sem_chamar_job_manual(self):
        config = replace(self.config, avaliacao="agendada")
        roteiro = Roteiro(self.backend, config)

        def scheduler(segundos):
            self.backend.dormir(segundos)
            BackendSimulado.avaliar_comunicacao(self.backend)

        with patch("roteiro_telemetria.time.sleep", side_effect=scheduler):
            roteiro.preparar()
            roteiro.demonstrar()
        self.assertEqual(self.backend.episodio["status"], "RESTABELECIDO")

    def test_renova_autenticacao_ao_receber_401(self):
        from unittest.mock import Mock

        self.backend.autenticar = Mock()
        operacao = Mock(side_effect=[ScadaHTTPError("/api/teste", 401), "ok"])
        with patch("roteiro_telemetria.time.sleep"):
            resposta = Roteiro(self.backend, self.config)._executar(operacao)
        self.assertEqual(resposta, "ok")
        self.backend.autenticar.assert_called_once_with(self.config.email, self.config.senha)


if __name__ == "__main__":
    unittest.main()
