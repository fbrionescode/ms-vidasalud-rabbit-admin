package cl.duoc.vidasalud.rabbitadmin.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades centralizadas de la topologia de RabbitMQ.
 * Se externalizan en configuracion para garantizar consistencia entre
 * microservicios y proteger los recursos criticos del sistema frente a borrados.
 */
@ConfigurationProperties("vidasalud.rabbit")
public record NombresRabbit(
        String exchangeTopic,
        String exchangeDirect,
        String exchangeDlx,
        String colaNotificaciones,
        String colaTickets,
        String colaAuditoria
) {

    /**
     * Devuelve las seis colas estructurales del sistema (tres de trabajo y sus tres DLQ)
     * que no pueden ser eliminadas ni purgadas mediante la API de administracion.
     */
    public Set<String> colasProtegidas() {
        return Set.of(
                colaNotificaciones,
                colaTickets,
                colaAuditoria,
                colaNotificaciones + ".dlq",
                colaTickets + ".dlq",
                colaAuditoria + ".dlq"
        );
    }

    /**
     * Devuelve los tres exchanges estructurales del sistema que no pueden ser eliminados.
     */
    public Set<String> exchangesProtegidos() {
        return Set.of(
                exchangeTopic,
                exchangeDirect,
                exchangeDlx
        );
    }
}