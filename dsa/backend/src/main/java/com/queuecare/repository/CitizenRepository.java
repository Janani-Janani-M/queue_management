package com.queuecare.repository;

import com.queuecare.model.Citizen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CitizenRepository extends JpaRepository<Citizen, Long> {

    Optional<Citizen> findByToken(String token);

    List<Citizen> findBySessionDateOrderByArrivalOrderAsc(LocalDate date);

    List<Citizen> findByStatusAndSessionDate(Citizen.Status status, LocalDate date);

    long countByStatusAndSessionDate(Citizen.Status status, LocalDate date);

    @Query("SELECT AVG(c.waitMinutes) FROM Citizen c WHERE c.status = 'SERVED' AND c.sessionDate = :date")
    Double avgWaitForServedOnDate(LocalDate date);

    boolean existsByToken(String token);
}
