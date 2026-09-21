package dev.sameerkhan.eventpulse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class EventPulseApplication {
  public static void main(String[] args) { SpringApplication.run(EventPulseApplication.class, args); }

  public enum Channel { EMAIL, SMS, PUSH }
  public record NotificationRequest(@NotBlank String eventId, @NotBlank String recipient, Channel channel, @NotBlank String message) {}
  public record Delivery(UUID id, String eventId, String recipient, Channel channel, String status, int attempts, Instant updatedAt) {}

  @RestController
  @RequestMapping("/api/notifications")
  static class NotificationController {
    private final DeliveryService service;
    NotificationController(DeliveryService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    Delivery accept(@Valid @RequestBody NotificationRequest request) { return service.accept(request); }
    @PostMapping("/{id}/retry") Delivery retry(@PathVariable UUID id) { return service.retry(id); }
    @GetMapping List<Delivery> list(@RequestParam Optional<String> status) { return service.list(status); }
    @GetMapping("/dead-letter") List<Delivery> deadLetters() { return service.list(Optional.of("DEAD_LETTER")); }
  }

  @org.springframework.stereotype.Service
  static class DeliveryService {
    private final Map<UUID, Delivery> deliveries = new ConcurrentHashMap<>();
    private final Map<String, UUID> processedEvents = new ConcurrentHashMap<>();
    Delivery accept(NotificationRequest request) {
      UUID existing = processedEvents.get(request.eventId());
      if (existing != null) return deliveries.get(existing);
      UUID id = UUID.randomUUID();
      Delivery delivery = new Delivery(id, request.eventId(), request.recipient(),
          request.channel() == null ? Channel.EMAIL : request.channel(), "QUEUED", 0, Instant.now());
      deliveries.put(id, delivery); processedEvents.put(request.eventId(), id); return delivery;
    }
    Delivery retry(UUID id) {
      Delivery current = require(id);
      int attempts = current.attempts() + 1;
      String status = attempts >= 3 ? "DEAD_LETTER" : "QUEUED";
      Delivery updated = new Delivery(current.id(), current.eventId(), current.recipient(), current.channel(), status, attempts, Instant.now());
      deliveries.put(id, updated); return updated;
    }
    List<Delivery> list(Optional<String> status) {
      return deliveries.values().stream().filter(d -> status.map(s -> s.equalsIgnoreCase(d.status())).orElse(true))
          .sorted(Comparator.comparing(Delivery::updatedAt).reversed()).toList();
    }
    Delivery require(UUID id) { return Optional.ofNullable(deliveries.get(id)).orElseThrow(() -> new NoSuchElementException("Delivery not found")); }
  }

  @RestControllerAdvice
  static class Errors {
    @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String,String> missing(RuntimeException ex) { return Map.of("error", ex.getMessage()); }
  }
}
