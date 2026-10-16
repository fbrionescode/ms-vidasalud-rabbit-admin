package cl.duoc.vidasalud.rabbitadmin.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

/**
 * Datos de entrada para crear un exchange.
 * El tipo se valida contra un enum cerrado para impedir configuraciones invalidas
 * antes de invocar al broker.
 */
public record CrearExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{3,255}$",
                message = "El nombre solo puede contener letras, numeros, puntos, guiones y guion bajo (entre 3 y 255 caracteres)"
        )
        String nombre,
        @NotNull(message = "El tipo de exchange es obligatorio y debe ser direct, topic o fanout")
        TipoExchange tipo,
        Boolean durable
) {

    /**
     * Tipos de exchange permitidos por la plataforma.
     */
    public enum TipoExchange {
        direct,
        topic,
        fanout;

        @JsonCreator
        public static TipoExchange desdeTexto(String valor) {
            if (valor == null || valor.isBlank()) {
                return null;
            }
            return TipoExchange.valueOf(valor.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Indica si el exchange debe ser durable (true por defecto).
     */
    public boolean esDurable() {
        return durable == null || durable;
    }
}