package edu.si.ossearch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.rest.core.event.ValidatingRepositoryEventListener;
import org.springframework.validation.Validator;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Guards {@link RepositoryRestValidationConfiguration}'s registration against the one failure
 * mode it has: Spring Data REST looks validators up by event-name string, so a typo'd or
 * missing event name silently registers nothing and the configurer goes on doing nothing at
 * all - no startup error, no failing request, just an un-validated PATCH that 500s at flush
 * time exactly as it did before the class existed.
 * <p>
 * Deliberately no Spring context: there is no bootable test context in this module (the only
 * DB profile points at a real MySQL), and the configurer is a plain object whose whole
 * contract is the two calls asserted below.
 */
class RepositoryRestValidationConfigurationTest {

    @Test
    @DisplayName("registers the injected validator for both beforeCreate and beforeSave")
    void registersValidatorForCreateAndSave() {
        Validator validator = mock(Validator.class);
        ValidatingRepositoryEventListener listener = mock(ValidatingRepositoryEventListener.class);

        new RepositoryRestValidationConfiguration(validator)
                .configureValidatingRepositoryEventListener(listener);

        // beforeCreate covers POST, beforeSave covers PUT and PATCH. PATCH is the path the UI
        // actually edits through, so beforeSave is the one that must never be dropped.
        verify(listener).addValidator("beforeCreate", validator);
        verify(listener).addValidator("beforeSave", validator);
        // Nothing else: a stray registration on a third event is as much a defect as a
        // missing one, since it would validate on an event the entity constraints were never
        // reasoned about for.
        verifyNoMoreInteractions(listener);
    }

    @Test
    @DisplayName("takes its Validator from the defaultValidator bean, not a locally declared one")
    void constructorIsQualifiedToTheDefaultValidator() throws Exception {
        // Cheap stand-in for a context test: assert the qualifier is present on the sole
        // constructor parameter. Declaring a LocalValidatorFactoryBean here instead would be
        // picked up as THE MVC validator and can trigger bean-definition cycles at startup,
        // which is why the class injects Boot's autoconfigured one by name.
        Constructor<?>[] constructors = RepositoryRestValidationConfiguration.class.getConstructors();
        assertThat(constructors).hasSize(1);

        org.springframework.beans.factory.annotation.Qualifier qualifier =
                constructors[0].getParameters()[0]
                        .getAnnotation(org.springframework.beans.factory.annotation.Qualifier.class);

        assertThat(qualifier).isNotNull();
        assertThat(qualifier.value()).isEqualTo("defaultValidator");
    }
}
