"""Testes do protocolo HTTP do cliente, sem depender de um backend em execução."""

import io
import json
import logging
import threading
import unittest
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs
from uuid import uuid4

from simulador_telemetria import IdempotencyConflict, ScadaClient, ScadaHTTPError


class ScadaClientTest(unittest.TestCase):
    def setUp(self):
        self.state = {"mode": "json", "authenticated": False, "requests": [], "readings": {}}
        state = self.state

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def respond(self, code, body, headers=None):
                data = json.dumps(body).encode()
                self.send_response(code)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                for name, value in (headers or {}).items():
                    self.send_header(name, value)
                self.end_headers()
                self.wfile.write(data)

            def do_GET(self):
                token = "after-login" if state["authenticated"] else "before-login"
                state["requests"].append(("GET", self.path))
                if self.path == "/api/monitoramento/snapshot?ativoId=1":
                    self.respond(200, {"carga": 12.345678})
                    return
                if state["mode"] == "json":
                    self.respond(200, {"token": token, "headerName": "X-CSRF-TOKEN"},
                                 {"Set-Cookie": "JSESSIONID=initial; Path=/"} if not state["authenticated"] else {})
                elif state["mode"] == "header":
                    self.respond(200, {}, {"X-CSRF-TOKEN": token, "Set-Cookie": "JSESSIONID=initial; Path=/"})
                else:
                    self.respond(200, {}, {"Set-Cookie": f"XSRF-TOKEN={token}; Path=/"})

            def do_POST(self):
                raw = self.rfile.read(int(self.headers["Content-Length"]))
                state["requests"].append(("POST", self.path))
                header = "X-XSRF-TOKEN" if state["mode"] == "cookie" else "X-CSRF-TOKEN"
                expected_token = "after-login" if state["authenticated"] else "before-login"
                if self.headers.get(header) != expected_token:
                    self.respond(403, {})
                    return
                if self.path == "/api/auth/login":
                    form = parse_qs(raw.decode())
                    if form != {"email": ["operador@example.test"], "password": ["senha-de-teste"]}:
                        self.respond(401, {})
                        return
                    state["authenticated"] = True
                    self.respond(200, {"id": 1, "perfil": "OPERADOR"},
                                 {"Set-Cookie": "JSESSIONID=renewed; Path=/"})
                    return
                if state["mode"] == "json" and "JSESSIONID=renewed" not in self.headers.get("Cookie", ""):
                    self.respond(401, {})
                    return
                if self.path == "/api/jobs/avaliar-comunicacao":
                    self.respond(200, {"toleranciaSegundos": 300})
                    return
                key = self.headers.get("Idempotency-Key")
                if not key:
                    self.respond(400, {})
                    return
                payload = json.loads(raw)
                if key not in state["readings"]:
                    state["readings"][key] = payload
                    self.respond(201, {"id": 42})
                elif state["readings"][key] == payload:
                    self.respond(200, {"id": 42})
                else:
                    self.respond(409, {"message": "conteudo-confidencial-de-teste"})

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.client = ScadaClient(f"http://127.0.0.1:{self.server.server_port}")

    def tearDown(self):
        self.client.close()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)

    def authenticate(self):
        return self.client.autenticar("  OPERADOR@EXAMPLE.TEST ", "senha-de-teste")

    def test_renova_csrf_preserva_cookie_e_distingue_criacao_replay_conflito(self):
        self.assertEqual(self.authenticate()["perfil"], "OPERADOR")
        key = uuid4()
        endpoint = "/api/transformadores/1/medicoes"
        payload = {"fonteId": 7, "potenciaAparenteMva": 8}
        logs = io.StringIO()
        handler = logging.StreamHandler(logs)
        logger = logging.getLogger("simulador_telemetria")
        previous_level = logger.level
        logger.setLevel(logging.INFO)
        logger.addHandler(handler)
        try:
            self.assertEqual(self.client.send_medicao(endpoint, payload, key).status_code, 201)
            self.assertEqual(self.client.send_medicao(endpoint, payload, str(key)).status_code, 200)
            with self.assertRaises(IdempotencyConflict) as raised:
                self.client.send_medicao(endpoint, {"potenciaAparenteMva": 9}, key)
            self.assertEqual(raised.exception.status_code, 409)
            self.assertNotIn("conteudo-confidencial", str(raised.exception))
        finally:
            logger.removeHandler(handler)
            logger.setLevel(previous_level)
        self.assertEqual(len(self.state["readings"]), 1)
        self.assertEqual(self.state["requests"][:3],
                         [("GET", "/api/auth/csrf"), ("POST", "/api/auth/login"), ("GET", "/api/auth/csrf")])
        self.assertIn("201", logs.getvalue())
        self.assertIn("200", logs.getvalue())
        self.assertIn("409", logs.getvalue())
        self.assertNotIn("senha-de-teste", logs.getvalue())
        self.assertNotIn("potenciaAparenteMva", logs.getvalue())
        self.assertNotIn("after-login", logs.getvalue())

    def test_aceita_token_csrf_no_header(self):
        self.state["mode"] = "header"
        self.authenticate()
        self.assertEqual(self.client.send_medicao("/api/linhas-transmissao/1/medicoes", {}, uuid4()).status_code, 201)

    def test_aceita_token_csrf_no_cookie(self):
        self.state["mode"] = "cookie"
        self.authenticate()
        self.assertEqual(self.client.send_medicao("/api/linhas-transmissao/1/medicoes", {}, uuid4()).status_code, 201)

    def test_rejeita_credenciais_invalidas(self):
        with self.assertRaises(ScadaHTTPError) as raised:
            self.client.autenticar("operador@example.test", "errada")
        self.assertEqual(raised.exception.status_code, 401)

    def test_exige_autenticacao_antes_de_enviar(self):
        with self.assertRaises(RuntimeError):
            self.client.send_medicao("/api/linhas-transmissao/1/medicoes", {}, uuid4())
        self.assertEqual(self.state["requests"], [])

    def test_rejeita_uuid_invalido_sem_enviar_medicao(self):
        self.authenticate()
        before = len(self.state["requests"])
        with self.assertRaises(ValueError):
            self.client.send_medicao("/api/linhas-transmissao/1/medicoes", {}, "invalido")
        self.assertEqual(len(self.state["requests"]), before)

    def test_rejeita_endpoint_externo(self):
        self.authenticate()
        before = len(self.state["requests"])
        with self.assertRaises(ValueError):
            self.client.send_medicao("https://example.test/api/medicoes", {}, uuid4())
        self.assertEqual(len(self.state["requests"]), before)

    def test_consulta_preserva_decimal_e_envia_filtros(self):
        self.authenticate()
        snapshot = self.client.get_json("/api/monitoramento/snapshot", {"ativoId": 1})
        self.assertEqual(snapshot["carga"], Decimal("12.345678"))

    def test_dispara_motor_manual_com_sessao_e_csrf_renovados(self):
        self.authenticate()
        self.assertEqual(self.client.avaliar_comunicacao(), {"toleranciaSegundos": 300})


if __name__ == "__main__":
    unittest.main()
