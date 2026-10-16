package cl.duoc.vidasalud.rabbitadmin.exception;

/**
 * Excepcion lanzada cuando se intenta operar sobre una cola o exchange que no existe.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}