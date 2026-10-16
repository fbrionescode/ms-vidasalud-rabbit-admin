package cl.duoc.vidasalud.rabbitadmin.exception;

/**
 * Excepcion lanzada cuando se intenta crear una cola o exchange cuyo nombre ya esta registrado.
 */
public class RecursoYaExisteException extends RuntimeException {

    public RecursoYaExisteException(String mensaje) {
        super(mensaje);
    }
}