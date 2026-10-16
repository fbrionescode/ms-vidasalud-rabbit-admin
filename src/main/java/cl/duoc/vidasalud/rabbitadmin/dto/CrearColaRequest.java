package cl.duoc.vidasalud.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Datos de entrada para crear una cola en caliente.
 * Restringe los caracteres permitidos para impedir nombres vacios o malformados
 * antes de contactar al broker.
 */
public record CrearColaRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{3,255}$",
                message = "El nombre solo puede contener letras, numeros, puntos, guiones y guion bajo (entre 3 y 255 caracteres)"
        )
        String nombre,
        Boolean durable,
        Boolean conDlq,
        String exchange,
        String routingKey
) {

    /**
     * Indica si la cola debe ser durable (true por defecto si no se especifica).
     */
    public boolean esDurable() {
        return durable == null || durable;
    }

    /**
     * Indica si se debe aprovisionar automaticamente su cola DLQ asociada.
     */
    public boolean requiereDlq() {
        return Boolean.TRUE.equals(conDlq);
    }

    /**
     * Indica si se proporcionaron tanto exchange como routingKey para crear el binding inicial.
     */
    public boolean tieneBindingInicial() {
        return exchange != null && !exchange.isBlank()
                && routingKey != null && !routingKey.isBlank();
    }
}