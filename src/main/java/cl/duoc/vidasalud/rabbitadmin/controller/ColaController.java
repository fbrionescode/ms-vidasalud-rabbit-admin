package cl.duoc.vidasalud.rabbitadmin.controller;

import cl.duoc.vidasalud.rabbitadmin.dto.ColaResponse;
import cl.duoc.vidasalud.rabbitadmin.dto.CrearColaRequest;
import cl.duoc.vidasalud.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Controlador REST para administrar colas de RabbitMQ.
 * Delega integramente la logica en RabbitAdminService sin conocer detalles de AMQP.
 */
@RestController
@RequestMapping("/api/rabbit/colas")
public class ColaController {

    private final RabbitAdminService servicio;

    public ColaController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<ColaResponse>> listarColas() {
        return ResponseEntity.ok(servicio.listarColas());
    }

    @GetMapping("/{nombre:.+}")
    public ResponseEntity<ColaResponse> obtenerCola(@PathVariable String nombre) {
        return ResponseEntity.ok(servicio.obtenerCola(nombre));
    }

    @PostMapping
    public ResponseEntity<ColaResponse> crearCola(@Valid @RequestBody CrearColaRequest solicitud) {
        ColaResponse creada = servicio.crearCola(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @DeleteMapping("/{nombre:.+}/mensajes")
    public ResponseEntity<Map<String, Object>> purgarCola(@PathVariable String nombre) {
        int cantidadPurgada = servicio.purgarCola(nombre);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("cola", nombre);
        respuesta.put("mensajesPurgados", cantidadPurgada);
        return ResponseEntity.ok(respuesta);
    }

    @DeleteMapping("/{nombre:.+}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String nombre) {
        servicio.eliminarCola(nombre);
        return ResponseEntity.noContent().build();
    }
}