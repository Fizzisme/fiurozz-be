package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface JpaProjectListRepository extends JpaRepository<ProjectJpaEntity, UUID>, JpaSpecificationExecutor<ProjectJpaEntity> {

    @Override
    @EntityGraph(attributePaths = {"subCategory", "subCategory.category"})
    Page<ProjectJpaEntity> findAll(Specification<ProjectJpaEntity> specification, Pageable pageable);
}
