package edu.si.ossearch.collection.repository;

import edu.si.ossearch.collection.entity.Collection;
import edu.si.ossearch.collection.entity.projections.CollectionTableData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;

import java.util.List;
import java.util.Optional;

/**
 * @author jbirkhimer
 */
@Tag(name = "Collection", description = "CollectionRepository")
@RepositoryRestResource(collectionResourceRel = "collection", path = "collection")
@SecurityRequirement(name = "bearerAuth")
public interface CollectionRepository extends JpaRepository<Collection, Long> {

    // I think this is what we need to do https://www.baeldung.com/spring-security-create-new-custom-security-expression#customExpression
    // or use ACL https://grails-plugins.github.io/grails-spring-security-acl/v3/index.html#tutorial
    //@PostFilter("hasRole('ROLE_ADMIN') or filterObject.assignee == authentication.principal.name")

    //@PostFilter("hasRole('ROLE_ADMIN') or filterObject.assignee == authentication.principal.name")
//    Page<T> findAll(Pageable pageable);

//    @PostFilter("hasRole('ROLE_ADMIN') or filterObject.owner.username == authentication.name or filterObject.hasUser(authentication.name)")
    @Query("select DISTINCT c from Collection c LEFT JOIN c.users cu where cu.username = ?#{authentication.name} or c.owner.username = ?#{authentication.name} or 1 = ?#{hasRole('ROLE_ADMIN') ? 1 : 0}")
    @Override
    Page<Collection> findAll(Pageable pageable);
    @PostFilter("hasRole('ROLE_ADMIN') or filterObject.owner.username == authentication.name or filterObject.hasUser(authentication.name)")
    List<Collection> findAll();

    @Query(value = "select distinct c.id, c.name from collection c left outer join collections_user cu on c.id = cu.collection_id left outer join users u2 on cu.user_id = u2.id cross join users u where c.owner_id = u.id  and (u2.username = ?#{authentication.name} or u.username = ?#{authentication.name} or 1 = ?#{hasRole('ROLE_ADMIN') ? 1 : 0})", nativeQuery = true)
    List<CollectionIdNameInfoTest> findAllCollectionsByOwnerAndUsers();

    @PostAuthorize("hasRole('ROLE_ADMIN') or returnObject.owner.username == authentication.name or returnObject.hasUser(authentication.name)")
    @Operation(summary = "Find collection by collection name")
    Collection getCollectionByName(@Param("name") String name);

    @Operation(summary = "Get Collection Search Information by collection name")
    Optional<Collection> getByName(@Param("name") String name);

    @Operation(summary = "Find collection name by collection id")
    @Query("select c.name from Collection c where c.id = :id")
    Optional<String> findCollectionById(@Param("id") long id);

    @Operation(summary = "Get collections that this collection is part of by collection id")
    @Query("select c.partOfCollections from Collection c where c.id = :id")
    List<Collection> getPartOfCollectionsByCollectionId(@Param("id") long id);

    @Operation(summary = "Get collections by id")
    Optional<CollectionTableData> getCollectionsById(@Param("id") long id);

    /**
     * Find all collection ids. Intentionally SpEL-free: this is called from a
     * {@code @Scheduled} thread (the scheduled backup job), which runs with an
     * EMPTY SecurityContext, so a SpEL auth predicate like {@code ?#{authentication.name}}
     * or {@code hasRole(...)} (as used by the neighbouring findAll() methods) would
     * fail or return an empty result. Not exposed as a REST endpoint.
     */
    @RestResource(exported = false)
    @Query("select c.id from Collection c order by c.id")
    List<Long> findAllCollectionIds();

    interface CollectionIdNameInfoTest {
        long getId();

        String getName();
    }
}


