package cl.duoc.vidasalud.rabbitadmin.service;

import cl.duoc.vidasalud.rabbitadmin.config.NombresRabbit;
import cl.duoc.vidasalud.rabbitadmin.dto.BindingRequest;
import cl.duoc.vidasalud.rabbitadmin.dto.ColaResponse;
import cl.duoc.vidasalud.rabbitadmin.dto.CrearColaRequest;
import cl.duoc.vidasalud.rabbitadmin.dto.CrearExchangeRequest;
import cl.duoc.vidasalud.rabbitadmin.dto.ExchangeResponse;
import cl.duoc.vidasalud.rabbitadmin.exception.OperacionNoPermitidaException;
import cl.duoc.vidasalud.rabbitadmin.exception.RecursoNoEncontradoException;
import cl.duoc.vidasalud.rabbitadmin.exception.RecursoYaExisteException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Servicio que concentra toda la logica de administracion de RabbitMQ.
 * Los controladores dependen unicamente de esta clase para mantener
 * desacoplada la capa web de las librerias AMQP y HTTP del broker.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitAdmin rabbitAdmin;
    private final RestClient clienteHttp;
    private final NombresRabbit nombres;
    private final String managementUrl;
    private final String vhost;

    public RabbitAdminService(
            RabbitAdmin rabbitAdmin,
            RestClient clienteHttp,
            NombresRabbit nombres,
            @Value("${vidasalud.rabbit.management-url:http://localhost:15672}") String managementUrl,
            @Value("${vidasalud.rabbit.vhost:/}") String vhost) {
        this.rabbitAdmin = rabbitAdmin;
        this.clienteHttp = clienteHttp;
        this.nombres = nombres;
        this.managementUrl = managementUrl;
        this.vhost = vhost;
    }

    /**
     * Lista todas las colas registradas en el vhost consultando la API HTTP de administracion,
     * ya que el protocolo AMQP no ofrece una operacion de listado global.
     */
    public List<ColaResponse> listarColas() {
        URI uri = construirUri("/api/queues/" + codificarSegmento(vhost));
        List<Map<String, Object>> respuesta = clienteHttp.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        if (respuesta == null) {
            return Collections.emptyList();
        }

        return respuesta.stream()
                .map(item -> {
                    String nombre = String.valueOf(item.getOrDefault("name", ""));
                    int mensajes = extraerEntero(item.get("messages"));
                    int consumidores = extraerEntero(item.get("consumers"));
                    return new ColaResponse(nombre, mensajes, consumidores, esColaProtegida(nombre));
                })
                .toList();
    }

    /**
     * Obtiene el detalle de una cola puntual usando AMQP (QueueInformation).
     */
    public ColaResponse obtenerCola(String nombre) {
        QueueInformation info = rabbitAdmin.getQueueInfo(nombre);
        if (info == null) {
            throw new RecursoNoEncontradoException("La cola no existe: " + nombre);
        }
        return new ColaResponse(
                nombre,
                info.getMessageCount(),
                info.getConsumerCount(),
                esColaProtegida(nombre)
        );
    }

    /**
     * Crea una cola nueva en el broker. Si se solicita conDlq, aprovisiona tambien
     * la cola dead-letter y su binding contra el exchange DLX del sistema.
     */
    public ColaResponse crearCola(CrearColaRequest solicitud) {
        if (solicitud == null || solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la cola es obligatorio");
        }

        String nombre = solicitud.nombre().trim();
        if (rabbitAdmin.getQueueInfo(nombre) != null) {
            throw new RecursoYaExisteException("La cola ya existe: " + nombre);
        }

        if (solicitud.requiereDlq()) {
            String nombreDlq = nombre + ".dlq";
            String dlx = nombres.exchangeDlx() != null ? nombres.exchangeDlx() : "cmd.dead.dlx";

            Queue colaDlq = QueueBuilder.durable(nombreDlq).build();
            rabbitAdmin.declareQueue(colaDlq);

            Binding bindingDlq = new Binding(
                    nombreDlq,
                    Binding.DestinationType.QUEUE,
                    dlx,
                    nombreDlq,
                    Collections.emptyMap()
            );
            rabbitAdmin.declareBinding(bindingDlq);

            QueueBuilder constructor = solicitud.esDurable()
                    ? QueueBuilder.durable(nombre)
                    : QueueBuilder.nonDurable(nombre);
            Queue colaPrincipal = constructor
                    .deadLetterExchange(dlx)
                    .deadLetterRoutingKey(nombreDlq)
                    .build();
            rabbitAdmin.declareQueue(colaPrincipal);
        } else {
            Queue colaPrincipal = solicitud.esDurable()
                    ? QueueBuilder.durable(nombre).build()
                    : QueueBuilder.nonDurable(nombre).build();
            rabbitAdmin.declareQueue(colaPrincipal);
        }

        if (solicitud.tieneBindingInicial()) {
            Binding bindingInicial = new Binding(
                    nombre,
                    Binding.DestinationType.QUEUE,
                    solicitud.exchange().trim(),
                    solicitud.routingKey().trim(),
                    Collections.emptyMap()
            );
            rabbitAdmin.declareBinding(bindingInicial);
        }

        log.info("admin | cola creada | {} | dlq: {}", nombre, solicitud.requiereDlq() ? "si" : "no");
        return new ColaResponse(nombre, 0, 0, esColaProtegida(nombre));
    }

    /**
     * Purga todos los mensajes pendientes de una cola siempre que no sea protegida.
     */
    public int purgarCola(String nombre) {
        if (esColaProtegida(nombre)) {
            log.warn("admin | intento de purgar cola protegida | {}", nombre);
            throw new OperacionNoPermitidaException("No se permite purgar la cola protegida: " + nombre);
        }
        if (rabbitAdmin.getQueueInfo(nombre) == null) {
            throw new RecursoNoEncontradoException("La cola no existe: " + nombre);
        }

        int purgados = rabbitAdmin.purgeQueue(nombre);
        log.info("admin | cola purgada | {} | mensajes eliminados: {}", nombre, purgados);
        return purgados;
    }

    /**
     * Elimina una cola del broker verificando primero que no sea parte de las colas protegidas.
     */
    public void eliminarCola(String nombre) {
        if (esColaProtegida(nombre)) {
            log.warn("admin | intento de eliminar cola protegida | {}", nombre);
            throw new OperacionNoPermitidaException("No se permite eliminar la cola protegida: " + nombre);
        }
        if (rabbitAdmin.getQueueInfo(nombre) == null) {
            throw new RecursoNoEncontradoException("La cola no existe: " + nombre);
        }

        rabbitAdmin.deleteQueue(nombre);
        log.info("admin | cola eliminada | {}", nombre);
    }

    /**
     * Lista los exchanges del vhost a traves de la API HTTP de administracion.
     */
    public List<ExchangeResponse> listarExchanges() {
        URI uri = construirUri("/api/exchanges/" + codificarSegmento(vhost));
        List<Map<String, Object>> respuesta = clienteHttp.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        if (respuesta == null) {
            return Collections.emptyList();
        }

        return respuesta.stream()
                .map(item -> {
                    String nombre = String.valueOf(item.getOrDefault("name", ""));
                    String tipo = String.valueOf(item.getOrDefault("type", ""));
                    boolean durable = Boolean.TRUE.equals(item.get("durable"));
                    return new ExchangeResponse(
                            nombre.isEmpty() ? "(default)" : nombre,
                            tipo,
                            durable,
                            esExchangeProtegido(nombre)
                    );
                })
                .toList();
    }

    /**
     * Crea un exchange de tipo direct, topic o fanout verificando que no exista previamente.
     */
    public ExchangeResponse crearExchange(CrearExchangeRequest solicitud) {
        if (solicitud == null || solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del exchange es obligatorio");
        }
        if (solicitud.tipo() == null) {
            throw new IllegalArgumentException("El tipo de exchange es obligatorio (direct, topic, fanout)");
        }

        String nombre = solicitud.nombre().trim();
        if (esExchangeProtegido(nombre) || existeExchangeEnBroker(nombre)) {
            throw new RecursoYaExisteException("El exchange ya existe: " + nombre);
        }

        boolean durable = solicitud.esDurable();
        Exchange nuevoExchange = switch (solicitud.tipo()) {
            case direct -> new DirectExchange(nombre, durable, false);
            case topic -> new TopicExchange(nombre, durable, false);
            case fanout -> new FanoutExchange(nombre, durable, false);
        };

        rabbitAdmin.declareExchange(nuevoExchange);
        log.info("admin | exchange creado | {} | tipo: {}", nombre, solicitud.tipo().name());
        return new ExchangeResponse(nombre, solicitud.tipo().name(), durable, false);
    }

    /**
     * Elimina un exchange siempre que no pertenezca a los exchanges protegidos del sistema.
     */
    public void eliminarExchange(String nombre) {
        if (esExchangeProtegido(nombre)) {
            log.warn("admin | intento de eliminar exchange protegido | {}", nombre);
            throw new OperacionNoPermitidaException("No se permite eliminar el exchange protegido: " + nombre);
        }
        if (!existeExchangeEnBroker(nombre)) {
            throw new RecursoNoEncontradoException("El exchange no existe: " + nombre);
        }

        rabbitAdmin.deleteExchange(nombre);
        log.info("admin | exchange eliminado | {}", nombre);
    }

    /**
     * Lista los bindings activos en el vhost mediante la API HTTP de administracion.
     */
    public List<Map<String, Object>> listarBindings() {
        URI uri = construirUri("/api/bindings/" + codificarSegmento(vhost));
        List<Map<String, Object>> respuesta = clienteHttp.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        if (respuesta == null) {
            return Collections.emptyList();
        }

        return respuesta.stream()
                .map(item -> {
                    Map<String, Object> limpio = new LinkedHashMap<>();
                    limpio.put("exchange", item.getOrDefault("source", ""));
                    limpio.put("cola", item.getOrDefault("destination", ""));
                    limpio.put("routingKey", item.getOrDefault("routing_key", ""));
                    limpio.put("propertiesKey", item.getOrDefault("properties_key", ""));
                    return limpio;
                })
                .toList();
    }

    /**
     * Declara un binding entre una cola existente y un exchange con la routing key indicada.
     */
    public Map<String, String> crearBinding(BindingRequest solicitud) {
        String cola = solicitud.cola().trim();
        String exchange = solicitud.exchange().trim();
        String routingKey = solicitud.routingKey().trim();

        if (rabbitAdmin.getQueueInfo(cola) == null) {
            throw new RecursoNoEncontradoException("La cola no existe: " + cola);
        }

        Binding binding = new Binding(
                cola,
                Binding.DestinationType.QUEUE,
                exchange,
                routingKey,
                Collections.emptyMap()
        );
        rabbitAdmin.declareBinding(binding);
        log.info("admin | binding creado | {} -> {} | clave: {}", exchange, cola, routingKey);

        Map<String, String> resultado = new LinkedHashMap<>();
        resultado.put("cola", cola);
        resultado.put("exchange", exchange);
        resultado.put("routingKey", routingKey);
        return resultado;
    }

    /**
     * Elimina un binding usando la API HTTP de administracion de RabbitMQ,
     * dado que RabbitAdmin no expone un metodo directo de borrado por clave.
     */
    public void eliminarBinding(BindingRequest solicitud) {
        String cola = solicitud.cola().trim();
        String exchange = solicitud.exchange().trim();
        String routingKey = solicitud.routingKey().trim();
        String propsKey = routingKey.isEmpty() ? "~" : codificarSegmento(routingKey);

        String ruta = String.format(
                "/api/bindings/%s/e/%s/q/%s/%s",
                codificarSegmento(vhost),
                codificarSegmento(exchange),
                codificarSegmento(cola),
                propsKey
        );

        try {
            clienteHttp.delete()
                    .uri(construirUri(ruta))
                    .retrieve()
                    .toBodilessEntity();
            log.info("admin | binding eliminado | {} -> {} | clave: {}", exchange, cola, routingKey);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException(
                    "No existe el binding entre " + exchange + " y " + cola + " con clave " + routingKey
            );
        }
    }

    private boolean existeExchangeEnBroker(String nombre) {
        URI uri = construirUri("/api/exchanges/" + codificarSegmento(vhost) + "/" + codificarSegmento(nombre));
        try {
            Map<String, Object> respuesta = clienteHttp.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return respuesta != null && !respuesta.isEmpty();
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        }
    }

    private boolean esColaProtegida(String nombre) {
        return nombres != null
                && nombres.colasProtegidas() != null
                && nombres.colasProtegidas().contains(nombre);
    }

    private boolean esExchangeProtegido(String nombre) {
        return nombres != null
                && nombres.exchangesProtegidos() != null
                && nombres.exchangesProtegidos().contains(nombre);
    }

    private URI construirUri(String rutaRelativa) {
        String base = managementUrl.endsWith("/")
                ? managementUrl.substring(0, managementUrl.length() - 1)
                : managementUrl;
        return URI.create(base + rutaRelativa);
    }

    private String codificarSegmento(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private int extraerEntero(Object valor) {
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        return 0;
    }
}