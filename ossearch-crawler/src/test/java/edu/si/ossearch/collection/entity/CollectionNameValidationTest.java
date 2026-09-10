package edu.si.ossearch.collection.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean validation tests for {@link Collection#getName()}.
 * <p>
 * Deliberately no Spring context - a plain {@link Validator} from
 * {@link Validation#buildDefaultValidatorFactory()} exercises exactly the constraints Hibernate's
 * {@code BeanValidationEventListener} applies on pre-insert/pre-update, and exactly what
 * {@code @Valid} applies at the controller boundary, which is the whole surface under test.
 * <p>
 * <b>The accept cases are the important half of this class.</b> The name constraints are a
 * denylist of path-hostile input only, because validation on an entity fires on update as well as
 * insert: an over-strict rule would permanently lock operators out of editing existing
 * collections. The four production-shaped names and the "ugly but harmless" names below are here
 * so that a future tightening into an allowlist (say {@code ^[A-Za-z0-9_-]+$}) fails loudly.
 */
class CollectionNameValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    /**
     * Validates only the {@code name} property, so unrelated entity constraints (or the lack of
     * them) cannot influence the result.
     */
    private Set<ConstraintViolation<Collection>> validateName(String name) {
        Collection collection = new Collection();
        collection.setName(name);
        return validator.validateProperty(collection, "name");
    }

    private String violationMessages(Set<ConstraintViolation<Collection>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(" | "));
    }

    // ---------------------------------------------------------------------------------------
    // Accepted: real production-shaped names
    // ---------------------------------------------------------------------------------------

    @DisplayName("accepts the collection names that actually exist today")
    @ParameterizedTest(name = "\"{0}\" is valid")
    @ValueSource(strings = {
            "americanhistory",
            "airandspace_d9_prod",
            "airandspace_d9_LR_prod",
            "airandspace_d9_dev"
    })
    void acceptsRealCollectionNames(String name) {
        Set<ConstraintViolation<Collection>> violations = validateName(name);
        assertThat(violations)
                .as("existing production collection name must stay editable: %s", violationMessages(violations))
                .isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Accepted: ugly in a path, but not dangerous
    // ---------------------------------------------------------------------------------------

    @DisplayName("accepts names with spaces, dots, ampersands and non-ASCII characters")
    @ParameterizedTest(name = "\"{0}\" is valid")
    @ValueSource(strings = {
            "American History",                 // spaces
            "air and space d9 prod",            // spaces
            "airandspace.si.edu",               // dots
            "collection.v2.prod",               // dots
            "Arts & Industries",                // ampersand
            "A & B & C",                        // ampersands
            "Café Nacional",                    // accented latin
            "Museo Nacional de Antropología",   // accented latin + spaces
            "日本館",                             // non-ASCII, non-latin
            "...",                              // three dots is a legal directory name
            "a",                                // single character
            "with-dash_and_underscore"
    })
    void acceptsUglyButHarmlessNames(String name) {
        Set<ConstraintViolation<Collection>> violations = validateName(name);
        assertThat(violations)
                .as("name is only cosmetically awkward in a path, not hostile: %s", violationMessages(violations))
                .isEmpty();
    }

    @Test
    @DisplayName("accepts a name exactly at the 200 character limit")
    void acceptsNameAtSizeLimit() {
        assertThat(validateName("a".repeat(200))).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Rejected: path-hostile input
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("rejects a forward slash, which would nest the backup below where retention sweeps")
    void rejectsForwardSlash() {
        Set<ConstraintViolation<Collection>> violations = validateName("a/b");
        assertThat(violations).isNotEmpty();
        assertThat(violationMessages(violations)).contains("slashes");
    }

    @Test
    @DisplayName("rejects a backslash")
    void rejectsBackslash() {
        assertThat(validateName("a\\b")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects \"..\", which would write the backup outside crawlDir")
    void rejectsParentDirectory() {
        assertThat(validateName("..")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects \".\"")
    void rejectsCurrentDirectory() {
        assertThat(validateName(".")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects a traversal prefix")
    void rejectsTraversalPrefix() {
        assertThat(validateName("../evil")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects an embedded newline, which produces an unmatchable filename")
    void rejectsNewline() {
        assertThat(validateName("foo\nbar")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects an embedded carriage return")
    void rejectsCarriageReturn() {
        assertThat(validateName("foo\rbar")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects an embedded NUL")
    void rejectsNul() {
        assertThat(validateName("foo\0bar")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects leading whitespace")
    void rejectsLeadingWhitespace() {
        assertThat(validateName(" lead")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects trailing whitespace")
    void rejectsTrailingWhitespace() {
        assertThat(validateName("trail ")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects an empty name")
    void rejectsEmptyName() {
        Set<ConstraintViolation<Collection>> violations = validateName("");
        assertThat(violations).isNotEmpty();
        assertThat(violations).anySatisfy(violation -> assertThat(violation.getMessage()).contains("required"));
    }

    @Test
    @DisplayName("rejects a blank (whitespace only) name")
    void rejectsBlankName() {
        assertThat(validateName("   ")).isNotEmpty();
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNullName() {
        assertThat(validateName(null)).isNotEmpty();
    }

    @Test
    @DisplayName("rejects a 300 character name, which would exceed the filesystem component limit")
    void rejectsOverlongName() {
        Set<ConstraintViolation<Collection>> violations = validateName("a".repeat(300));
        assertThat(violations).isNotEmpty();
        assertThat(violationMessages(violations)).contains("200 characters");
    }
}
