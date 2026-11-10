package com.obrasviales.ms_obras.repository;

import com.obrasviales.ms_obras.domain.Tramo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TramoRepository extends JpaRepository<Tramo, Long> {

    List<Tramo> findByObraId(Long obraId);
}