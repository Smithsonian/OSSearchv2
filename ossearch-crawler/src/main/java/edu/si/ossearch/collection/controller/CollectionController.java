package edu.si.ossearch.collection.controller;

import edu.si.ossearch.collection.entity.Collection;
import edu.si.ossearch.collection.entity.projections.CollectionFormData;
import edu.si.ossearch.collection.service.CollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * @author jbirkhimer
 */
@Slf4j
@RestController
@RequestMapping("/api/collection2")
@Tag(description = "Collection Manager", name = "Collection Manager")
@SecurityRequirement(name = "bearerAuth")
public class CollectionController {

    @Autowired
    CollectionService collectionService;

    @Autowired
    EntityLinks entityLinks;

    @GetMapping
    @Operation(summary = "list collections", responses = {@ApiResponse(content = @Content(mediaType = "text/plain"))})
    public List<Collection> getCollections() {
        return collectionService.getCollection();
    }

    @GetMapping(value = "/{id:\\d+}")
    @Operation(summary = "get collection id", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    public ResponseEntity<Collection> getCollectionById(@PathVariable(name = "id") Long id) {
        Optional<Collection> optionalCollection = collectionService.getCollectionById(id);
        if (!optionalCollection.isPresent()) {
            return ResponseEntity.unprocessableEntity().build();
        }

        return ResponseEntity.ok(optionalCollection.get());
    }

    @GetMapping(value = "/{name:(?!\\d+$)\\S*$}")
    @Operation(summary = "get collection name", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    public Collection getCollectionByName(@PathVariable(name = "name") String name) {
        return collectionService.getCollectionByName(name);
    }

    @PostMapping
    @Operation(summary = "create collection", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    // @Valid so an invalid name (see Collection#name) is rejected at the request boundary as a
    // 400 MethodArgumentNotValidException, instead of escaping as a Hibernate
    // ConstraintViolationException at flush time - which the catch-all @ExceptionHandler below
    // would turn into an opaque 500.
    public ResponseEntity<CollectionFormData> createCollection(@Valid @RequestBody Collection collectionFormData) {
        log.info("create collection: {}", collectionFormData);

        Collection savedCollection = collectionService.createCollection(collectionFormData);

        //URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(savedCollection.getId()).toUri();
        ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
        CollectionFormData collectionFormData1 = projectionFactory.createProjection(CollectionFormData.class, savedCollection);
        Link link = entityLinks.linkToItemResource(Collection.class, savedCollection.getId()).expand();
        URI location = URI.create(link.getHref());

        return ResponseEntity.created(location).body(collectionFormData1);
    }

    @PutMapping(value = "/{id:\\d+}")
    @Operation(summary = "update collection by id", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    public Collection updateCollectionId(@PathVariable Integer id, @RequestBody Collection collectionFormData) {
        return collectionService.updateCollection();
    }

    @PutMapping(value = "/{name:(?!\\d+$)\\S*$}")
    @Operation(summary = "update collection by name", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    public Collection updateCollectionName(@PathVariable String name) {
        return collectionService.updateCollection();
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e, WebRequest request) {

//        UserResponse response = null;

        if (e instanceof MethodArgumentNotValidException) {
            // This controller-local @ExceptionHandler(Exception.class) is consulted by
            // ExceptionHandlerExceptionResolver before Spring's own DefaultHandlerExceptionResolver,
            // so without this branch a @Valid failure would fall through to the else below and be
            // reported as an opaque 500 rather than the 400 it is. Deliberately minimal: the rest
            // of this handler is left exactly as it was.
            log.warn("Validation failed for collection request ::: {}", e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } else if(e instanceof DataIntegrityViolationException){
            log.error("DataIntegrity Violation Exception ::: {}", e);
            DataIntegrityViolationException ex = (DataIntegrityViolationException) e;
//            response = new UserResponse(ErrorCodes.DuplicateMobNo, "This mobile no is already Registered!");
            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
        } else if (e instanceof DataRetrievalFailureException) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
