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
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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


    // The body is a JSON object with a "message" field rather than a bare String. The UI reads
    // errors.response.data.message (CollectionCreate.vue), which is undefined against a String
    // body - so the carefully worded @Pattern/@Size messages on Collection#name were being
    // replaced in the dialog by axios' generic "Request failed with status code 400". Every
    // branch below keeps its original HTTP status.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e, WebRequest request) {

//        UserResponse response = null;

        if (e instanceof MethodArgumentNotValidException) {
            // This controller-local @ExceptionHandler(Exception.class) is consulted by
            // ExceptionHandlerExceptionResolver before Spring's own DefaultHandlerExceptionResolver,
            // so without this branch a @Valid failure would fall through to the else below and be
            // reported as an opaque 500 rather than the 400 it is.
            MethodArgumentNotValidException ex = (MethodArgumentNotValidException) e;
            log.warn("Validation failed for collection request ::: {}", e.getMessage());
            // e.getMessage() here is the whole BindingResult dump (object name, rejected value,
            // codes). Report only the constraint messages, which are written to be shown to a
            // user verbatim.
            String message = ex.getBindingResult().getFieldErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(" "));
            if (message.isEmpty()) {
                message = "The submitted collection is not valid.";
            }
            return new ResponseEntity<>(errorBody(HttpStatus.BAD_REQUEST, message), HttpStatus.BAD_REQUEST);
        } else if(e instanceof DataIntegrityViolationException){
            log.error("DataIntegrity Violation Exception ::: {}", e);
            DataIntegrityViolationException ex = (DataIntegrityViolationException) e;
//            response = new UserResponse(ErrorCodes.DuplicateMobNo, "This mobile no is already Registered!");
            return new ResponseEntity<>(errorBody(HttpStatus.CONFLICT, e.getMessage()), HttpStatus.CONFLICT);
        } else if (e instanceof DataRetrievalFailureException) {
            return new ResponseEntity<>(errorBody(HttpStatus.NOT_FOUND, e.getMessage()), HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Error body shaped like Spring Boot's own default error response, so the UI's existing
     * {@code errors.response.data.message} read works against it.
     */
    private Map<String, Object> errorBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message != null ? message : status.getReasonPhrase());
        return body;
    }

}
