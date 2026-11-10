package com.mytoday.identity;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
@Component
public class LoginLimiter {
 private final Clock clock;
 // shortcut: limits are process-local; use a shared store before running multiple API instances.
 private final Map<String,Window> attempts=new HashMap<>();
 private record Window(Instant start,int count) {}
 public LoginLimiter(Clock clock){this.clock=clock;}
 public synchronized void check(String key){
  Instant now=clock.instant();
  attempts.entrySet().removeIf(e->e.getValue().start().plusSeconds(900).isBefore(now));
  Window w=attempts.get(key);
  if((w!=null&&w.count()>=10)||(w==null&&attempts.size()>=10000)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"시도 횟수가 많습니다. 잠시 후 다시 시도해 주세요.");
  attempts.put(key,new Window(w==null?now:w.start(),w==null?1:w.count()+1));
 }
}
