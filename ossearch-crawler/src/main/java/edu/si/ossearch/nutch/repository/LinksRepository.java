package edu.si.ossearch.nutch.repository;

import edu.si.ossearch.nutch.entity.Links;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@Tag(name = "Links", description = "LinksRepository")
@RepositoryRestResource(collectionResourceRel = "links", path = "links")
@SecurityRequirement(name = "bearerAuth")
public interface LinksRepository extends JpaRepository<Links, Long> {
}
