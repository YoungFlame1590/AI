package com.printshop.mis.repository;

import com.printshop.mis.domain.DesignTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignTemplateRepository extends JpaRepository<DesignTemplate, Long> {
    List<DesignTemplate> findByPublishedTrueOrderByUpdatedAtDesc();
}
