"""Configuração local. Variáveis de ambiente prevalecem sobre o arquivo .env."""

import os
from dataclasses import dataclass, field
from pathlib import Path


def ler_env(arquivo: Path) -> dict[str, str]:
    valores = {}
    if arquivo.exists():
        for linha in arquivo.read_text(encoding="utf-8-sig").splitlines():
            linha = linha.strip()
            if not linha or linha.startswith("#"):
                continue
            chave, separador, valor = linha.partition("=")
            if not separador or not chave.strip().isidentifier():
                raise ValueError("Arquivo de configuração inválido. Use NOME=valor em cada linha.")
            valor = valor.strip()
            if valor.startswith(("'", '"')):
                if len(valor) < 2 or valor[-1] != valor[0]:
                    raise ValueError("Há um valor com aspas não fechadas na configuração.")
                valor = valor[1:-1]
            valores[chave.strip()] = valor
    valores.update(os.environ)
    return valores


@dataclass(frozen=True)
class Configuracao:
    url: str
    email: str = field(repr=False)
    senha: str = field(repr=False)
    linha_id: int | None = None
    fonte_linha_id: int | None = None
    transformador_id: int | None = None
    fonte_transformador_id: int | None = None
    semente: int = 42
    cadencia: int = 30
    intervalo_demo: int = 5
    leituras_demo: int = 3
    pausa: int = 305
    timeout_comunicacao: int = 300
    espera_alerta: int = 90
    tentativas: int = 3
    avaliacao: str = "manual"
    pendencia: Path = Path(".estado-telemetria.json")

    @classmethod
    def carregar(cls, arquivo: Path) -> "Configuracao":
        valores = ler_env(arquivo)

        def inteiro(nome: str, padrao: int | None = None) -> int | None:
            valor = valores.get(nome, "")
            if not valor:
                return padrao
            if not valor.isdecimal() or int(valor) < 1:
                raise ValueError(f"{nome} deve ser um inteiro positivo.")
            return int(valor)

        linha = inteiro("LINHA_ID")
        fonte_linha = inteiro("FONTE_LINHA_ID")
        transformador = inteiro("TRANSFORMADOR_ID")
        fonte_transformador = inteiro("FONTE_TRANSFORMADOR_ID")
        if (linha is None) != (fonte_linha is None) or (transformador is None) != (fonte_transformador is None):
            raise ValueError("Preencha o ID do ativo e o ID da respectiva fonte em conjunto.")
        if linha is None and transformador is None:
            raise ValueError("Configure pelo menos um ativo e sua fonte de medição.")
        email = valores.get("SCADA_EMAIL", "").strip()
        if not email:
            raise ValueError("Configure SCADA_EMAIL com uma conta real de gerente ou operador.")
        avaliacao = valores.get("AVALIACAO_COMUNICACAO", "manual")
        if avaliacao not in {"manual", "agendada"}:
            raise ValueError("AVALIACAO_COMUNICACAO deve ser manual ou agendada.")
        config = cls(
            url=valores.get("SCADA_URL", "http://localhost:8080"),
            email=email,
            senha=valores.get("SCADA_SENHA", ""),
            linha_id=linha,
            fonte_linha_id=fonte_linha,
            transformador_id=transformador,
            fonte_transformador_id=fonte_transformador,
            semente=inteiro("SEMENTE", 42),
            cadencia=inteiro("CADENCIA_SEGUNDOS", 30),
            intervalo_demo=inteiro("INTERVALO_DEMO_SEGUNDOS", 5),
            leituras_demo=inteiro("LEITURAS_DEMO", 3),
            pausa=inteiro("PAUSA_SEGUNDOS", 305),
            timeout_comunicacao=inteiro("TIMEOUT_COMUNICACAO_SEGUNDOS", 300),
            espera_alerta=inteiro("ESPERA_ALERTA_SEGUNDOS", 90),
            tentativas=inteiro("TENTATIVAS", 3),
            avaliacao=avaliacao,
            pendencia=arquivo.parent / ".estado-telemetria.json",
        )
        if config.pausa <= config.timeout_comunicacao:
            raise ValueError("PAUSA_SEGUNDOS deve ser maior que TIMEOUT_COMUNICACAO_SEGUNDOS.")
        return config
