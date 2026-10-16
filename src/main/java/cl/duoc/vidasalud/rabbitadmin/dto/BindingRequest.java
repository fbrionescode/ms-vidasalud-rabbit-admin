package cl.duoc.vidasalud.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos para enlazar o desenlazar una cola con un exchange mediante una routing key.
 */
public record BindingRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        String cola,
        @NotBlank(message = "El nombre del exchange es obligatorio")
        String exchange,
        @NotBlank(message = "La routingKey es obligatoria")
        String routingKey
) {
}