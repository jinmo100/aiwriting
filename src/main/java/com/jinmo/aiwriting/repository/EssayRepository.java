package com.jinmo.aiwriting.repository;

import com.jinmo.aiwriting.domain.entity.Essay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EssayRepository extends JpaRepository<Essay, Long> {
} 