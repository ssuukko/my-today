package com.mytoday.identity;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException ex){return response(ex.getStatusCode().value(),ex.getReason());}
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
  org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
  org.springframework.web.bind.MissingServletRequestParameterException.class}) ResponseEntity<?> validation(Exception ex){return response(400,"입력 정보를 확인해 주세요.");}
 private ResponseEntity<?> response(int status,String message){
  var builder=ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
  if(status==429)builder.header("Retry-After","900");
  return builder.body(Map.of("type","about:blank","title",HttpStatus.valueOf(status).getReasonPhrase(),"status",status,"code",HttpStatus.valueOf(status).name(),"message",message==null?"요청을 처리할 수 없습니다.":message));
 }
}
