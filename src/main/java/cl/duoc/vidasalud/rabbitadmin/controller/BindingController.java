package cl.duoc.vidasalud.rabbitadmin.controller;

import cl.duoc.vidasalud.rabbitadmin.dto.BindingRequest;
import cl.duoc.vidasalud.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para listar, crear y eliminar bindings entre exchanges y colas.
 */
@RestController
@RequestMapping("/api/rabbit/bindings")
public class BindingController {

    private final RabbitAdminService servicio;

    public BindingController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarBindings() {
        return ResponseEntity.ok(servicio.listarBindings());
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> crearBinding(@Valid @RequestBody BindingRequest solicitud) {
        Map<String, String> creado = servicio.crearBinding(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminarBinding(@Valid @RequestBody BindingRequest solicitud) {
        servicio.eliminarBinding(solicitud);
        return ResponseEntity.noContent().build();
    }
}