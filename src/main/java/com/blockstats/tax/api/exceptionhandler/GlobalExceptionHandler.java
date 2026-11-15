package com.blockstats.tax.api.exceptionhandler;

import static com.blockstats.commons.constants.ErrorCodes.SERVICE_ERROR_00001;
import static com.blockstats.commons.constants.ErrorCodes.SERVICE_ERROR_00004;
import static com.blockstats.commons.constants.ErrorCodes.SERVICE_ERROR_00005;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.blockstats.commons.exception.ForbiddenException;
import com.blockstats.commons.exception.InsufficientPlanException;
import com.blockstats.commons.exception.InvalidRequestException;
import com.blockstats.commons.exception.NoDataFoundException;
import com.blockstats.error.ErrorResponse;
import com.blockstats.response.RecommendedPlanResponse;

import lombok.extern.slf4j.Slf4j;

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

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.debug(ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(SERVICE_ERROR_00001);
        errorResponse.setErrorDescription("Bad Request, Incorrect or Missing Information.");
        errorResponse.setErrorMessages(List.of("This file is too large (max 20MB)."));
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

    @ExceptionHandler({ForbiddenException.class, AuthorizationDeniedException.class})
    public final ResponseEntity<ErrorResponse> forbiddenException(Exception forbiddenException) {
        log.debug(forbiddenException.getMessage(), forbiddenException);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(SERVICE_ERROR_00004);
        errorResponse.setErrorDescription("Unable to retrieve User Details");
        errorResponse.setErrorMessages(List.of(forbiddenException.getMessage()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    @ExceptionHandler(InsufficientPlanException.class)
    public final ResponseEntity<RecommendedPlanResponse> forbiddenException(InsufficientPlanException insufficientPlanException) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(insufficientPlanException.getRecommendedPlanResponse());
    }

    @ExceptionHandler(Throwable.class)
    public final ResponseEntity<ErrorResponse> handleAllOthers(Throwable throwable) {
        log.error(throwable.getMessage(), throwable);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(SERVICE_ERROR_00005);
        errorResponse.setErrorDescription("Internal Server Exception.");
        errorResponse.setErrorMessages(List.of("Application Failure. Contact Customer Support"));
        return ResponseEntity.internalServerError().body(errorResponse);
    }

}
