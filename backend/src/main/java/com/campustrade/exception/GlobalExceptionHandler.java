
package com.campustrade.exception;
import org.springframework.web.server.ResponseStatusException;
import com.campustrade.common.ApiResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex) {

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }
    @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
                ResponseStatusException ex) {

        int status = ex.getStatusCode().value();

        String message = ex.getReason();

        if (message == null || message.isBlank()) {
                message = "请求处理失败";
        }

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.error(status, message));
        }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, message));
    }

    @ExceptionHandler({
            ProductStatusException.class,
            ProductConflictException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleProductConflict(
            RuntimeException ex) {

        return ApiResponse.error(409, ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleProductNotFound(
            ProductNotFoundException ex) {

        return ApiResponse.error(404, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(
            NoResourceFoundException ex) {

        return ApiResponse.error(404, "请求的资源不存在");
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException ex) {

        return ApiResponse.error(409, ex.getMessage());
    }

    @ExceptionHandler(VerificationCodeLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerificationLimit(
            VerificationCodeLimitException ex) {

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(
                        429,
                        "验证码请求暂时受限，请稍后重试"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
    @ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ApiResponse<Void>> handleAuthentication(
        AuthenticationException ex) {

    return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(
                    401,
                    "邮箱或密码错误，或账户不可登录"
            ));
}
}
