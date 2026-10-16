package cl.duoc.vidasalud.rabbitadmin.controller;

import cl.duoc.vidasalud.rabbitadmin.dto.CrearExchangeRequest;
import cl.duoc.vidasalud.rabbitadmin.dto.ExchangeResponse;
import cl.duoc.vidasalud.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para administrar exchanges en RabbitMQ.
 */
@RestController
@RequestMapping("/api/rabbit/exchanges")
public class ExchangeController {

    private final RabbitAdminService servicio;

    public ExchangeController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<ExchangeResponse>> listarExchanges() {
        return ResponseEntity.ok(servicio.listarExchanges());
    }

    @PostMapping
    public ResponseEntity<ExchangeResponse> crearExchange(@Valid @RequestBody CrearExchangeRequest solicitud) {
        ExchangeResponse creado = servicio.crearExchange(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String nombre) {
        servicio.eliminarExchange(nombre);
        return ResponseEntity.noContent().build();
    }
}