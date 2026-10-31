package br.com.powergrid.controlcenter.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FonteMedicaoRepository extends JpaRepository<FonteMedicao, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FonteMedicao f where f.id = :id")
    Optional<FonteMedicao> buscarPorIdComBloqueio(@Param("id") Long id);

    @Query("select coalesce(max(f.id), 0) from FonteMedicao f")
    Long maiorId();

    @Query("select f.id from FonteMedicao f where f.id > :apos and f.id <= :limite order by f.id")
    List<Long> buscarIdsParaAvaliacao(@Param("apos") Long apos, @Param("limite") Long limite, Pageable pageable);

    boolean existsByIdentificador(String identificador);
    List<FonteMedicao> findByAtivoIdOrderByIdAsc(Long ativoId);
}
