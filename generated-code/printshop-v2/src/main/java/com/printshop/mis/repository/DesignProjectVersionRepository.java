package com.printshop.mis.repository;

import com.printshop.mis.domain.DesignProjectVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignProjectVersionRepository extends JpaRepository<DesignProjectVersion, Long> {
    List<DesignProjectVersion> findByProjectIdOrderByVersionNoDesc(Long projectId);
    Optional<DesignProjectVersion> findByProjectIdAndVersionNo(Long projectId, Integer versionNo);
    long countByProjectId(Long projectId);
}
