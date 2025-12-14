package com.blockstats.tax.exception.handler;

import com.blockstats.commons.exception.InvalidRequestException;
import com.blockstats.commons.exception.NoDataFoundException;
import com.blockstats.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.blockstats.commons.constants.ErrorCodes.*;

/**
 * This class handle all expected and unexpected exceptions thrown by
 * application and convert that to error response and return it
 *
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    public final ResponseEntity<ErrorResponse> invalidRequestException(
            InvalidRequestException invalidRequestException) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(SERVICE_ERROR_00001);
        errorResponse.setErrorDescription("Bad Request, Incorrect or Missing Information.");
        if (Objects.nonNull(invalidRequestException.getViolations())) {
            errorResponse.setErrorMessages(invalidRequestException.getViolations().stream()
                    .map(violation -> violation.getName() + ":" + violation.getMessage()).collect(Collectors.toList()));
        }
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(NoDataFoundException.class)
    public final ResponseEntity<ErrorResponse> noDataFoundException(NoDataFoundException noDataFoundException) {
        log.debug(noDataFoundException.getMessage());
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(SERVICE_ERROR_00004);
        errorResponse.setErrorDescription("No Data Found Exception.");
        errorResponse.setErrorMessages(List.of(noDataFoundException.getMessage()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(Throwable.class)
    public final ResponseEntity<ErrorResponse> handleAllOthers(Throwable throwable) {
        log.error(throwable.getMessage(), throwable.getCause());
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(SERVICE_ERROR_00005);
        errorResponse.setErrorDescription("Internal Server Exception.");
        errorResponse.setErrorMessages(List.of("Application Failure. Contact Customer Support"));
        return ResponseEntity.internalServerError().body(errorResponse);
    }

}
