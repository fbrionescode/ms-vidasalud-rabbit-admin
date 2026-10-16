package cl.duoc.vidasalud.rabbitadmin.dto;

/**
 * Respuesta con las metricas principales de una cola y su estado de proteccion.
 */
public record ColaResponse(
        String nombre,
        long mensajes,
        int consumidores,
        boolean protegida
) {
}