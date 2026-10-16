package cl.duoc.vidasalud.rabbitadmin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.vidasalud.rabbitadmin.config.NombresRabbit;
import cl.duoc.vidasalud.rabbitadmin.dto.ColaResponse;
import cl.duoc.vidasalud.rabbitadmin.dto.CrearColaRequest;
import cl.duoc.vidasalud.rabbitadmin.dto.CrearExchangeRequest;
import cl.duoc.vidasalud.rabbitadmin.exception.OperacionNoPermitidaException;
import cl.duoc.vidasalud.rabbitadmin.exception.RecursoNoEncontradoException;
import cl.duoc.vidasalud.rabbitadmin.exception.RecursoYaExisteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.web.client.RestClient;

/**
 * Pruebas unitarias de RabbitAdminService con Mockito sin broker real.
 */
@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    @Mock
    private RabbitAdmin rabbitAdmin;

    @Mock
    private RestClient clienteHttp;

    private RabbitAdminService servicio;

    @BeforeEach
    void configurar() {
        NombresRabbit nombres = new NombresRabbit(
                "cmd.topic",
                "cmd.direct",
                "cmd.dead.dlx",
                "vidasalud.notificaciones",
                "vidasalud.tickets",
                "vidasalud.auditoria"
        );
        servicio = new RabbitAdminService(
                rabbitAdmin,
                clienteHttp,
                nombres,
                "http://localhost:15672",
                "/"
        );
    }

    @Test
    @DisplayName("1. Crear cola nueva llama a declareQueue y devuelve el nombre")
    void crearColaNuevaDeclaraColaYDevuelveNombre() {
        when(rabbitAdmin.getQueueInfo("vidasalud.pruebas")).thenReturn(null);
        CrearColaRequest solicitud = new CrearColaRequest("vidasalud.pruebas", true, false, null, null);

        ColaResponse respuesta = servicio.crearCola(solicitud);

        assertEquals("vidasalud.pruebas", respuesta.nombre());
        verify(rabbitAdmin, times(1)).declareQueue(any(Queue.class));
    }

    @Test
    @DisplayName("2. Crear cola existente lanza RecursoYaExisteException")
    void crearColaExistenteLanzaExcepcion() {
        when(rabbitAdmin.getQueueInfo("vidasalud.pruebas"))
                .thenReturn(new QueueInformation("vidasalud.pruebas", 0, 0));
        CrearColaRequest solicitud = new CrearColaRequest("vidasalud.pruebas", true, false, null, null);

        assertThrows(RecursoYaExisteException.class, () -> servicio.crearCola(solicitud));
        verify(rabbitAdmin, never()).declareQueue(any(Queue.class));
    }

    @Test
    @DisplayName("3. Eliminar cola protegida lanza OperacionNoPermitidaException y no llama a deleteQueue")
    void eliminarColaProtegidaLanzaExcepcion() {
        assertThrows(
                OperacionNoPermitidaException.class,
                () -> servicio.eliminarCola("vidasalud.notificaciones")
        );
        verify(rabbitAdmin, never()).deleteQueue(anyString());
    }

    @Test
    @DisplayName("4. Eliminar cola inexistente lanza RecursoNoEncontradoException")
    void eliminarColaInexistenteLanzaExcepcion() {
        when(rabbitAdmin.getQueueInfo("vidasalud.inexistente")).thenReturn(null);

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.eliminarCola("vidasalud.inexistente")
        );
        verify(rabbitAdmin, never()).deleteQueue(anyString());
    }

    @Test
    @DisplayName("5. Crear cola con conDlq = true declara dos colas y un binding")
    void crearColaConDlqDeclaraDosColasYUnBinding() {
        when(rabbitAdmin.getQueueInfo("vidasalud.pruebas")).thenReturn(null);
        CrearColaRequest solicitud = new CrearColaRequest("vidasalud.pruebas", true, true, null, null);

        servicio.crearCola(solicitud);

        verify(rabbitAdmin, times(2)).declareQueue(any(Queue.class));
        verify(rabbitAdmin, times(1)).declareBinding(any(Binding.class));
    }

    @Test
    @DisplayName("6. Crear exchange con tipo invalido falla antes de tocar el broker")
    void crearExchangeConTipoInvalidoFallaSinTocarElBroker() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CrearExchangeRequest.TipoExchange.desdeTexto("headers")
        );

        CrearExchangeRequest solicitudSinTipo = new CrearExchangeRequest("cmd.invalido", null, true);
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.crearExchange(solicitudSinTipo)
        );
        verify(rabbitAdmin, never()).declareExchange(any(Exchange.class));
    }
}