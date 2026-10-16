package cl.duoc.vidasalud.rabbitadmin.exception;

/**
 * Excepcion lanzada cuando se intenta eliminar o purgar un recurso protegido de la topologia base.
 */
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}