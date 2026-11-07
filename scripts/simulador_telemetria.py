"""Cliente HTTP do simulador de telemetria. Requer Python 3.10 ou superior."""

from __future__ import annotations

import logging
from typing import Any
from urllib.parse import unquote, urlsplit
from uuid import UUID

import requests


LOGGER = logging.getLogger(__name__)


class ScadaHTTPError(RuntimeError):
    """Resposta HTTP incompatível com a operação solicitada."""

    def __init__(self, endpoint: str, status_code: int) -> None:
        self.status_code = status_code
        super().__init__(f"Requisição para {endpoint} retornou HTTP {status_code}.")


class IdempotencyConflict(ScadaHTTPError):
    """A API rejeitou a chave ou o conteúdo da ingestão com HTTP 409."""


class ScadaClient:
    """Mantém cookies e CSRF de uma sessão, usada por um produtor de dados."""

    def __init__(
        self,
        base_url: str,
        timeout: tuple[float, float] = (5.0, 15.0),
    ) -> None:
        url = urlsplit(base_url)
        if url.scheme not in {"http", "https"} or not url.hostname:
            raise ValueError("Informe uma URL HTTP ou HTTPS para a API.")
        if url.username or url.password or url.query or url.fragment:
            raise ValueError("A URL da API deve conter somente a origem e o caminho base.")
        if len(timeout) != 2 or any(valor <= 0 for valor in timeout):
            raise ValueError("Os timeouts de conexão e leitura devem ser positivos.")
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self._session = requests.Session()
        self._csrf_header: str | None = None
        self._csrf_token: str | None = None
        self._authenticated = False

    def autenticar(self, email: str, senha: str) -> dict[str, Any]:
        """Autentica por formulário e obtém o novo CSRF da sessão autenticada."""
        self._authenticated = False
        self._obter_csrf()
        response = self._request(
            "POST",
            "/api/auth/login",
            data={"email": email.strip().lower(), "password": senha},
            headers=self._headers_csrf(),
        )
        if response.status_code != 200:
            raise ScadaHTTPError("/api/auth/login", response.status_code)
        usuario = response.json()
        if not isinstance(usuario, dict):
            raise ValueError("A API retornou uma resposta de autenticação inválida.")
        # O login renova a sessão e invalida o token anterior.
        self._obter_csrf()
        self._authenticated = True
        return usuario

    def send_medicao(
        self,
        endpoint: str,
        payload: dict[str, Any],
        idempotency_key: UUID | str,
    ) -> requests.Response:
        """Envia uma leitura. O chamador preserva payload e chave nos retries."""
        if not self._authenticated:
            raise RuntimeError("Autentique o cliente antes de enviar medições.")
        chave = UUID(str(idempotency_key))
        headers = self._headers_csrf()
        headers["Idempotency-Key"] = str(chave)
        response = self._request("POST", endpoint, json=payload, headers=headers)
        if response.status_code in {200, 201}:
            resultado = "replay aceito" if response.status_code == 200 else "nova medição"
            LOGGER.info("POST %s -> %s (%s)", endpoint, response.status_code, resultado)
            return response
        LOGGER.warning("POST %s -> %s", endpoint, response.status_code)
        if response.status_code == 409:
            raise IdempotencyConflict(endpoint, response.status_code)
        if response.status_code == 401:
            self._authenticated = False
        raise ScadaHTTPError(endpoint, response.status_code)

    def close(self) -> None:
        self._session.close()
        self._authenticated = False
        self._csrf_header = None
        self._csrf_token = None

    def _obter_csrf(self) -> None:
        response = self._request("GET", "/api/auth/csrf")
        if response.status_code != 200:
            raise ScadaHTTPError("/api/auth/csrf", response.status_code)
        try:
            dados = response.json()
        except requests.exceptions.JSONDecodeError:
            dados = {}
        token = dados.get("token") if isinstance(dados, dict) else None
        header = dados.get("headerName", "X-CSRF-TOKEN") if isinstance(dados, dict) else "X-CSRF-TOKEN"
        if not token:
            token = response.headers.get("X-CSRF-TOKEN")
            header = "X-CSRF-TOKEN"
        if not token:
            cookie = self._session.cookies.get("XSRF-TOKEN")
            token = unquote(cookie) if cookie else None
            header = "X-XSRF-TOKEN"
        if not isinstance(token, str) or not token.strip() or not isinstance(header, str):
            raise ValueError("A API não forneceu um token CSRF válido.")
        self._csrf_header = header
        self._csrf_token = token

    def _headers_csrf(self) -> dict[str, str]:
        if self._csrf_header is None or self._csrf_token is None:
            raise RuntimeError("O token CSRF ainda não foi obtido.")
        return {self._csrf_header: self._csrf_token}

    def _request(self, method: str, endpoint: str, **kwargs: Any) -> requests.Response:
        destino = urlsplit(endpoint)
        if not endpoint.startswith("/api/") or destino.scheme or destino.netloc or destino.fragment:
            raise ValueError("O endpoint deve ser um caminho da API iniciado por /api/.")
        return self._session.request(
            method,
            self.base_url + endpoint,
            timeout=self.timeout,
            allow_redirects=False,
            **kwargs,
        )
