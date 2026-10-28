package edu.si.ossearch.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.event.ValidatingRepositoryEventListener;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.validation.Validator;

/**
 * Applies JSR-303 bean validation to the Spring Data REST repository endpoints
 * ({@code /api/collection}, {@code /api/crawlConfig}, ...).
 * <p>
 * Spring Data REST does <b>not</b> validate entities out of the box: it only runs the
 * validators explicitly registered on its {@link ValidatingRepositoryEventListener}. Without
 * this class, the UI's real edit path - {@code PATCH /api/collection/{id}} from
 * CollectionOverview.vue / CollectionDetails.vue - bypasses every constraint on the entity
 * (see {@code Collection#name}) until Hibernate validates at flush time, at which point the
 * resulting {@code ConstraintViolationException} is wrapped in a
 * {@code TransactionSystemException} and surfaces as an opaque <b>500 with no field message</b>.
 * The hand-written {@code CollectionController} create endpoint is unaffected either way: it
 * has its own {@code @Valid}.
 * <p>
 * With the validator registered for {@code beforeCreate} and {@code beforeSave}, the same
 * constraints instead produce a {@code 400 Bad Request} carrying per-field messages, which is
 * what the UI can actually show the user.
 * <p>
 * <b>Blast radius, considered and accepted.</b> The validator applies to every entity exposed
 * through Data REST, but of them only {@code Collection} and {@code User} carry constraints at
 * all. {@code User}'s column widths were themselves derived from its own {@code @Size} under
 * {@code ddl-auto: update}, so no stored row can violate them; Hibernate's pre-update bean
 * validation already fires today, so this change converts a 500 into a 400 rather than newly
 * rejecting anything; and role changes travel through the association endpoints, which emit
 * {@code BeforeLinkSaveEvent} rather than the {@code BeforeSaveEvent} registered here. The one
 * residual: a legacy {@code user} row that violates one of the two @NotBlank declarations -
 * a blank {@code username}, or an {@code email} that is blank or not RFC-shaped - would now
 * fail a scalar PATCH with a 400 instead of succeeding. That is judged the better failure - the
 * row is already invalid by the entity's own declaration - but it is a behaviour change, not an
 * absence of one.
 * <p>
 * The injected validator is the Boot-autoconfigured {@code LocalValidatorFactoryBean}, pulled
 * in by the {@code defaultValidator} qualifier. Declaring a new {@code LocalValidatorFactoryBean}
 * bean here instead is the classic mistake: it would be picked up as <i>the</i> MVC validator
 * and can trigger bean-definition cycles during context startup.
 *
 * @author jbirkhimer
 */
@Configuration
public class RepositoryRestValidationConfiguration implements RepositoryRestConfigurer {

    private final Validator validator;

    public RepositoryRestValidationConfiguration(@Qualifier("defaultValidator") Validator validator) {
        this.validator = validator;
    }

    @Override
    public void configureValidatingRepositoryEventListener(ValidatingRepositoryEventListener validatingListener) {
        // beforeCreate covers POST, beforeSave covers PUT and PATCH. Both are needed: the UI
        // creates through the hand-written controller but edits through Data REST PATCH, so
        // registering only beforeCreate would leave the actually-used path unvalidated.
        validatingListener.addValidator("beforeCreate", validator);
        validatingListener.addValidator("beforeSave", validator);
    }
}
