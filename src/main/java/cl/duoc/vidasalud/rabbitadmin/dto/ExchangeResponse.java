package cl.duoc.vidasalud.rabbitadmin.dto;

/**
 * Respuesta con los datos de un exchange registrado en el vhost.
 */
public record ExchangeResponse(
        String nombre,
        String tipo,
        boolean durable,
        boolean protegido
) {
}