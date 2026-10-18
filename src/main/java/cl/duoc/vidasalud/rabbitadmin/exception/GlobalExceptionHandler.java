package cl.duoc.vidasalud.rabbitadmin.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Manejador centralizado de errores HTTP.
 * Garantiza respuestas claras con codigos semanticos (400, 403, 404, 409, 502)
 * sin exponer trazas internas del servidor al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return construirRespuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RecursoYaExisteException.class)
    public ResponseEntity<Map<String, Object>> manejarYaExiste(RecursoYaExisteException ex) {
        return construirRespuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<Map<String, Object>> manejarNoPermitida(OperacionNoPermitidaException ex) {
        return construirRespuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> manejarArgumentoInvalido(IllegalArgumentException ex) {
        return construirRespuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errores = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapearErrorCampo)
                .toList();

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("estado", HttpStatus.BAD_REQUEST.value());
        cuerpo.put("mensaje", "Parametros de entrada invalidos");
        cuerpo.put("errores", errores);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarJsonInvalido(HttpMessageNotReadableException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("estado", HttpStatus.BAD_REQUEST.value());
        cuerpo.put("campo", "tipo");
        cuerpo.put("mensaje", "Cuerpo JSON invalido o tipo de exchange no soportado (valores permitidos: direct, topic, fanout)");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    /**
     * Red de seguridad para fallas del broker por AMQP: nodo caido, canal cerrado,
     * credenciales rechazadas. No es culpa del cliente, por eso 502 y no 500:
     * este servicio funciona, el que fallo es RabbitMQ, que esta detras.
     * El detalle tecnico queda en el log, no en la respuesta.
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorAmqp(AmqpException ex) {
        log.error("admin | error del broker por AMQP | {}", ex.getMessage());
        return construirRespuesta(HttpStatus.BAD_GATEWAY,
                "No fue posible completar la operacion en RabbitMQ. Intente nuevamente en unos momentos.");
    }

    /**
     * Mismo criterio para la API HTTP de administracion (puerto 15672), que se usa
     * para listar y para eliminar bindings. Los 404 esperados se traducen dentro
     * del servicio; aqui solo llegan fallas reales de comunicacion.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorApiAdministracion(RestClientException ex) {
        log.error("admin | error de la API de administracion de RabbitMQ | {}", ex.getMessage());
        return construirRespuesta(HttpStatus.BAD_GATEWAY,
                "No fue posible comunicarse con la API de administracion de RabbitMQ. Intente nuevamente en unos momentos.");
    }

    private Map<String, String> mapearErrorCampo(FieldError error) {
        Map<String, String> detalle = new LinkedHashMap<>();
        detalle.put("campo", error.getField());
        detalle.put("mensaje", error.getDefaultMessage());
        return detalle;
    }

    private ResponseEntity<Map<String, Object>> construirRespuesta(HttpStatus estado, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("estado", estado.value());
        cuerpo.put("mensaje", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}