package com.flagship.server.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.flagship.server.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RFC 9457 ProblemDetail exception handler for all FlagShip Server errors (C4).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://flagship.dev/problems/not-found"));
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail handleConflict(ResourceConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Resource Conflict");
        problem.setType(URI.create("https://flagship.dev/problems/conflict"));
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(RuleValidationException.class)
    public ProblemDetail handleRuleValidation(RuleValidationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Rule Tree Validation Failed");
        problem.setType(URI.create("https://flagship.dev/problems/rule-validation"));
        problem.setProperty("errors", ex.getErrors());
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Request Argument");
        problem.setType(URI.create("https://flagship.dev/problems/invalid-argument"));
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request payload failed Bean Validation constraints"
        );
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://flagship.dev/problems/validation-error"));

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("fieldErrors", fieldErrors);
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Constraint Violation");
        problem.setType(URI.create("https://flagship.dev/problems/constraint-violation"));
        List<String> violations = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        problem.setProperty("errors", violations);
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof IllegalArgumentException iae && !(cause instanceof JsonProcessingException)) {
                return handleIllegalArgument(iae, request);
            }
            cause = cause.getCause();
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON request body or invalid enum value"
        );
        problem.setTitle("Malformed Request Body");
        problem.setType(URI.create("https://flagship.dev/problems/malformed-json"));
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'"
        );
        problem.setTitle("Invalid Parameter Type");
        problem.setType(URI.create("https://flagship.dev/problems/type-mismatch"));
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Resource conflicts with an existing record or integrity constraint"
        );
        problem.setTitle("Resource Conflict");
        problem.setType(URI.create("https://flagship.dev/problems/conflict"));
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.getMessage() != null ? ex.getMessage() : "Insufficient role permissions for this project operation"
        );
        problem.setTitle("Forbidden");
        problem.setType(URI.create("https://flagship.dev/problems/forbidden"));
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage() != null ? ex.getMessage() : "Authentication is required to access this resource"
        );
        problem.setTitle("Unauthorized");
        problem.setType(URI.create("https://flagship.dev/problems/unauthorized"));
        enrichProblem(problem, request);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            ProblemDetail body = errorResponse.getBody();
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    status,
                    body.getDetail() != null ? body.getDetail() : ex.getMessage()
            );
            if (body.getTitle() != null) {
                problem.setTitle(body.getTitle());
            }
            problem.setType(URI.create("https://flagship.dev/problems/http-" + status.value()));
            enrichProblem(problem, request);
            return ResponseEntity.status(status).body(problem);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal server error occurred"
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://flagship.dev/problems/internal-error"));
        enrichProblem(problem, request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    public static void enrichProblem(ProblemDetail problem, HttpServletRequest request) {
        if (request != null && request.getRequestURI() != null) {
            try {
                problem.setInstance(URI.create(request.getRequestURI()));
            } catch (IllegalArgumentException ignored) {
                problem.setProperty("path", request.getRequestURI());
            }
        }
        problem.setProperty("timestamp", Instant.now().toString());
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            problem.setProperty("traceId", traceId);
        }
    }
}
