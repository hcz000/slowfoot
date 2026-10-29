package org.hcz;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.hcz.work.dto.ImageWorkPublishRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


// 钉住请求字段长度与 work 表列宽的一致性。

class ImageWorkPublishRequestTests {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void titleOfColumnLengthIsAccepted() {
        assertTrue(VALIDATOR.validate(request("标".repeat(255))).isEmpty());
    }

    @Test
    void titleLongerThanColumnIsRejected() {
        var violations = VALIDATOR.validate(request("标".repeat(256)));

        assertFalse(violations.isEmpty());
        assertEquals("title", violations.iterator().next().getPropertyPath().toString());
    }

    private ImageWorkPublishRequest request(String title) {
        return new ImageWorkPublishRequest(title, null, 1L, null, Boolean.TRUE, List.of(10L));
    }
}
