"""Grandezas simuladas reproduzíveis, com unidades e precisão do contrato SCADA."""

from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_EVEN
from random import Random


class GeradorTelemetria:
    def __init__(self, semente: int) -> None:
        self._random = Random(semente)

    def linha(self, fonte_id: int, tensao_kv: Decimal, instante: datetime) -> dict:
        tensao = tensao_kv * self._fator(980, 1020)
        corrente = Decimal(self._random.randint(50000, 150000)) / 1000
        aparente = Decimal(3).sqrt() * tensao * corrente / 1000
        ativa = aparente * self._fator(920, 990)
        reativa = (aparente * aparente - ativa * ativa).sqrt()
        return {
            "fonteId": fonte_id,
            "medidoEm": self._utc(instante),
            "tensaoKv": self._decimal(tensao),
            "correnteA": self._decimal(corrente),
            "potenciaAtivaMw": self._decimal(ativa),
            "potenciaReativaMvar": self._decimal(reativa),
            "frequenciaHz": self._decimal(Decimal(self._random.randint(59950, 60050)) / 1000),
        }

    def transformador(self, fonte_id: int, capacidade_mva: Decimal, instante: datetime) -> dict:
        # A capacidade é parâmetro de geração. O snapshot persistido vem do cadastro no backend.
        return {
            "fonteId": fonte_id,
            "medidoEm": self._utc(instante),
            "potenciaAparenteMva": self._decimal(capacidade_mva * self._fator(700, 900)),
        }

    def _fator(self, minimo: int, maximo: int) -> Decimal:
        return Decimal(self._random.randint(minimo, maximo)) / 1000

    @staticmethod
    def _decimal(valor: Decimal) -> str:
        return format(valor.quantize(Decimal("0.000001"), rounding=ROUND_HALF_EVEN), "f")

    @staticmethod
    def _utc(instante: datetime) -> str:
        if instante.tzinfo is None or instante.utcoffset() is None:
            raise ValueError("O instante da leitura deve possuir fuso horário.")
        return instante.astimezone(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")
